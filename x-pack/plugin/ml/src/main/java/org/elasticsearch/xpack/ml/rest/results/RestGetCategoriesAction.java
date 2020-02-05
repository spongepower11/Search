/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.rest.results;

import org.apache.logging.log4j.LogManager;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.core.action.util.PageParams;
import org.elasticsearch.xpack.core.ml.action.GetCategoriesAction;
import org.elasticsearch.xpack.core.ml.action.GetCategoriesAction.Request;
import org.elasticsearch.xpack.core.ml.job.config.Job;
import org.elasticsearch.xpack.ml.MachineLearning;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestGetCategoriesAction extends BaseRestHandler {

    private static final DeprecationLogger deprecationLogger =
        new DeprecationLogger(LogManager.getLogger(RestGetCategoriesAction.class));

    @Override
    public Map<String, List<Method>> handledMethodsAndPaths() {
        return Collections.emptyMap();
    }

    @Override
    public List<ReplacedRestApi> replacedMethodsAndPaths() {
        // TODO: remove deprecated endpoint in 8.0.0
        return Collections.unmodifiableList(Arrays.asList(
            new ReplacedRestApi(
                GET, MachineLearning.BASE_PATH + "anomaly_detectors/{" + Job.ID.getPreferredName() + "}/results/categories/{"
                + Request.CATEGORY_ID.getPreferredName() + "}",
                GET, MachineLearning.PRE_V7_BASE_PATH + "anomaly_detectors/{" + Job.ID.getPreferredName() + "}/results/categories/{"
                + Request.CATEGORY_ID.getPreferredName() + "}",
                deprecationLogger),
            new ReplacedRestApi(POST, MachineLearning.BASE_PATH + "anomaly_detectors/{" + Job.ID.getPreferredName() +
                "}/results/categories/{" + Request.CATEGORY_ID.getPreferredName() + "}",
                POST, MachineLearning.PRE_V7_BASE_PATH + "anomaly_detectors/{" + Job.ID.getPreferredName() +
                "}/results/categories/{" + Request.CATEGORY_ID.getPreferredName() + "}",
                deprecationLogger),
            new ReplacedRestApi(
                GET, MachineLearning.BASE_PATH + "anomaly_detectors/{" + Job.ID.getPreferredName() + "}/results/categories",
                GET, MachineLearning.PRE_V7_BASE_PATH + "anomaly_detectors/{" + Job.ID.getPreferredName() + "}/results/categories",
                deprecationLogger),
            new ReplacedRestApi(
                POST, MachineLearning.BASE_PATH + "anomaly_detectors/{" + Job.ID.getPreferredName() + "}/results/categories",
                POST, MachineLearning.PRE_V7_BASE_PATH + "anomaly_detectors/{" + Job.ID.getPreferredName() + "}/results/categories",
                deprecationLogger)
        ));
    }

    @Override
    public String getName() {
        return "ml_get_catagories_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        Request request;
        String jobId = restRequest.param(Job.ID.getPreferredName());
        Long categoryId = restRequest.hasParam(Request.CATEGORY_ID.getPreferredName()) ? Long.parseLong(
                restRequest.param(Request.CATEGORY_ID.getPreferredName())) : null;

        if (restRequest.hasContentOrSourceParam()) {
            XContentParser parser = restRequest.contentOrSourceParamParser();
            request = GetCategoriesAction.Request.parseRequest(jobId, parser);
            if (categoryId != null) {
                request.setCategoryId(categoryId);
            }
        } else {
            request = new Request(jobId);
            if (categoryId != null) {
                request.setCategoryId(categoryId);
            }
            if (restRequest.hasParam(Request.FROM.getPreferredName())
                    || restRequest.hasParam(Request.SIZE.getPreferredName())
                    || categoryId == null){

                request.setPageParams(new PageParams(
                        restRequest.paramAsInt(Request.FROM.getPreferredName(), PageParams.DEFAULT_FROM),
                        restRequest.paramAsInt(Request.SIZE.getPreferredName(), PageParams.DEFAULT_SIZE)
                ));
            }
        }

        return channel -> client.execute(GetCategoriesAction.INSTANCE, request, new RestToXContentListener<>(channel));
    }

}
