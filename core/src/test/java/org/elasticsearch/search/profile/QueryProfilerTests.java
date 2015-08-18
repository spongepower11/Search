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

package org.elasticsearch.search.profile;


import org.elasticsearch.search.SearchHit;


import org.apache.lucene.util.English;
import org.elasticsearch.action.index.IndexRequestBuilder;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;


import org.elasticsearch.index.query.*;

import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.elasticsearch.search.profile.RandomQueryGenerator.randomQueryBuilder;
import static org.elasticsearch.test.hamcrest.DoubleMatcher.nearlyEqual;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;


@ESIntegTestCase.ClusterScope(scope=ESIntegTestCase.Scope.TEST)
public class QueryProfilerTests extends ESIntegTestCase {


    @Test
    /**
     * This test simply checks to make sure nothing crashes.  Unsure how best to validate the Profile response...
     */
    public void testProfileQuery() throws Exception {
        createIndex("test");
        ensureGreen();

        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        List<String> stringFields = Arrays.asList("field1");
        List<String> numericFields = Arrays.asList("field2");

        indexRandom(true, docs);

        refresh();
        int iters = between(20, 100);
        for (int i = 0; i < iters; i++) {
            QueryBuilder q = randomQueryBuilder(stringFields, numericFields, numDocs, 3);
            logger.info(q.toString());

            SearchResponse resp = client().prepareSearch().setQuery(q).setProfile(true).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();
            assertNotNull("Profile response element should not be null", resp.getProfileResults());

        }
    }

    @Test
    /**
     * This test simply checks to make sure nothing crashes.  Unsure how best to validate the Profile response...
     */
    public void testProfileMatchesRegular() throws Exception {
        createIndex("test");
        ensureGreen();

        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        List<String> stringFields = Arrays.asList("field1");
        List<String> numericFields = Arrays.asList("field2");

        indexRandom(true, docs);

        refresh();
        int iters = between(1, 10);
        for (int i = 0; i < iters; i++) {
            QueryBuilder q = randomQueryBuilder(stringFields, numericFields, numDocs, 3);
            logger.info(q.toString());

            SearchResponse vanilla = client().prepareSearch().setQuery(q).setProfile(false).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();
            SearchResponse profile = client().prepareSearch().setQuery(q).setProfile(true).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();

            float vanillaMaxScore = vanilla.getHits().getMaxScore();
            float profileMaxScore = profile.getHits().getMaxScore();
            if (Float.isNaN(vanillaMaxScore)) {
                assertTrue("Vanilla maxScore is NaN but Profile is not [" + profileMaxScore + "]",
                        Float.isNaN(profileMaxScore));
            } else {
                assertTrue("Profile maxScore of [" + profileMaxScore + "] is not close to Vanilla maxScore [" + vanillaMaxScore + "]",
                        nearlyEqual(vanillaMaxScore, profileMaxScore, 0.001));
            }

            assertThat("Profile totalHits of [" + profile.getHits().totalHits() + "] is not close to Vanilla totalHist [" + vanilla.getHits().totalHits() + "]",
                    vanilla.getHits().getTotalHits(), equalTo(profile.getHits().getTotalHits()));

            SearchHit[] vanillaHits = vanilla.getHits().getHits();
            SearchHit[] profileHits = profile.getHits().getHits();

            for (int j = 0; j < vanillaHits.length; j++) {
                assertThat("Profile hit #" + j + " has a different ID from Vanilla",
                        vanillaHits[j].getId(), equalTo(profileHits[j].getId()));
                assertTrue("Profile hit #" + j + "'s score [" + profileHits[j].getScore() + "] is not close to Vanilla [" + vanillaHits[j].getScore() + "]",
                        nearlyEqual(vanillaHits[j].getScore(), profileHits[j].getScore(), 0.001));
            }

        }
    }

    @Test
    /**
     * This test verifies that the output is reasonable for a simple, non-nested query
     */
    public void testSimpleMatch() throws Exception {
        createIndex("test");
        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);
        ensureGreen();

        QueryBuilder q = QueryBuilders.matchQuery("field1", "one");

        SearchResponse resp = client().prepareSearch().setQuery(q).setProfile(true).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();
        ProfileResults p = resp.getProfileResults();
        assertNotNull(p);

        for (ProfileResult result : p.asCollection()) {
            assertEquals(result.getQueryName(), "TermQuery");
            assertEquals(result.getLuceneDescription(), "field1:one");
            assertThat(result.getRelativeTime(), greaterThan(0.0));
            assertThat(result.getTime(), greaterThan(0L));
            assertNotNull(result.getTimeBreakdown());
            assertEquals(result.getProfiledChildren().size(), 0);
        }
    }

    @Test
    /**
     * This test verifies that the output is reasonable for a nested query
     */
    public void testBool() throws Exception {
        createIndex("test");
        ensureGreen();

        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);

        QueryBuilder q = QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("field1", "one")).must(QueryBuilders.matchQuery("field1", "two"));

        SearchResponse resp = client().prepareSearch().setQuery(q).setProfile(true).execute().actionGet();

        ProfileResults p = resp.getProfileResults();
        assertNotNull(p);

        for (ProfileResult result : p.asCollection()) {
            assertEquals(result.getQueryName(), "BooleanQuery");
            assertEquals(result.getLuceneDescription(), "+field1:one +field1:two");
            assertThat(result.getRelativeTime(), greaterThan(0.0));
            assertThat(result.getTime(), greaterThan(0L));
            assertNotNull(result.getTimeBreakdown());
            assertEquals(result.getProfiledChildren().size(), 2);

            // Check the children
            List<ProfileResult> children = result.getProfiledChildren();
            assertEquals(children.size(), 2);

            ProfileResult childProfile = children.get(0);
            assertEquals(childProfile.getQueryName(), "TermQuery");
            assertEquals(childProfile.getLuceneDescription(), "field1:one");
            assertThat(childProfile.getRelativeTime(), greaterThan(0.0));
            assertThat(childProfile.getTime(), greaterThan(0L));
            assertNotNull(childProfile.getTimeBreakdown());
            assertEquals(childProfile.getProfiledChildren().size(), 0);

            childProfile = children.get(1);
            assertEquals(childProfile.getQueryName(), "TermQuery");
            assertEquals(childProfile.getLuceneDescription(), "field1:two");
            assertThat(childProfile.getRelativeTime(), greaterThan(0.0));
            assertThat(childProfile.getTime(), greaterThan(0L));
            assertNotNull(childProfile.getTimeBreakdown());
            assertEquals(childProfile.getProfiledChildren().size(), 0);
        }


    }

    @Test
    public void testEmptyBool() throws Exception {
        createIndex("test");
        ensureGreen();

        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);

        refresh();

        QueryBuilder q = QueryBuilders.boolQuery();
        logger.info(q.toString());

        SearchResponse resp = client().prepareSearch().setQuery(q).setProfile(true).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();
        assertNotNull("Profile response element should not be null", resp.getProfileResults());


    }

    @Test
    public void testCollapsingBool() throws Exception {
        createIndex("test");
        ensureGreen();

        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);

        refresh();

        QueryBuilder q = QueryBuilders.boolQuery().must(QueryBuilders.boolQuery().must(QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("field1", "one"))));


        logger.info(q.toString());

        SearchResponse resp = client().prepareSearch().setQuery(q).setProfile(true).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();
        assertNotNull("Profile response element should not be null", resp.getProfileResults());


    }

    @Test
    public void testBoosting() throws Exception {
        createIndex("test");
        ensureGreen();

        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);

        refresh();

        QueryBuilder q = QueryBuilders.boostingQuery().boost(randomFloat())
                .positive(QueryBuilders.matchQuery("field1", "one"))
                .negativeBoost(randomFloat())
                .negative(QueryBuilders.matchQuery("field1", "two"));
        logger.info(q.toString());

        SearchResponse resp = client().prepareSearch().setQuery(q).setProfile(true).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();
        assertNotNull("Profile response element should not be null", resp.getProfileResults());


    }

    @Test
    public void testDisMaxRange() throws Exception {
        createIndex("test");
        ensureGreen();

        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);

        refresh();

        QueryBuilder q = QueryBuilders.disMaxQuery()
                .boost(0.33703882f)
                .add(QueryBuilders.rangeQuery("field2").from(null).to(73).includeLower(true).includeUpper(true));
        logger.info(q.toString());

        SearchResponse resp = client().prepareSearch().setQuery(q).setProfile(true).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();
        assertNotNull("Profile response element should not be null", resp.getProfileResults());

    }

    @Test
    public void testRange() throws Exception {
        createIndex("test");
        ensureGreen();

        int numDocs = randomIntBetween(100, 150);
        IndexRequestBuilder[] docs = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docs[i] = client().prepareIndex("test", "type1", String.valueOf(i)).setSource(
                    "field1", English.intToEnglish(i),
                    "field2", i
            );
        }

        indexRandom(true, docs);

        refresh();

        QueryBuilder q = QueryBuilders.rangeQuery("field2").from(0).to(5);

        logger.info(q.toString());

        SearchResponse resp = client().prepareSearch().setQuery(q).setProfile(true).setSearchType(SearchType.QUERY_THEN_FETCH).execute().actionGet();
        assertNotNull("Profile response element should not be null", resp.getProfileResults());


    }

}

