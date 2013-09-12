/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.action.explain;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.single.shard.SingleShardOperationRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.internal.InternalClient;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.fetch.source.FetchSourceContext;

/**
 * A builder for {@link ExplainRequest}.
 */
public class ExplainRequestBuilder extends SingleShardOperationRequestBuilder<ExplainRequest, ExplainResponse, ExplainRequestBuilder> {

    private ExplainSourceBuilder sourceBuilder;

    ExplainRequestBuilder(Client client) {
        super((InternalClient) client, new ExplainRequest());
    }

    public ExplainRequestBuilder(Client client, String index, String type, String id) {
        super((InternalClient) client, new ExplainRequest().index(index).type(type).id(id));
    }

    /**
     * Sets the type to get a score explanation for.
     */
    public ExplainRequestBuilder setType(String type) {
        request().type(type);
        return this;
    }

    /**
     * Sets the id to get a score explanation for.
     */
    public ExplainRequestBuilder setId(String id) {
        request().id(id);
        return this;
    }

    /**
     * Sets the routing for sharding.
     */
    public ExplainRequestBuilder setRouting(String routing) {
        request().routing(routing);
        return this;
    }

    /**
     * Simple sets the routing. Since the parent is only used to get to the right shard.
     */
    public ExplainRequestBuilder setParent(String parent) {
        request().parent(parent);
        return this;
    }

    /**
     * Sets the shard preference.
     */
    public ExplainRequestBuilder setPreference(String preference) {
        request().preference(preference);
        return this;
    }

    /**
     * Sets the query to get a score explanation for.
     */
    public ExplainRequestBuilder setQuery(QueryBuilder query) {
        sourceBuilder().setQuery(query);
        return this;
    }

    /**
     * Sets the query to get a score explanation for.
     */
    public ExplainRequestBuilder setQuery(BytesReference query) {
        sourceBuilder().setQuery(query);
        return this;
    }

    /**
     * Explicitly specify the fields that will be returned for the explained document. By default, nothing is returned.
     */
    public ExplainRequestBuilder setFields(String... fields) {
        request.fields(fields);
        return this;
    }

    /**
     * Indicates whether the response should contain the stored _source
     *
     *
     * @param fetch
     * @return
     */
    public ExplainRequestBuilder setFetchSource(boolean fetch) {
        FetchSourceContext context = request.fetchSourceContext();
        if (context == null) {
            request.fetchSourceContext(new FetchSourceContext(fetch));
        }
        else {
            context.fetchSource(fetch);
        }
        return this;
    }

    /**
     * Indicate that _source should be returned, with an "include" and/or "exclude" set which can include simple wildcard
     * elements.
     *
     * @param include An optional include (optionally wildcarded) pattern to filter the returned _source
     * @param exclude An optional exclude (optionally wildcarded) pattern to filter the returned _source
     */
    public ExplainRequestBuilder setFetchSource(@Nullable String include, @Nullable String exclude) {
        return setFetchSource(
                include == null? Strings.EMPTY_ARRAY : new String[] {include},
                exclude == null? Strings.EMPTY_ARRAY : new String[] {exclude});
    }

    /**
     * Indicate that _source should be returned, with an "include" and/or "exclude" set which can include simple wildcard
     * elements.
     *
     * @param includes An optional list of include (optionally wildcarded) pattern to filter the returned _source
     * @param excludes An optional list of exclude (optionally wildcarded) pattern to filter the returned _source
     */
    public ExplainRequestBuilder setFetchSource(@Nullable String[] includes, @Nullable String[] excludes) {
        FetchSourceContext context = request.fetchSourceContext();
        if (context == null) {
            request.fetchSourceContext(new FetchSourceContext(includes, excludes));
        }
        else {
            context.fetchSource(true);
            context.includes(includes);
            context.excludes(excludes);
        }
        return this;
    }

    /**
     * Sets the full source of the explain request (for example, wrapping an actual query).
     */
    public ExplainRequestBuilder setSource(BytesReference source, boolean unsafe) {
        request().source(source, unsafe);
        return this;
    }

    /**
     * Sets whether the actual explain action should occur in a different thread if executed locally.
     */
    public ExplainRequestBuilder operationThreaded(boolean threadedOperation) {
        request().operationThreaded(threadedOperation);
        return this;
    }

    protected void doExecute(ActionListener<ExplainResponse> listener) {
        if (sourceBuilder != null) {
            request.source(sourceBuilder);
        }

        ((Client) client).explain(request, listener);
    }

    private ExplainSourceBuilder sourceBuilder() {
        if (sourceBuilder == null) {
            sourceBuilder = new ExplainSourceBuilder();
        }
        return sourceBuilder;
    }

}
