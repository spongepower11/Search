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
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.metrics.CounterMetric;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends and receives transport-level connection handshakes. This class will send the initial handshake,
 * manage state/timeouts while the handshake is in transit, and handle the eventual response.
 */
final class TransportHandshaker {

    static final String HANDSHAKE_ACTION_NAME = "internal:tcp/handshake";
    private final ConcurrentMap<Long, HandshakeResponseHandler> pendingHandshakes = new ConcurrentHashMap<>();
    private final CounterMetric numHandshakes = new CounterMetric();

    private final Version version;
    private final ThreadPool threadPool;
    private final HandshakeRequestSender handshakeRequestSender;
    private final HandshakeResponseSender handshakeResponseSender;

    TransportHandshaker(Version version, ThreadPool threadPool, HandshakeRequestSender handshakeRequestSender,
                        HandshakeResponseSender handshakeResponseSender) {
        this.version = version;
        this.threadPool = threadPool;
        this.handshakeRequestSender = handshakeRequestSender;
        this.handshakeResponseSender = handshakeResponseSender;
    }

    void sendHandshake(long requestId, DiscoveryNode node, TcpChannel channel, TimeValue timeout, ActionListener<Version> listener) {
        numHandshakes.inc();
        final HandshakeResponseHandler handler = new HandshakeResponseHandler(requestId, version, listener);
        pendingHandshakes.put(requestId, handler);
        channel.addCloseListener(ActionListener.wrap(
            () -> handler.handleLocalException(new TransportException("handshake failed because connection reset"))));
        boolean success = false;
        try {
            // for the request we use the minCompatVersion since we don't know what's the version of the node we talk to
            // we also have no payload on the request but the response will contain the actual version of the node we talk
            // to as the payload.
            final Version minCompatVersion = version.minimumCompatibilityVersion();
            handshakeRequestSender.sendRequest(node, channel, requestId, minCompatVersion);

            threadPool.schedule(timeout, ThreadPool.Names.GENERIC,
                () -> handler.handleLocalException(new ConnectTransportException(node, "handshake_timeout[" + timeout + "]")));
            success = true;
        } catch (Exception e) {
            handler.handleLocalException(new ConnectTransportException(node, "failure to send " + HANDSHAKE_ACTION_NAME, e));
        } finally {
            if (success == false) {
                TransportResponseHandler<?> removed = pendingHandshakes.remove(requestId);
                assert removed == null : "Handshake should not be pending if exception was thrown";
            }
        }
    }

    void handleHandshake(Version version, Set<String> features, TcpChannel channel, long requestId, StreamInput input) throws IOException {
        HandshakeRequest handshakeRequest = new HandshakeRequest(input);
        handshakeResponseSender.sendResponse(version, features, channel, new HandshakeResponse(handshakeRequest.version, this.version), requestId);
    }

    TransportResponseHandler<HandshakeResponse> removeHandlerForHandshake(long requestId) {
        return pendingHandshakes.remove(requestId);
    }

    int getNumPendingHandshakes() {
        return pendingHandshakes.size();
    }

    long getNumHandshakes() {
        return numHandshakes.count();
    }

    private class HandshakeResponseHandler implements TransportResponseHandler<HandshakeResponse> {

        private final long requestId;
        private final Version currentVersion;
        private final ActionListener<Version> listener;
        private final AtomicBoolean isDone = new AtomicBoolean(false);

        private HandshakeResponseHandler(long requestId, Version currentVersion, ActionListener<Version> listener) {
            this.requestId = requestId;
            this.currentVersion = currentVersion;
            this.listener = listener;
        }

        @Override
        public HandshakeResponse read(StreamInput in) throws IOException {
            return new HandshakeResponse(in);
        }

        @Override
        public void handleResponse(HandshakeResponse response) {
            if (isDone.compareAndSet(false, true)) {
                Version version = response.responseVersion;
                if (currentVersion.isCompatible(version) == false) {
                    listener.onFailure(new IllegalStateException("Received message from unsupported version: [" + version
                        + "] minimal compatible version is: [" + currentVersion.minimumCompatibilityVersion() + "]"));
                } else {
                    listener.onResponse(version);
                }
            }
        }

        @Override
        public void handleException(TransportException e) {
            if (isDone.compareAndSet(false, true)) {
                listener.onFailure(new IllegalStateException("handshake failed", e));
            }
        }

        void handleLocalException(TransportException e) {
            if (removeHandlerForHandshake(requestId) != null && isDone.compareAndSet(false, true)) {
                listener.onFailure(e);
            }
        }

        @Override
        public String executor() {
            return ThreadPool.Names.SAME;
        }
    }

    static final class HandshakeRequest extends TransportRequest {

        private static final byte[] EMPTY_ARRAY = new byte[0];

        private final Version version;
        private final byte[] futureVersionBytes;

        HandshakeRequest(Version version) {
            this.version = version;
            this.futureVersionBytes = EMPTY_ARRAY;
        }

        HandshakeRequest(StreamInput streamInput) throws IOException {
            super(streamInput);
            int messageBytes = streamInput.readInt();
            int currentlyAvailable = streamInput.available();
            this.version = Version.readVersion(streamInput);
            int futureBytesLength = messageBytes - (currentlyAvailable - streamInput.available());
            this.futureVersionBytes = new byte[futureBytesLength];
            streamInput.readBytes(futureVersionBytes, 0, futureVersionBytes.length);
        }

        @Override
        public void readFrom(StreamInput in) {
            throw new UnsupportedOperationException("usage of Streamable is to be replaced by Writeable");
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            assert version != null;
            try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput(4)) {
                Version.writeVersion(version, bytesStreamOutput);
                BytesReference reference = bytesStreamOutput.bytes();
                out.writeInt(reference.length());
                reference.writeTo(out);
            }
        }
    }

    static final class HandshakeResponse extends TransportResponse {

        private final Version responseVersion;
        private Version requestVersion;

        HandshakeResponse(Version requestVersion, Version responseVersion) {
            this.responseVersion = responseVersion;
        }

        private HandshakeResponse(StreamInput in) throws IOException {
            super.readFrom(in);
            responseVersion = Version.readVersion(in);
        }

        @Override
        public void readFrom(StreamInput in) {
            throw new UnsupportedOperationException("usage of Streamable is to be replaced by Writeable");
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            assert responseVersion != null;
            Version.writeVersion(responseVersion, out);
        }
    }

    @FunctionalInterface
    interface HandshakeRequestSender {

        void sendRequest(DiscoveryNode node, TcpChannel channel, long requestId, Version version) throws IOException;
    }

    @FunctionalInterface
    interface HandshakeResponseSender {

        void sendResponse(Version version, Set<String> features, TcpChannel channel, TransportResponse response, long requestId)
            throws IOException;
    }
}
