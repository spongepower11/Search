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

package org.elasticsearch.percolator;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.elasticsearch.Version;
import org.elasticsearch.action.percolate.PercolateShardRequest;
import org.elasticsearch.cluster.action.index.MappingUpdatedAction;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.TermQueryParser;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.node.settings.NodeSettingsService;
import org.elasticsearch.search.SearchShardTarget;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.highlight.HighlightPhase;
import org.elasticsearch.search.highlight.Highlighters;
import org.elasticsearch.search.sort.SortParseElement;
import org.elasticsearch.test.ESTestCase;
import org.junit.Before;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Set;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class PercolateDocumentParserTests extends ESTestCase {

    private Index index;
    private MapperService mapperService;
    private PercolateDocumentParser parser;
    private IndexQueryParserService indexQueryParserService;

    @Before
    public void init() {
        index = new Index("_index");
        Settings indexSettings = Settings.builder()
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();
        AnalysisService analysisService = new AnalysisService(index, indexSettings);
        mapperService = new MapperService(index, indexSettings, analysisService, null, null);

        Set<QueryParser> parsers = Collections.singleton(new TermQueryParser());
        IndicesQueriesRegistry indicesQueriesRegistry = new IndicesQueriesRegistry(indexSettings, parsers, new NamedWriteableRegistry());

        indexQueryParserService = new IndexQueryParserService(
                index, indexSettings, indexSettings, indicesQueriesRegistry, null, analysisService, mapperService, null, null, null, null, null, null, null, null
        );

        HighlightPhase highlightPhase = new HighlightPhase(Settings.EMPTY, new Highlighters());
        AggregatorParsers aggregatorParsers = new AggregatorParsers(Collections.emptySet(), Collections.emptySet());
        AggregationPhase aggregationPhase = new AggregationPhase(new AggregationParseElement(aggregatorParsers), new AggregationBinaryParseElement(aggregatorParsers));
        MappingUpdatedAction mappingUpdatedAction = Mockito.mock(MappingUpdatedAction.class);
        parser = new PercolateDocumentParser(
                highlightPhase, new SortParseElement(), aggregationPhase, mappingUpdatedAction
        );
    }

    public void testParseDoc() throws Exception {
        XContentBuilder source = jsonBuilder().startObject()
                .startObject("doc")
                    .field("field1", "value1")
                .endObject()
                .endObject();
        PercolateShardRequest request = new PercolateShardRequest(new ShardId(index, 0), null);
        request.documentType("type");
        request.source(source.bytes());

        PercolateContext context = new PercolateContext(request, new SearchShardTarget("_node", "_index", 0), mapperService);
        ParsedDocument parsedDocument = parser.parse(request, context, mapperService, indexQueryParserService);
        assertThat(parsedDocument.rootDoc().get("field1"), equalTo("value1"));
    }

    public void testParseDocAndOtherOptions() throws Exception {
        XContentBuilder source = jsonBuilder().startObject()
                .startObject("doc")
                    .field("field1", "value1")
                .endObject()
                .startObject("query")
                    .startObject("term").field("field1", "value1").endObject()
                .endObject()
                .field("track_scores", true)
                .field("size", 123)
                .startObject("sort").startObject("_score").endObject().endObject()
                .endObject();
        PercolateShardRequest request = new PercolateShardRequest(new ShardId(index, 0), null);
        request.documentType("type");
        request.source(source.bytes());

        PercolateContext context = new PercolateContext(request, new SearchShardTarget("_node", "_index", 0), mapperService);
        ParsedDocument parsedDocument = parser.parse(request, context, mapperService, indexQueryParserService);
        assertThat(parsedDocument.rootDoc().get("field1"), equalTo("value1"));
        assertThat(context.percolateQuery(), equalTo(new TermQuery(new Term("field1", "value1"))));
        assertThat(context.trackScores(), is(true));
        assertThat(context.size(), is(123));
        assertThat(context.sort(), nullValue());
    }

    public void testParseDocSource() throws Exception {
        XContentBuilder source = jsonBuilder().startObject()
                .startObject("query")
                .startObject("term").field("field1", "value1").endObject()
                .endObject()
                .field("track_scores", true)
                .field("size", 123)
                .startObject("sort").startObject("_score").endObject().endObject()
                .endObject();
        XContentBuilder docSource = jsonBuilder().startObject()
                .field("field1", "value1")
                .endObject();
        PercolateShardRequest request = new PercolateShardRequest(new ShardId(index, 0), null);
        request.documentType("type");
        request.source(source.bytes());
        request.docSource(docSource.bytes());

        PercolateContext context = new PercolateContext(request, new SearchShardTarget("_node", "_index", 0), mapperService);
        ParsedDocument parsedDocument = parser.parse(request, context, mapperService, indexQueryParserService);
        assertThat(parsedDocument.rootDoc().get("field1"), equalTo("value1"));
        assertThat(context.percolateQuery(), equalTo(new TermQuery(new Term("field1", "value1"))));
        assertThat(context.trackScores(), is(true));
        assertThat(context.size(), is(123));
        assertThat(context.sort(), nullValue());
    }

    public void testParseDocSourceAndSource() throws Exception {
        XContentBuilder source = jsonBuilder().startObject()
                .startObject("doc")
                .field("field1", "value1")
                .endObject()
                .startObject("query")
                .startObject("term").field("field1", "value1").endObject()
                .endObject()
                .field("track_scores", true)
                .field("size", 123)
                .startObject("sort").startObject("_score").endObject().endObject()
                .endObject();
        XContentBuilder docSource = jsonBuilder().startObject()
                .field("field1", "value1")
                .endObject();
        PercolateShardRequest request = new PercolateShardRequest(new ShardId(index, 0), null);
        request.documentType("type");
        request.source(source.bytes());
        request.docSource(docSource.bytes());

        PercolateContext context = new PercolateContext(request, new SearchShardTarget("_node", "_index", 0), mapperService);
        try {
            parser.parse(request, context, mapperService, indexQueryParserService);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("Can't specify the document to percolate in the source of the request and as document id"));
        }
    }

}
