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
package org.elasticsearch.transport;

import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.test.ESTestCase;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class RemoteConnectionManagerTests extends ESTestCase {

    private Transport transport;
    private ConnectionManager connectionManager;
    private RemoteConnectionManager remoteConnectionManager;
    private ConnectionManager.ConnectionValidator validator = (connection, profile, listener) -> listener.onResponse(null);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        transport = mock(Transport.class);
        connectionManager = new ConnectionManager(Settings.EMPTY, transport);
        remoteConnectionManager = new RemoteConnectionManager("remote-cluster", connectionManager);
    }

    @SuppressWarnings("unchecked")
    public void testGetConnection() {
        TransportAddress address = new TransportAddress(InetAddress.getLoopbackAddress(), 1000);

        doAnswer(invocationOnMock -> {
            ActionListener<Transport.Connection> listener = (ActionListener<Transport.Connection>) invocationOnMock.getArguments()[2];
            listener.onResponse(new TestRemoteConnection((DiscoveryNode) invocationOnMock.getArguments()[0]));
            return null;
        }).when(transport).openConnection(any(DiscoveryNode.class), any(ConnectionProfile.class), any(ActionListener.class));

        DiscoveryNode node1 = new DiscoveryNode("node-1", address, Version.CURRENT);
        remoteConnectionManager.connectToNode(node1, null, validator, PlainActionFuture.newFuture());
        DiscoveryNode node2 = new DiscoveryNode("node-2", address, Version.CURRENT.minimumCompatibilityVersion());
        remoteConnectionManager.connectToNode(node2, null, validator, PlainActionFuture.newFuture());

        assertEquals(node1, remoteConnectionManager.getRemoteConnection(node1).getNode());
        assertEquals(node2, remoteConnectionManager.getRemoteConnection(node2).getNode());

        DiscoveryNode node4 = new DiscoveryNode("node-4", address, Version.CURRENT);
        assertThat(remoteConnectionManager.getRemoteConnection(node4), instanceOf(RemoteConnectionManager.ProxyConnection.class));

        // Test round robin
        Set<Version> versions = new HashSet<>();
        versions.add(remoteConnectionManager.getRemoteConnection(node4).getVersion());
        versions.add(remoteConnectionManager.getRemoteConnection(node4).getVersion());

        assertThat(versions, hasItems(Version.CURRENT, Version.CURRENT.minimumCompatibilityVersion()));

        // Test that the connection is cleared from the round robin list when it is closed
        remoteConnectionManager.getRemoteConnection(node1).close();

        versions.clear();
        versions.add(remoteConnectionManager.getRemoteConnection(node4).getVersion());
        versions.add(remoteConnectionManager.getRemoteConnection(node4).getVersion());

        assertThat(versions, hasItems(Version.CURRENT.minimumCompatibilityVersion()));
        assertEquals(1, versions.size());
    }

    private static class TestRemoteConnection extends CloseableConnection {

        private final DiscoveryNode node;

        private TestRemoteConnection(DiscoveryNode node) {
            this.node = node;
        }

        @Override
        public DiscoveryNode getNode() {
            return node;
        }

        @Override
        public void sendRequest(long requestId, String action, TransportRequest request, TransportRequestOptions options)
            throws TransportException {
        }
    }
}
