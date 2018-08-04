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

package org.elasticsearch.cluster.coordination;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.discovery.ConfiguredHostsResolver;
import org.elasticsearch.discovery.zen.UnicastHostsProvider;
import org.elasticsearch.threadpool.ThreadPool.Names;
import org.elasticsearch.transport.TransportException;
import org.elasticsearch.transport.TransportResponseHandler;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static org.elasticsearch.common.util.concurrent.ConcurrentCollections.newConcurrentMap;

public abstract class PeerFinder extends AbstractLifecycleComponent {

    public static final String REQUEST_PEERS_ACTION_NAME = "internal:discovery/requestpeers";

    // the time between attempts to find all peers
    public static final Setting<TimeValue> DISCOVERY_FIND_PEERS_INTERVAL_SETTING =
        Setting.timeSetting("discovery.find_peers_interval",
            TimeValue.timeValueMillis(1000), TimeValue.timeValueMillis(1), Setting.Property.NodeScope);

    private final TimeValue findPeersDelay;

    private final Object mutex = new Object();
    private final TransportService transportService;
    private final FutureExecutor futureExecutor;
    private final TransportAddressConnector transportAddressConnector;
    private final ConfiguredHostsResolver configuredHostsResolver;

    private Optional<ActivePeerFinder> peerFinder = Optional.empty();
    private volatile long currentTerm;
    private Optional<DiscoveryNode> leader = Optional.empty();

    PeerFinder(Settings settings, TransportService transportService, UnicastHostsProvider hostsProvider,
               FutureExecutor futureExecutor, TransportAddressConnector transportAddressConnector,
               Supplier<ExecutorService> executorServiceFactory) {
        super(settings);
        findPeersDelay = DISCOVERY_FIND_PEERS_INTERVAL_SETTING.get(settings);
        this.transportService = transportService;
        this.futureExecutor = futureExecutor;
        this.transportAddressConnector = transportAddressConnector;
        configuredHostsResolver = new ConfiguredHostsResolver(settings, transportService, hostsProvider, executorServiceFactory);

        transportService.registerRequestHandler(REQUEST_PEERS_ACTION_NAME, Names.GENERIC, false, false,
            PeersRequest::new,
            (request, channel, task) -> channel.sendResponse(handlePeersRequest(request)));
    }

    public Iterable<DiscoveryNode> getFoundPeers() {
        synchronized (mutex) {
            return getActivePeerFinder().getKnownPeers();
        }
    }

    public void activate(final DiscoveryNodes lastAcceptedNodes) {
        if (lifecycle.started() == false) {
            logger.debug("ignoring activation, not started");
            return;
        }

        logger.trace("activating PeerFinder {}", lastAcceptedNodes);

        synchronized (mutex) {
            assert peerFinder.isPresent() == false;
            peerFinder = Optional.of(new ActivePeerFinder(lastAcceptedNodes));
            peerFinder.get().start();
            leader = Optional.empty();
        }
    }

    // exposed to subclasses for testing
    protected final boolean holdsLock() {
        return Thread.holdsLock(mutex);
    }

    public void deactivate(DiscoveryNode leader) {
        synchronized (mutex) {
            if (peerFinder.isPresent()) {
                logger.trace("deactivating PeerFinder");
                assert peerFinder.get().running;
                peerFinder.get().stop();
                peerFinder = Optional.empty();
            }
            this.leader = Optional.of(leader);
        }
    }

    private ActivePeerFinder getActivePeerFinder() {
        assert holdsLock() : "Peerfinder mutex not held";
        final ActivePeerFinder activePeerFinder = this.peerFinder.get();
        assert activePeerFinder.running;
        return activePeerFinder;
    }

    PeersResponse handlePeersRequest(PeersRequest peersRequest) {
        synchronized (mutex) {
            assert peersRequest.getSourceNode().equals(getLocalNode()) == false;
            if (isActive()) {
                final ActivePeerFinder activePeerFinder = getActivePeerFinder();
                activePeerFinder.startProbe(peersRequest.getSourceNode().getAddress());
                peersRequest.getKnownPeers().stream().map(DiscoveryNode::getAddress).forEach(activePeerFinder::startProbe);
                return new PeersResponse(Optional.empty(), activePeerFinder.getKnownPeers(), currentTerm);
            } else {
                return new PeersResponse(leader, Collections.emptyList(), currentTerm);
            }
        }
    }

    public boolean isActive() {
        synchronized (mutex) {
            if (peerFinder.isPresent()) {
                assert peerFinder.get().running;
                return true;
            } else {
                return false;
            }
        }
    }

    public void setCurrentTerm(long currentTerm) {
        this.currentTerm = currentTerm;
    }

    private DiscoveryNode getLocalNode() {
        final DiscoveryNode localNode = transportService.getLocalNode();
        assert localNode != null;
        return localNode;
    }

    @Override
    protected void doStart() {
        configuredHostsResolver.start();
    }

    @Override
    protected void doStop() {
        configuredHostsResolver.stop();
    }

    @Override
    protected void doClose() {
        configuredHostsResolver.close();
    }

    /**
     * Called on receipt of a PeersResponse from a node that believes it's an active leader, which this node should therefore try and join.
     */
    protected abstract void onActiveMasterFound(DiscoveryNode masterNode, long term);

    public interface TransportAddressConnector {
        /**
         * Identify the node at the given address and, if it is a master node and not the local node then establish a full connection to it.
         */
        void connectToRemoteMasterNode(TransportAddress transportAddress, ActionListener<DiscoveryNode> listener);
    }

    private class ActivePeerFinder {
        private final DiscoveryNodes lastAcceptedNodes;
        boolean running;
        private final Map<TransportAddress, Peer> peersByAddress = newConcurrentMap();

        ActivePeerFinder(DiscoveryNodes lastAcceptedNodes) {
            this.lastAcceptedNodes = lastAcceptedNodes;
        }

        void start() {
            assert holdsLock() : "PeerFinder mutex not held";
            assert running == false;
            running = true;
            handleWakeUpUnderLock();
        }

        void stop() {
            assert holdsLock() : "PeerFinder mutex not held";
            assert running;
            running = false;
        }

        private void handleWakeUp() {
            synchronized (mutex) {
                handleWakeUpUnderLock();
            }
        }

        List<DiscoveryNode> getKnownPeers() {
            assert holdsLock() : "PeerFinder mutex not held";
            List<DiscoveryNode> knownPeers = new ArrayList<>(peersByAddress.size());
            for (final Peer peer : peersByAddress.values()) {
                DiscoveryNode peerNode = peer.getDiscoveryNode();
                if (peerNode != null) {
                    knownPeers.add(peerNode);
                }
            }
            return knownPeers;
        }

        private Peer createConnectingPeer(TransportAddress transportAddress) {
            Peer peer = new Peer(transportAddress);
            peer.establishConnection();
            return peer;
        }

        private void handleWakeUpUnderLock() {
            assert holdsLock() : "PeerFinder mutex not held";

            if (running == false) {
                logger.trace("ActivePeerFinder#handleWakeUp(): not running");
                return;
            }

            for (final Peer peer : peersByAddress.values()) {
                peer.handleWakeUp();
            }

            for (ObjectCursor<DiscoveryNode> discoveryNodeObjectCursor : lastAcceptedNodes.getMasterNodes().values()) {
                startProbe(discoveryNodeObjectCursor.value.getAddress());
            }

            configuredHostsResolver.resolveConfiguredHosts(providedAddresses -> {
                synchronized (mutex) {
                    logger.trace("ActivePeerFinder#handleNextWakeUp(): probing resolved transport addresses {}", providedAddresses);
                    providedAddresses.forEach(ActivePeerFinder.this::startProbe);
                }
            });

            futureExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    handleWakeUp();
                }

                @Override
                public String toString() {
                    return "ActivePeerFinder::handleWakeUp";
                }
            }, findPeersDelay);
        }

        void startProbe(DiscoveryNode discoveryNode) {
            startProbe(discoveryNode.getAddress());
        }

        void startProbe(TransportAddress transportAddress) {
            assert holdsLock() : "PeerFinder mutex not held";
            if (running == false) {
                logger.trace("startProbe({}) not running", transportAddress);
                return;
            }

            if (transportAddress.equals(getLocalNode().getAddress())) {
                logger.trace("startProbe({}) not probing local node", transportAddress);
                return;
            }

            peersByAddress.computeIfAbsent(transportAddress, this::createConnectingPeer);
        }

        private class Peer {
            private final TransportAddress transportAddress;
            private SetOnce<DiscoveryNode> discoveryNode = new SetOnce<>();
            private volatile boolean peersRequestInFlight;

            Peer(TransportAddress transportAddress) {
                this.transportAddress = transportAddress;
            }

            DiscoveryNode getDiscoveryNode() {
                return discoveryNode.get();
            }

            void handleWakeUp() {
                assert holdsLock() : "PeerFinder mutex not held";

                if (running == false) {
                    return;
                }

                final DiscoveryNode discoveryNode = getDiscoveryNode();
                // may be null if connection not yet established

                if (discoveryNode != null) {
                    if (transportService.nodeConnected(discoveryNode)) {
                        if (peersRequestInFlight == false) {
                            requestPeers();
                        }
                    } else {
                        logger.trace("{} no longer connected to {}", this, discoveryNode);
                        removePeer();
                    }
                }
            }

            void establishConnection() {
                assert holdsLock() : "PeerFinder mutex not held";
                assert getDiscoveryNode() == null : "unexpectedly connected to " + getDiscoveryNode();
                assert running;

                logger.trace("{} attempting connection", this);
                transportAddressConnector.connectToRemoteMasterNode(transportAddress, new ActionListener<DiscoveryNode>() {
                    @Override
                    public void onResponse(DiscoveryNode remoteNode) {
                        assert remoteNode.isMasterNode() : remoteNode + " is not master-eligible";
                        assert remoteNode.equals(getLocalNode()) == false : remoteNode + " is the local node";
                        synchronized (mutex) {
                            if (running) {
                                assert discoveryNode.get() == null : "discoveryNode unexpectedly already set to " + discoveryNode.get();
                                discoveryNode.set(remoteNode);
                                requestPeers();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        logger.debug(() -> new ParameterizedMessage("{} connection failed", Peer.this), e);
                        removePeer();
                    }
                });
            }

            private void removePeer() {
                final Peer removed = peersByAddress.remove(transportAddress);
                assert removed == Peer.this;
            }

            private void requestPeers() {
                assert holdsLock() : "PeerFinder mutex not held";
                assert peersRequestInFlight == false : "PeersRequest already in flight";
                assert running;

                final DiscoveryNode discoveryNode = getDiscoveryNode();
                assert discoveryNode != null : "cannot request peers without first connecting";

                logger.trace("{} requesting peers from {}", this, discoveryNode);
                peersRequestInFlight = true;

                List<DiscoveryNode> knownNodes = getKnownPeers();

                transportService.sendRequest(discoveryNode, REQUEST_PEERS_ACTION_NAME,
                    new PeersRequest(getLocalNode(), knownNodes),
                    new TransportResponseHandler<PeersResponse>() {

                        @Override
                        public PeersResponse read(StreamInput in) throws IOException {
                            return new PeersResponse(in);
                        }

                        @Override
                        public void handleResponse(PeersResponse response) {
                            logger.trace("{} received {} from {}", this, response, discoveryNode);
                            synchronized (mutex) {
                                if (running == false) {
                                    return;
                                }

                                peersRequestInFlight = false;

                                if (response.getMasterNode().isPresent()) {
                                    final DiscoveryNode masterNode = response.getMasterNode().get();
                                    if (masterNode.equals(discoveryNode) == false) {
                                        startProbe(masterNode);
                                    }
                                } else {
                                    response.getKnownPeers().stream().map(DiscoveryNode::getAddress)
                                        .forEach(ActivePeerFinder.this::startProbe);
                                }
                            }

                            if (response.getMasterNode().equals(Optional.of(discoveryNode))) {
                                // Must not hold lock here to avoid deadlock
                                assert holdsLock() == false : "PeerFinder mutex is held in error";
                                onActiveMasterFound(discoveryNode, response.getTerm());
                            }
                        }

                        @Override
                        public void handleException(TransportException exp) {
                            peersRequestInFlight = false;
                            logger.debug("PeersRequest failed", exp);
                        }

                        @Override
                        public String executor() {
                            return Names.GENERIC;
                        }
                    });
            }

            @Override
            public String toString() {
                return "Peer{" + transportAddress + " peersRequestInFlight=" + peersRequestInFlight + "}";
            }
        }
    }
}
