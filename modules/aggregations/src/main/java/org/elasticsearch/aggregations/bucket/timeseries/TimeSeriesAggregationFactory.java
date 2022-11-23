/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.aggregations.bucket.timeseries;

import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.CardinalityUpperBound;
import org.elasticsearch.search.aggregations.support.AggregationContext;

import java.io.IOException;
import java.util.Map;

public class TimeSeriesAggregationFactory extends AggregatorFactory {

    private final boolean keyed;
    private final boolean expectTsidBucketInOrder;

    public TimeSeriesAggregationFactory(
        String name,
        boolean keyed,
        AggregationContext context,
        AggregatorFactory parent,
        AggregatorFactories.Builder subFactoriesBuilder,
        Map<String, Object> metadata,
        boolean expectTsidBucketInOrder
    ) throws IOException {
        super(name, context, parent, subFactoriesBuilder, metadata);
        this.keyed = keyed;
        this.expectTsidBucketInOrder = expectTsidBucketInOrder;
    }

    @Override
    protected Aggregator createInternal(Aggregator parent, CardinalityUpperBound cardinality, Map<String, Object> metadata)
        throws IOException {
        if (expectTsidBucketInOrder) {
            return new TimeSeriesInOrderAggregator(name, factories, keyed, context, parent, metadata);
        } else {
            return new TimeSeriesAggregator(name, factories, keyed, context, parent, cardinality, metadata);
        }
    }
}
