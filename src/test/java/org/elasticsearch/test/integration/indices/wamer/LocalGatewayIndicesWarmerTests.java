/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.test.integration.indices.wamer;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.gateway.Gateway;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.internal.InternalNode;
import org.elasticsearch.search.warmer.IndexWarmersMetaData;
import org.elasticsearch.test.integration.AbstractNodesTests;
import org.hamcrest.Matchers;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 */
public class LocalGatewayIndicesWarmerTests extends AbstractNodesTests {

    private final ESLogger logger = Loggers.getLogger(LocalGatewayIndicesWarmerTests.class);

    @AfterMethod
    public void cleanAndCloseNodes() throws Exception {
        for (int i = 0; i < 10; i++) {
            if (node("node" + i) != null) {
                node("node" + i).stop();
                // since we store (by default) the index snapshot under the gateway, resetting it will reset the index data as well
                if (((InternalNode) node("node" + i)).injector().getInstance(NodeEnvironment.class).hasNodeFile()) {
                    ((InternalNode) node("node" + i)).injector().getInstance(Gateway.class).reset();
                }
            }
        }
        closeAllNodes();
    }

    @Test
    public void testStatePersistence() throws Exception {
        logger.info("--> cleaning nodes");
        buildNode("node1", settingsBuilder().put("gateway.type", "local"));
        buildNode("node2", settingsBuilder().put("gateway.type", "local"));
        cleanAndCloseNodes();

        logger.info("--> starting 1 nodes");
        startNode("node1", settingsBuilder().put("gateway.type", "local"));

        logger.info("--> putting two templates");
        client("node1").admin().indices().prepareCreate("test")
                .setSettings(ImmutableSettings.settingsBuilder().put("index.number_of_shards", 1))
                .execute().actionGet();

        client("node1").admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();

        client("node1").admin().indices().preparePutWarmer("warmer_1")
                .setSearchRequest(client("node1").prepareSearch("test").setQuery(QueryBuilders.termQuery("field", "value1")))
                .execute().actionGet();
        client("node1").admin().indices().preparePutWarmer("warmer_2")
                .setSearchRequest(client("node1").prepareSearch("test").setQuery(QueryBuilders.termQuery("field", "value2")))
                .execute().actionGet();

        logger.info("--> verify warmers are registered in cluster state");
        ClusterState clusterState = client("node1").admin().cluster().prepareState().execute().actionGet().state();
        IndexWarmersMetaData warmersMetaData = clusterState.metaData().index("test").custom(IndexWarmersMetaData.TYPE);
        assertThat(warmersMetaData, Matchers.notNullValue());
        assertThat(warmersMetaData.entries().size(), equalTo(2));

        logger.info("--> close the node");
        closeNode("node1");

        logger.info("--> starting the node again...");
        startNode("node1", settingsBuilder().put("gateway.type", "local"));

        ClusterHealthResponse healthResponse = client("node1").admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
        assertThat(healthResponse.timedOut(), equalTo(false));

        logger.info("--> verify warmers are recovered");
        clusterState = client("node1").admin().cluster().prepareState().execute().actionGet().state();
        IndexWarmersMetaData recoveredWarmersMetaData = clusterState.metaData().index("test").custom(IndexWarmersMetaData.TYPE);
        assertThat(recoveredWarmersMetaData.entries().size(), equalTo(warmersMetaData.entries().size()));
        for (int i = 0; i < warmersMetaData.entries().size(); i++) {
            assertThat(recoveredWarmersMetaData.entries().get(i).name(), equalTo(warmersMetaData.entries().get(i).name()));
            assertThat(recoveredWarmersMetaData.entries().get(i).source(), equalTo(warmersMetaData.entries().get(i).source()));
        }

        logger.info("--> delete warmer warmer_1");
        client("node1").admin().indices().prepareDeleteWarmer().setIndices("test").setName("warmer_1").execute().actionGet();

        logger.info("--> verify warmers (delete) are registered in cluster state");
        clusterState = client("node1").admin().cluster().prepareState().execute().actionGet().state();
        warmersMetaData = clusterState.metaData().index("test").custom(IndexWarmersMetaData.TYPE);
        assertThat(warmersMetaData, Matchers.notNullValue());
        assertThat(warmersMetaData.entries().size(), equalTo(1));

        logger.info("--> close the node");
        closeNode("node1");

        logger.info("--> starting the node again...");
        startNode("node1", settingsBuilder().put("gateway.type", "local"));

        healthResponse = client("node1").admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
        assertThat(healthResponse.timedOut(), equalTo(false));

        logger.info("--> verify warmers are recovered");
        clusterState = client("node1").admin().cluster().prepareState().execute().actionGet().state();
        recoveredWarmersMetaData = clusterState.metaData().index("test").custom(IndexWarmersMetaData.TYPE);
        assertThat(recoveredWarmersMetaData.entries().size(), equalTo(warmersMetaData.entries().size()));
        for (int i = 0; i < warmersMetaData.entries().size(); i++) {
            assertThat(recoveredWarmersMetaData.entries().get(i).name(), equalTo(warmersMetaData.entries().get(i).name()));
            assertThat(recoveredWarmersMetaData.entries().get(i).source(), equalTo(warmersMetaData.entries().get(i).source()));
        }
    }
}
