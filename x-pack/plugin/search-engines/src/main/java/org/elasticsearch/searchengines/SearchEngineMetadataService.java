/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.searchengines;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.cluster.ClusterStateTaskConfig;
import org.elasticsearch.cluster.ClusterStateTaskExecutor;
import org.elasticsearch.cluster.ClusterStateTaskListener;
import org.elasticsearch.cluster.metadata.SearchEngine;
import org.elasticsearch.cluster.metadata.SearchEngineMetadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Priority;
import org.elasticsearch.searchengines.action.CreateSearchEngineAction;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SearchEngineMetadataService {

    private final ClusterService clusterService;

    /**
     * Cluster state task executor for ingest pipeline operations
     */
    static final ClusterStateTaskExecutor<SearchEngineMetadataService.ClusterStateUpdateTask> TASK_EXECUTOR = batchExecutionContext -> {
        final SearchEngineMetadata initialMetadata = batchExecutionContext.initialState().metadata().custom(SearchEngineMetadata.TYPE, SearchEngineMetadata.EMPTY);
        var currentMetadata = initialMetadata;
        for (final var taskContext : batchExecutionContext.taskContexts()) {
            try {
                final var task = taskContext.getTask();
                try (var ignored = taskContext.captureResponseHeaders()) {
                    currentMetadata = task.execute(currentMetadata);
                }
                taskContext.success(() -> task.listener.onResponse(AcknowledgedResponse.TRUE));
            } catch (Exception e) {
                taskContext.onFailure(e);
            }
        }
        final var finalMetadata = currentMetadata;
        return finalMetadata == initialMetadata
            ? batchExecutionContext.initialState()
            : batchExecutionContext.initialState().copyAndUpdateMetadata(b -> b.putCustom(SearchEngineMetadata.TYPE, finalMetadata));
    };

    /**
     * Specialized cluster state update task specifically for ingest pipeline operations.
     * These operations all receive an AcknowledgedResponse.
     */
    abstract static class ClusterStateUpdateTask implements ClusterStateTaskListener {
        final ActionListener<AcknowledgedResponse> listener;

        ClusterStateUpdateTask(ActionListener<AcknowledgedResponse> listener) {
            this.listener = listener;
        }

        public abstract SearchEngineMetadata execute(SearchEngineMetadata currentMetadata);

        @Override
        public void onFailure(Exception e) {
            listener.onFailure(e);
        }
    }


    public SearchEngineMetadataService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    public void createSearchEngine(CreateSearchEngineAction.Request request, ActionListener<AcknowledgedResponse> listener) {
        clusterService.submitStateUpdateTask(
            "create-search-engine-" + request.getName(),
            new CreateSearchEngineClusterStateUpdateTask(listener, request),
            ClusterStateTaskConfig.build(Priority.NORMAL, request.masterNodeTimeout()),
            TASK_EXECUTOR
        );
    }

    static class CreateSearchEngineClusterStateUpdateTask extends ClusterStateUpdateTask {
        private final CreateSearchEngineAction.Request request;

        CreateSearchEngineClusterStateUpdateTask(ActionListener<AcknowledgedResponse> listener, CreateSearchEngineAction.Request request) {
            super(listener);
            this.request = request;
        }

        @Override
        public SearchEngineMetadata execute(SearchEngineMetadata currentMetadata) {
            Map<String, SearchEngine> searchEngines = new HashMap<>(currentMetadata.searchEngines());

            // TODO:
            // - validate the engine name
            // - handle indices list
            SearchEngine searchEngine = new SearchEngine(
                request.getName(),
                Collections.emptyList(),
                false,
                false
            );
            searchEngines.put(request.getName(), searchEngine);

            return new SearchEngineMetadata(searchEngines);
        }
    }
}
