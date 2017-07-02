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

package org.elasticsearch.transport.nio.channel;

import org.elasticsearch.transport.nio.SocketEventHandler;
import org.elasticsearch.transport.nio.SocketSelector;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;

public class NioSocketChannelTests extends AbstractNioChannelTestCase {

    private InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
    private SocketSelector selector;
    private Thread thread;

    @Before
    @SuppressWarnings("unchecked")
    public void startSelector() throws IOException {
        selector = new SocketSelector(new SocketEventHandler(logger, mock(BiConsumer.class)));
        thread = new Thread(selector::runLoop);
        thread.start();
        selector.isRunningFuture().actionGet();
    }

    @After
    public void stopSelector() throws IOException, InterruptedException {
        selector.close();
        thread.join();
    }

    @Override
    public NioChannel channelToClose(Consumer<NioChannel> closeListener) throws IOException {
        InetSocketAddress address = new InetSocketAddress(loopbackAddress, mockServerSocket.getLocalPort());
        return channelFactory.openNioChannel(address, selector, closeListener);
    }

    public void testConnectSucceeds() throws IOException, InterruptedException {
        InetSocketAddress remoteAddress = new InetSocketAddress(loopbackAddress, mockServerSocket.getLocalPort());
        NioSocketChannel socketChannel = channelFactory.openNioChannel(remoteAddress, selector);

        ConnectFuture connectFuture = socketChannel.getConnectFuture();
        assertTrue(connectFuture.awaitConnectionComplete(100, TimeUnit.SECONDS));

        assertTrue(socketChannel.isConnectComplete());
        assertTrue(socketChannel.isOpen());
        assertFalse(connectFuture.connectFailed());
        assertNull(connectFuture.getException());
    }

    public void testConnectFails() throws IOException, InterruptedException {
        int port = mockServerSocket.getLocalPort() == 9876 ? 9877 : 9876;
        InetSocketAddress remoteAddress = new InetSocketAddress(loopbackAddress, port);
        NioSocketChannel socketChannel = channelFactory.openNioChannel(remoteAddress, selector);

        ConnectFuture connectFuture = socketChannel.getConnectFuture();
        assertFalse(connectFuture.awaitConnectionComplete(100, TimeUnit.SECONDS));

        assertFalse(socketChannel.isConnectComplete());
        // Even if connection fails the channel is 'open' until close() is called
        assertTrue(socketChannel.isOpen());
        assertTrue(connectFuture.connectFailed());
        assertThat(connectFuture.getException(), instanceOf(ConnectException.class));
    }
}
