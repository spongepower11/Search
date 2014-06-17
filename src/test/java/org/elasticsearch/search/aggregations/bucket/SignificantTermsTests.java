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

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantStringTerms;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantTerms;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantTerms.Bucket;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantTermsAggregatorFactory.ExecutionMode;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantTermsBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.*;

/**
 *
 */
@ElasticsearchIntegrationTest.SuiteScopeTest
public class SignificantTermsTests extends ElasticsearchIntegrationTest {

    public String randomExecutionHint() {
        return randomBoolean() ? null : randomFrom(ExecutionMode.values()).toString();
    }

    @Override
    public Settings indexSettings() {
        return ImmutableSettings.builder()
                .put("index.number_of_shards", between(1, 5))
                .put("index.number_of_replicas", between(0, 1))
                .build();
    }

    public static final int MUSIC_CATEGORY=1;
    public static final int OTHER_CATEGORY=2;
    public static final int SNOWBOARDING_CATEGORY=3;
    
    @Override
    public void setupSuiteScopeCluster() throws Exception {
        assertAcked(prepareCreate("test").setSettings(SETTING_NUMBER_OF_SHARDS, 5, SETTING_NUMBER_OF_REPLICAS, 0).addMapping("fact",
                "_routing", "required=true,path=routing_id", "routing_id", "type=string,index=not_analyzed", "fact_category",
                "type=integer,index=not_analyzed", "description", "type=string,index=analyzed"));
        createIndex("idx_unmapped");

        ensureGreen();
        String data[] = {                    
                    "A\t1\tpaul weller was lead singer of the jam before the style council",
                    "B\t1\tpaul weller left the jam to form the style council",
                    "A\t2\tpaul smith is a designer in the fashion industry",
                    "B\t1\tthe stranglers are a group originally from guildford",
                    "A\t1\tafter disbanding the style council in 1985 paul weller became a solo artist",
                    "B\t1\tjean jaques burnel is a bass player in the stranglers and has a black belt in karate",
                    "A\t1\tmalcolm owen was the lead singer of the ruts",
                    "B\t1\tpaul weller has denied any possibility of a reunion of the jam",
                    "A\t1\tformer frontman of the jam paul weller became the father of twins",
                    "B\t2\tex-england football star paul gascoigne has re-emerged following recent disappearance",
                    "A\t2\tdavid smith has recently denied connections with the mafia",
                    "B\t1\tthe damned's new rose single was considered the first 'punk' single in the UK",
                    "A\t1\tthe sex pistols broke up after a few short years together",
                    "B\t1\tpaul gascoigne was a midfielder for england football team",
                    "A\t3\tcraig kelly became the first world champion snowboarder and has a memorial at baldface lodge",
                    "B\t3\tterje haakonsen has credited craig kelly as his snowboard mentor",
                    "A\t3\tterje haakonsen and craig kelly were some of the first snowboarders sponsored by burton snowboards",
                    "B\t3\tlike craig kelly before him terje won the mt baker banked slalom many times - once riding switch",
                    "A\t3\tterje haakonsen has been a team rider for burton snowboards for over 20 years"                         
            };
            
        for (int i = 0; i < data.length; i++) {
            String[] parts = data[i].split("\t");
            client().prepareIndex("test", "fact", "" + i)
                    .setSource("routing_id", parts[0], "fact_category", parts[1], "description", parts[2]).get();
        }
        client().admin().indices().refresh(new RefreshRequest("test")).get();
    }

    @Test
    public void structuredAnalysis() throws Exception {
        SearchResponse response = client().prepareSearch("test")
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .setQuery(new TermQueryBuilder("_all", "terje"))
                .setFrom(0).setSize(60).setExplain(true)
                .addAggregation(new SignificantTermsBuilder("mySignificantTerms").field("fact_category").executionHint(randomExecutionHint())
                           .minDocCount(2))
                .execute()
                .actionGet();
        assertSearchResponse(response);
        SignificantTerms topTerms = response.getAggregations().get("mySignificantTerms");
        Number topCategory = topTerms.getBuckets().iterator().next().getKeyAsNumber();
        assertTrue(topCategory.equals(new Long(SNOWBOARDING_CATEGORY)));
    }

    @Test
    public void includeExclude() throws Exception {
        SearchResponse response = client().prepareSearch("test")
                .setQuery(new TermQueryBuilder("_all", "weller"))
                .addAggregation(new SignificantTermsBuilder("mySignificantTerms").field("description").executionHint(randomExecutionHint())
                        .exclude("weller"))
                .get();
        assertSearchResponse(response);
        SignificantTerms topTerms = response.getAggregations().get("mySignificantTerms");
        Set<String> terms  = new HashSet<>();
        for (Bucket topTerm : topTerms) {
            terms.add(topTerm.getKey());
        }
        assertThat(terms, hasSize(6));
        assertThat(terms.contains("jam"), is(true));
        assertThat(terms.contains("council"), is(true));
        assertThat(terms.contains("style"), is(true));
        assertThat(terms.contains("paul"), is(true));
        assertThat(terms.contains("of"), is(true));
        assertThat(terms.contains("the"), is(true));

        response = client().prepareSearch("test")
                .setQuery(new TermQueryBuilder("_all", "weller"))
                .addAggregation(new SignificantTermsBuilder("mySignificantTerms").field("description").executionHint(randomExecutionHint())
                        .include("weller"))
                .get();
        assertSearchResponse(response);
        topTerms = response.getAggregations().get("mySignificantTerms");
        terms  = new HashSet<>();
        for (Bucket topTerm : topTerms) {
            terms.add(topTerm.getKey());
        }
        assertThat(terms, hasSize(1));
        assertThat(terms.contains("weller"), is(true));
    }
    
    @Test
    public void unmapped() throws Exception {
        SearchResponse response = client().prepareSearch("idx_unmapped")
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .setQuery(new TermQueryBuilder("_all", "terje"))
                .setFrom(0).setSize(60).setExplain(true)
                .addAggregation(new SignificantTermsBuilder("mySignificantTerms").field("fact_category").executionHint(randomExecutionHint())
                        .minDocCount(2))
                .execute()
                .actionGet();
        assertSearchResponse(response);
        SignificantTerms topTerms = response.getAggregations().get("mySignificantTerms");        
        assertThat(topTerms.getBuckets().size(), equalTo(0));
    }

    @Test
    public void textAnalysis() throws Exception {
        SearchResponse response = client().prepareSearch("test")
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .setQuery(new TermQueryBuilder("_all", "terje"))
                .setFrom(0).setSize(60).setExplain(true)
                .addAggregation(new SignificantTermsBuilder("mySignificantTerms").field("description").executionHint(randomExecutionHint())
                           .minDocCount(2))
                .execute()
                .actionGet();
        assertSearchResponse(response);
        SignificantTerms topTerms = response.getAggregations().get("mySignificantTerms");
        checkExpectedStringTermsFound(topTerms);
    }   
    
    @Test
    public void badFilteredAnalysis() throws Exception {
        // Deliberately using a bad choice of filter here for the background context in order
        // to test robustness. 
        // We search for the name of a snowboarder but use music-related content (fact_category:1)
        // as the background source of term statistics.
        SearchResponse response = client().prepareSearch("test")
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .setQuery(new TermQueryBuilder("_all", "terje"))
                .setFrom(0).setSize(60).setExplain(true)                
                .addAggregation(new SignificantTermsBuilder("mySignificantTerms").field("description")
                           .minDocCount(2).backgroundFilter(FilterBuilders.termFilter("fact_category", 1)))
                .execute()
                .actionGet();
        assertSearchResponse(response);
        SignificantTerms topTerms = response.getAggregations().get("mySignificantTerms");
        // We expect at least one of the significant terms to have been selected on the basis
        // that it is present in the foreground selection but entirely missing from the filtered
        // background used as context.
        boolean hasMissingBackgroundTerms = false;
        for (Bucket topTerm : topTerms) {
            if (topTerm.getSupersetDf() == 0) {
                hasMissingBackgroundTerms = true;
                break;
            }
        }
        assertTrue(hasMissingBackgroundTerms);
    }       
    
    @Test
    public void filteredAnalysis() throws Exception {
        SearchResponse response = client().prepareSearch("test")
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .setQuery(new TermQueryBuilder("_all", "weller"))
                .setFrom(0).setSize(60).setExplain(true)                
                .addAggregation(new SignificantTermsBuilder("mySignificantTerms").field("description")
                           .minDocCount(1).backgroundFilter(FilterBuilders.termsFilter("description",  "paul")))
                .execute()
                .actionGet();
        assertSearchResponse(response);
        SignificantTerms topTerms = response.getAggregations().get("mySignificantTerms");
        HashSet<String> topWords = new HashSet<String>();
        for (Bucket topTerm : topTerms) {
            topWords.add(topTerm.getKey());
        }
        //The word "paul" should be a constant of all docs in the background set and therefore not seen as significant 
        assertFalse(topWords.contains("paul"));
        //"Weller" is the only Paul who was in The Jam and therefore this should be identified as a differentiator from the background of all other Pauls. 
        assertTrue(topWords.contains("jam"));
    }       

    @Test
    public void nestedAggs() throws Exception {
        String[][] expectedKeywordsByCategory={
                { "paul", "weller", "jam", "style", "council" },                
                { "paul", "smith" },
                { "craig", "kelly", "terje", "haakonsen", "burton" }};
        SearchResponse response = client().prepareSearch("test")
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .addAggregation(new TermsBuilder("myCategories").field("fact_category").minDocCount(2)
                        .subAggregation(
                                   new SignificantTermsBuilder("mySignificantTerms").field("description")
                                   .executionHint(randomExecutionHint())
                                   .minDocCount(2)))
                .execute()
                .actionGet();
        assertSearchResponse(response);
        Terms topCategoryTerms = response.getAggregations().get("myCategories");
        for (org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket topCategory : topCategoryTerms.getBuckets()) {
            SignificantTerms topTerms = topCategory.getAggregations().get("mySignificantTerms");
            HashSet<String> foundTopWords = new HashSet<String>();
            for (Bucket topTerm : topTerms) {
                foundTopWords.add(topTerm.getKey());
            }
            String[] expectedKeywords = expectedKeywordsByCategory[Integer.parseInt(topCategory.getKey()) - 1];
            for (String expectedKeyword : expectedKeywords) {
                assertTrue(expectedKeyword + " missing from category keywords", foundTopWords.contains(expectedKeyword));
            }
        }
    }    


    @Test
    public void partiallyUnmapped() throws Exception {
        SearchResponse response = client().prepareSearch("idx_unmapped","test")
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .setQuery(new TermQueryBuilder("_all", "terje"))
                .setFrom(0).setSize(60).setExplain(true)
                .addAggregation(new SignificantTermsBuilder("mySignificantTerms").field("description")
                            .executionHint(randomExecutionHint())
                           .minDocCount(2))
                .execute()
                .actionGet();
        assertSearchResponse(response);
        SignificantTerms topTerms = response.getAggregations().get("mySignificantTerms");
        checkExpectedStringTermsFound(topTerms);
    }


    private void checkExpectedStringTermsFound(SignificantTerms topTerms) {
        HashMap<String,Bucket>topWords=new HashMap<>();
        for (Bucket topTerm : topTerms ){
            topWords.put(topTerm.getKey(),topTerm);
        }
        assertTrue( topWords.containsKey("haakonsen"));
        assertTrue( topWords.containsKey("craig"));
        assertTrue( topWords.containsKey("kelly"));
        assertTrue( topWords.containsKey("burton"));
        assertTrue( topWords.containsKey("snowboards"));
        Bucket kellyTerm=topWords.get("kelly");
        assertEquals(3, kellyTerm.getSubsetDf());
        assertEquals(4, kellyTerm.getSupersetDf());
    }

    @Test
    public void testXContentResponse() throws Exception {
        cluster().wipeIndices("goodbad");
        String type = randomBoolean() ? "string" : "long";
        prepareGoodBad(type);
        SearchResponse response = client().prepareSearch("goodbad").setTypes("doc")
                .addAggregation(new TermsBuilder("class").field("class").subAggregation(new SignificantTermsBuilder("sig_terms").field("text")))
                .execute()
                .actionGet();
        assertSearchResponse(response);
        StringTerms classes = (StringTerms) response.getAggregations().get("class");
        assertThat(classes.getBuckets().size(), equalTo(2));
        for (Terms.Bucket classBucket : classes.getBuckets()) {
            Map<String, Aggregation> aggs = classBucket.getAggregations().asMap();
            assertTrue(aggs.containsKey("sig_terms"));
            SignificantTerms agg = (SignificantTerms) aggs.get("sig_terms");
            assertThat(agg.getBuckets().size(), equalTo(1));
            String term = agg.iterator().next().getKey();
            String classTerm = classBucket.getKey();
            assertTrue(term.equals(classTerm));
        }

        XContentBuilder responseBuilder = XContentFactory.jsonBuilder();
        classes.toXContent(responseBuilder, null);
        String result = null;
        if (type.equals("long")) {
            result = "\"class\"{\"buckets\":[{\"key\":\"0\",\"doc_count\":4,\"sig_terms\":{\"doc_count\":4,\"buckets\":[{\"key\":0,\"key_as_string\":\"0\",\"doc_count\":4,\"score\":0.39999999999999997,\"bg_count\":5}]}},{\"key\":\"1\",\"doc_count\":3,\"sig_terms\":{\"doc_count\":3,\"buckets\":[{\"key\":1,\"key_as_string\":\"1\",\"doc_count\":3,\"score\":0.75,\"bg_count\":4}]}}]}";
        } else {
            result = "\"class\"{\"buckets\":[{\"key\":\"0\",\"doc_count\":4,\"sig_terms\":{\"doc_count\":4,\"buckets\":[{\"key\":\"0\",\"doc_count\":4,\"score\":0.39999999999999997,\"bg_count\":5}]}},{\"key\":\"1\",\"doc_count\":3,\"sig_terms\":{\"doc_count\":3,\"buckets\":[{\"key\":\"1\",\"doc_count\":3,\"score\":0.75,\"bg_count\":4}]}}]}";
        }
        assertThat(responseBuilder.string(), equalTo(result));

    }

    private void prepareGoodBad(String type) {
        String mappings = "{\"doc\": {\"properties\":{\"text\": {\"type\":\"" + type + "\"}}}}";
        assertAcked(prepareCreate("goodbad").setSettings(SETTING_NUMBER_OF_SHARDS, 1, SETTING_NUMBER_OF_REPLICAS, 0).addMapping("doc", mappings));
        String[] gb = {"0", "1"};
        client().prepareIndex("goodbad", "doc", "1")
                .setSource("text", "1", "class", "1").get();
        client().prepareIndex("goodbad", "doc", "2")
                .setSource("text", "1", "class", "1").get();
        client().prepareIndex("goodbad", "doc", "3")
                .setSource("text", "0", "class", "0").get();
        client().prepareIndex("goodbad", "doc", "4")
                .setSource("text", "0", "class", "0").get();
        client().prepareIndex("goodbad", "doc", "5")
                .setSource("text", gb, "class", "1").get();
        client().prepareIndex("goodbad", "doc", "6")
                .setSource("text", gb, "class", "0").get();
        client().prepareIndex("goodbad", "doc", "7")
                .setSource("text", "0", "class", "0").get();
        refresh();
    }
}
