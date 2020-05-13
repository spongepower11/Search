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
package org.elasticsearch.search.aggregations.bucket.terms;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.PriorityQueue;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.util.LongArray;
import org.elasticsearch.index.fielddata.FieldData;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.InternalMultiBucketAggregation;
import org.elasticsearch.search.aggregations.InternalOrder;
import org.elasticsearch.search.aggregations.LeafBucketCollector;
import org.elasticsearch.search.aggregations.LeafBucketCollectorBase;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude.LongFilter;
import org.elasticsearch.search.aggregations.bucket.terms.LongKeyedBucketOrds.BucketOrdsEnum;
import org.elasticsearch.search.aggregations.bucket.terms.heuristic.SignificanceHeuristic;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.internal.ContextIndexSearcher;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.Collections.emptyList;

public class NumericTermsAggregator extends TermsAggregator {
    private final ResultStrategy<?, ?> resultStrategy;
    private final ValuesSource.Numeric valuesSource;
    private final LongKeyedBucketOrds bucketOrds;
    private final LongFilter longFilter;

    public NumericTermsAggregator(String name, AggregatorFactories factories,
            Function<NumericTermsAggregator, ResultStrategy<?, ?>> resultStrategy, ValuesSource.Numeric valuesSource, DocValueFormat format,
            BucketOrder order, BucketCountThresholds bucketCountThresholds, SearchContext aggregationContext,
            Aggregator parent, SubAggCollectionMode subAggCollectMode, IncludeExclude.LongFilter longFilter,
            boolean collectsFromSingleBucket, Map<String, Object> metadata) throws IOException {
        super(name, factories, aggregationContext, parent, bucketCountThresholds, order, format, subAggCollectMode, metadata);
        this.resultStrategy = resultStrategy.apply(this);
        this.valuesSource = valuesSource;
        this.longFilter = longFilter;
        bucketOrds = LongKeyedBucketOrds.build(context.bigArrays(), collectsFromSingleBucket);
    }

    @Override
    public ScoreMode scoreMode() {
        if (valuesSource != null && valuesSource.needsScores()) {
            return ScoreMode.COMPLETE;
        }
        return super.scoreMode();
    }

    @Override
    public LeafBucketCollector getLeafCollector(LeafReaderContext ctx,
            final LeafBucketCollector sub) throws IOException {
        final SortedNumericDocValues values = resultStrategy.getValues(ctx);
        return resultStrategy.wrapCollector(new LeafBucketCollectorBase(sub, values) {
            @Override
            public void collect(int doc, long owningBucketOrd) throws IOException {
                if (values.advanceExact(doc)) {
                    final int valuesCount = values.docValueCount();

                    long previous = Long.MAX_VALUE;
                    for (int i = 0; i < valuesCount; ++i) {
                        final long val = values.nextValue();
                        if (previous != val || i == 0) {
                            if ((longFilter == null) || (longFilter.accept(val))) {
                                long bucketOrdinal = bucketOrds.add(owningBucketOrd, val);
                                if (bucketOrdinal < 0) { // already seen
                                    bucketOrdinal = -1 - bucketOrdinal;
                                    collectExistingBucket(sub, doc, bucketOrdinal);
                                } else {
                                    collectBucket(sub, doc, bucketOrdinal);
                                }
                            }

                            previous = val;
                        }
                    }
                }
            }
        });
    }

    @Override
    public InternalAggregation[] buildAggregations(long[] owningBucketOrds) throws IOException {
        return resultStrategy.buildAggregations(owningBucketOrds);
    }

    @Override
    public InternalAggregation buildEmptyAggregation() {
        return resultStrategy.buildEmptyResult();
    }

    @Override
    public void doClose() {
        Releasables.close(super::doClose, bucketOrds, resultStrategy);
    }

    @Override
    public void collectDebugInfo(BiConsumer<String, Object> add) {
        super.collectDebugInfo(add);
        add.accept("result_strategy", resultStrategy.describe());
        add.accept("total_buckets", bucketOrds.size());
    }

    /**
     * Strategy for building results.
     */
    abstract class ResultStrategy<R extends InternalAggregation, B extends InternalMultiBucketAggregation.InternalBucket>
            implements Releasable {
        private InternalAggregation[] buildAggregations(long[] owningBucketOrds) throws IOException {
            B[][] topBucketsPerOrd = buildTopBucketsPerOrd(owningBucketOrds.length);
            long[] otherDocCounts = new long[owningBucketOrds.length];
            for (int ordIdx = 0; ordIdx < owningBucketOrds.length; ordIdx++) {
                collectZeroDocEntriesIfNeeded(owningBucketOrds[ordIdx]);
                long bucketsInOrd = bucketOrds.bucketsInOrd(owningBucketOrds[ordIdx]);

                final int size = (int) Math.min(bucketsInOrd, bucketCountThresholds.getShardSize());
                PriorityQueue<B> ordered = buildPriorityQueue(size);
                B spare = null;
                BucketOrdsEnum ordsEnum = bucketOrds.ordsEnum(owningBucketOrds[ordIdx]);
                while (ordsEnum.next()) {
                    if (spare == null) {
                        spare = buildEmptyBucket(owningBucketOrds[ordIdx]);
                    }
                    long docCount = bucketDocCount(ordsEnum.ord());
                    otherDocCounts[ordIdx] += docCount;
                    if (bucketCountThresholds.getShardMinDocCount() <= docCount) {
                        updateBucket(spare, ordsEnum, docCount);
                        spare = ordered.insertWithOverflow(spare);
                        if (spare == null) {
                            consumeBucketsAndMaybeBreak(1);
                        }
                    }
                }

                // Get the top buckets
                B[] bucketsForOrd = buildBuckets(ordered.size());
                topBucketsPerOrd[ordIdx] = bucketsForOrd;
                for (int b = ordered.size() - 1; b >= 0; --b) {
                    topBucketsPerOrd[ordIdx][b] = ordered.pop();
                    otherDocCounts[ordIdx] -= topBucketsPerOrd[ordIdx][b].getDocCount();
                }
            }

            buildSubAggs(topBucketsPerOrd);

            InternalAggregation[] result = new InternalAggregation[owningBucketOrds.length];
            for (int ordIdx = 0; ordIdx < owningBucketOrds.length; ordIdx++) {
                result[ordIdx] = buildResult(owningBucketOrds[ordIdx], otherDocCounts[ordIdx], topBucketsPerOrd[ordIdx]);
            }
            return result;
        }

        abstract String describe();
        abstract SortedNumericDocValues getValues(LeafReaderContext ctx) throws IOException;
        abstract LeafBucketCollector wrapCollector(LeafBucketCollector primary);
        abstract B[][] buildTopBucketsPerOrd(int size);
        abstract B[] buildBuckets(int size);
        abstract B buildEmptyBucket(long owningBucketOrd);
        abstract void updateBucket(B spare, BucketOrdsEnum ordsEnum, long docCount) throws IOException;
        abstract PriorityQueue<B> buildPriorityQueue(int size); 
        abstract void buildSubAggs(B[][] topBucketsPerOrd) throws IOException;
        abstract void collectZeroDocEntriesIfNeeded(long ord) throws IOException;
        abstract R buildResult(long owningBucketOrd, long otherDocCounts, B[] topBuckets);
        abstract R buildEmptyResult();
    }

    abstract class StandardTermsResultStrategy<R extends InternalMappedTerms<R, B>, B extends InternalTerms.Bucket<B>>
            extends ResultStrategy<R, B> {
        protected final boolean showTermDocCountError;

        StandardTermsResultStrategy(boolean showTermDocCountError) {
            this.showTermDocCountError = showTermDocCountError;
        }

        @Override
        final LeafBucketCollector wrapCollector(LeafBucketCollector primary) {
            return primary;
        }

        @Override
        final PriorityQueue<B> buildPriorityQueue(int size) {
            return new BucketPriorityQueue<>(size, partiallyBuiltBucketComparator);
        }

        @Override
        final void buildSubAggs(B[][] topBucketsPerOrd) throws IOException {
            buildSubAggsForAllBuckets(topBucketsPerOrd, b -> b.bucketOrd, (b, aggs) -> b.aggregations = aggs);
        }

        @Override
        final void collectZeroDocEntriesIfNeeded(long ord) throws IOException {
            if (bucketCountThresholds.getMinDocCount() != 0) {
                return;
            }
            if (InternalOrder.isCountDesc(order) && bucketOrds.bucketsInOrd(ord) >= bucketCountThresholds.getRequiredSize()) {
                return;
            }
            // we need to fill-in the blanks
            for (LeafReaderContext ctx : context.searcher().getTopReaderContext().leaves()) {
                SortedNumericDocValues values = getValues(ctx);
                for (int docId = 0; docId < ctx.reader().maxDoc(); ++docId) {
                    if (values.advanceExact(docId)) {
                        final int valueCount = values.docValueCount();
                        for (int v = 0; v < valueCount; ++v) {
                            long value = values.nextValue();
                            if (longFilter == null || longFilter.accept(value)) {
                                bucketOrds.add(ord, value);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public final void close() {}
    }

    class LongTermsResults extends StandardTermsResultStrategy<LongTerms, LongTerms.Bucket> {
        LongTermsResults(boolean showTermDocCountError) {
            super(showTermDocCountError);
        }

        @Override
        String describe() {
            return "long_terms";
        }

        @Override
        SortedNumericDocValues getValues(LeafReaderContext ctx) throws IOException {
            return valuesSource.longValues(ctx);
        }

        @Override
        LongTerms.Bucket[][] buildTopBucketsPerOrd(int size) {
            return new LongTerms.Bucket[size][];
        }

        @Override
        LongTerms.Bucket[] buildBuckets(int size) {
            return new LongTerms.Bucket[size];
        }

        @Override
        LongTerms.Bucket buildEmptyBucket(long owningBucketOrd) {
            return new LongTerms.Bucket(0, 0, null, showTermDocCountError, 0, format);
        }

        @Override
        void updateBucket(LongTerms.Bucket spare, BucketOrdsEnum ordsEnum, long docCount) {
            spare.term = ordsEnum.value();
            spare.docCount = docCount;
            spare.bucketOrd = ordsEnum.ord();
        }

        @Override
        LongTerms buildResult(long owningBucketOrd, long otherDocCount, LongTerms.Bucket[] topBuckets) {
            return new LongTerms(name, order, bucketCountThresholds.getRequiredSize(), bucketCountThresholds.getMinDocCount(),
                metadata(), format, bucketCountThresholds.getShardSize(), showTermDocCountError, otherDocCount,
                List.of(topBuckets), 0);
        }

        @Override
        LongTerms buildEmptyResult() {
            return new LongTerms(name, order, bucketCountThresholds.getRequiredSize(), bucketCountThresholds.getMinDocCount(),
                metadata(), format, bucketCountThresholds.getShardSize(), showTermDocCountError, 0, emptyList(), 0);
        }
    }

    class DoubleTermsResults extends StandardTermsResultStrategy<DoubleTerms, DoubleTerms.Bucket> {
        DoubleTermsResults(boolean showTermDocCountError) {
            super(showTermDocCountError);
        }

        @Override
        String describe() {
            return "double_terms";
        }

        @Override
        SortedNumericDocValues getValues(LeafReaderContext ctx) throws IOException {
            return FieldData.toSortableLongBits(valuesSource.doubleValues(ctx));
        }

        @Override
        DoubleTerms.Bucket[][] buildTopBucketsPerOrd(int size) {
            return new DoubleTerms.Bucket[size][];
        }

        @Override
        DoubleTerms.Bucket[] buildBuckets(int size) {
            return new DoubleTerms.Bucket[size];
        }

        @Override
        DoubleTerms.Bucket buildEmptyBucket(long owningBucketeOrd) {
            return new DoubleTerms.Bucket(0, 0, null, showTermDocCountError, 0, format);
        }

        @Override
        void updateBucket(DoubleTerms.Bucket spare, BucketOrdsEnum ordsEnum, long docCount) {
            spare.term = NumericUtils.sortableLongToDouble(ordsEnum.value());
            spare.docCount = docCount;
            spare.bucketOrd = ordsEnum.ord();
        }

        @Override
        DoubleTerms buildResult(long owningBucketOrd, long otherDocCount, DoubleTerms.Bucket[] topBuckets) {
            return new DoubleTerms(name, order, bucketCountThresholds.getRequiredSize(), bucketCountThresholds.getMinDocCount(),
                metadata(), format, bucketCountThresholds.getShardSize(), showTermDocCountError, otherDocCount,
                List.of(topBuckets), 0);
        }

        @Override
        DoubleTerms buildEmptyResult() {
            return new DoubleTerms(name, order, bucketCountThresholds.getRequiredSize(), bucketCountThresholds.getMinDocCount(),
                metadata(), format, bucketCountThresholds.getShardSize(), showTermDocCountError, 0, emptyList(), 0);
        }
    }

    class SignificantLongTermsResults extends ResultStrategy<SignificantLongTerms, SignificantLongTerms.Bucket> {
        // TODO a reference to the factory is weird - probably should be reference to what we need from it.
        private final SignificantTermsAggregatorFactory termsAggFactory;
        private final SignificanceHeuristic significanceHeuristic;
        private LongArray subsetSize;

        SignificantLongTermsResults(SignificantTermsAggregatorFactory termsAggFactory, SignificanceHeuristic significanceHeuristic) {
            this.termsAggFactory = termsAggFactory;
            this.significanceHeuristic = significanceHeuristic;
            this.subsetSize = context.bigArrays().newLongArray(1, true);
        }

        @Override
        SortedNumericDocValues getValues(LeafReaderContext ctx) throws IOException {
            return valuesSource.longValues(ctx);
        }

        @Override
        String describe() {
            return "significant_terms";
        }

        @Override
        LeafBucketCollector wrapCollector(LeafBucketCollector primary) {
            return new LeafBucketCollectorBase(primary, null) {
                @Override
                public void collect(int doc, long owningBucketOrd) throws IOException {
                    primary.collect(doc, owningBucketOrd);
                    subsetSize = context.bigArrays().grow(subsetSize, owningBucketOrd + 1);
                    subsetSize.increment(owningBucketOrd, 1);
                }
            };
        }

        @Override
        SignificantLongTerms.Bucket[][] buildTopBucketsPerOrd(int size) {
            return new SignificantLongTerms.Bucket[size][];
        }

        @Override
        SignificantLongTerms.Bucket[] buildBuckets(int size) {
            return new SignificantLongTerms.Bucket[size];
        }

        @Override
        SignificantLongTerms.Bucket buildEmptyBucket(long owningBucketOrd) {
            return new SignificantLongTerms.Bucket(
                0, subsetSize.get(owningBucketOrd), 0, termsAggFactory.getSupersetNumDocs(), 0, null, format, 0
            );
        }

        @Override
        void updateBucket(SignificantLongTerms.Bucket spare, BucketOrdsEnum ordsEnum, long docCount) throws IOException {
            spare.term = ordsEnum.value();
            spare.subsetDf = docCount;
            spare.supersetDf = termsAggFactory.getBackgroundFrequency(spare.term);
            spare.bucketOrd = ordsEnum.ord();
            // During shard-local down-selection we use subset/superset stats that are for this shard only
            // Back at the central reducer these properties will be updated with global stats
            spare.updateScore(significanceHeuristic);
        }

        @Override
        PriorityQueue<SignificantLongTerms.Bucket> buildPriorityQueue(int size) {
            return new BucketSignificancePriorityQueue<>(size);
        }

        @Override
        void buildSubAggs(SignificantLongTerms.Bucket[][] topBucketsPerOrd) throws IOException {
            buildSubAggsForAllBuckets(topBucketsPerOrd, b -> b.bucketOrd, (b, aggs) -> b.aggregations = aggs);
        }

        @Override
        void collectZeroDocEntriesIfNeeded(long ord) throws IOException {}

        @Override
        SignificantLongTerms buildResult(long owningBucketOrd, long otherDocCounts, SignificantLongTerms.Bucket[] topBuckets) {
            return new SignificantLongTerms(name, bucketCountThresholds.getRequiredSize(), bucketCountThresholds.getMinDocCount(),
                metadata(), format, subsetSize.get(owningBucketOrd), termsAggFactory.getSupersetNumDocs(),
                significanceHeuristic, List.of(topBuckets));
        }

        @Override
        SignificantLongTerms buildEmptyResult() {
            // We need to account for the significance of a miss in our global stats - provide corpus size as context
            ContextIndexSearcher searcher = context.searcher();
            IndexReader topReader = searcher.getIndexReader();
            int supersetSize = topReader.numDocs();
            return new SignificantLongTerms(name, bucketCountThresholds.getRequiredSize(), bucketCountThresholds.getMinDocCount(),
                    metadata(), format, 0, supersetSize, significanceHeuristic, emptyList());
        }

        @Override
        public void close() {
            Releasables.close(termsAggFactory, subsetSize);
        }
    }
}
