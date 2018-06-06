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

import org.apache.http.Header;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsResponse;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

/**
 * A wrapper for the {@link RestHighLevelClient} that provides methods for accessing the Cluster API.
 * <p>
 * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster.html">Cluster API on elastic.co</a>
 */
public final class ClusterClient {
    private final RestHighLevelClient restHighLevelClient;

    ClusterClient(RestHighLevelClient restHighLevelClient) {
        this.restHighLevelClient = restHighLevelClient;
    }

    /**
     * Updates cluster wide specific settings using the Cluster Update Settings API
     * <p>
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-update-settings.html"> Cluster Update Settings
     * API on elastic.co</a>
     */
    public ClusterUpdateSettingsResponse putSettings(ClusterUpdateSettingsRequest clusterUpdateSettingsRequest, Header... headers)
            throws IOException {
        return restHighLevelClient.performRequestAndParseEntity(clusterUpdateSettingsRequest, RequestConverters::clusterPutSettings,
                ClusterUpdateSettingsResponse::fromXContent, emptySet(), headers);
    }

    /**
     * Asynchronously updates cluster wide specific settings using the Cluster Update Settings API
     * <p>
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-update-settings.html"> Cluster Update Settings
     * API on elastic.co</a>
     */
    public void putSettingsAsync(ClusterUpdateSettingsRequest clusterUpdateSettingsRequest,
            ActionListener<ClusterUpdateSettingsResponse> listener, Header... headers) {
        restHighLevelClient.performRequestAsyncAndParseEntity(clusterUpdateSettingsRequest, RequestConverters::clusterPutSettings,
                ClusterUpdateSettingsResponse::fromXContent, listener, emptySet(), headers);
    }

    /**
     * Get cluster health using the Cluster Health API
     * <p>
     * See
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-health.html"> Cluster Health API on elastic.co</a>
     * <p>
     * If timeout occurred, {@link ClusterHealthResponse} will have isTimedOut() == true and status() == RestStatus.REQUEST_TIMEOUT
     */
    public ClusterHealthResponse health(ClusterHealthRequest healthRequest, Header... headers) throws IOException {
        return restHighLevelClient.performRequestAndParseEntity(healthRequest, RequestConverters::clusterHealth,
                ClusterHealthResponse::fromXContent, singleton(RestStatus.REQUEST_TIMEOUT.getStatus()), headers);
    }

    /**
     * Asynchronously get cluster health using the Cluster Health API
     * <p>
     * See
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-health.html"> Cluster Health API on elastic.co</a>
     * If timeout occurred, {@link ClusterHealthResponse} will have isTimedOut() == true and status() == RestStatus.REQUEST_TIMEOUT
     */
    public void healthAsync(ClusterHealthRequest healthRequest, ActionListener<ClusterHealthResponse> listener, Header... headers) {
        restHighLevelClient.performRequestAsyncAndParseEntity(healthRequest, RequestConverters::clusterHealth,
                ClusterHealthResponse::fromXContent, listener, singleton(RestStatus.REQUEST_TIMEOUT.getStatus()), headers);
    }
}
