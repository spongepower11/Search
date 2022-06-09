/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.operator.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateTaskConfig;
import org.elasticsearch.cluster.metadata.OperatorErrorMetadata;
import org.elasticsearch.cluster.metadata.OperatorMetadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Priority;
import org.elasticsearch.operator.OperatorHandler;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentParser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.elasticsearch.core.Strings.format;

/**
 * Controller class for applying file based settings to ClusterState.
 * This class contains the logic about validation, ordering and applying of
 * the cluster state specified in a file.
 */
public class OperatorClusterStateController {
    private static final Logger logger = LogManager.getLogger(FileSettingsService.class);

    public static final String SETTINGS = "settings";
    public static final String METADATA = "metadata";

    Map<String, OperatorHandler<?>> handlers = null;
    final ClusterService clusterService;

    public OperatorClusterStateController(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    /**
     * Initializes the controller with the currently implemented state handlers
     *
     * @param handlerList the list of supported operator handlers
     */
    public void initHandlers(List<OperatorHandler<?>> handlerList) {
        handlers = handlerList.stream().collect(Collectors.toMap(OperatorHandler::name, Function.identity()));
    }

    static class SettingsFile {
        public static final ParseField STATE_FIELD = new ParseField("state");
        public static final ParseField METADATA_FIELD = new ParseField("metadata");
        @SuppressWarnings("unchecked")
        private static final ConstructingObjectParser<SettingsFile, Void> PARSER = new ConstructingObjectParser<>(
            "operator_state",
            a -> new SettingsFile((Map<String, Object>) a[0], (OperatorStateVersionMetadata) a[1])
        );
        static {
            PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> p.map(), STATE_FIELD);
            PARSER.declareObject(ConstructingObjectParser.constructorArg(), OperatorStateVersionMetadata::parse, METADATA_FIELD);
        }

        Map<String, Object> state;
        OperatorStateVersionMetadata metadata;

        SettingsFile(Map<String, Object> state, OperatorStateVersionMetadata metadata) {
            this.state = state;
            this.metadata = metadata;
        }
    }

    /**
     * Saves an operator cluster state for a given 'namespace' from XContentParser
     *
     * @param namespace the namespace under which we'll store the operator keys in the cluster state metadata
     * @param parser the XContentParser to process
     * @param errorListener a consumer called with IllegalStateException if the content has errors and the
     *        cluster state cannot be correctly applied, IncompatibleVersionException if the content is stale or
     *        incompatible with this node {@link Version}, null if successful.
     */
    public void process(String namespace, XContentParser parser, Consumer<Exception> errorListener) {
        SettingsFile operatorStateFileContent;

        try {
            operatorStateFileContent = SettingsFile.PARSER.apply(parser, null);
        } catch (Exception e) {
            List<String> errors = List.of(e.getMessage());
            recordErrorState(new OperatorErrorState(namespace, -1L, errors, OperatorErrorMetadata.ErrorKind.PARSING));
            logger.error("Error processing state change request for [{}] with the following errors [{}]", namespace, errors);

            errorListener.accept(new IllegalStateException("Error processing state change request for " + namespace, e));
            return;
        }

        Map<String, Object> operatorState = operatorStateFileContent.state;
        OperatorStateVersionMetadata stateVersionMetadata = operatorStateFileContent.metadata;

        LinkedHashSet<String> orderedHandlers;
        try {
            orderedHandlers = orderedStateHandlers(operatorState.keySet());
        } catch (Exception e) {
            List<String> errors = List.of(e.getMessage());
            recordErrorState(
                new OperatorErrorState(namespace, stateVersionMetadata.version(), errors, OperatorErrorMetadata.ErrorKind.PARSING)
            );
            logger.error("Error processing state change request for [{}] with the following errors [{}]", namespace, errors);

            errorListener.accept(new IllegalStateException("Error processing state change request for " + namespace, e));
            return;
        }

        ClusterState state = clusterService.state();
        OperatorMetadata existingMetadata = state.metadata().operatorState(namespace);
        if (checkMetadataVersion(existingMetadata, stateVersionMetadata, errorListener) == false) {
            return;
        }

        // Do we need to retry this, or it retries automatically?
        clusterService.submitStateUpdateTask(
            "operator state [" + namespace + "]",
            new OperatorUpdateStateTask(
                namespace,
                operatorStateFileContent,
                handlers,
                orderedHandlers,
                (errorState) -> recordErrorState(errorState),
                new ActionListener<>() {
                    @Override
                    public void onResponse(ActionResponse.Empty empty) {
                        logger.info("Successfully applied new cluster state for namespace [{}]", namespace);
                        errorListener.accept(null);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        logger.error("Failed to apply operator cluster state", e);
                        errorListener.accept(e);
                    }
                }
            ),
            ClusterStateTaskConfig.build(Priority.URGENT),
            new OperatorUpdateStateTask.OperatorUpdateStateTaskExecutor(namespace, clusterService.getRerouteService())
        );
    }

    // package private for testing
    static boolean checkMetadataVersion(
        OperatorMetadata existingMetadata,
        OperatorStateVersionMetadata stateVersionMetadata,
        Consumer<Exception> errorListener
    ) {
        if (Version.CURRENT.before(stateVersionMetadata.minCompatibleVersion())) {
            errorListener.accept(
                new IncompatibleVersionException(
                    format(
                        "Cluster state version [%s] is not compatible with this Elasticsearch node",
                        stateVersionMetadata.minCompatibleVersion()
                    )
                )
            );
            return false;
        }

        if (existingMetadata != null && existingMetadata.version() >= stateVersionMetadata.version()) {
            errorListener.accept(
                new IncompatibleVersionException(
                    format(
                        "Not updating cluster state because version [%s] is less or equal to the current metadata version [%s]",
                        stateVersionMetadata.version(),
                        existingMetadata.version()
                    )
                )
            );
            return false;
        }

        return true;
    }

    record OperatorErrorState(String namespace, Long version, List<String> errors, OperatorErrorMetadata.ErrorKind errorKind) {}

    private void recordErrorState(OperatorErrorState state) {
        clusterService.submitStateUpdateTask(
            "operator state error for [ " + state.namespace + "]",
            new OperatorUpdateErrorTask(state, new ActionListener<>() {
                @Override
                public void onResponse(ActionResponse.Empty empty) {
                    logger.info("Successfully applied new operator error state for namespace [{}]", state.namespace);
                }

                @Override
                public void onFailure(Exception e) {
                    logger.error("Failed to apply operator error cluster state", e);
                }
            }),
            ClusterStateTaskConfig.build(Priority.URGENT),
            new OperatorUpdateErrorTask.OperatorUpdateErrorTaskExecutor()
        );
    }

    // package private for testing
    LinkedHashSet<String> orderedStateHandlers(Set<String> keys) {
        LinkedHashSet<String> orderedHandlers = new LinkedHashSet<>();
        LinkedHashSet<String> dependencyStack = new LinkedHashSet<>();

        for (String key : keys) {
            addStateHandler(key, keys, orderedHandlers, dependencyStack);
        }

        return orderedHandlers;
    }

    private void addStateHandler(String key, Set<String> keys, LinkedHashSet<String> ordered, LinkedHashSet<String> visited) {
        if (visited.contains(key)) {
            StringBuilder msg = new StringBuilder("Cycle found in settings dependencies: ");
            visited.forEach(s -> {
                msg.append(s);
                msg.append(" -> ");
            });
            msg.append(key);
            throw new IllegalStateException(msg.toString());
        }

        if (ordered.contains(key)) {
            // already added by another dependent handler
            return;
        }

        visited.add(key);
        OperatorHandler<?> handler = handlers.get(key);

        if (handler == null) {
            throw new IllegalStateException("Unknown settings definition type: " + key);
        }

        for (String dependency : handler.dependencies()) {
            if (keys.contains(dependency) == false) {
                throw new IllegalStateException("Missing settings dependency definition: " + key + " -> " + dependency);
            }
            addStateHandler(dependency, keys, ordered, visited);
        }

        visited.remove(key);
        ordered.add(key);
    }

    /**
     * {@link IncompatibleVersionException} is thrown when we try to update the cluster state
     * without changing the update version id, or if we try to update cluster state on
     * an incompatible Elasticsearch version in mixed cluster mode.
     */
    public static class IncompatibleVersionException extends RuntimeException {
        public IncompatibleVersionException(String message) {
            super(message);
        }
    }
}
