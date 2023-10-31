/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.metrics.InternalAvg;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.junit.Before;

import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.search.aggregations.AggregationBuilders.avg;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.hamcrest.Matchers.hasSize;

@SuppressWarnings("resource")
public class IgnoreFieldIT extends ESIntegTestCase {

    public static final String NUMERIC_FIELD_NAME = "numeric_field";
    public static final String DATE_FIELD_NAME = "date_field";
    public static final String TEST_INDEX = "test-index";
    public static final String CORRECT_FIELD_TYPE_DOC_ID = "1";
    public static final String WRONG_FIELD_TYPE_DOC_ID = "2";

    @Before
    public void createTestIndex() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("_doc")
            .startObject("properties")
            .startObject(NUMERIC_FIELD_NAME)
            .field("type", "long")
            .field("ignore_malformed", true)
            .endObject()
            .startObject(DATE_FIELD_NAME)
            .field("type", "date")
            .field("ignore_malformed", true)
            .endObject()
            .endObject()
            .endObject()
            .endObject();
        assertAcked(indicesAdmin().prepareCreate(TEST_INDEX).setMapping(mapping));
        indexTestDoc(NUMERIC_FIELD_NAME, CORRECT_FIELD_TYPE_DOC_ID, "42");
        indexTestDoc(NUMERIC_FIELD_NAME, WRONG_FIELD_TYPE_DOC_ID, "forty-two");
    }

    public void testIgnoreFieldFetch() {
        SearchResponse searchResponse = prepareSearch().setQuery(new IdsQueryBuilder().addIds(CORRECT_FIELD_TYPE_DOC_ID))
            .addFetchField(NUMERIC_FIELD_NAME)
            .get();
        assertHitCount(searchResponse, 1);
        SearchHit hit = searchResponse.getHits().getAt(0);
        DocumentField numericField = hit.field(NUMERIC_FIELD_NAME);
        assertNotNull(numericField);
        assertEquals(42, (long) numericField.getValue());
        DocumentField ignoredField = hit.field(IgnoredFieldMapper.NAME);
        assertNull(ignoredField);

        searchResponse = prepareSearch().setQuery(new IdsQueryBuilder().addIds(WRONG_FIELD_TYPE_DOC_ID))
            .addFetchField(NUMERIC_FIELD_NAME)
            .get();
        assertHitCount(searchResponse, 1);
        hit = searchResponse.getHits().getAt(0);
        numericField = hit.field(NUMERIC_FIELD_NAME);
        assertNotNull(numericField);
        assertEquals("forty-two", numericField.getIgnoredValues().get(0));
        ignoredField = hit.field(IgnoredFieldMapper.NAME);
        assertNotNull(ignoredField);
        assertEquals(NUMERIC_FIELD_NAME, ignoredField.getValue());
    }

    public void testExistsQuery() {
        SearchResponse searchResponse = prepareSearch().setQuery(new ExistsQueryBuilder(IgnoredFieldMapper.NAME))
            .addFetchField(NUMERIC_FIELD_NAME)
            .get();
        assertHitCount(searchResponse, 1);
        SearchHit hit = searchResponse.getHits().getAt(0);
        assertEquals(WRONG_FIELD_TYPE_DOC_ID, hit.getId());
    }

    public void testIgnoreFieldAggregation() {
        indexTestDoc(NUMERIC_FIELD_NAME, "correct-44", "44");
        SearchResponse avgSearch = prepareSearch(TEST_INDEX).setSize(0)
            .addAggregation(avg("numeric-field-aggs").field(NUMERIC_FIELD_NAME))
            .get();
        assertTrue(avgSearch.hasAggregations());
        InternalAvg avg = avgSearch.getAggregations().get("numeric-field-aggs");
        assertNotNull(avg);
        assertEquals(43.0, avg.getValue(), 0.0);

        indexTestDoc(NUMERIC_FIELD_NAME, "wrong-44", "forty-four");
        indexTestDoc(DATE_FIELD_NAME, "wrong-date", "today");
        SearchResponse termsSearch = prepareSearch(TEST_INDEX).setSize(0)
            .addAggregation(terms("ignored-field-aggs").field(IgnoredFieldMapper.NAME))
            .get();
        assertTrue(termsSearch.hasAggregations());
        StringTerms terms = termsSearch.getAggregations().get("ignored-field-aggs");
        assertNotNull(terms);
        assertThat(terms.getBuckets(), hasSize(2));
        StringTerms.Bucket numericFieldBucket = terms.getBucketByKey(NUMERIC_FIELD_NAME);
        assertEquals(NUMERIC_FIELD_NAME, numericFieldBucket.getKeyAsString());
        assertEquals(2, numericFieldBucket.getDocCount());
        StringTerms.Bucket dateFieldBucket = terms.getBucketByKey(DATE_FIELD_NAME);
        assertEquals(DATE_FIELD_NAME, dateFieldBucket.getKeyAsString());
        assertEquals(1, dateFieldBucket.getDocCount());
    }

    private static void indexTestDoc(String testField, String docId, String testValue) {
        DocWriteResponse docWriteResponse = client().prepareIndex(TEST_INDEX)
            .setId(docId)
            .setSource(testField, testValue)
            .setRefreshPolicy(IMMEDIATE)
            .get();
        assertEquals(RestStatus.CREATED, docWriteResponse.status());
    }
}
