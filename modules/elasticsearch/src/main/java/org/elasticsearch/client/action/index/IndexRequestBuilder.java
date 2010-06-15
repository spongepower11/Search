/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
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

package org.elasticsearch.client.action.index;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.PlainListenableActionFuture;
import org.elasticsearch.action.support.replication.ReplicationType;
import org.elasticsearch.client.internal.InternalClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.builder.XContentBuilder;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * An index document action request builder.
 *
 * @author kimchy (shay.banon)
 */
public class IndexRequestBuilder {

    private final InternalClient client;

    private final IndexRequest request;

    public IndexRequestBuilder(InternalClient client, @Nullable String index) {
        this.client = client;
        this.request = new IndexRequest(index);
    }

    /**
     * Sets the index to index the document to.
     */
    public IndexRequestBuilder setIndex(String index) {
        request.index(index);
        return this;
    }

    /**
     * Sets the type to index the document to.
     */
    public IndexRequestBuilder setType(String type) {
        request.type(type);
        return this;
    }

    /**
     * Sets the id to index the document under. Optional, and if not set, one will be automatically
     * generated.
     */
    public IndexRequestBuilder setId(String id) {
        request.id(id);
        return this;
    }

    /**
     * Index the Map as a JSON.
     *
     * @param source The map to index
     */
    public IndexRequestBuilder setSource(Map<String, Object> source) {
        request.source(source);
        return this;
    }

    /**
     * Index the Map as the provided content type.
     *
     * @param source The map to index
     */
    public IndexRequestBuilder setSource(Map<String, Object> source, XContentType contentType) {
        request.source(source, contentType);
        return this;
    }

    /**
     * Sets the document source to index.
     *
     * <p>Note, its preferable to either set it using {@link #setSource(org.elasticsearch.common.xcontent.builder.XContentBuilder)}
     * or using the {@link #setSource(byte[])}.
     */
    public IndexRequestBuilder setSource(String source) {
        request.source(source);
        return this;
    }

    /**
     * Sets the content source to index.
     */
    public IndexRequestBuilder setSource(XContentBuilder sourceBuilder) {
        request.source(sourceBuilder);
        return this;
    }

    /**
     * Sets the document to index in bytes form.
     */
    public IndexRequestBuilder setSource(byte[] source) {
        request.source(source);
        return this;
    }

    /**
     * Sets the document to index in bytes form (assumed to be safe to be used from different
     * threads).
     *
     * @param source The source to index
     * @param offset The offset in the byte array
     * @param length The length of the data
     */
    public IndexRequestBuilder setSource(byte[] source, int offset, int length) {
        request.source(source, offset, length);
        return this;
    }

    /**
     * Sets the document to index in bytes form.
     *
     * @param source The source to index
     * @param offset The offset in the byte array
     * @param length The length of the data
     * @param unsafe Is the byte array safe to be used form a different thread
     */
    public IndexRequestBuilder setSource(byte[] source, int offset, int length, boolean unsafe) {
        request.source(source, offset, length, unsafe);
        return this;
    }

    /**
     * A timeout to wait if the index operation can't be performed immediately. Defaults to <tt>1m</tt>.
     */
    public IndexRequestBuilder setTimeout(TimeValue timeout) {
        request.timeout(timeout);
        return this;
    }

    /**
     * A timeout to wait if the index operation can't be performed immediately. Defaults to <tt>1m</tt>.
     */
    public IndexRequestBuilder setTimeout(String timeout) {
        request.timeout(timeout);
        return this;
    }

    /**
     * Sets the type of operation to perform.
     */
    public IndexRequestBuilder setOpType(IndexRequest.OpType opType) {
        request.opType(opType);
        return this;
    }

    /**
     * Sets a string representation of the {@link #setOpType(org.elasticsearch.action.index.IndexRequest.OpType)}. Can
     * be either "index" or "create".
     */
    public IndexRequestBuilder setOpType(String opType) {
        request.opType(opType);
        return this;
    }

    /**
     * Set to <tt>true</tt> to force this index to use {@link IndexRequest.OpType#CREATE}.
     */
    public IndexRequestBuilder setCreate(boolean create) {
        request.create(create);
        return this;
    }

    /**
     * Set the replication type for this operation.
     */
    public IndexRequestBuilder setReplicationType(ReplicationType replicationType) {
        request.replicationType(replicationType);
        return this;
    }

    /**
     * Set the replication type for this operation.
     */
    public IndexRequestBuilder setReplicationType(String replicationType) {
        request.replicationType(replicationType);
        return this;
    }

    /**
     * Should the listener be called on a separate thread if needed.
     */
    public IndexRequestBuilder setListenerThreaded(boolean listenerThreaded) {
        request.listenerThreaded(listenerThreaded);
        return this;
    }

    /**
     * Controls if the operation will be executed on a separate thread when executed locally. Defaults
     * to <tt>true</tt> when running in embedded mode.
     */
    public IndexRequestBuilder setOperationThreaded(boolean operationThreaded) {
        request.operationThreaded(operationThreaded);
        return this;
    }

    /**
     * Executes the operation asynchronously and returns a future.
     */
    public ListenableActionFuture<IndexResponse> execute() {
        PlainListenableActionFuture<IndexResponse> future = new PlainListenableActionFuture<IndexResponse>(request.listenerThreaded(), client.threadPool());
        client.index(request, future);
        return future;
    }

    /**
     * Executes the operation asynchronously with the provided listener.
     */
    public void execute(ActionListener<IndexResponse> listener) {
        client.index(request, listener);
    }
}
