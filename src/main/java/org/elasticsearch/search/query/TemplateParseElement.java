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

package org.elasticsearch.search.query;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.TemplateParser;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.SearchParseElement;
import org.elasticsearch.search.SearchService;
import org.elasticsearch.search.internal.SearchContext;

public class TemplateParseElement implements SearchParseElement {

    private static final String CONTENT = "content";
    private static final String PARAMS = "params";

    private final Provider<SearchService> searchServiceProvider;
    private final ScriptService scriptService;
    
    @Inject
    public TemplateParseElement(ScriptService scriptService, Provider<SearchService> searchServiceProvider) {
        this.searchServiceProvider = searchServiceProvider;
        this.scriptService = scriptService;
    }
    
    @Override
    public void parse(XContentParser parser, SearchContext context) throws Exception {
        BytesReference querySource = TemplateParser.parse(parser, scriptService, CONTENT, PARAMS);
        this.searchServiceProvider.get().parseSource(context, querySource);
    }
}
