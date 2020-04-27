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

package org.elasticsearch.search.aggregations.bucket.histogram;

import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.aggregations.AggregationExecutionException;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.bucket.histogram.AutoDateHistogramAggregationBuilder.RoundingInfo;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSource.Numeric;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.usage.UsageService.OTHER_SUBTYPE;

public final class AutoDateHistogramAggregatorFactory
        extends ValuesSourceAggregatorFactory {

    private final int numBuckets;
    private RoundingInfo[] roundingInfos;

    public AutoDateHistogramAggregatorFactory(String name,
                                              ValuesSourceConfig config,
                                              int numBuckets,
                                              RoundingInfo[] roundingInfos,
                                              QueryShardContext queryShardContext,
                                              AggregatorFactory parent,
                                              AggregatorFactories.Builder subFactoriesBuilder,
                                              Map<String, Object> metadata) throws IOException {
        super(name, config, queryShardContext, parent, subFactoriesBuilder, metadata);
        this.numBuckets = numBuckets;
        this.roundingInfos = roundingInfos;
    }

    @Override
    protected Aggregator doCreateInternal(ValuesSource valuesSource,
                                            SearchContext searchContext,
                                            Aggregator parent,
                                            boolean collectsFromSingleBucket,
                                            Map<String, Object> metadata) throws IOException {
        if (valuesSource instanceof Numeric == false) {
            throw new AggregationExecutionException("ValuesSource type " + valuesSource.toString() + "is not supported for aggregation " +
                this.name());
        }
        if (collectsFromSingleBucket == false) {
            return asMultiBucketAggregator(this, searchContext, parent);
        }
        return createAggregator((Numeric) valuesSource, searchContext, parent, metadata);
    }

    private Aggregator createAggregator(ValuesSource.Numeric valuesSource,
                                            SearchContext searchContext,
                                            Aggregator parent,
                                            Map<String, Object> metadata) throws IOException {
        return new AutoDateHistogramAggregator(name, factories, numBuckets, roundingInfos,
            valuesSource, config.format(), searchContext, parent, metadata);
    }

    @Override
    protected Aggregator createUnmapped(SearchContext searchContext,
                                            Aggregator parent,
                                            Map<String, Object> metadata) throws IOException {
        return createAggregator(null, searchContext, parent, metadata);
    }

    @Override
    public String getStatsSubtype() {
        // AutoDateHistogramAggregatorFactory doesn't register itself with ValuesSourceRegistry
        return OTHER_SUBTYPE;
    }
}
