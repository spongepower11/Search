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

package org.elasticsearch.plugin.indexbysearch;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;

public class IndexBySearchRequestBuilder extends
        AbstractBulkByScrollRequestBuilder<IndexBySearchRequest, IndexBySearchResponse, IndexBySearchRequestBuilder> {
    private final IndexRequestBuilder index;

    public IndexBySearchRequestBuilder(ElasticsearchClient client,
            Action<IndexBySearchRequest, IndexBySearchResponse, IndexBySearchRequestBuilder> action) {
        this(client, action, new SearchRequestBuilder(client, SearchAction.INSTANCE),
                new IndexRequestBuilder(client, IndexAction.INSTANCE));
    }

    private IndexBySearchRequestBuilder(ElasticsearchClient client,
            Action<IndexBySearchRequest, IndexBySearchResponse, IndexBySearchRequestBuilder> action,
            SearchRequestBuilder search, IndexRequestBuilder index) {
        super(client, action, search, new IndexBySearchRequest(search.request(), index.request()));
        this.index = index;
    }

    @Override
    protected IndexBySearchRequestBuilder self() {
        return this;
    }

    public IndexRequestBuilder index() {
        return index;
    }

    @Override
    protected IndexBySearchRequest beforeExecute(IndexBySearchRequest request) {
        request = super.beforeExecute(request);
        request.fillInConditionalDefaults();
        return request;
    }
}
