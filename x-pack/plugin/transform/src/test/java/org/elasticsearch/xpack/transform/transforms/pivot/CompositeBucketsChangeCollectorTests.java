/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.transform.transforms.pivot;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchResponseSections;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation.SingleValue;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.core.transform.transforms.TransformCheckpoint;
import org.elasticsearch.xpack.core.transform.transforms.pivot.DateHistogramGroupSource;
import org.elasticsearch.xpack.core.transform.transforms.pivot.GeoTileGroupSourceTests;
import org.elasticsearch.xpack.core.transform.transforms.pivot.GroupConfig;
import org.elasticsearch.xpack.core.transform.transforms.pivot.GroupConfigTests;
import org.elasticsearch.xpack.core.transform.transforms.pivot.ScriptConfigTests;
import org.elasticsearch.xpack.core.transform.transforms.pivot.SingleGroupSource;
import org.elasticsearch.xpack.core.transform.transforms.pivot.TermsGroupSource;
import org.elasticsearch.xpack.core.transform.transforms.pivot.TermsGroupSourceTests;
import org.elasticsearch.xpack.transform.transforms.Function.ChangeCollector;
import org.elasticsearch.xpack.transform.transforms.pivot.CompositeBucketsChangeCollector.FieldCollector;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompositeBucketsChangeCollectorTests extends ESTestCase {

    /**
     * Simple unit tests to check that a field collector has been implemented for any single source type
     */
    public void testCreateFieldCollector() {
        GroupConfig groupConfig = GroupConfigTests.randomGroupConfig();

        Map<String, FieldCollector> changeCollector = CompositeBucketsChangeCollector.createFieldCollectors(
            groupConfig.getGroups(),
            randomBoolean() ? randomAlphaOfLengthBetween(3, 10) : null
        );

        assertNotNull(changeCollector);
    }

    public void testPageSize() throws IOException {
        Map<String, SingleGroupSource> groups = new LinkedHashMap<>();

        // a terms group_by is limited by terms query
        SingleGroupSource termsGroupBy = TermsGroupSourceTests.randomTermsGroupSourceNoScript();
        groups.put("terms", termsGroupBy);

        ChangeCollector collector = CompositeBucketsChangeCollector.buildChangeCollector(groups, null);
        assertEquals(1_000, getCompositeAggregationBuilder(collector.buildChangesQuery(new SearchSourceBuilder(), null, 1_000)).size());
        assertEquals(10_000, getCompositeAggregationBuilder(collector.buildChangesQuery(new SearchSourceBuilder(), null, 10_000)).size());
        assertEquals(10, getCompositeAggregationBuilder(collector.buildChangesQuery(new SearchSourceBuilder(), null, 10)).size());
        assertEquals(65536, getCompositeAggregationBuilder(collector.buildChangesQuery(new SearchSourceBuilder(), null, 100_000)).size());
        assertEquals(
            65536,
            getCompositeAggregationBuilder(collector.buildChangesQuery(new SearchSourceBuilder(), null, Integer.MAX_VALUE)).size()
        );

        // a geo tile group_by is limited by query clauses
        SingleGroupSource geoGroupBy = GeoTileGroupSourceTests.randomGeoTileGroupSource();
        groups.put("geo_tile", geoGroupBy);

        collector = CompositeBucketsChangeCollector.buildChangeCollector(groups, null);
        assertEquals(1_000, getCompositeAggregationBuilder(collector.buildChangesQuery(new SearchSourceBuilder(), null, 1_000)).size());
        assertEquals(1_024, getCompositeAggregationBuilder(collector.buildChangesQuery(new SearchSourceBuilder(), null, 10_000)).size());
    }

    public void testTermsFieldCollector() throws IOException {
        Map<String, SingleGroupSource> groups = new LinkedHashMap<>();

        // a terms group_by is limited by terms query
        SingleGroupSource termsGroupBy = new TermsGroupSource("id", null, false);
        groups.put("id", termsGroupBy);

        ChangeCollector collector = CompositeBucketsChangeCollector.buildChangeCollector(groups, null);

        CompositeAggregation composite = mock(CompositeAggregation.class);
        when(composite.getName()).thenReturn("_transform_change_collector");
        when(composite.getBuckets()).thenAnswer((Answer<List<CompositeAggregation.Bucket>>) invocationOnMock -> {
            List<CompositeAggregation.Bucket> compositeBuckets = new ArrayList<>();
            CompositeAggregation.Bucket bucket = mock(CompositeAggregation.Bucket.class);
            when(bucket.getKey()).thenReturn(Collections.singletonMap("id", "id1"));
            compositeBuckets.add(bucket);

            bucket = mock(CompositeAggregation.Bucket.class);
            when(bucket.getKey()).thenReturn(Collections.singletonMap("id", "id2"));
            compositeBuckets.add(bucket);

            bucket = mock(CompositeAggregation.Bucket.class);
            when(bucket.getKey()).thenReturn(Collections.singletonMap("id", "id3"));
            compositeBuckets.add(bucket);

            return compositeBuckets;
        });
        Aggregations aggs = new Aggregations(Collections.singletonList(composite));

        SearchResponseSections sections = new SearchResponseSections(null, aggs, null, false, null, null, 1);
        SearchResponse response = new SearchResponse(sections, null, 1, 1, 0, 0, ShardSearchFailure.EMPTY_ARRAY, null);

        collector.processSearchResponse(response);

        QueryBuilder queryBuilder = collector.buildFilterQuery(
            new TransformCheckpoint("t_id", 42L, 42L, Collections.emptyMap(), 0L),
            new TransformCheckpoint("t_id", 42L, 42L, Collections.emptyMap(), 0L)
        );
        assertNotNull(queryBuilder);
        assertThat(queryBuilder, instanceOf(TermsQueryBuilder.class));
        assertThat(((TermsQueryBuilder) queryBuilder).values(), containsInAnyOrder("id1", "id2", "id3"));
    }

    public void testNoTermsFieldCollectorForScripts() throws IOException {
        Map<String, SingleGroupSource> groups = new LinkedHashMap<>();

        // terms with value script
        SingleGroupSource termsGroupBy = new TermsGroupSource("id", ScriptConfigTests.randomScriptConfig(), false);
        groups.put("id", termsGroupBy);

        Map<String, FieldCollector> fieldCollectors = CompositeBucketsChangeCollector.createFieldCollectors(groups, null);
        assertTrue(fieldCollectors.isEmpty());

        // terms with only a script
        termsGroupBy = new TermsGroupSource(null, ScriptConfigTests.randomScriptConfig(), false);
        groups.put("id", termsGroupBy);

        fieldCollectors = CompositeBucketsChangeCollector.createFieldCollectors(groups, null);
        assertTrue(fieldCollectors.isEmpty());
    }

    private static CompositeAggregationBuilder getCompositeAggregationBuilder(SearchSourceBuilder builder) {
        return (CompositeAggregationBuilder) builder.aggregations().getAggregatorFactories().iterator().next();
    }
}
