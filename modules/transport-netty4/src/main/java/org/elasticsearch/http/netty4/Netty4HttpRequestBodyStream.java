/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.http.netty4;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.FutureListener;

import org.elasticsearch.http.HttpBody;
import org.elasticsearch.transport.netty4.Netty4Utils;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Netty based implementation of {@link HttpBody.Stream}.
 * This implementation utilize {@link io.netty.channel.ChannelConfig#setAutoRead(boolean)}
 * to prevent entire payload buffering. But sometimes upstream can send few chunks of data despite
 * autoRead=off. In this case chunks will be queued until downstream calls {@link Stream#next()}
 */
public class Netty4HttpRequestBodyStream implements HttpBody.Stream {

    private final Channel channel;
    private final Queue<HttpContent> chunkQueue = new ArrayDeque<>();
    private boolean requested = false;
    private boolean hasLast = false;
    private boolean closing = false;
    private HttpBody.ChunkHandler handler;

    private final FutureListener<Void> closeListener = future -> doClose();

    public Netty4HttpRequestBodyStream(Channel channel) {
        this.channel = channel;
        channel.closeFuture().addListener(closeListener);
        channel.config().setAutoRead(false);
    }

    @Override
    public ChunkHandler handler() {
        return handler;
    }

    @Override
    public void setHandler(ChunkHandler chunkHandler) {
        this.handler = chunkHandler;
    }

    private void sendQueuedOrRead() {
        assert channel.eventLoop().inEventLoop();
        requested = true;
        var chunk = chunkQueue.poll();
        if (chunk == null) {
            channel.read();
        } else {
            sendChunk(chunk);
        }
    }

    @Override
    public void next() {
        assert handler != null : "handler must be set before requesting next chunk";
        if (channel.eventLoop().inEventLoop()) {
            sendQueuedOrRead();
        } else {
            channel.eventLoop().submit(this::sendQueuedOrRead);
        }
    }

    public void handleNettyContent(HttpContent httpContent) {
        hasLast = httpContent instanceof LastHttpContent;
        if (closing) {
            httpContent.release();
            return;
        }
        assert handler != null : "handler must be set before processing http content";
        if (requested && chunkQueue.isEmpty()) {
            sendChunk(httpContent);
        } else {
            chunkQueue.add(httpContent);
        }
    }

    // visible for test
    Channel channel() {
        return channel;
    }

    // visible for test
    Queue<HttpContent> chunkQueue() {
        return chunkQueue;
    }

    // visible for test
    boolean isClosing() {
        return closing;
    }

    // visible for test
    boolean hasLast() {
        return hasLast;
    }

    private void sendChunk(HttpContent httpContent) {
        assert requested;
        requested = false;
        var bytesRef = Netty4Utils.toReleasableBytesReference(httpContent.content());
        var isLast = httpContent instanceof LastHttpContent;
        handler.onNext(bytesRef, isLast);
        if (isLast) {
            channel.config().setAutoRead(true);
            channel.closeFuture().removeListener(closeListener);
        }
    }

    private void releaseQueuedChunks() {
        while (chunkQueue.isEmpty() == false) {
            chunkQueue.poll().release();
        }
    }

    @Override
    public void close() {
        if (channel.eventLoop().inEventLoop()) {
            doClose();
        } else {
            channel.eventLoop().submit(this::doClose);
        }
    }

    private void doClose() {
        closing = true;
        releaseQueuedChunks();
        channel.config().setAutoRead(true);
    }
}
