/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.search;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.InternalTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.metrics.InternalMax;
import org.elasticsearch.search.aggregations.metrics.InternalMin;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xpack.core.search.action.AsyncSearchResponse;
import org.elasticsearch.xpack.core.search.action.SubmitAsyncSearchRequest;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class AsyncSearchIT extends AsyncSearchIntegTestCase {
    private String indexName;
    private int numShards;
    private int numDocs;

    private int numKeywords;
    private Map<String, AtomicInteger> keywordFreqs;
    private float maxMetric = Float.NEGATIVE_INFINITY;
    private float minMetric = Float.POSITIVE_INFINITY;

    @Before
    public void indexDocuments() throws InterruptedException {
        indexName = "test-async";
        numShards = randomIntBetween(internalCluster().numDataNodes(), internalCluster().numDataNodes()*10);
        numDocs = randomIntBetween(numShards, numShards*10);
        createIndex(indexName, Settings.builder().put("index.number_of_shards", numShards).build());
        numKeywords = randomIntBetween(1, 100);
        keywordFreqs = new HashMap<>();
        String[] keywords = new String[numKeywords];
        for (int i = 0; i < numKeywords; i++) {
            keywords[i] = randomAlphaOfLengthBetween(10, 20);
        }
        for (int i = 0; i < numDocs; i++) {
            List<IndexRequestBuilder> reqs = new ArrayList<>();
            float metric = randomFloat();
            maxMetric = Math.max(metric, maxMetric);
            minMetric = Math.min(metric, minMetric);
            String keyword = keywords[randomIntBetween(0, numKeywords-1)];
            keywordFreqs.compute(keyword,
                (k, v) -> {
                    if (v == null) {
                        return new AtomicInteger(1);
                    }
                    v.incrementAndGet();
                    return v;
            });
            reqs.add(client().prepareIndex(indexName).setSource("terms", keyword, "metric", metric));
            indexRandom(true, true, reqs);
        }
        ensureGreen("test-async");
    }

    public void testMaxMinAggregation() throws Exception {
        int step = numShards > 2 ? randomIntBetween(2, numShards) : 2;
        int numFailures = numShards;//randomBoolean() ? randomIntBetween(0, numShards) : 0;
        SearchSourceBuilder source = new SearchSourceBuilder()
            .aggregation(AggregationBuilders.min("min").field("metric"))
            .aggregation(AggregationBuilders.max("max").field("metric"));
        try (SearchResponseIterator it =
                 assertBlockingIterator(indexName, source, numFailures, step)) {
            AsyncSearchResponse response = it.next();
            while (it.hasNext()) {
                response = it.next();
                if (response.hasPartialResponse() && response.getPartialResponse().getSuccessfulShards() > 0) {
                    assertNotNull(response.getPartialResponse().getAggregations());
                    assertNotNull(response.getPartialResponse().getAggregations().get("max"));
                    assertNotNull(response.getPartialResponse().getAggregations().get("min"));
                    InternalMax max = response.getPartialResponse().getAggregations().get("max");
                    InternalMin min = response.getPartialResponse().getAggregations().get("min");
                    assertThat((float) min.getValue(), greaterThanOrEqualTo(minMetric));
                    assertThat((float) max.getValue(), lessThanOrEqualTo(maxMetric));
                }
            }
            if (numFailures == numShards) {
                assertTrue(response.hasFailed());
            } else {
                assertTrue(response.isFinalResponse());
                assertNotNull(response.getSearchResponse().getAggregations());
                assertNotNull(response.getSearchResponse().getAggregations().get("max"));
                assertNotNull(response.getSearchResponse().getAggregations().get("min"));
                InternalMax max = response.getSearchResponse().getAggregations().get("max");
                InternalMin min = response.getSearchResponse().getAggregations().get("min");
                if (numFailures == 0) {
                    assertThat((float) min.getValue(), equalTo(minMetric));
                    assertThat((float) max.getValue(), equalTo(maxMetric));
                } else {
                    assertThat((float) min.getValue(), greaterThanOrEqualTo(minMetric));
                    assertThat((float) max.getValue(), lessThanOrEqualTo(maxMetric));
                }
            }
            ensureTaskRemoval(response.id());
        }
    }

    public void testTermsAggregation() throws Exception {
        int step = numShards > 2 ? randomIntBetween(2, numShards) : 2;
        int numFailures = randomBoolean() ? randomIntBetween(0, numShards) : 0;
        int termsSize = randomIntBetween(1, numKeywords);
        SearchSourceBuilder source = new SearchSourceBuilder()
            .aggregation(AggregationBuilders.terms("terms").field("terms.keyword").size(termsSize).shardSize(termsSize*2));
        try (SearchResponseIterator it =
                 assertBlockingIterator(indexName, source, numFailures, step)) {
            AsyncSearchResponse response = it.next();
            while (it.hasNext()) {
                response = it.next();
                if (response.hasPartialResponse() && response.getPartialResponse().getSuccessfulShards() > 0) {
                    assertNotNull(response.getPartialResponse().getAggregations());
                    assertNotNull(response.getPartialResponse().getAggregations().get("terms"));
                    StringTerms terms = response.getPartialResponse().getAggregations().get("terms");
                    assertThat(terms.getBuckets().size(), greaterThanOrEqualTo(0));
                    assertThat(terms.getBuckets().size(), lessThanOrEqualTo(termsSize));
                    for (InternalTerms.Bucket bucket : terms.getBuckets()) {
                        long count = keywordFreqs.getOrDefault(bucket.getKeyAsString(), new AtomicInteger(0)).get();
                        assertThat(bucket.getDocCount(), lessThanOrEqualTo(count));
                    }
                }
            }
            if (numFailures == numShards) {
                assertTrue(response.hasFailed());
            } else {
                assertTrue(response.isFinalResponse());
                assertNotNull(response.getSearchResponse().getAggregations());
                assertNotNull(response.getSearchResponse().getAggregations().get("terms"));
                StringTerms terms = response.getSearchResponse().getAggregations().get("terms");
                assertThat(terms.getBuckets().size(), greaterThanOrEqualTo(0));
                assertThat(terms.getBuckets().size(), lessThanOrEqualTo(termsSize));
                for (InternalTerms.Bucket bucket : terms.getBuckets()) {
                    long count = keywordFreqs.getOrDefault(bucket.getKeyAsString(), new AtomicInteger(0)).get();
                    if (numFailures > 0) {
                        assertThat(bucket.getDocCount(), lessThanOrEqualTo(count));
                    } else {
                        assertThat(bucket.getDocCount(), equalTo(count));
                    }
                }
            }
            ensureTaskRemoval(response.id());
        }
    }

    public void testRestartAfterCompletion() throws Exception {
        final AsyncSearchResponse initial;
        try (SearchResponseIterator it =
                 assertBlockingIterator(indexName, new SearchSourceBuilder(), 0, 2)) {
            initial = it.next();
        }
        ensureTaskCompletion(initial.id());
        restartTaskNode(initial.id());
        AsyncSearchResponse response = getAsyncSearch(initial.id());
        assertTrue(response.isFinalResponse());
        assertFalse(response.isRunning());
        assertFalse(response.hasPartialResponse());
        ensureTaskRemoval(response.id());
    }

    public void testDeleteCancelRunningTask() throws Exception {
        final AsyncSearchResponse initial;
        SearchResponseIterator it =
            assertBlockingIterator(indexName, new SearchSourceBuilder(), randomBoolean() ? 1 : 0, 2);
        initial = it.next();
        deleteAsyncSearch(initial.id());
        it.close();
        ensureTaskCompletion(initial.id());
        ensureTaskRemoval(initial.id());
    }

    public void testDeleteCleanupIndex() throws Exception {
        SubmitAsyncSearchRequest request = new SubmitAsyncSearchRequest(new String[] { indexName });
        request.setWaitForCompletion(TimeValue.timeValueMillis(1));
        SearchResponseIterator it =
            assertBlockingIterator(indexName, new SearchSourceBuilder(), randomBoolean() ? 1 : 0, 2);
        AsyncSearchResponse response = it.next();
        deleteAsyncSearch(response.id());
        it.close();
        ensureTaskCompletion(response.id());
        ensureTaskRemoval(response.id());
    }

    public void testCleanupOnFailure() throws Exception {
        final AsyncSearchResponse initial;
        try (SearchResponseIterator it =
                 assertBlockingIterator(indexName, new SearchSourceBuilder(), numShards, 2)) {
            initial = it.next();
        }
        ensureTaskCompletion(initial.id());
        AsyncSearchResponse response = getAsyncSearch(initial.id());
        assertTrue(response.hasFailed());
        assertTrue(response.hasPartialResponse());
        assertThat(response.getPartialResponse().getTotalShards(), equalTo(numShards));
        assertThat(response.getPartialResponse().getShardFailures(), equalTo(numShards));
        ensureTaskRemoval(initial.id());
    }

    public void testInvalidId() throws Exception {
        SubmitAsyncSearchRequest request = new SubmitAsyncSearchRequest(new String[] { indexName });
        request.setWaitForCompletion(TimeValue.timeValueMillis(1));
        SearchResponseIterator it =
            assertBlockingIterator(indexName, new SearchSourceBuilder(), randomBoolean() ? 1 : 0, 2);
        AsyncSearchResponse response = it.next();
        AsyncSearchId original = AsyncSearchId.decode(response.id());
        String invalid = AsyncSearchId.encode("another_index", original.getDocId(), original.getTaskId());
        ExecutionException exc = expectThrows(ExecutionException.class, () -> getAsyncSearch(invalid));
        assertThat(exc.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(exc.getMessage(), containsString("invalid id"));
        while (it.hasNext()) {
            response = it.next();
        }
        assertTrue(response.isFinalResponse());
    }
}
