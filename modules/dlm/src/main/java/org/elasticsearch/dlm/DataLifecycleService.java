/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.dlm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ResultDeduplicator;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeRequest;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeResponse;
import org.elasticsearch.action.admin.indices.rollover.RolloverConfiguration;
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest;
import org.elasticsearch.action.admin.indices.rollover.RolloverResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.ClusterStateTaskListener;
import org.elasticsearch.cluster.SimpleBatchedExecutor;
import org.elasticsearch.cluster.metadata.DataLifecycle;
import org.elasticsearch.cluster.metadata.DataStream;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.cluster.service.MasterServiceTaskQueue;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.scheduler.SchedulerEngine;
import org.elasticsearch.common.scheduler.TimeValueSchedule;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.Strings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.gateway.GatewayService;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.snapshots.SnapshotInProgressException;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportRequest;

import java.io.Closeable;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * This service will implement the needed actions (e.g. rollover, retention) to manage the data streams with a DLM lifecycle configured.
 * It runs on the master node and it schedules a job according to the configured {@link DataLifecycleService#DLM_POLL_INTERVAL_SETTING}.
 */
public class DataLifecycleService implements ClusterStateListener, Closeable, SchedulerEngine.Listener {

    public static final String DLM_POLL_INTERVAL = "indices.dlm.poll_interval";
    public static final Setting<TimeValue> DLM_POLL_INTERVAL_SETTING = Setting.timeSetting(
        DLM_POLL_INTERVAL,
        TimeValue.timeValueMinutes(10),
        TimeValue.timeValueSeconds(1),
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );
    private static final Logger logger = LogManager.getLogger(DataLifecycleService.class);
    /**
     * Name constant for the job DLM schedules
     */
    private static final String DATA_LIFECYCLE_JOB_NAME = "dlm";
    private static final String DLM_CUSTOM_INDEX_METADATA_KEY = "dlm";
    private static final String FORCE_MERGE_METADATA_KEY = "force_merge";
    private static final String FORCE_MERGE_BEGIN_MARKER = "begin";
    private static final String FORCE_MERGE_COMPLETE_MARKER = "complete";

    private final Settings settings;
    private final Client client;
    private final ClusterService clusterService;
    private final ResultDeduplicator<TransportRequest, Void> transportActionsDeduplicator;
    private final LongSupplier nowSupplier;
    private final Clock clock;
    private final DataLifecycleErrorStore errorStore;
    private volatile boolean isMaster = false;
    private volatile TimeValue pollInterval;
    private volatile RolloverConfiguration rolloverConfiguration;
    private SchedulerEngine.Job scheduledJob;
    private final SetOnce<SchedulerEngine> scheduler = new SetOnce<>();
    private final MasterServiceTaskQueue<ForceMergeClusterStateUpdateTask> taskQueue;

    private static final SimpleBatchedExecutor<ForceMergeClusterStateUpdateTask, Void> STATE_UPDATE_TASK_EXECUTOR =
        new SimpleBatchedExecutor<>() {
            @Override
            public Tuple<ClusterState, Void> executeTask(ForceMergeClusterStateUpdateTask task, ClusterState clusterState)
                throws Exception {
                return Tuple.tuple(task.execute(clusterState), null);
            }

            @Override
            public void taskSucceeded(ForceMergeClusterStateUpdateTask task, Void unused) {
                logger.trace("Updated cluster state for force merge");
                task.listener.onResponse(null);
            }
        };

    public DataLifecycleService(
        Settings settings,
        Client client,
        ClusterService clusterService,
        Clock clock,
        ThreadPool threadPool,
        LongSupplier nowSupplier,
        DataLifecycleErrorStore errorStore
    ) {
        this.settings = settings;
        this.client = client;
        this.clusterService = clusterService;
        this.clock = clock;
        this.transportActionsDeduplicator = new ResultDeduplicator<>(threadPool.getThreadContext());
        this.nowSupplier = nowSupplier;
        this.errorStore = errorStore;
        this.scheduledJob = null;
        this.pollInterval = DLM_POLL_INTERVAL_SETTING.get(settings);
        this.rolloverConfiguration = clusterService.getClusterSettings().get(DataLifecycle.CLUSTER_DLM_DEFAULT_ROLLOVER_SETTING);
        this.taskQueue = clusterService.createTaskQueue("forcemerge-clusterstate", Priority.NORMAL, STATE_UPDATE_TASK_EXECUTOR);
    }

    /**
     * Initializer method to avoid the publication of a self reference in the constructor.
     */
    public void init() {
        clusterService.addListener(this);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(DLM_POLL_INTERVAL_SETTING, this::updatePollInterval);
        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(DataLifecycle.CLUSTER_DLM_DEFAULT_ROLLOVER_SETTING, this::updateRolloverConfiguration);
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        // wait for the cluster state to be recovered
        if (event.state().blocks().hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)) {
            return;
        }

        final boolean prevIsMaster = this.isMaster;
        if (prevIsMaster != event.localNodeMaster()) {
            this.isMaster = event.localNodeMaster();
            if (this.isMaster) {
                // we weren't the master, and now we are
                maybeScheduleJob();
            } else {
                // we were the master, and now we aren't
                cancelJob();
                // clear the deduplicator on master failover so we could re-send the requests in case we're re-elected
                transportActionsDeduplicator.clear();
                errorStore.clearStore();
            }
        }
    }

    @Override
    public void close() {
        SchedulerEngine engine = scheduler.get();
        if (engine != null) {
            engine.stop();
        }
        errorStore.clearStore();
    }

    @Override
    public void triggered(SchedulerEngine.Event event) {
        if (event.getJobName().equals(DATA_LIFECYCLE_JOB_NAME)) {
            if (this.isMaster) {
                logger.trace("DLM job triggered: {}, {}, {}", event.getJobName(), event.getScheduledTime(), event.getTriggeredTime());
                run(clusterService.state());
            }
        }
    }

    /**
     * Iterates over the DLM managed data streams and executes the needed operations
     * to satisfy the configured {@link org.elasticsearch.cluster.metadata.DataLifecycle}.
     */
    // default visibility for testing purposes
    void run(ClusterState state) {
        for (DataStream dataStream : state.metadata().dataStreams().values()) {
            clearErrorStoreForUnmanagedIndices(dataStream);
            if (dataStream.getLifecycle() == null) {
                continue;
            }

            Index originalWriteIndex = dataStream.getWriteIndex();
            String writeIndexName = originalWriteIndex.getName();
            try {
                maybeExecuteRollover(state, dataStream);
            } catch (Exception e) {
                logger.error(() -> String.format(Locale.ROOT, "DLM failed to rollver data stream [%s]", dataStream.getName()), e);
                DataStream latestDataStream = clusterService.state().metadata().dataStreams().get(dataStream.getName());
                if (latestDataStream != null) {
                    if (latestDataStream.getWriteIndex().getName().equals(writeIndexName)) {
                        // data stream has not been rolled over in the meantime so record the error against the write index we
                        // attempted the rollover
                        errorStore.recordError(writeIndexName, e);
                    }
                }
            }
            Set<Index> indicesBeingRemoved;
            try {
                indicesBeingRemoved = maybeExecuteRetention(state, dataStream);
            } catch (Exception e) {
                indicesBeingRemoved = Set.of();
                // individual index errors would be reported via the API action listener for every delete call
                // we could potentially record errors at a data stream level and expose it via the _data_stream API?
                logger.error(
                    () -> String.format(Locale.ROOT, "DLM failed to execute retention for data stream [%s]", dataStream.getName()),
                    e
                );
            }

            try {
                /*
                 * When considering indices for force merge, we want to exclude several indices: (1) We exclude the current write index
                 * because obviously it is still likely to get writes, (2) we exclude the most recent previous write index because since
                 * we just switched over it might still be getting some writes, and (3) we exclude any indices that we're in the process
                 * of deleting because they'll be gone soon anyway.
                 */
                Set<Index> indicesToExclude = new HashSet<>();
                Index currentWriteIndex = dataStream.getWriteIndex();
                indicesToExclude.add(currentWriteIndex);
                indicesToExclude.add(originalWriteIndex);
                indicesToExclude.addAll(indicesBeingRemoved);
                maybeExecuteForceMerge(state, dataStream, indicesToExclude);
            } catch (Exception e) {
                logger.error(
                    () -> String.format(Locale.ROOT, "DLM failed to execute force merge for data stream [%s]", dataStream.getName()),
                    e
                );
            }
        }
    }

    /**
     * This clears the error store for the case where a data stream or some backing indices were managed by DLM, failed in their
     * lifecycle execution, and then they were not managed by DLM (maybe they were switched to ILM).
     */
    private void clearErrorStoreForUnmanagedIndices(DataStream dataStream) {
        Metadata metadata = clusterService.state().metadata();
        for (String indexName : errorStore.getAllIndices()) {
            IndexMetadata indexMeta = metadata.index(indexName);
            if (indexMeta == null) {
                errorStore.clearRecordedError(indexName);
            } else if (dataStream.isIndexManagedByDLM(indexMeta.getIndex(), metadata::index) == false) {
                errorStore.clearRecordedError(indexName);
            }
        }
    }

    private void maybeExecuteRollover(ClusterState state, DataStream dataStream) {
        Index writeIndex = dataStream.getWriteIndex();
        if (dataStream.isIndexManagedByDLM(writeIndex, state.metadata()::index)) {
            RolloverRequest rolloverRequest = getDefaultRolloverRequest(
                rolloverConfiguration,
                dataStream.getName(),
                dataStream.getLifecycle().getDataRetention()
            );
            transportActionsDeduplicator.executeOnce(
                rolloverRequest,
                new ErrorRecordingActionListener(writeIndex.getName(), errorStore),
                (req, reqListener) -> rolloverDataStream(writeIndex.getName(), rolloverRequest, reqListener)
            );
        }
    }

    /**
     * This method sends requests to delete any indices in the datastream that exceed its retention policy. It returns the set of indices
     * it has sent delete requests for.
     * @param state The cluster state from which to get index metadata
     * @param dataStream The datastream
     * @return The set of indices that delete requests have been sent for
     */
    private Set<Index> maybeExecuteRetention(ClusterState state, DataStream dataStream) {
        TimeValue retention = getRetentionConfiguration(dataStream);
        Set<Index> indicesToBeRemoved = new HashSet<>();
        if (retention != null) {
            Metadata metadata = state.metadata();
            List<Index> backingIndicesOlderThanRetention = dataStream.getIndicesPastRetention(metadata::index, nowSupplier);

            for (Index index : backingIndicesOlderThanRetention) {
                indicesToBeRemoved.add(index);
                IndexMetadata backingIndex = metadata.index(index);
                assert backingIndex != null : "the data stream backing indices must exist";

                // there's an opportunity here to batch the delete requests (i.e. delete 100 indices / request)
                // let's start simple and reevaluate
                String indexName = backingIndex.getIndex().getName();
                DeleteIndexRequest deleteRequest = new DeleteIndexRequest(indexName).masterNodeTimeout(TimeValue.MAX_VALUE);

                // time to delete the index
                transportActionsDeduplicator.executeOnce(
                    deleteRequest,
                    new ErrorRecordingActionListener(indexName, errorStore),
                    (req, reqListener) -> deleteIndex(deleteRequest, retention, reqListener)
                );
            }
        }
        return indicesToBeRemoved;
    }

    /*
     * This method force merges any indices in the datastream that are not in the indicesToExclude set and that have not be force merged
     * previously. Before force merging, it writes custom metadata as a marker in the cluster state so that we know a force merge is in
     * process. And it writes another marker in the cluster state upon completion of the force merge.
     */
    private void maybeExecuteForceMerge(ClusterState state, DataStream dataStream, Set<Index> indicesToExclude) {
        List<Index> readOnlyIndices = dataStream.getIndices().stream().filter(index -> indicesToExclude.contains(index) == false).toList();
        Metadata metadata = state.metadata();
        for (Index index : readOnlyIndices) {
            if (dataStream.isIndexManagedByDLM(index, state.metadata()::index)) {
                IndexMetadata backingIndex = metadata.index(index);
                assert backingIndex != null : "the data stream backing indices must exist";
                String indexName = index.getName();
                Map<String, String> customMetadata = backingIndex.getCustomData(DLM_CUSTOM_INDEX_METADATA_KEY);
                boolean alreadyForceMerged = customMetadata != null && customMetadata.containsKey(FORCE_MERGE_METADATA_KEY);
                logger.trace("Already force merged {}: {}", indexName, alreadyForceMerged);
                if (alreadyForceMerged == false) {
                    ForceMergeRequest forceMergeRequest = new ForceMergeRequest(indexName);
                    // time to force merge the index
                    transportActionsDeduplicator.executeOnce(
                        forceMergeRequest,
                        new ErrorRecordingActionListener(indexName, errorStore),
                        (req, reqListener) -> forceMergeIndex(forceMergeRequest, reqListener)
                    );
                }
            }
        }
    }

    private void rolloverDataStream(String writeIndexName, RolloverRequest rolloverRequest, ActionListener<Void> listener) {
        // "saving" the rollover target name here so we don't capture the entire request
        String rolloverTarget = rolloverRequest.getRolloverTarget();
        logger.trace("DLM issues rollover request for data stream [{}]", rolloverTarget);
        client.admin().indices().rolloverIndex(rolloverRequest, new ActionListener<>() {
            @Override
            public void onResponse(RolloverResponse rolloverResponse) {
                // Log only when the conditions were met and the index was rolled over.
                if (rolloverResponse.isRolledOver()) {
                    List<String> metConditions = rolloverResponse.getConditionStatus()
                        .entrySet()
                        .stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .toList();
                    logger.info(
                        "DLM successfully rolled over datastream [{}] due to the following met rollover conditions {}. The new index is "
                            + "[{}]",
                        rolloverTarget,
                        metConditions,
                        rolloverResponse.getNewIndex()
                    );
                }
                listener.onResponse(null);
            }

            @Override
            public void onFailure(Exception e) {
                logger.error(() -> Strings.format("DLM encountered an error trying to rollover data steam [%s]", rolloverTarget), e);
                DataStream dataStream = clusterService.state().metadata().dataStreams().get(rolloverTarget);
                if (dataStream == null || dataStream.getWriteIndex().getName().equals(writeIndexName) == false) {
                    // the data stream has another write index so no point in recording an error for the previous write index we were
                    // attempting to rollover
                    // if there are persistent issues with rolling over this data stream, the next DLM run will attempt to rollover the
                    // _current_ write index and the error problem should surface then
                    listener.onResponse(null);
                } else {
                    // the data stream has NOT been rolled over since we issued our rollover request, so let's record the
                    // error against the data stream's write index.
                    listener.onFailure(e);
                }
            }
        });
    }

    private void deleteIndex(DeleteIndexRequest deleteIndexRequest, TimeValue retention, ActionListener<Void> listener) {
        assert deleteIndexRequest.indices() != null && deleteIndexRequest.indices().length == 1 : "DLM deletes one index at a time";
        // "saving" the index name here so we don't capture the entire request
        String targetIndex = deleteIndexRequest.indices()[0];
        logger.trace("DLM issues request to delete index [{}]", targetIndex);
        client.admin().indices().delete(deleteIndexRequest, new ActionListener<>() {
            @Override
            public void onResponse(AcknowledgedResponse acknowledgedResponse) {
                logger.info("DLM successfully deleted index [{}] due to the lapsed [{}] retention period", targetIndex, retention);
                listener.onResponse(null);
            }

            @Override
            public void onFailure(Exception e) {
                if (e instanceof IndexNotFoundException) {
                    // index was already deleted, treat this as a success
                    errorStore.clearRecordedError(targetIndex);
                    listener.onResponse(null);
                    return;
                }

                if (e instanceof SnapshotInProgressException) {
                    logger.info(
                        "DLM was unable to delete index [{}] because it's currently being snapshotted. Retrying on the next DLM run",
                        targetIndex
                    );
                } else {
                    logger.error(() -> Strings.format("DLM encountered an error trying to delete index [%s]", targetIndex), e);
                }
                listener.onFailure(e);
            }
        });
    }

    private void forceMergeIndex(ForceMergeRequest forceMergeRequest, ActionListener<Void> listener) {
        assert forceMergeRequest.indices() != null && forceMergeRequest.indices().length == 1 : "DLM force merges one index at a time";
        // "saving" the index name here so we don't capture the entire request
        String targetIndex = forceMergeRequest.indices()[0];
        ActionListener<Void> forceMergeRunningListener = new ActionListener<>() {
            @Override
            public void onResponse(Void unused) {
                logger.trace("DLM issues request to force merge index [{}]", targetIndex);
                client.admin().indices().forceMerge(forceMergeRequest, new ActionListener<>() {
                    @Override
                    public void onResponse(ForceMergeResponse forceMergeResponse) {
                        logger.info("DLM successfully force merged index [{}]", targetIndex);
                        taskQueue.submitTask(
                            "Adding force merge marker to cluster state for [" + targetIndex + "]",
                            new ForceMergeClusterStateUpdateTask(listener) {
                                @Override
                                public ClusterState execute(ClusterState currentState) {
                                    logger.trace("Updating cluster state with forcemerge complete result for {}", targetIndex);
                                    IndexMetadata indexMetadata = currentState.metadata().index(targetIndex);
                                    Map<String, String> customMetadata = indexMetadata.getCustomData(DLM_CUSTOM_INDEX_METADATA_KEY);
                                    Map<String, String> newCustomMetadata = new HashMap<>();
                                    if (customMetadata != null) {
                                        newCustomMetadata.putAll(customMetadata);
                                    }
                                    newCustomMetadata.put(FORCE_MERGE_METADATA_KEY, FORCE_MERGE_COMPLETE_MARKER);
                                    IndexMetadata updatededIndexMetadata = new IndexMetadata.Builder(indexMetadata).putCustom(
                                        DLM_CUSTOM_INDEX_METADATA_KEY,
                                        newCustomMetadata
                                    ).build();
                                    Metadata metadata = Metadata.builder(currentState.metadata()).put(updatededIndexMetadata, true).build();
                                    return ClusterState.builder(currentState).metadata(metadata).build();
                                }
                            },
                            null
                        );
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (e instanceof SnapshotInProgressException) {
                            logger.info(
                                "DLM was unable to force merge index [{}] because it's currently being snapshotted. "
                                    + "Retrying on the next DLM run",
                                targetIndex
                            );
                        } else {
                            logger.error(() -> Strings.format("DLM encountered an error trying to force merge index [%s]", targetIndex), e);
                        }
                        listener.onFailure(e);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(e);
            }
        };

        taskQueue.submitTask(
            "Adding force merge marker to cluster state for [" + targetIndex + "]",
            new ForceMergeClusterStateUpdateTask(forceMergeRunningListener) {
                @Override
                public ClusterState execute(ClusterState currentState) {
                    logger.trace("Updating cluster state with forcemerge began result for {}", targetIndex);
                    IndexMetadata indexMetadata = currentState.metadata().index(targetIndex);
                    Map<String, String> customMetadata = indexMetadata.getCustomData(DLM_CUSTOM_INDEX_METADATA_KEY);
                    Map<String, String> newCustomMetadata = new HashMap<>();
                    if (customMetadata != null) {
                        newCustomMetadata.putAll(customMetadata);
                    }
                    newCustomMetadata.put(FORCE_MERGE_METADATA_KEY, FORCE_MERGE_BEGIN_MARKER);
                    IndexMetadata updatededIndexMetadata = new IndexMetadata.Builder(indexMetadata).putCustom(
                        DLM_CUSTOM_INDEX_METADATA_KEY,
                        newCustomMetadata
                    ).build();
                    Metadata metadata = Metadata.builder(currentState.metadata()).put(updatededIndexMetadata, true).build();
                    return ClusterState.builder(currentState).metadata(metadata).build();
                }
            },
            null
        );
    }

    @Nullable
    static TimeValue getRetentionConfiguration(DataStream dataStream) {
        if (dataStream.getLifecycle() == null) {
            return null;
        }
        return dataStream.getLifecycle().getDataRetention();
    }

    /**
     * Action listener that records the encountered failure using the provided recordError callback for the
     * provided target index. If the listener is notified of success it will clear the recorded entry for the provided
     * target index using the clearErrorRecord callback.
     */
    static class ErrorRecordingActionListener implements ActionListener<Void> {
        private final String targetIndex;
        private final DataLifecycleErrorStore errorStore;

        ErrorRecordingActionListener(String targetIndex, DataLifecycleErrorStore errorStore) {
            this.targetIndex = targetIndex;
            this.errorStore = errorStore;
        }

        @Override
        public void onResponse(Void unused) {
            errorStore.clearRecordedError(targetIndex);
        }

        @Override
        public void onFailure(Exception e) {
            errorStore.recordError(targetIndex, e);
        }
    }

    static RolloverRequest getDefaultRolloverRequest(
        RolloverConfiguration rolloverConfiguration,
        String dataStream,
        TimeValue dataRetention
    ) {
        RolloverRequest rolloverRequest = new RolloverRequest(dataStream, null).masterNodeTimeout(TimeValue.MAX_VALUE);
        rolloverRequest.setConditions(rolloverConfiguration.resolveRolloverConditions(dataRetention));
        return rolloverRequest;
    }

    private void updatePollInterval(TimeValue newInterval) {
        this.pollInterval = newInterval;
        maybeScheduleJob();
    }

    private void updateRolloverConfiguration(RolloverConfiguration newRolloverConfiguration) {
        this.rolloverConfiguration = newRolloverConfiguration;
    }

    private void cancelJob() {
        if (scheduler.get() != null) {
            scheduler.get().remove(DATA_LIFECYCLE_JOB_NAME);
            scheduledJob = null;
        }
    }

    private boolean isClusterServiceStoppedOrClosed() {
        final Lifecycle.State state = clusterService.lifecycleState();
        return state == Lifecycle.State.STOPPED || state == Lifecycle.State.CLOSED;
    }

    private void maybeScheduleJob() {
        if (this.isMaster == false) {
            return;
        }

        // don't schedule the job if the node is shutting down
        if (isClusterServiceStoppedOrClosed()) {
            logger.trace("Skipping scheduling a DLM job due to the cluster lifecycle state being: [{}] ", clusterService.lifecycleState());
            return;
        }

        if (scheduler.get() == null) {
            scheduler.set(new SchedulerEngine(settings, clock));
            scheduler.get().register(this);
        }

        assert scheduler.get() != null : "scheduler should be available";
        scheduledJob = new SchedulerEngine.Job(DATA_LIFECYCLE_JOB_NAME, new TimeValueSchedule(pollInterval));
        scheduler.get().add(scheduledJob);
    }

    // package visibility for testing
    DataLifecycleErrorStore getErrorStore() {
        return errorStore;
    }

    private abstract static class ForceMergeClusterStateUpdateTask implements ClusterStateTaskListener {
        final ActionListener<Void> listener;

        ForceMergeClusterStateUpdateTask(ActionListener<Void> listener) {
            this.listener = listener;
        }

        public abstract ClusterState execute(ClusterState currentState) throws Exception;

        @Override
        public void onFailure(Exception e) {
            listener.onFailure(e);
        }
    }
}
