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

package org.elasticsearch.script.mustache;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.TransportSearchAction;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.script.TemplateService;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static org.elasticsearch.script.ScriptContext.Standard.SEARCH;

public class TransportSearchTemplateAction extends HandledTransportAction<SearchTemplateRequest, SearchTemplateResponse> {
    private final TemplateService templateService;
    private final TransportSearchAction searchAction;
    private final NamedXContentRegistry xContentRegistry;

    @Inject
    public TransportSearchTemplateAction(Settings settings, ThreadPool threadPool, TransportService transportService,
                                         ActionFilters actionFilters, IndexNameExpressionResolver resolver,
                                         TemplateService templateService,
                                         TransportSearchAction searchAction,
                                         NamedXContentRegistry xContentRegistry) {
        super(settings, SearchTemplateAction.NAME, threadPool, transportService, actionFilters, resolver, SearchTemplateRequest::new);
        this.templateService = templateService;
        this.searchAction = searchAction;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(SearchTemplateRequest request, ActionListener<SearchTemplateResponse> listener) {
        final SearchTemplateResponse response = new SearchTemplateResponse();
        try {
            Function<Map<String, Object>, BytesReference> template =
                    templateService.template(request.getScript(), request.getScriptType(), SEARCH);
            Map<String, Object> params =
                    request.getScriptParams() == null ? emptyMap() : request.getScriptParams();
            BytesReference source = template.apply(params);
            response.setSource(source);

            if (request.isSimulate()) {
                listener.onResponse(response);
                return;
            }

            // Executes the search
            SearchRequest searchRequest = request.getRequest();
            //we can assume the template is always json as we convert it before compiling it
            try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(xContentRegistry, source)) {
                SearchSourceBuilder builder = SearchSourceBuilder.searchSource();
                builder.parseXContent(new QueryParseContext(parser));
                builder.explain(request.isExplain());
                builder.profile(request.isProfile());
                searchRequest.source(builder);

                searchAction.execute(searchRequest, new ActionListener<SearchResponse>() {
                    @Override
                    public void onResponse(SearchResponse searchResponse) {
                        try {
                            response.setResponse(searchResponse);
                            listener.onResponse(response);
                        } catch (Exception t) {
                            listener.onFailure(t);
                        }
                    }

                    @Override
                    public void onFailure(Exception t) {
                        listener.onFailure(t);
                    }
                });
            }
        } catch (Exception t) {
            listener.onFailure(t);
        }
    }
}
