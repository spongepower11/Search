/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.query;

import org.elasticsearch.client.internal.Client;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.index.mapper.DateFieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.xcontent.XContentParserConfiguration;

import java.util.function.LongSupplier;

/**
 * Context object used to rewrite {@link QueryBuilder} instances into simplified version in the coordinator.
 * Instances of this object rely on information stored in the {@code IndexMetadata} for certain indices.
 * Right now this context object is able to rewrite range queries that include a known timestamp field
 * (i.e. the timestamp field for DataStreams) into a MatchNoneQueryBuilder and skip the shards that
 * don't hold queried data. See IndexMetadata#getTimestampRange() for more details
 */
public class CoordinatorRewriteContext extends QueryRewriteContext {
    private final TimeSeriesRange timeSeriesRange;
    private final DateFieldMapper.DateFieldType timestampFieldType;

    public CoordinatorRewriteContext(
        XContentParserConfiguration parserConfig,
        NamedWriteableRegistry writeableRegistry,
        Client client,
        LongSupplier nowInMillis,
        TimeSeriesRange timeSeriesRange,
        DateFieldMapper.DateFieldType timestampFieldType
    ) {
        super(parserConfig, writeableRegistry, client, nowInMillis);
        this.timeSeriesRange = timeSeriesRange;
        this.timestampFieldType = timestampFieldType;
    }

    long getMinTimestamp() {
        return timeSeriesRange.min();
    }

    long getMaxTimestamp() {
        return timeSeriesRange.max();
    }

    boolean hasTimestampData() {
        return timeSeriesRange != null;
    }

    @Nullable
    public MappedFieldType getFieldType(String fieldName) {
        if (fieldName.equals(timestampFieldType.name()) == false) {
            return null;
        }

        return timestampFieldType;
    }

    @Override
    public CoordinatorRewriteContext convertToCoordinatorRewriteContext() {
        return this;
    }
}
