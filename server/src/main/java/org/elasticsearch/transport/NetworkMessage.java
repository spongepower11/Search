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
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.bytes.CompositeBytesReference;
import org.elasticsearch.common.compress.Compressor;
import org.elasticsearch.common.compress.CompressorFactory;
import org.elasticsearch.common.compress.NotCompressedException;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.IOException;
import java.util.Set;

import static org.elasticsearch.transport.TcpTransport.ensureVersionCompatibility;

public abstract class NetworkMessage implements Writeable {

    protected final Version version;

    private final ThreadContext threadContext;
    private final ThreadContext.StoredContext storedContext;
    private final Writeable message;
    private final long requestId;
    private byte status;

    NetworkMessage(ThreadContext threadContext, Version version, byte status, long requestId, Writeable message, boolean compress) {
        this.threadContext = threadContext;
        storedContext = threadContext.stashContext();
        storedContext.restore();
        this.version = version;
        this.requestId = requestId;
        this.message = message;
        if (compress && canCompress(message)) {
            this.status = TransportStatus.setCompress(status);
        } else {
            this.status = status;
        }
    }

    static NetworkMessage deserialize(ThreadPool threadPool, BytesReference reference, StreamInput streamInput) throws IOException {
//        int messageLengthBytes = reference.length();
//        final int totalMessageSize = messageLengthBytes + TcpHeader.MARKER_BYTES_SIZE + TcpHeader.MESSAGE_LENGTH_SIZE;
//        boolean hasMessageBytesToRead = (totalMessageSize - TcpHeader.HEADER_SIZE) > 0;
//        boolean success = false;
//        try (ThreadContext.StoredContext tCtx = threadPool.getThreadContext().stashContext()) {
//            long requestId = streamInput.readLong();
//            byte status = streamInput.readByte();
//            Version version = Version.fromId(streamInput.readInt());
//            if (TransportStatus.isCompress(status) && hasMessageBytesToRead && streamInput.available() > 0) {
//                Compressor compressor;
//                try {
//                    final int bytesConsumed = TcpHeader.REQUEST_ID_SIZE + TcpHeader.STATUS_SIZE + TcpHeader.VERSION_ID_SIZE;
//                    compressor = CompressorFactory.compressor(reference.slice(bytesConsumed, reference.length() - bytesConsumed));
//                } catch (NotCompressedException ex) {
//                    int maxToRead = Math.min(reference.length(), 10);
//                    StringBuilder sb = new StringBuilder("stream marked as compressed, but no compressor found, first [").append(maxToRead)
//                        .append("] content bytes out of [").append(reference.length())
//                        .append("] readable bytes with message size [").append(messageLengthBytes).append("] ").append("] are [");
//                    for (int i = 0; i < maxToRead; i++) {
//                        sb.append(reference.get(i)).append(",");
//                    }
//                    sb.append("]");
//                    throw new IllegalStateException(sb.toString());
//                }
//                streamInput = compressor.streamInput(streamInput);
//            }
//            final boolean isHandshake = TransportStatus.isHandshake(status);
//            ensureVersionCompatibility(version, this.version, isHandshake);
//            streamInput = new NamedWriteableAwareStreamInput(streamInput, namedWriteableRegistry);
//            streamInput.setVersion(version);
//            threadPool.getThreadContext().readHeaders(streamInput);
//            threadPool.getThreadContext().putTransient("_remote_address", remoteAddress);
//            if (TransportStatus.isRequest(status)) {
////                handleRequest(channel, profileName, streamInput, requestId, messageLengthBytes, version, remoteAddress, status);
//            } else {
//                final TransportResponseHandler<?> handler;
//                if (isHandshake) {
////                    handler = handshaker.removeHandlerForHandshake(requestId);
//                } else {
////                    TransportResponseHandler<? extends TransportResponse> theHandler =
////                        responseHandlers.onResponseReceived(requestId, messageListener);
////                    if (theHandler == null && TransportStatus.isError(status)) {
////                        handler = handshaker.removeHandlerForHandshake(requestId);
////                    } else {
////                        handler = theHandler;
////                    }
//                }
//                // ignore if its null, the service logs it
////                if (handler != null) {
//                    if (TransportStatus.isError(status)) {
////                        handlerResponseError(streamIn, handler);
//                    } else {
////                        handleResponse(remoteAddress, streamIn, handler);
//                    }
//                    // Check the entire message has been read
//                    final int nextByte = streamInput.read();
//                    // calling read() is useful to make sure the message is fully read, even if there is an EOS marker
//                    if (nextByte != -1) {
//                        throw new IllegalStateException("Message not fully read (response) for requestId [" + requestId + "], handler ["
//                            + handler + "], error [" + TransportStatus.isError(status) + "]; resetting");
//                    }
////                }
//            }
//            success = true;
//        } finally {
//            if (success) {
//                IOUtils.close(streamInput);
//            } else {
//                IOUtils.closeWhileHandlingException(streamInput);
//            }
//        }
        return null;
    }

    BytesReference serialize(BytesStreamOutput bytesStream) throws IOException {
        storedContext.restore();
        bytesStream.setVersion(version);
        bytesStream.skip(TcpHeader.HEADER_SIZE);

        // The compressible bytes stream will not close the underlying bytes stream
        BytesReference reference;
        try (CompressibleBytesOutputStream stream = new CompressibleBytesOutputStream(bytesStream, TransportStatus.isCompress(status))) {
            stream.setVersion(version);
            threadContext.writeTo(stream);
            writeTo(stream);
            reference = writeMessage(stream);
        }
        bytesStream.seek(0);
        TcpHeader.writeHeader(bytesStream, requestId, status, version, reference.length() - TcpHeader.HEADER_SIZE);
        return reference;
    }

    private BytesReference writeMessage(CompressibleBytesOutputStream stream) throws IOException {
        final BytesReference zeroCopyBuffer;
        if (message instanceof BytesTransportRequest) {
            BytesTransportRequest bRequest = (BytesTransportRequest) message;
            bRequest.writeThin(stream);
            zeroCopyBuffer = bRequest.bytes;
        } else if (message instanceof RemoteTransportException) {
            stream.writeException((RemoteTransportException) message);
            zeroCopyBuffer = BytesArray.EMPTY;
        } else {
            message.writeTo(stream);
            zeroCopyBuffer = BytesArray.EMPTY;
        }
        // we have to call materializeBytes() here before accessing the bytes. A CompressibleBytesOutputStream
        // might be implementing compression. And materializeBytes() ensures that some marker bytes (EOS marker)
        // are written. Otherwise we barf on the decompressing end when we read past EOF on purpose in the
        // #validateRequest method. this might be a problem in deflate after all but it's important to write
        // the marker bytes.
        final BytesReference message = stream.materializeBytes();
        if (zeroCopyBuffer.length() == 0) {
            return message;
        } else {
            return new CompositeBytesReference(message, zeroCopyBuffer);
        }
    }

    static class Request extends NetworkMessage {

        private final String[] features;
        private final String action;

        Request(ThreadContext threadContext, String[] features, Writeable message, Version version, String action, long requestId,
                boolean isHandshake, boolean compress) {
            super(threadContext, version, setStatus(compress, isHandshake, message), requestId, message, compress);
            this.features = features;
            this.action = action;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            if (version.onOrAfter(Version.V_6_3_0)) {
                out.writeStringArray(features);
            }
            out.writeString(action);
        }

        private static byte setStatus(boolean compress, boolean isHandshake, Writeable message) {
            byte status = 0;
            status = TransportStatus.setRequest(status);
            if (compress && NetworkMessage.canCompress(message)) {
                status = TransportStatus.setCompress(status);
            }
            if (isHandshake) {
                status = TransportStatus.setHandshake(status);
            }

            return status;
        }
    }

    static class Response extends NetworkMessage {

        private final Set<String> features;

        Response(ThreadContext threadContext, Set<String> features, Writeable message, Version version, long requestId, boolean isHandshake,
                 boolean compress) {
            super(threadContext, version, setStatus(compress, isHandshake, message), requestId, message, compress);
            this.features = features;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.setFeatures(features);
        }

        private static byte setStatus(boolean compress, boolean isHandshake, Writeable message) {
            byte status = 0;
            status = TransportStatus.setResponse(status);
            if (message instanceof RemoteTransportException) {
                status = TransportStatus.setError(status);
            }
            if (compress) {
                status = TransportStatus.setCompress(status);
            }
            if (isHandshake) {
                status = TransportStatus.setHandshake(status);
            }

            return status;
        }
    }

    private static boolean canCompress(Writeable message) {
        return message instanceof BytesTransportRequest == false;
    }
}
