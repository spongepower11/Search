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

package org.elasticsearch.client;

import java.io.IOException;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;

/**
 * Stateless view into a {@link RestClient} with customized parameters.
 */
class RestClientView extends AbstractRestClientActions {
    /**
     * {@linkplain RestClient} to which to delegate all requests.
     */
    private final RestClient delegate;
    /**
     * Selects which hosts are valid destinations requests.
     */
    private final NodeSelector nodeSelector;

    RestClientView(RestClient delegate, NodeSelector nodeSelector) {
        this.delegate = delegate;
        this.nodeSelector = nodeSelector;
    }

    @Override
    final SyncResponseListener syncResponseListener() {
        return delegate.syncResponseListener();
    }

    @Override
    public final RestClientView withNodeSelector(final NodeSelector newNodeSelector) {
        return new RestClientView(delegate, new NodeSelector.And(nodeSelector, newNodeSelector));
    }

    @Override
    final void performRequestAsyncNoCatch(String method, String endpoint, Map<String, String> params,
            HttpEntity entity, HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory,
            ResponseListener responseListener, Header[] headers) throws IOException {
        delegate.performRequestAsyncNoCatch(method, endpoint, params, entity, httpAsyncResponseConsumerFactory,
            responseListener, nodeSelector, headers);
    }
}
