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

package org.elasticsearch.search.aggregations.bucket;

import com.carrotsearch.hppc.LongOpenHashSet;
import com.carrotsearch.hppc.LongSet;
import com.carrotsearch.randomizedtesting.generators.RandomStrings;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Before;

import java.util.*;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.search.aggregations.AggregationBuilders.histogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

public class MinDocCountTests extends ElasticsearchIntegrationTest {

    private static final QueryBuilder QUERY = QueryBuilders.termQuery("match", true);

    @Override
    public Settings indexSettings() {
        return ImmutableSettings.builder()
                .put("index.number_of_shards", between(1, 5))
                .put("index.number_of_replicas", between(0, 1))
                .build();
    }

    private int cardinality;

    @Before
    public void indexData() throws Exception {
        createIndex("idx");

        cardinality = randomIntBetween(8, 30);
        final List<IndexRequestBuilder> indexRequests = new ArrayList<IndexRequestBuilder>();
        final Set<String> stringTerms = new HashSet<String>();
        final LongSet longTerms = new LongOpenHashSet();
        for (int i = 0; i < cardinality; ++i) {
            String stringTerm;
            do {
                stringTerm = RandomStrings.randomAsciiOfLength(getRandom(), 8);
            } while (!stringTerms.add(stringTerm));
            long longTerm;
            do {
                longTerm = randomInt(cardinality * 2);
            } while (!longTerms.add(longTerm));
            double doubleTerm = longTerm * Math.PI;
            final int frequency = randomBoolean() ? 1 : randomIntBetween(2, 20);
            for (int j = 0; j < frequency; ++j) {
                indexRequests.add(client().prepareIndex("idx", "type").setSource(jsonBuilder().startObject().field("s", stringTerm).field("l", longTerm).field("d", doubleTerm).field("match", randomBoolean()).endObject()));
            }
        }
        cardinality = stringTerms.size();

        indexRandom(true, indexRequests);
        ensureSearchable();
    }

    private enum Script {
        NO {
            @Override
            TermsBuilder apply(TermsBuilder builder, String field) {
                return builder.field(field);
            }
        },
        YES {
            @Override
            TermsBuilder apply(TermsBuilder builder, String field) {
                return builder.script("doc['" + field + "'].values");
            }
        };
        abstract TermsBuilder apply(TermsBuilder builder, String field);
    }

    // check that terms2 is a subset of terms1
    private void assertSubset(Terms terms1, Terms terms2, long minDocCount, int size) {
        final Iterator<Terms.Bucket> it1 = terms1.iterator();
        final Iterator<Terms.Bucket> it2 = terms2.iterator();
        int size2 = 0;
        while (it1.hasNext()) {
            final Terms.Bucket bucket1 = it1.next();
            if (bucket1.getDocCount() >= minDocCount) {
                if (size2++ == size) {
                    break;
                }
                assertTrue(it2.hasNext());
                final Terms.Bucket bucket2 = it2.next();
                assertEquals(bucket1.getKey(), bucket2.getKey());
                assertEquals(bucket1.getDocCount(), bucket2.getDocCount());
            }
        }
        assertFalse(it2.hasNext());
    }

    private void assertSubset(Histogram histo1, Histogram histo2, long minDocCount) {
        final Iterator<Histogram.Bucket> it2 = histo2.iterator();
        for (Histogram.Bucket b1 : histo1) {
            if (b1.getDocCount() >= minDocCount) {
                final Histogram.Bucket b2 = it2.next();
                assertEquals(b1.getKey(), b2.getKey());
                assertEquals(b1.getDocCount(), b2.getDocCount());
            }
        }
    }

    public void testStringTermAsc() throws Exception {
        testMinDocCountOnTerms("s", Script.NO, Terms.Order.term(true));
    }

    public void testStringScriptTermAsc() throws Exception {
        testMinDocCountOnTerms("s", Script.YES, Terms.Order.term(true));
    }

    public void testStringTermDesc() throws Exception {
        testMinDocCountOnTerms("s", Script.NO, Terms.Order.term(false));
    }

    public void testStringScriptTermDesc() throws Exception {
        testMinDocCountOnTerms("s", Script.YES, Terms.Order.term(false));
    }

    public void testStringCountAsc() throws Exception {
        testMinDocCountOnTerms("s", Script.NO, Terms.Order.count(true));
    }

    public void testStringScriptCountAsc() throws Exception {
        testMinDocCountOnTerms("s", Script.YES, Terms.Order.count(true));
    }

    public void testStringCountDesc() throws Exception {
        testMinDocCountOnTerms("s", Script.NO, Terms.Order.count(false));
    }

    public void testStringScriptCountDesc() throws Exception {
        testMinDocCountOnTerms("s", Script.YES, Terms.Order.count(false));
    }

    public void testLongTermAsc() throws Exception {
        testMinDocCountOnTerms("l", Script.NO, Terms.Order.term(true));
    }

    public void testLongScriptTermAsc() throws Exception {
        testMinDocCountOnTerms("l", Script.YES, Terms.Order.term(true));
    }

    public void testLongTermDesc() throws Exception {
        testMinDocCountOnTerms("l", Script.NO, Terms.Order.term(false));
    }

    public void testLongScriptTermDesc() throws Exception {
        testMinDocCountOnTerms("l", Script.YES, Terms.Order.term(false));
    }

    public void testLongCountAsc() throws Exception {
        testMinDocCountOnTerms("l", Script.NO, Terms.Order.count(true));
    }

    public void testLongScriptCountAsc() throws Exception {
        testMinDocCountOnTerms("l", Script.YES, Terms.Order.count(true));
    }

    public void testLongCountDesc() throws Exception {
        testMinDocCountOnTerms("l", Script.NO, Terms.Order.count(false));
    }

    public void testLongScriptCountDesc() throws Exception {
        testMinDocCountOnTerms("l", Script.YES, Terms.Order.count(false));
    }

    public void testDoubleTermAsc() throws Exception {
        testMinDocCountOnTerms("d", Script.NO, Terms.Order.term(true));
    }

    public void testDoubleScriptTermAsc() throws Exception {
        testMinDocCountOnTerms("d", Script.YES, Terms.Order.term(true));
    }

    public void testDoubleTermDesc() throws Exception {
        testMinDocCountOnTerms("d", Script.NO, Terms.Order.term(false));
    }

    public void testDoubleScriptTermDesc() throws Exception {
        testMinDocCountOnTerms("d", Script.YES, Terms.Order.term(false));
    }

    public void testDoubleCountAsc() throws Exception {
        testMinDocCountOnTerms("d", Script.NO, Terms.Order.count(true));
    }

    public void testDoubleScriptCountAsc() throws Exception {
        testMinDocCountOnTerms("d", Script.YES, Terms.Order.count(true));
    }

    public void testDoubleCountDesc() throws Exception {
        testMinDocCountOnTerms("d", Script.NO, Terms.Order.count(false));
    }

    public void testDoubleScriptCountDesc() throws Exception {
        testMinDocCountOnTerms("d", Script.YES, Terms.Order.count(false));
    }

    public void testMinDocCountOnTerms(String field, Script script, Terms.Order order) throws Exception {
        // all terms
        final SearchResponse allTermsResponse = client().prepareSearch("idx").setTypes("type")
                .setSearchType(SearchType.COUNT)
                .setQuery(QUERY)
                .addAggregation(script.apply(terms("terms"), field)
                        .executionHint(StringTermsTests.randomExecutionHint())
                        .order(order)
                        .size(cardinality + randomInt(10))
                        .minDocCount(0))
                .execute().actionGet();
        final Terms allTerms = allTermsResponse.getAggregations().get("terms");
        assertEquals(cardinality, allTerms.buckets().size());

        for (long minDocCount = 0; minDocCount < 20; ++minDocCount) {
            final int size = randomIntBetween(1, cardinality + 2);
            final SearchResponse response = client().prepareSearch("idx").setTypes("type")
                    .setSearchType(SearchType.COUNT)
                    .setQuery(QUERY)
                    .addAggregation(script.apply(terms("terms"), field)
                            .executionHint(StringTermsTests.randomExecutionHint())
                            .order(order)
                            .size(size)
                            .shardSize(cardinality + randomInt(10))
                            .minDocCount(minDocCount))
                    .execute().actionGet();
            assertSubset(allTerms, (Terms) response.getAggregations().get("terms"), minDocCount, size);
        }

    }

    public void testHistogramCountAsc() throws Exception {
        testHistogram(Histogram.Order.COUNT_ASC);
    }

    public void testHistogramCountDesc() throws Exception {
        testHistogram(Histogram.Order.COUNT_DESC);
    }

    public void testHistogramKeyAsc() throws Exception {
        testHistogram(Histogram.Order.KEY_ASC);
    }

    public void testHistogramKeyDesc() throws Exception {
        testHistogram(Histogram.Order.KEY_DESC);
    }

    public void testHistogram(Histogram.Order order) throws Exception {
        final int interval = randomIntBetween(1, 3);
        final SearchResponse allResponse = client().prepareSearch("idx").setTypes("type")
                .setSearchType(SearchType.COUNT)
                .setQuery(QUERY)
                .addAggregation(histogram("histo").field("d").interval(interval).order(order).minDocCount(0))
                .execute().actionGet();

        final Histogram allHisto = allResponse.getAggregations().get("histo");

        for (long minDocCount = 0; minDocCount < 50; ++minDocCount) {
            final SearchResponse response = client().prepareSearch("idx").setTypes("type")
                    .setSearchType(SearchType.COUNT)
                    .setQuery(QUERY)
                    .addAggregation(histogram("histo").field("d").interval(interval).order(order).minDocCount(minDocCount))
                    .execute().actionGet();
            assertSubset(allHisto, (Histogram) response.getAggregations().get("histo"), minDocCount);
        }

    }

}
