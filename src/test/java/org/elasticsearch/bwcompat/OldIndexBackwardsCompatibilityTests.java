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

package org.elasticsearch.bwcompat;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.hamcrest.ElasticsearchAssertions;
import org.hamcrest.Matchers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OldIndexBackwardsCompatibilityTests extends StaticIndexBackwardCompatibilityTest {
    
    List<String> indexes = Arrays.asList(
        "index-0.20.5.zip",
        "index-1.1.2.zip",
        "index-1.2.4.zip",
        "index-1.3.1.zip",
        "index-1.3.2.zip",
        "index-1.3.4.zip",
        "index-1.4.0.Beta1.zip"
    );

    public void testOldIndexes() throws Exception {
        Collections.shuffle(indexes, getRandom());
        // TODO: increase the minimum here, as we add more old indexes, since this doesn't take very long anyways
        final int numToTest = scaledRandomIntBetween(5, indexes.size());
        for (int i = 0; i < numToTest; ++i) {
            logger.info("Testing old index " + indexes.get(i));
            assertOldIndexWorks(indexes.get(i));
        }
    }

    void assertOldIndexWorks(String index) throws Exception {
        loadIndex(index);
        assertBasicSearchWorks();
        assertRealtimeGetWorks();
        assertNewReplicasWork();
        unloadIndex();
    }

    void assertBasicSearchWorks() {
        SearchRequestBuilder searchReq = client().prepareSearch("test").setQuery(QueryBuilders.matchAllQuery());
        SearchResponse searchRsp = searchReq.get();
        ElasticsearchAssertions.assertNoFailures(searchRsp);
        long numDocs = searchRsp.getHits().getTotalHits();
        logger.debug("Found " + numDocs + " in old index");
        
        searchReq.addSort("long_sort", SortOrder.ASC);
        ElasticsearchAssertions.assertNoFailures(searchReq.get());
    }

    void assertRealtimeGetWorks() {
        client().admin().indices().prepareUpdateSettings("test").setSettings(ImmutableSettings.builder()
            .put("refresh_interval", -1)
            .build());
        SearchRequestBuilder searchReq = client().prepareSearch("test").setQuery(QueryBuilders.matchAllQuery());
        SearchHit hit = searchReq.get().getHits().getAt(0);
        String docId = hit.getId();
        // foo is new, it is not a field in the generated index
        client().prepareUpdate("test", "doc", docId).setDoc("foo", "bar").get();
        GetResponse getRsp = client().prepareGet("test", "doc", docId).get();
        Map<String, Object> source = getRsp.getSourceAsMap();
        assertThat(source, Matchers.hasKey("foo"));

        client().admin().indices().prepareUpdateSettings("test").setSettings(ImmutableSettings.builder()
            .put("refresh_interval", "1s")
            .build());
    }

    void assertNewReplicasWork() {
        final int numReplicas = randomIntBetween(1, 2);
        for (int i = 0; i < numReplicas; ++i) {
            logger.debug("Creating another node for replica " + i);
            internalCluster().startNode(ImmutableSettings.builder()
                .put("data.node", true)
                .put("master.node", false).build());
        }
        client().admin().indices().prepareUpdateSettings("test").setSettings(ImmutableSettings.builder()
            .put("num_replicas", numReplicas)
            .build());
        ensureGreen("test"); // TODO: what is the proper way to wait for new replicas to recover?

        client().admin().indices().prepareUpdateSettings("test").setSettings(ImmutableSettings.builder()
            .put("num_replicas", 0)
            .build());
    }

}
