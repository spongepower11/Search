/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.rest.action.document;

import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkRequestParser;
import org.elasticsearch.action.bulk.BulkShardRequest;
import org.elasticsearch.action.bulk.IncrementalBulkService;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.bytes.CompositeBytesReference;
import org.elasticsearch.common.bytes.ReleasableBytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.core.Releasable;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.core.RestApiVersion;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.Scope;
import org.elasticsearch.rest.ServerlessScope;
import org.elasticsearch.rest.action.RestRefCountedChunkedToXContentListener;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.PUT;

/**
 * <pre>
 * { "index" : { "_index" : "test", "_id" : "1" }
 * { "type1" : { "field1" : "value1" } }
 * { "delete" : { "_index" : "test", "_id" : "2" } }
 * { "create" : { "_index" : "test", "_id" : "1" }
 * { "type1" : { "field1" : "value1" } }
 * </pre>
 */
@ServerlessScope(Scope.PUBLIC)
public class RestBulkAction extends BaseRestHandler {
    public static final String TYPES_DEPRECATION_MESSAGE = "[types removal] Specifying types in bulk requests is deprecated.";

    private final boolean allowExplicitIndex;
    private final ThreadContext threadContext;
    private volatile IncrementalBulkService bulkHandler;

    public RestBulkAction(Settings settings) {
        this(settings, new ThreadContext(settings));
    }

    public RestBulkAction(Settings settings, ThreadContext threadContext) {
        this.allowExplicitIndex = MULTI_ALLOW_EXPLICIT_INDEX.get(settings);
        this.threadContext = threadContext;
    }

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(POST, "/_bulk"),
            new Route(PUT, "/_bulk"),
            new Route(POST, "/{index}/_bulk"),
            new Route(PUT, "/{index}/_bulk"),
            Route.builder(POST, "/{index}/{type}/_bulk").deprecated(TYPES_DEPRECATION_MESSAGE, RestApiVersion.V_7).build(),
            Route.builder(PUT, "/{index}/{type}/_bulk").deprecated(TYPES_DEPRECATION_MESSAGE, RestApiVersion.V_7).build()
        );
    }

    @Override
    public String getName() {
        return "bulk_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        // TODO: Move this to CTOR and hook everything up
        synchronized (this) {
            if (bulkHandler == null) {
                bulkHandler = new IncrementalBulkService(client, threadContext);
            }
        }

        if (request.getRestApiVersion() == RestApiVersion.V_7 && request.hasParam("type")) {
            request.param("type");
        }

        return new ChunkHandler(
            allowExplicitIndex,
            request,
            bulkHandler.newBulkRequest(
                request.param("wait_for_active_shards"),
                request.paramAsTime("timeout", BulkShardRequest.DEFAULT_TIMEOUT),
                request.param("refresh")
            )
        );
    }

    private static class ChunkHandler implements BaseRestHandler.RequestBodyChunkConsumer {

        private final boolean allowExplicitIndex;
        private final RestRequest request;

        private final Map<String, String> stringDeduplicator = new HashMap<>();
        private final String defaultIndex;
        private final String defaultRouting;
        private final FetchSourceContext defaultFetchSourceContext;
        private final String defaultPipeline;
        private final boolean defaultListExecutedPipelines;
        private final Boolean defaultRequireAlias;
        private final boolean defaultRequireDataStream;
        private final BulkRequestParser parser;
        private final IncrementalBulkService.Handler handler;

        private volatile RestChannel restChannel;
        private final ArrayDeque<ReleasableBytesReference> unParsedChunks = new ArrayDeque<>(4);
        private final ArrayList<DocWriteRequest<?>> items = new ArrayList<>(4);

        private ChunkHandler(boolean allowExplicitIndex, RestRequest request, IncrementalBulkService.Handler handler) {
            this.allowExplicitIndex = allowExplicitIndex;
            this.request = request;
            this.defaultIndex = request.param("index");
            this.defaultRouting = request.param("routing");
            this.defaultFetchSourceContext = FetchSourceContext.parseFromRestRequest(request);
            this.defaultPipeline = request.param("pipeline");
            this.defaultListExecutedPipelines = request.paramAsBoolean("list_executed_pipelines", false);
            this.defaultRequireAlias = request.paramAsBoolean(DocWriteRequest.REQUIRE_ALIAS, false);
            this.defaultRequireDataStream = request.paramAsBoolean(DocWriteRequest.REQUIRE_DATA_STREAM, false);
            this.parser = new BulkRequestParser(true, request.getRestApiVersion());
            this.handler = handler;
        }

        @Override
        public void accept(RestChannel restChannel) throws Exception {
            this.restChannel = restChannel;
        }

        @Override
        public void handleChunk(RestChannel channel, ReleasableBytesReference chunk, boolean isLast) {
            assert channel == restChannel;

            final BytesReference data;
            final Releasable releasable;
            try {
                // TODO: Check that the behavior here vs. globalRouting, globalPipeline, globalRequireAlias, globalRequireDatsStream in
                // BulkRequest#add is fine

                unParsedChunks.add(chunk);

                if (unParsedChunks.size() > 1) {
                    data = CompositeBytesReference.of(unParsedChunks.toArray(new ReleasableBytesReference[0]));
                } else {
                    data = chunk;
                }

                int bytesConsumed = parser.incrementalParse(
                    data,
                    defaultIndex,
                    defaultRouting,
                    defaultFetchSourceContext,
                    defaultPipeline,
                    defaultRequireAlias,
                    defaultRequireDataStream,
                    defaultListExecutedPipelines,
                    allowExplicitIndex,
                    request.getXContentType(),
                    (request, type) -> items.add(request),
                    items::add,
                    items::add,
                    isLast == false,
                    stringDeduplicator
                );

                releasable = accountParsing(bytesConsumed);

            } catch (IOException e) {
                // TODO: Exception Handling
                throw new UncheckedIOException(e);
            }

            if (isLast) {
                assert unParsedChunks.isEmpty();
                assert channel != null;
                handler.lastItems(new ArrayList<>(items), releasable, new RestRefCountedChunkedToXContentListener<>(channel));
                items.clear();
            } else if (items.isEmpty() == false) {
                handler.addItems(new ArrayList<>(items), releasable, () -> request.contentStream().next());
                items.clear();
            }
        }

        @Override
        public void close() {
            Releasables.close(handler);
            Releasables.close(unParsedChunks);
            RequestBodyChunkConsumer.super.close();
        }

        private Releasable accountParsing(int bytesConsumed) {
            ArrayList<Releasable> releasables = new ArrayList<>(unParsedChunks.size());
            while (bytesConsumed > 0) {
                ReleasableBytesReference reference = unParsedChunks.removeFirst();
                releasables.add(reference);
                if (bytesConsumed >= reference.length()) {
                    bytesConsumed -= reference.length();
                    releasables.add(reference);
                } else {
                    unParsedChunks.addFirst(reference.retainedSlice(bytesConsumed, reference.length() - bytesConsumed));
                    bytesConsumed = 0;
                }
            }
            return () -> Releasables.close(releasables);
        }
    }

    @Override
    public boolean supportsBulkContent() {
        return true;
    }

    @Override
    public boolean allowsUnsafeBuffers() {
        // TODO: Does this change with the chunking?
        return true;
    }
}
