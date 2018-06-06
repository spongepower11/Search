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

package org.elasticsearch.nio;

import org.elasticsearch.nio.utils.ExceptionsHelper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The NioGroup is a group of selectors for interfacing with java nio. When it is started it will create the
 * configured number of selectors. Each selector will be running in a dedicated thread. Server connections
 * can be bound using the {@link #bindServerChannel(InetSocketAddress, ChannelFactory)} method. Client
 * connections can be opened using the {@link #openChannel(InetSocketAddress, ChannelFactory)} method.
 * <p>
 * The logic specific to a particular channel is provided by the {@link ChannelFactory} passed to the method
 * when the channel is created. This is what allows an NioGroup to support different channel types.
 */
public class NioGroup implements AutoCloseable {


    private final List<NioSelector> dedicatedAcceptors;
    private final RoundRobinSupplier<NioSelector> acceptorSupplier;

    private final List<NioSelector> selectors;
    private final RoundRobinSupplier<NioSelector> selectorSupplier;

    private final AtomicBoolean isOpen = new AtomicBoolean(true);

    /**
     * This will create an NioGroup with no dedicated acceptors. All server channels will be handled by the
     * same selectors that are handling child channels.
     *
     * @param threadFactory factory to create selector threads
     * @param selectorCount the number of selectors to be created
     * @param eventHandlerFunction function for creating event handlers
     * @throws IOException occurs if there is a problem while opening a java.nio.Selector
     */
    public NioGroup(ThreadFactory threadFactory, int selectorCount, Function<Supplier<NioSelector>, EventHandler> eventHandlerFunction)
        throws IOException {
        this(null, 0, threadFactory, selectorCount, eventHandlerFunction);
    }

    /**
     * This will create an NioGroup with dedicated acceptors. All server channels will be handled by a group
     * of selectors dedicated to accepting channels. These accepted channels will be handed off the
     * non-server selectors.
     *
     * @param acceptorThreadFactory factory to create acceptor selector threads
     * @param dedicatedAcceptorCount the number of dedicated acceptor selectors to be created
     * @param selectorThreadFactory factory to create non-acceptor selector threads
     * @param selectorCount the number of non-acceptor selectors to be created
     * @param eventHandlerFunction function for creating event handlers
     * @throws IOException occurs if there is a problem while opening a java.nio.Selector
     */
    public NioGroup(ThreadFactory acceptorThreadFactory, int dedicatedAcceptorCount, ThreadFactory selectorThreadFactory, int selectorCount,
                    Function<Supplier<NioSelector>, EventHandler> eventHandlerFunction) throws IOException {
        dedicatedAcceptors = new CopyOnWriteArrayList<>();
        selectors = new CopyOnWriteArrayList<>();

        try {
            for (int i = 0; i < selectorCount; ++i) {
                NioSelector selector = new NioSelector(eventHandlerFunction.apply(new RoundRobinSupplier<>(selectors, selectorCount)));
                selectors.add(selector);
            }

            for (int i = 0; i < dedicatedAcceptorCount; ++i) {
                NioSelector acceptor = new NioSelector(eventHandlerFunction.apply(new RoundRobinSupplier<>(selectors, selectorCount)));
                dedicatedAcceptors.add(acceptor);
            }

            startSelectors(selectors, selectorThreadFactory);
            startSelectors(dedicatedAcceptors, acceptorThreadFactory);

            if (dedicatedAcceptorCount != 0) {
                acceptorSupplier = new RoundRobinSupplier<>(dedicatedAcceptors, dedicatedAcceptorCount);
            } else {
                acceptorSupplier = new RoundRobinSupplier<>(selectors, selectorCount);
            }
            selectorSupplier = new RoundRobinSupplier<>(selectors, selectorCount);
            assert selectorCount == selectors.size() : "We need to have created all the selectors at this point.";
            assert dedicatedAcceptorCount == dedicatedAcceptors.size() : "We need to have created all the acceptors at this point.";
        } catch (Exception e) {
            try {
                close();
            } catch (Exception e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    public <S extends NioServerSocketChannel> S bindServerChannel(InetSocketAddress address, ChannelFactory<S, ?> factory)
        throws IOException {
        ensureOpen();
        return factory.openNioServerSocketChannel(address, acceptorSupplier);
    }

    public <S extends NioSocketChannel> S openChannel(InetSocketAddress address, ChannelFactory<?, S> factory) throws IOException {
        ensureOpen();
        return factory.openNioChannel(address, selectorSupplier);
    }

    @Override
    public void close() throws IOException {
        if (isOpen.compareAndSet(true, false)) {
            List<NioSelector> toClose = Stream.concat(dedicatedAcceptors.stream(), selectors.stream()).collect(Collectors.toList());
            List<IOException> closingExceptions = new ArrayList<>();
            for (NioSelector selector : toClose) {
                try {
                    selector.close();
                } catch (IOException e) {
                    closingExceptions.add(e);
                }
            }
            ExceptionsHelper.rethrowAndSuppress(closingExceptions);
        }
    }

    private static void startSelectors(Iterable<NioSelector> selectors, ThreadFactory threadFactory) {
        for (NioSelector selector : selectors) {
            if (selector.isRunning() == false) {
                threadFactory.newThread(selector::runLoop).start();
                try {
                    selector.isRunningFuture().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for selector to start.", e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    } else {
                        throw new RuntimeException("Exception during selector start.", e);
                    }
                }
            }
        }
    }

    private void ensureOpen() {
        if (isOpen.get() == false) {
            throw new IllegalStateException("NioGroup is closed.");
        }
    }
}
