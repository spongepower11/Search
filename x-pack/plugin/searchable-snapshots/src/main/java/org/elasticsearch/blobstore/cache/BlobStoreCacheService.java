/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.blobstore.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.NoShardAvailableActionException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.OriginSettingClient;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.routing.IndexRoutingTable;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.ReleasableBytesReference;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.mapper.MapperService.SINGLE_MAPPING_NAME;
import static org.elasticsearch.xpack.core.ClientHelper.SEARCHABLE_SNAPSHOTS_ORIGIN;

public class BlobStoreCacheService extends AbstractLifecycleComponent implements ClusterStateListener {

    private static final Logger logger = LogManager.getLogger(BlobStoreCacheService.class);

    public static final int DEFAULT_SIZE = Math.toIntExact(ByteSizeUnit.KB.toBytes(4L));

    private final ClusterService clusterService;
    private final ThreadPool threadPool;
    private final AtomicBoolean ready;
    private final Client client;
    private final String index;

    public BlobStoreCacheService(ClusterService clusterService, ThreadPool threadPool, Client client, String index) {
        this.client = new OriginSettingClient(client, SEARCHABLE_SNAPSHOTS_ORIGIN);
        this.ready = new AtomicBoolean(false);
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.index = index;
    }

    @Override
    protected void doStart() {
        clusterService.addListener(this);
    }

    @Override
    protected void doStop() {
        clusterService.removeListener(this);
    }

    @Override
    protected void doClose() {}

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        if (lifecycle.started() == false || event.routingTableChanged() == false) {
            return;
        }
        if (event.indexRoutingTableChanged(index)) {
            final IndexRoutingTable indexRoutingTable = event.state().routingTable().index(index);
            if (indexRoutingTable == null) {
                ready.set(false);
                return;
            }
            ready.set(indexRoutingTable.allPrimaryShardsActive());
        }
    }

    public boolean isReady() {
        return lifecycle.started() && ready.get();
    }

    private void createIndexIfNecessary(ActionListener<String> listener) {
        if (clusterService.state().routingTable().hasIndex(index)) {
            listener.onResponse(index);
            return;
        }
        try {
            client.admin()
                .indices()
                .prepareCreate(index)
                .setSettings(settings())
                .setMapping(mappings())
                .execute(ActionListener.wrap(success -> listener.onResponse(index), e -> {
                    if (e instanceof ResourceAlreadyExistsException
                        || ExceptionsHelper.unwrapCause(e) instanceof ResourceAlreadyExistsException) {
                        listener.onResponse(index);
                    } else {
                        listener.onFailure(e);
                    }
                }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private static Settings settings() {
        return Settings.builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_AUTO_EXPAND_REPLICAS, "0-1")
            .put(IndexMetadata.SETTING_PRIORITY, "900")
            .build();
    }

    private static XContentBuilder mappings() throws IOException {
        final XContentBuilder builder = jsonBuilder();
        {
            builder.startObject();
            {
                builder.startObject(SINGLE_MAPPING_NAME);
                builder.field("dynamic", "false");
                {
                    builder.startObject("_meta");
                    builder.field("version", Version.CURRENT);
                    builder.endObject();
                }
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("type");
                        builder.field("type", "keyword");
                        builder.endObject();
                    }
                    {
                        builder.startObject("creation_time");
                        builder.field("type", "date");
                        builder.field("format", "epoch_millis");
                        builder.endObject();
                    }
                    {
                        builder.startObject("accessed_time");
                        builder.field("type", "date");
                        builder.field("format", "epoch_millis");
                        builder.endObject();
                    }
                    {
                        builder.startObject("version");
                        builder.field("type", "integer");
                        builder.endObject();
                    }
                    {
                        builder.startObject("repository");
                        builder.field("type", "keyword");
                        builder.endObject();
                    }
                    {
                        builder.startObject("blob");
                        builder.field("type", "object");
                        {
                            builder.startObject("properties");
                            {
                                builder.startObject("name");
                                builder.field("type", "keyword");
                                builder.endObject();
                                builder.startObject("path");
                                builder.field("type", "keyword");
                                builder.endObject();
                            }
                            builder.endObject();
                        }
                        builder.endObject();
                    }
                    {
                        builder.startObject("data");
                        builder.field("type", "object");
                        {
                            builder.startObject("properties");
                            {
                                builder.startObject("content");
                                builder.field("type", "binary");
                                builder.endObject();
                            }
                            {
                                builder.startObject("length");
                                builder.field("type", "long");
                                builder.endObject();
                            }
                            {
                                builder.startObject("from");
                                builder.field("type", "long");
                                builder.endObject();
                            }
                            {
                                builder.startObject("to");
                                builder.field("type", "long");
                                builder.endObject();
                            }
                            builder.endObject();
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            builder.endObject();
        }
        return builder;
    }

    public CachedBlob get(String repository, String name, String path, long offset) {
        final PlainActionFuture<CachedBlob> future = PlainActionFuture.newFuture();
        getAsync(repository, name, path, offset, future);
        return future.actionGet();
    }

    protected void getAsync(String repository, String name, String path, long offset, ActionListener<CachedBlob> listener) {
        if (isReady() == false) {
            logger.debug("blob cache system index [{}] is not ready", index);
            listener.onResponse(null);
            return;
        }
        try {
            final GetRequest request = new GetRequest(index).id(CachedBlob.generateId(repository, name, path, offset));
            client.get(request, ActionListener.wrap(response -> {
                if (response.isExists()) {
                    logger.debug("found cached blob with id [{}] in cache", request.id());
                    assert response.isSourceEmpty() == false;

                    final CachedBlob cachedBlob = CachedBlob.fromSource(response.getSource());
                    assert response.getId().equals(cachedBlob.generatedId());
                    listener.onResponse(cachedBlob);
                } else {
                    logger.debug("no cached blob found with id [{}] in cache", request.id());
                    listener.onResponse(null);
                }
            }, e -> {
                logger.warn(() -> new ParameterizedMessage("failed to retrieve cached blob from system index [{}]", index), e);
                if (e instanceof IndexNotFoundException || e instanceof NoShardAvailableActionException) {
                    // In case the blob cache system index got unavailable, we pretend we didn't find a cache entry and we move on.
                    // Failing here might bubble up the exception and fail the searchable snapshot shard which is potentially recovering.
                    listener.onResponse(null);
                } else {
                    listener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    public void putAsync(
        String repository,
        String name,
        String path,
        long offset,
        ReleasableBytesReference content,
        ActionListener<Void> listener
    ) {
        createIndexIfNecessary(ActionListener.wrap(index -> {
            try {
                final CachedBlob cachedBlob = new CachedBlob(
                    Instant.ofEpochMilli(threadPool.absoluteTimeInMillis()),
                    Version.CURRENT,
                    repository,
                    name,
                    path,
                    content,
                    offset
                );
                final IndexRequest request = new IndexRequest(index).id(cachedBlob.generatedId());
                try (XContentBuilder builder = jsonBuilder()) {
                    request.source(cachedBlob.toXContent(builder, ToXContent.EMPTY_PARAMS));
                }
                client.index(request, ActionListener.wrap(response -> {
                    if (response.status() == RestStatus.CREATED) {
                        logger.trace("cached blob [{}] successfully indexed in [{}]", request, index);
                    }
                }, listener::onFailure));
            } catch (IOException e) {
                logger.warn("failed to index cached blob in cache", e);
            } finally {
                IOUtils.closeWhileHandlingException(content);
            }
        }, e -> {
            logger.error(() -> new ParameterizedMessage("failed to create blob cache system index [{}]", index), e);
            IOUtils.closeWhileHandlingException(content);
        }));
    }
}
