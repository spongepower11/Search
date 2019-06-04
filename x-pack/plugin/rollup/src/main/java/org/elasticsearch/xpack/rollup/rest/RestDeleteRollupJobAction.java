/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.rollup.rest;

import org.apache.logging.log4j.LogManager;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.core.rollup.action.DeleteRollupJobAction;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.DELETE;

public class RestDeleteRollupJobAction extends BaseRestHandler {

    private static final DeprecationLogger deprecationLogger = new DeprecationLogger(LogManager.getLogger(RestDeleteRollupJobAction.class));

    public static final ParseField ID = new ParseField("id");


    public RestDeleteRollupJobAction(Settings settings, RestController controller) {
        super(settings);
        // TODO: remove deprecated endpoint in 8.0.0
        controller.registerWithDeprecatedHandler(
                DELETE, "/_rollup/job/{id}", this,
                DELETE, "/_xpack/rollup/job/{id}/", deprecationLogger);
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        String id = restRequest.param(ID.getPreferredName());
        boolean deleteData = restRequest.paramAsBoolean(DeleteRollupJobAction.DELETE_DATA.getPreferredName(), false);
        DeleteRollupJobAction.Request request = new DeleteRollupJobAction.Request(id, deleteData);

        return channel -> client.execute(DeleteRollupJobAction.INSTANCE, request,
            new RestToXContentListener<>(channel) {
                @Override
                protected RestStatus getStatus(DeleteRollupJobAction.Response response) {
                    if (response.getNodeFailures().size() > 0 || response.getTaskFailures().size() > 0) {
                        return RestStatus.INTERNAL_SERVER_ERROR;
                    }
                    return RestStatus.OK;
                }
            });
    }

    @Override
    public String getName() {
        return "delete_rollup_job";
    }

}
