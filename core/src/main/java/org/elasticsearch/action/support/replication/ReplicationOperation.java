/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.action.support.replication;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ReplicationResponse;
import org.elasticsearch.action.UnavailableShardsException;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.support.TransportActions;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.routing.IndexRoutingTable;
import org.elasticsearch.cluster.routing.IndexShardRoutingTable;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.transport.TransportResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ReplicationOperation<Request extends ReplicationRequest<Request>, ReplicaRequest extends ReplicationRequest<ReplicaRequest>,
    Response extends ReplicationResponse> {

    public static final Setting<WriteConsistencyLevel> WRITE_CONSISTENCY_LEVEL_SETTING =
        new Setting<>("action.write_consistency",
                      WriteConsistencyLevel.QUORUM.name().toLowerCase(Locale.ROOT),
                      WriteConsistencyLevel::fromString,
                      Setting.Property.NodeScope);

    final private ESLogger logger;
    final private Request request;
    final private Supplier<ClusterState> clusterStateSupplier;
    final private String opType;
    final private AtomicInteger totalShards = new AtomicInteger();
    final private AtomicInteger pendingShards = new AtomicInteger();
    final private AtomicInteger successfulShards = new AtomicInteger();
    final private boolean executeOnReplicas;
    final private boolean checkWriteConsistency;
    final private Primary<Request, ReplicaRequest, Response> primary;
    final private Replicas<ReplicaRequest> replicasProxy;
    final private AtomicBoolean finished = new AtomicBoolean();
    final protected ActionListener<Response> finalResponseListener;

    private volatile Response finalResponse = null;

    private final List<ReplicationResponse.ShardInfo.Failure> shardReplicaFailures = Collections.synchronizedList(new ArrayList<>());

    ReplicationOperation(Request request, Primary<Request, ReplicaRequest, Response> primary,
                         ActionListener<Response> listener,
                         boolean executeOnReplicas, boolean checkWriteConsistency,
                         Replicas<ReplicaRequest> replicas,
                         Supplier<ClusterState> clusterStateSupplier, ESLogger logger, String opType) {
        this.checkWriteConsistency = checkWriteConsistency;
        this.executeOnReplicas = executeOnReplicas;
        this.replicasProxy = replicas;
        this.primary = primary;
        this.finalResponseListener = listener;
        this.logger = logger;
        this.request = request;
        this.clusterStateSupplier = clusterStateSupplier;
        this.opType = opType;
    }

    void execute() throws Exception {
        final String writeConsistencyFailure = checkWriteConsistency ? checkWriteConsistency() : null;
        final ShardId shardId = primary.routingEntry().shardId();
        if (writeConsistencyFailure != null) {
            finishAsFailed(new UnavailableShardsException(shardId,
                "{} Timeout: [{}], request: [{}]", writeConsistencyFailure, request.timeout(), request));
            return;
        }

        totalShards.incrementAndGet();
        pendingShards.incrementAndGet(); // increase by 1 until we finish all primary coordination
        Tuple<Response, ReplicaRequest> primaryResponse = primary.perform(request);
        successfulShards.incrementAndGet(); // mark primary as successful
        finalResponse = primaryResponse.v1();
        ReplicaRequest replicaRequest = primaryResponse.v2();
        assert replicaRequest.primaryTerm() > 0 : "replicaRequest doesn't have a primary term";
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] op [{}] completed on primary for request [{}]", shardId, opType, request);
        }
        // we have to get a new state after successfully indexing into the primary in order to honour recovery semantics.
        // we have to make sure that every operation indexed into the primary after recovery start will also be replicated
        // to the recovery target. If we use an old cluster state, we may miss a relocation that has started since then.
        // If the index gets deleted after primary operation, we skip replication
        List<ShardRouting> shards = getShards(shardId, clusterStateSupplier.get());
        final String localNodeId = primary.routingEntry().currentNodeId();
        for (final ShardRouting shard : shards) {
            if (executeOnReplicas == false || shard.unassigned()) {
                if (shard.primary() == false) {
                    totalShards.incrementAndGet();
                }
                continue;
            }

            if (shard.currentNodeId().equals(localNodeId) == false) {
                performOnReplica(shard, replicaRequest);
            }

            if (shard.relocating() && shard.relocatingNodeId().equals(localNodeId) == false) {
                performOnReplica(shard.buildTargetRelocatingShard(), replicaRequest);
            }
        }

        // decrement pending and finish (if there are no replicas, or those are done)
        decPendingAndFinishIfNeeded(); // incremented in the beginning of this method
    }

    private void performOnReplica(final ShardRouting shard, final ReplicaRequest replicaRequest) {
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] sending op [{}] to replica {} for request [{}]", shard.shardId(), opType, shard, replicaRequest);
        }

        totalShards.incrementAndGet();
        pendingShards.incrementAndGet();
        replicasProxy.performOn(shard, replicaRequest, new ActionListener<TransportResponse.Empty>() {
            @Override
            public void onResponse(TransportResponse.Empty empty) {
                successfulShards.incrementAndGet();
                decPendingAndFinishIfNeeded();
            }

            @Override
            public void onFailure(Throwable replicaException) {
                logger.trace("[{}] failure while performing [{}] on replica {}, request [{}]", replicaException, shard.shardId(), opType,
                    shard, replicaRequest);
                if (ignoreReplicaException(replicaException)) {
                    decPendingAndFinishIfNeeded();
                } else {
                    RestStatus restStatus = ExceptionsHelper.status(replicaException);
                    shardReplicaFailures.add(new ReplicationResponse.ShardInfo.Failure(
                        shard.shardId(), shard.currentNodeId(), replicaException, restStatus, false));
                    String message = String.format(Locale.ROOT, "failed to perform %s on replica %s", opType, shard);
                    logger.warn("[{}] {}", replicaException, shard.shardId(), message);
                    replicasProxy.failShard(shard, primary.routingEntry(), message, replicaException,
                        ReplicationOperation.this::decPendingAndFinishIfNeeded,
                        ReplicationOperation.this::onPrimaryDemoted,
                        throwable -> decPendingAndFinishIfNeeded()
                    );
                }
            }
        });
    }

    private void onPrimaryDemoted(Throwable demotionFailure) {
        String primaryFail = String.format(Locale.ROOT,
            "primary shard [%s] was demoted while failing replica shard",
            primary.routingEntry());
        // we are no longer the primary, fail ourselves and start over
        primary.failShard(primaryFail, demotionFailure);
        finishAsFailed(new RetryOnPrimaryException(primary.routingEntry().shardId(), primaryFail, demotionFailure));
    }

    /**
     * checks whether we can perform a write based on the write consistency setting
     * returns **null* if OK to proceed, or a string describing the reason to stop
     */
    String checkWriteConsistency() {
        assert request.consistencyLevel() != WriteConsistencyLevel.DEFAULT : "consistency level should be set";
        final ShardId shardId = primary.routingEntry().shardId();
        final ClusterState state = clusterStateSupplier.get();
        final WriteConsistencyLevel consistencyLevel = request.consistencyLevel();
        final IndexRoutingTable indexRoutingTable = state.getRoutingTable().index(shardId.getIndexName());
        if (indexRoutingTable == null) {
            logger.trace("[{}] not enough active copies to meet write consistency of [{}] (index [{}] not founded in cluster state), " +
                         "scheduling a retry. op [{}], request [{}]", shardId, consistencyLevel, shardId.getIndexName(), opType, request);
            return "Not enough active copies to meet write consistency of [" + consistencyLevel + "] (index [" +
                   shardId.getIndexName() + "] not found in cluster state).";
        }
        final IndexShardRoutingTable shardRoutingTable = indexRoutingTable.shard(shardId.getId());
        Optional<Tuple<Integer, Integer>> failureCheck = checkWriteConsistency(consistencyLevel, shardRoutingTable);
        if (failureCheck.isPresent()) {
            final int sizeActive = failureCheck.get().v1();
            final int requiredNumber = failureCheck.get().v2();
            logger.trace("[{}] not enough active copies to meet write consistency of [{}] (have {}, needed {}), scheduling a retry." +
                         " op [{}], request [{}]", shardId, consistencyLevel, sizeActive, requiredNumber, opType, request);
            return "Not enough active copies to meet write consistency of [" + consistencyLevel + "] (have " + sizeActive +
                   ", needed " + requiredNumber + ").";
        } else {
            return null;
        }
    }

    public static Optional<Tuple<Integer, Integer>>
    checkWriteConsistency(final WriteConsistencyLevel consistencyLevel, final IndexShardRoutingTable shardRoutingTable) {
        final int sizeActive;
        final int requiredNumber;
        if (shardRoutingTable != null) {
            sizeActive = shardRoutingTable.activeShards().size();
            if (consistencyLevel == WriteConsistencyLevel.QUORUM && shardRoutingTable.getSize() > 2) {
                // only for more than 2 in the number of shardIt it makes sense, otherwise its 1 shard with 1 replica,
                // quorum is 1 (which is what it is initialized to)
                requiredNumber = (shardRoutingTable.getSize() / 2) + 1;
            } else if (consistencyLevel == WriteConsistencyLevel.ALL) {
                requiredNumber = shardRoutingTable.getSize();
            } else {
                requiredNumber = 1;
            }
        } else {
            sizeActive = 0;
            requiredNumber = 1;
        }
        if (sizeActive < requiredNumber) {
            return Optional.of(Tuple.tuple(sizeActive, requiredNumber));
        } else {
            return Optional.empty();
        }
    }

    protected List<ShardRouting> getShards(ShardId shardId, ClusterState state) {
        // can be null if the index is deleted / closed on us..
        final IndexShardRoutingTable shardRoutingTable = state.getRoutingTable().shardRoutingTableOrNull(shardId);
        List<ShardRouting> shards = shardRoutingTable == null ? Collections.emptyList() : shardRoutingTable.shards();
        return shards;
    }

    private void decPendingAndFinishIfNeeded() {
        assert pendingShards.get() > 0;
        if (pendingShards.decrementAndGet() == 0) {
            finish();
        }
    }

    private void finish() {
        if (finished.compareAndSet(false, true)) {
            final ReplicationResponse.ShardInfo.Failure[] failuresArray;
            if (shardReplicaFailures.isEmpty()) {
                failuresArray = ReplicationResponse.EMPTY;
            } else {
                failuresArray = new ReplicationResponse.ShardInfo.Failure[shardReplicaFailures.size()];
                shardReplicaFailures.toArray(failuresArray);
            }
            finalResponse.setShardInfo(new ReplicationResponse.ShardInfo(
                    totalShards.get(),
                    successfulShards.get(),
                    failuresArray
                )
            );
            finalResponseListener.onResponse(finalResponse);
        }
    }

    private void finishAsFailed(Throwable throwable) {
        if (finished.compareAndSet(false, true)) {
            finalResponseListener.onFailure(throwable);
        }
    }


    /**
     * Should an exception be ignored when the operation is performed on the replica.
     */
    public static boolean ignoreReplicaException(Throwable e) {
        if (TransportActions.isShardNotAvailableException(e)) {
            return true;
        }
        // on version conflict or document missing, it means
        // that a new change has crept into the replica, and it's fine
        if (isConflictException(e)) {
            return true;
        }
        return false;
    }

    public static boolean isConflictException(Throwable e) {
        Throwable cause = ExceptionsHelper.unwrapCause(e);
        // on version conflict or document missing, it means
        // that a new change has crept into the replica, and it's fine
        if (cause instanceof VersionConflictEngineException) {
            return true;
        }
        return false;
    }


    interface Primary<Request extends ReplicationRequest<Request>, ReplicaRequest extends ReplicationRequest<ReplicaRequest>,
        Response extends ReplicationResponse> {

        /** routing entry for this primary */
        ShardRouting routingEntry();

        /** fail the primary, typically due to the fact that the operation has learned the primary has been demoted by the master */
        void failShard(String message, Throwable throwable);

        /**
         * Performs the given request on this primary
         *
         * @return A tuple containing not null values, as first value the result of the primary operation and as second value
         * the request to be executed on the replica shards.
         */
        Tuple<Response, ReplicaRequest> perform(Request request) throws Exception;

    }

    interface Replicas<ReplicaRequest extends ReplicationRequest<ReplicaRequest>> {

        /**
         * performs the the given request on the specified replica
         *
         * @param replica {@link ShardRouting} of the shard this request should be executed on
         * @param replicaRequest operation to peform
         * @param listener a callback to call once the operation has been complicated, either successfully or with an error.
         */
        void performOn(ShardRouting replica, ReplicaRequest replicaRequest, ActionListener<TransportResponse.Empty> listener);

        /**
         * Fail the specified shard, removing it from the current set of active shards
         * @param replica shard to fail
         * @param primary the primary shard that requested the failure
         * @param message a (short) description of the reason
         * @param throwable the original exception which caused the ReplicationOperation to request the shard to be failed
         * @param onSuccess a callback to call when the shard has been successfully removed from the active set.
         * @param onPrimaryDemoted a callback to call when the shard can not be failed because the current primary has been demoted
         *                         by the master.
         * @param onIgnoredFailure a callback to call when failing a shard has failed, but it that failure can be safely ignored and the
         *                         replication operation can finish processing
         *                         Note: this callback should be used in extreme situations, typically node shutdown.
         */
        void failShard(ShardRouting replica, ShardRouting primary, String message, Throwable throwable, Runnable onSuccess,
                       Consumer<Throwable> onPrimaryDemoted, Consumer<Throwable> onIgnoredFailure);
    }

    public static class RetryOnPrimaryException extends ElasticsearchException {
        public RetryOnPrimaryException(ShardId shardId, String msg) {
            this(shardId, msg, null);
        }

        public RetryOnPrimaryException(ShardId shardId, String msg, Throwable cause) {
            super(msg, cause);
            setShard(shardId);
        }

        public RetryOnPrimaryException(StreamInput in) throws IOException {
            super(in);
        }
    }
}
