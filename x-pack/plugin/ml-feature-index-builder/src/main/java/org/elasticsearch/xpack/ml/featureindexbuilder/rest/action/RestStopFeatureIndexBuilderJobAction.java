/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.featureindexbuilder.rest.action;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.ml.featureindexbuilder.DataFrame;
import org.elasticsearch.xpack.ml.featureindexbuilder.action.StopDataFrameJobAction;
import org.elasticsearch.xpack.ml.featureindexbuilder.job.DataFrameJob;

import java.io.IOException;

public class RestStopFeatureIndexBuilderJobAction extends BaseRestHandler {

    public RestStopFeatureIndexBuilderJobAction(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(RestRequest.Method.POST, DataFrame.BASE_PATH_JOBS_BY_ID + "_stop", this);
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        String id = restRequest.param(DataFrameJob.ID.getPreferredName());
        StopDataFrameJobAction.Request request = new StopDataFrameJobAction.Request(id);

        return channel -> client.execute(StopDataFrameJobAction.INSTANCE, request, new RestToXContentListener<>(channel));
    }

    @Override
    public String getName() {
        return "feature_index_builder_stop_job_action";
    }
}
