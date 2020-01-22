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

package org.elasticsearch.rest.action.admin.cluster.dangling;

import org.elasticsearch.action.admin.indices.dangling.DeleteDanglingIndexRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.DELETE;

public class RestDeleteDanglingIndexAction extends BaseRestHandler {
    public RestDeleteDanglingIndexAction(RestController controller) {
        controller.registerHandler(DELETE, "/_dangling/{index_uuid}", this);
    }

    @Override
    public String getName() {
        return "delete_dangling_index";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, NodeClient client) throws IOException {
        String indexUUID = request.param("index_uuid");
        boolean acceptDataLoss = false;

        for (Map.Entry<String, String> entry : request.params().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if ("accept_data_loss".equals(key)) {
                acceptDataLoss = Boolean.parseBoolean(value);
            } else {
                throw new IllegalArgumentException("Unknown URL parameter [" + key + "]");
            }
        }

        final DeleteDanglingIndexRequest deleteRequest = new DeleteDanglingIndexRequest(indexUUID, acceptDataLoss);

        return channel -> client.admin().cluster().deleteDanglingIndex(deleteRequest, new RestStatusToXContentListener<>(channel));
    }
}
