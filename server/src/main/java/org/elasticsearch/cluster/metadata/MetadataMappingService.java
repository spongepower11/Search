/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingClusterStateUpdateRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.cluster.AckedClusterStateTaskListener;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateTaskConfig;
import org.elasticsearch.cluster.ClusterStateTaskExecutor;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.MapperService.MergeReason;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.indices.InvalidTypeNameException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.mapper.MapperService.isMappingSourceTyped;
import static org.elasticsearch.indices.cluster.IndicesClusterStateService.AllocatedIndices.IndexRemovalReason.NO_LONGER_ASSIGNED;

/**
 * Service responsible for submitting mapping changes
 */
public class MetadataMappingService {

    private static final Logger logger = LogManager.getLogger(MetadataMappingService.class);

    private final ClusterService clusterService;
    private final IndicesService indicesService;

    final RefreshTaskExecutor refreshExecutor = new RefreshTaskExecutor();
    final PutMappingExecutor putMappingExecutor = new PutMappingExecutor();


    @Inject
    public MetadataMappingService(ClusterService clusterService, IndicesService indicesService) {
        this.clusterService = clusterService;
        this.indicesService = indicesService;
    }

    static class RefreshTask {
        final String index;
        final String indexUUID;

        RefreshTask(String index, final String indexUUID) {
            this.index = index;
            this.indexUUID = indexUUID;
        }

        @Override
        public String toString() {
            return "[" + index + "][" + indexUUID + "]";
        }
    }

    class RefreshTaskExecutor implements ClusterStateTaskExecutor<RefreshTask> {
        @Override
        public ClusterTasksResult<RefreshTask> execute(ClusterState currentState, List<RefreshTask> tasks) throws Exception {
            ClusterState newClusterState = executeRefresh(currentState, tasks);
            return ClusterTasksResult.<RefreshTask>builder().successes(tasks).build(newClusterState);
        }
    }

    /**
     * Batch method to apply all the queued refresh operations. The idea is to try and batch as much
     * as possible so we won't create the same index all the time for example for the updates on the same mapping
     * and generate a single cluster change event out of all of those.
     */
    ClusterState executeRefresh(final ClusterState currentState, final List<RefreshTask> allTasks) throws Exception {
        // break down to tasks per index, so we can optimize the on demand index service creation
        // to only happen for the duration of a single index processing of its respective events
        Map<String, List<RefreshTask>> tasksPerIndex = new HashMap<>();
        for (RefreshTask task : allTasks) {
            if (task.index == null) {
                logger.debug("ignoring a mapping task of type [{}] with a null index.", task);
            }
            tasksPerIndex.computeIfAbsent(task.index, k -> new ArrayList<>()).add(task);
        }

        boolean dirty = false;
        Metadata.Builder mdBuilder = Metadata.builder(currentState.metadata());

        for (Map.Entry<String, List<RefreshTask>> entry : tasksPerIndex.entrySet()) {
            IndexMetadata indexMetadata = mdBuilder.get(entry.getKey());
            if (indexMetadata == null) {
                // index got deleted on us, ignore...
                logger.debug("[{}] ignoring tasks - index meta data doesn't exist", entry.getKey());
                continue;
            }
            final Index index = indexMetadata.getIndex();
            // the tasks lists to iterate over, filled with the list of mapping tasks, trying to keep
            // the latest (based on order) update mapping one per node
            List<RefreshTask> allIndexTasks = entry.getValue();
            boolean hasTaskWithRightUUID = false;
            for (RefreshTask task : allIndexTasks) {
                if (indexMetadata.isSameUUID(task.indexUUID)) {
                    hasTaskWithRightUUID = true;
                } else {
                    logger.debug("{} ignoring task [{}] - index meta data doesn't match task uuid", index, task);
                }
            }
            if (hasTaskWithRightUUID == false) {
                continue;
            }

            // construct the actual index if needed, and make sure the relevant mappings are there
            boolean removeIndex = false;
            IndexService indexService = indicesService.indexService(indexMetadata.getIndex());
            if (indexService == null) {
                // we need to create the index here, and add the current mapping to it, so we can merge
                indexService = indicesService.createIndex(indexMetadata, Collections.emptyList(), false);
                removeIndex = true;
                indexService.mapperService().merge(indexMetadata, MergeReason.MAPPING_RECOVERY);
            }

            IndexMetadata.Builder builder = IndexMetadata.builder(indexMetadata);
            try {
                boolean indexDirty = refreshIndexMapping(indexService, builder);
                if (indexDirty) {
                    mdBuilder.put(builder);
                    dirty = true;
                }
            } finally {
                if (removeIndex) {
                    indicesService.removeIndex(index, NO_LONGER_ASSIGNED, "created for mapping processing");
                }
            }
        }

        if (dirty == false) {
            return currentState;
        }
        return ClusterState.builder(currentState).metadata(mdBuilder).build();
    }

    private boolean refreshIndexMapping(IndexService indexService, IndexMetadata.Builder builder) {
        boolean dirty = false;
        String index = indexService.index().getName();
        try {
            List<String> updatedTypes = new ArrayList<>();
            MapperService mapperService = indexService.mapperService();
            for (DocumentMapper mapper : Arrays.asList(mapperService.documentMapper(),
                                                       mapperService.documentMapper(MapperService.DEFAULT_MAPPING))) {
                if (mapper != null) {
                    final String type = mapper.type();
                    if (!mapper.mappingSource().equals(builder.mapping(type).source())) {
                        updatedTypes.add(type);
                    }
                }
            }

            // if a single type is not up-to-date, re-send everything
            if (updatedTypes.isEmpty() == false) {
                logger.warn("[{}] re-syncing mappings with cluster state because of types [{}]", index, updatedTypes);
                dirty = true;
                for (DocumentMapper mapper : Arrays.asList(mapperService.documentMapper(),
                                                           mapperService.documentMapper(MapperService.DEFAULT_MAPPING))) {
                    if (mapper != null) {
                        builder.putMapping(new MappingMetadata(mapper));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn(() -> new ParameterizedMessage("[{}] failed to refresh-mapping in cluster state", index), e);
        }
        return dirty;
    }

    /**
     * Refreshes mappings if they are not the same between original and parsed version
     */
    public void refreshMapping(final String index, final String indexUUID) {
        final RefreshTask refreshTask = new RefreshTask(index, indexUUID);
        clusterService.submitStateUpdateTask("refresh-mapping [" + index + "]",
            refreshTask,
            ClusterStateTaskConfig.build(Priority.HIGH),
            refreshExecutor,
                (source, e) -> logger.warn(() -> new ParameterizedMessage("failure during [{}]", source), e)
        );
    }

    class PutMappingExecutor implements ClusterStateTaskExecutor<PutMappingClusterStateUpdateRequest> {
        @Override
        public ClusterTasksResult<PutMappingClusterStateUpdateRequest>
        execute(ClusterState currentState, List<PutMappingClusterStateUpdateRequest> tasks) throws Exception {
            Map<Index, MapperService> indexMapperServices = new HashMap<>();
            ClusterTasksResult.Builder<PutMappingClusterStateUpdateRequest> builder = ClusterTasksResult.builder();
            try {
                for (PutMappingClusterStateUpdateRequest request : tasks) {
                    try {
                        for (Index index : request.indices()) {
                            final IndexMetadata indexMetadata = currentState.metadata().getIndexSafe(index);
                            if (indexMapperServices.containsKey(indexMetadata.getIndex()) == false) {
                                MapperService mapperService = indicesService.createIndexMapperService(indexMetadata);
                                indexMapperServices.put(index, mapperService);
                                // add mappings for all types, we need them for cross-type validation
                                mapperService.merge(indexMetadata, MergeReason.MAPPING_RECOVERY);
                            }
                        }
                        currentState = applyRequest(currentState, request, indexMapperServices);
                        builder.success(request);
                    } catch (Exception e) {
                        builder.failure(request, e);
                    }
                }
                return builder.build(currentState);
            } finally {
                IOUtils.close(indexMapperServices.values());
            }
        }

        private ClusterState applyRequest(ClusterState currentState, PutMappingClusterStateUpdateRequest request,
                                          Map<Index, MapperService> indexMapperServices) throws IOException {
            String mappingType = request.type();
            CompressedXContent mappingUpdateSource = new CompressedXContent(request.source());
            final Metadata metadata = currentState.metadata();
            final List<IndexMetadata> updateList = new ArrayList<>();
            for (Index index : request.indices()) {
                MapperService mapperService = indexMapperServices.get(index);
                // IMPORTANT: always get the metadata from the state since it get's batched
                // and if we pull it from the indexService we might miss an update etc.
                final IndexMetadata indexMetadata = currentState.getMetadata().getIndexSafe(index);

                // this is paranoia... just to be sure we use the exact same metadata tuple on the update that
                // we used for the validation, it makes this mechanism little less scary (a little)
                updateList.add(indexMetadata);
                // try and parse it (no need to add it here) so we can bail early in case of parsing exception
                DocumentMapper newMapper;
                DocumentMapper existingMapper = mapperService.documentMapper();

                String typeForUpdate = mapperService.getTypeForUpdate(mappingType, mappingUpdateSource);
                if (existingMapper != null && existingMapper.type().equals(typeForUpdate) == false) {
                    throw new IllegalArgumentException("Rejecting mapping update to [" + mapperService.index().getName() +
                        "] as the final mapping would have more than 1 type: " + Arrays.asList(existingMapper.type(), typeForUpdate));
                }

                if (MapperService.DEFAULT_MAPPING.equals(request.type())) {
                    // _default_ types do not go through merging, but we do test the new settings. Also don't apply the old default
                    newMapper = mapperService.parse(request.type(), mappingUpdateSource, false);
                } else {
                    newMapper = mapperService.parse(request.type(), mappingUpdateSource, existingMapper == null);
                    if (existingMapper != null) {
                        // first, simulate: just call merge and ignore the result
                        existingMapper.merge(newMapper.mapping(), MergeReason.MAPPING_UPDATE);
                    }
                }
                if (mappingType == null) {
                    mappingType = newMapper.type();
                } else if (mappingType.equals(newMapper.type()) == false
                        && (isMappingSourceTyped(request.type(), mappingUpdateSource)
                                || mapperService.resolveDocumentType(mappingType).equals(newMapper.type()) == false)) {
                    throw new InvalidTypeNameException("Type name provided does not match type name within mapping definition.");
                }
            }
            assert mappingType != null;

            if (MapperService.DEFAULT_MAPPING.equals(mappingType) == false
                    && MapperService.SINGLE_MAPPING_NAME.equals(mappingType) == false
                    && mappingType.charAt(0) == '_') {
                throw new InvalidTypeNameException("Document mapping type name can't start with '_', found: [" + mappingType + "]");
            }
            Metadata.Builder builder = Metadata.builder(metadata);
            boolean updated = false;
            for (IndexMetadata indexMetadata : updateList) {
                boolean updatedMapping = false;
                // do the actual merge here on the master, and update the mapping source
                // we use the exact same indexService and metadata we used to validate above here to actually apply the update
                final Index index = indexMetadata.getIndex();
                final MapperService mapperService = indexMapperServices.get(index);

                String typeForUpdate = mapperService.getTypeForUpdate(mappingType, mappingUpdateSource);
                CompressedXContent existingSource = null;
                DocumentMapper existingMapper = mapperService.documentMapper(typeForUpdate);
                if (existingMapper != null) {
                    existingSource = existingMapper.mappingSource();
                }
                DocumentMapper mergedMapper = mapperService.merge(typeForUpdate, mappingUpdateSource, MergeReason.MAPPING_UPDATE);
                CompressedXContent updatedSource = mergedMapper.mappingSource();

                if (existingSource != null) {
                    if (existingSource.equals(updatedSource)) {
                        // same source, no changes, ignore it
                    } else {
                        updatedMapping = true;
                        // use the merged mapping source
                        if (logger.isDebugEnabled()) {
                            logger.debug("{} update_mapping [{}] with source [{}]", index, mergedMapper.type(), updatedSource);
                        } else if (logger.isInfoEnabled()) {
                            logger.info("{} update_mapping [{}]", index, mergedMapper.type());
                        }

                    }
                } else {
                    updatedMapping = true;
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} create_mapping [{}] with source [{}]", index, mappingType, updatedSource);
                    } else if (logger.isInfoEnabled()) {
                        logger.info("{} create_mapping [{}]", index, mappingType);
                    }
                }

                IndexMetadata.Builder indexMetadataBuilder = IndexMetadata.builder(indexMetadata);
                // Mapping updates on a single type may have side-effects on other types so we need to
                // update mapping metadata on all types
                for (DocumentMapper mapper : Arrays.asList(mapperService.documentMapper(),
                                                           mapperService.documentMapper(MapperService.DEFAULT_MAPPING))) {
                    if (mapper != null) {
                        indexMetadataBuilder.putMapping(new MappingMetadata(mapper.mappingSource()));
                    }
                }
                if (updatedMapping) {
                    indexMetadataBuilder.mappingVersion(1 + indexMetadataBuilder.mappingVersion());
                }
                /*
                 * This implicitly increments the index metadata version and builds the index metadata. This means that we need to have
                 * already incremented the mapping version if necessary. Therefore, the mapping version increment must remain before this
                 * statement.
                 */
                builder.put(indexMetadataBuilder);
                updated |= updatedMapping;
            }
            if (updated) {
                return ClusterState.builder(currentState).metadata(builder).build();
            } else {
                return currentState;
            }
        }

        @Override
        public String describeTasks(List<PutMappingClusterStateUpdateRequest> tasks) {
            return String.join(", ", tasks.stream().map(t -> (CharSequence)t.type())::iterator);
        }
    }

    public void putMapping(final PutMappingClusterStateUpdateRequest request, final ActionListener<AcknowledgedResponse> listener) {
        clusterService.submitStateUpdateTask("put-mapping " + Strings.arrayToCommaDelimitedString(request.indices()),
                request,
                ClusterStateTaskConfig.build(Priority.HIGH, request.masterNodeTimeout()),
                putMappingExecutor,
                new AckedClusterStateTaskListener() {

                    @Override
                    public void onFailure(String source, Exception e) {
                        listener.onFailure(e);
                    }

                    @Override
                    public boolean mustAck(DiscoveryNode discoveryNode) {
                        return true;
                    }

                    @Override
                    public void onAllNodesAcked(@Nullable Exception e) {
                        listener.onResponse(AcknowledgedResponse.of(e == null));
                    }

                    @Override
                    public void onAckTimeout() {
                        listener.onResponse(AcknowledgedResponse.FALSE);
                    }

                    @Override
                    public TimeValue ackTimeout() {
                        return request.ackTimeout();
                    }
                });
    }
}
