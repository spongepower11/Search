/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.health.node.selection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.persistent.AllocatedPersistentTask;
import org.elasticsearch.persistent.PersistentTaskParams;
import org.elasticsearch.persistent.PersistentTaskState;
import org.elasticsearch.persistent.PersistentTasksCustomMetadata;
import org.elasticsearch.persistent.PersistentTasksExecutor;
import org.elasticsearch.persistent.PersistentTasksService;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.RemoteTransportException;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.ParseField;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.elasticsearch.health.node.selection.HealthNodeSelector.HEALTH_NODE_SELECTOR_TASK_NAME;

/**
 * Persistent task executor that is managing the {@link HealthNodeSelector}
 */
public final class HealthNodeSelectorTaskExecutor extends PersistentTasksExecutor<HealthNodeSelectorTaskParams> {

    private static final Logger logger = LogManager.getLogger(HealthNodeSelector.class);

    private final ClusterService clusterService;
    private final PersistentTasksService persistentTasksService;
    private final AtomicReference<HealthNodeSelector> currentTask = new AtomicReference<>();

    public HealthNodeSelectorTaskExecutor(Client client, ClusterService clusterService, ThreadPool threadPool) {
        super(HEALTH_NODE_SELECTOR_TASK_NAME, ThreadPool.Names.MANAGEMENT);
        this.clusterService = clusterService;
        persistentTasksService = new PersistentTasksService(clusterService, threadPool, client);
    }

    @Override
    protected void nodeOperation(AllocatedPersistentTask task, HealthNodeSelectorTaskParams params, PersistentTaskState state) {
        HealthNodeSelector healthNodeSelector = (HealthNodeSelector) task;
        currentTask.set(healthNodeSelector);
        String nodeId = clusterService.localNode().getId();
        logger.info("Node [" + nodeId + "] is selected as the current health node.");
    }

    @Override
    protected HealthNodeSelector createTask(
        long id,
        String type,
        String action,
        TaskId parentTaskId,
        PersistentTasksCustomMetadata.PersistentTask<HealthNodeSelectorTaskParams> taskInProgress,
        Map<String, String> headers
    ) {
        return new HealthNodeSelector(id, type, action, getDescription(taskInProgress), parentTaskId, headers);
    }

    /**
     * Returns the node id from the eligible health nodes
     */
    @Override
    public PersistentTasksCustomMetadata.Assignment getAssignment(
        HealthNodeSelectorTaskParams params,
        Collection<DiscoveryNode> candidateNodes,
        ClusterState clusterState
    ) {
        DiscoveryNode discoveryNode = selectLeastLoadedNode(clusterState, candidateNodes, DiscoveryNode::isHealthNode);
        if (discoveryNode == null) {
            return NO_NODE_FOUND;
        } else {
            return new PersistentTasksCustomMetadata.Assignment(discoveryNode.getId(), "");
        }
    }

    void createTask() {
        persistentTasksService.sendStartRequest(
            HEALTH_NODE_SELECTOR_TASK_NAME,
            HEALTH_NODE_SELECTOR_TASK_NAME,
            new HealthNodeSelectorTaskParams(),
            ActionListener.wrap(r -> logger.info("Created the health node selector task"), e -> {
                Throwable t = e instanceof RemoteTransportException ? e.getCause() : e;
                if (t instanceof ResourceAlreadyExistsException == false) {
                    logger.error("failed to create health node selector task", e);
                }
            })
        );
    }

    void stop() {
        HealthNodeSelector task = currentTask.get();
        if (task != null) {
            String nodeId = clusterService.localNode().getId();
            task.markAsLocallyAborted("Node [" + nodeId + "] is shutting down.");
            HealthNodeSelectorTaskExecutor.logger.info("Node [" + nodeId + "] is releasing health node selector task due to shutdown");
        }
    }

    public static List<NamedXContentRegistry.Entry> getNamedXContentParsers() {
        return List.of(
            new NamedXContentRegistry.Entry(
                PersistentTaskParams.class,
                new ParseField(HEALTH_NODE_SELECTOR_TASK_NAME),
                HealthNodeSelectorTaskParams::fromXContent
            )
        );
    }

    public static List<NamedWriteableRegistry.Entry> getNamedWriteables() {
        return List.of(
            new NamedWriteableRegistry.Entry(PersistentTaskParams.class, HEALTH_NODE_SELECTOR_TASK_NAME, HealthNodeSelectorTaskParams::new)
        );
    }
}
