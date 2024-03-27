/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nodescapabilities;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.capabilities.NodesCapabilitiesRequest;
import org.elasticsearch.action.admin.cluster.node.capabilities.NodesCapabilitiesResponse;
import org.elasticsearch.test.ESIntegTestCase;

import java.io.IOException;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST, numDataNodes = 0)
public class SimpleNodesCapabilitiesIT extends ESIntegTestCase {

    public void testNodesCapabilities() throws IOException {
        internalCluster().startNodes(2);

        ClusterHealthResponse clusterHealth = clusterAdmin().prepareHealth().setWaitForGreenStatus().setWaitForNodes("2").get();
        logger.info("--> done cluster_health, status {}", clusterHealth.getStatus());

        // check we support the capabilities API itself. Which we do.
        NodesCapabilitiesResponse response = clusterAdmin().nodesCapabilities(new NodesCapabilitiesRequest().path("_capabilities"))
            .actionGet();
        assertThat(response.getNodes(), hasSize(2));
        assertThat(response.isSupported(), is(true));
    }
}
