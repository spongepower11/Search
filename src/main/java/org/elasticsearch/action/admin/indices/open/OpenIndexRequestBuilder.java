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

package org.elasticsearch.action.admin.indices.open;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.AcknowledgedRequestBuilder;
import org.elasticsearch.client.IndicesAdminClient;

/**
 * Builder for for open index request
 */
public class OpenIndexRequestBuilder extends AcknowledgedRequestBuilder<OpenIndexRequest, OpenIndexResponse, OpenIndexRequestBuilder, IndicesAdminClient> {

    public OpenIndexRequestBuilder(IndicesAdminClient indicesClient) {
        super(indicesClient, new OpenIndexRequest());
    }

    public OpenIndexRequestBuilder(IndicesAdminClient indicesClient, String... indices) {
        super(indicesClient, new OpenIndexRequest(indices));
    }

    /**
     * Sets the indices to be opened
     * @param indices the indices to be opened
     * @return the request itself
     */
    public OpenIndexRequestBuilder setIndices(String... indices) {
        request.indices(indices);
        return this;
    }

    /**
     * Specifies what type of requested indices to ignore and how to deal with wildcard indices expressions.
     * For example indices that don't exist.
     *
     * @param indicesOptions the desired behaviour regarding indices to ignore and wildcard indices expressions
     * @return the request itself
     */
    public OpenIndexRequestBuilder setIndicesOptions(IndicesOptions indicesOptions) {
        request.indicesOptions(indicesOptions);
        return this;
    }

    @Override
    protected void doExecute(ActionListener<OpenIndexResponse> listener) {
        client.open(request, listener);
    }
}
