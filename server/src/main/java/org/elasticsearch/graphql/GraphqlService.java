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

package org.elasticsearch.graphql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionModule;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.graphql.api.GqlApi;
import org.elasticsearch.graphql.api.GqlElasticsearchApi;
import org.elasticsearch.graphql.gql.GqlServer;
import org.elasticsearch.graphql.rest.GraphqlRestHandler;
import org.elasticsearch.graphql.server.StartDemoServer;
import org.elasticsearch.plugins.NetworkPlugin;
import org.elasticsearch.rest.RestRequest;

import java.util.List;

final public class GraphqlService {
    private static final Logger logger = LogManager.getLogger(GraphqlService.class);

    private NodeClient client;
    private ActionModule actionModule;
    private List<NetworkPlugin> networkPlugins;

    private GqlApi api;
    private GqlServer gqlServer;
    private GraphqlRestHandler graphqlRestHandler;

    public GraphqlService(NodeClient client, ActionModule actionModule, List<NetworkPlugin> networkPlugins) {
        this.client = client;
        this.actionModule = actionModule;
        this.networkPlugins = networkPlugins;

        api = new GqlElasticsearchApi(client);
        gqlServer = new GqlServer(api);
        graphqlRestHandler = new GraphqlRestHandler(gqlServer);

        init();
    }

    private void init() {
        actionModule.getRestController().registerHandler(RestRequest.Method.POST, "/graphql", graphqlRestHandler);

        (new Thread(() -> new StartDemoServer(networkPlugins, gqlServer))).start();
    }
}
