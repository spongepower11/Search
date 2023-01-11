/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.action;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.TaskOperationFailure;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.GroupedActionListener;
import org.elasticsearch.action.support.tasks.TransportTasksAction;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.CancellableTask;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.ml.action.InferTrainedModelDeploymentAction;
import org.elasticsearch.xpack.core.ml.inference.results.InferenceResults;
import org.elasticsearch.xpack.ml.inference.deployment.TrainedModelDeploymentTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TransportInferTrainedModelDeploymentAction extends TransportTasksAction<
    TrainedModelDeploymentTask,
    InferTrainedModelDeploymentAction.Request,
    InferTrainedModelDeploymentAction.Response,
    InferTrainedModelDeploymentAction.Response> {

    @Inject
    public TransportInferTrainedModelDeploymentAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters
    ) {
        super(
            InferTrainedModelDeploymentAction.NAME,
            clusterService,
            transportService,
            actionFilters,
            InferTrainedModelDeploymentAction.Request::new,
            InferTrainedModelDeploymentAction.Response::new,
            InferTrainedModelDeploymentAction.Response::new,
            ThreadPool.Names.SAME
        );
    }

    @Override
    protected InferTrainedModelDeploymentAction.Response newResponse(
        InferTrainedModelDeploymentAction.Request request,
        List<InferTrainedModelDeploymentAction.Response> tasks,
        List<TaskOperationFailure> taskOperationFailures,
        List<FailedNodeException> failedNodeExceptions
    ) {
        if (taskOperationFailures.isEmpty() == false) {
            throw org.elasticsearch.ExceptionsHelper.convertToElastic(taskOperationFailures.get(0).getCause());
        } else if (failedNodeExceptions.isEmpty() == false) {
            throw org.elasticsearch.ExceptionsHelper.convertToElastic(failedNodeExceptions.get(0));
        } else if (tasks.isEmpty()) {
            throw new ElasticsearchStatusException(
                "Unable to find deployment task for model [{}] please stop and start the deployment or try again momentarily",
                RestStatus.NOT_FOUND,
                request.getModelId()
            );
        } else {
            assert tasks.size() == 1;
            return tasks.get(0);
        }
    }

    @Override
    protected void taskOperation(
        Task actionTask,
        InferTrainedModelDeploymentAction.Request request,
        TrainedModelDeploymentTask task,
        ActionListener<InferTrainedModelDeploymentAction.Response> listener
    ) {
        assert actionTask instanceof CancellableTask : "task [" + actionTask + "] not cancellable";

        // Multiple documents to infer on, wait for all results
        ActionListener<Collection<InferenceResults>> collectingListener = ActionListener.wrap(
            pyTorchResults -> { listener.onResponse(new InferTrainedModelDeploymentAction.Response(new ArrayList<>(pyTorchResults))); },
            listener::onFailure
        );

        GroupedActionListener<InferenceResults> groupedListener = new GroupedActionListener<>(collectingListener, request.getDocs().size());
        for (var doc : request.getDocs()) {
            task.infer(doc, request.getUpdate(), request.isSkipQueue(), request.getInferenceTimeout(), actionTask, groupedListener);
        }
    }
}
