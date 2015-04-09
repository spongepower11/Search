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

package org.elasticsearch.index.query;

import com.carrotsearch.randomizedtesting.annotations.Repeat;

import org.apache.lucene.search.Query;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.inject.util.Providers;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.cache.IndexCacheModule;
import org.elasticsearch.index.query.functionscore.FunctionScoreModule;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.index.similarity.SimilarityModule;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.indices.query.IndicesQueriesModule;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.test.ElasticsearchTestCase;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.threadpool.ThreadPoolModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.is;

public class TermQueryBuilderTest extends ElasticsearchTestCase {

    private TermQueryBuilder testQuery;

    private XContentParser parser;

    protected QueryParseContext context;

    protected Injector injector;

    @Before
    public void setup() throws IOException {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("path.conf", this.getResourcePath("config"))
                .put("name", getClass().getName())
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();

        Index index = new Index("test");
        injector = new ModulesBuilder().add(
                new EnvironmentModule(new Environment(settings)),
                new SettingsModule(settings),
                new ThreadPoolModule(settings),
                new IndicesQueriesModule(),
                new ScriptModule(settings),
                new IndexSettingsModule(index, settings),
                new IndexCacheModule(settings),
                new AnalysisModule(settings),
                new SimilarityModule(settings),
                new IndexNameModule(index),
                new IndexQueryParserModule(settings),
                new FunctionScoreModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ClusterService.class).toProvider(Providers.of((ClusterService) null));
                        bind(CircuitBreakerService.class).to(NoneCircuitBreakerService.class);
                    }
                }
        ).createInjector();

        IndexQueryParserService queryParserService = injector.getInstance(IndexQueryParserService.class);
        context = new QueryParseContext(index, queryParserService);
        testQuery = createTestQuery();
        String contentString = createXContent(testQuery).string();
        System.out.println(contentString);
        parser = XContentFactory.xContent(contentString).createParser(contentString);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        terminate(injector.getInstance(ThreadPool.class));
    }

    XContentBuilder createXContent(BaseQueryBuilder query) throws IOException {
        XContentBuilder content = XContentFactory.jsonBuilder();
        query.toXContent(content, null);
        return content;
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder content = createXContent(new TermQueryBuilder("user", "christoph").boost(1.5f).queryName("theName"));
        assertThat(content.string(), is("{\"term\":{\"user\":{\"value\":\"christoph\",\"boost\":1.5,\"_name\":\"theName\"}}}"));
    }

    @Test
    public void testFromXContent() throws IOException {
        context.reset(parser);
        assertThat(parser.nextToken(), is(XContentParser.Token.START_OBJECT));
        assertThat(parser.nextToken(), is(XContentParser.Token.FIELD_NAME));
        assertThat(parser.currentName(), is(TermQueryBuilder.NAME));
        assertThat(parser.nextToken(), is(XContentParser.Token.START_OBJECT));
        TermQueryBuilder newQuery = injector.getInstance(TermQueryBuilder.class);
        newQuery.fromXContent(context);
        // compare these
        assertNotSame(newQuery, testQuery);
        assertThat(newQuery, is(testQuery));
    }

    @Test
    public void testToQuery() throws IOException {
        context.reset(parser);
        assertThat(parser.nextToken(), is(XContentParser.Token.START_OBJECT));
        assertThat(parser.nextToken(), is(XContentParser.Token.FIELD_NAME));
        assertThat(parser.currentName(), is(TermQueryBuilder.NAME));
        assertThat(parser.nextToken(), is(XContentParser.Token.START_OBJECT));
        TermQueryBuilder newQuery = injector.getInstance(TermQueryBuilder.class);
        newQuery.fromXContent(context);
        Query query = newQuery.toQuery(context);
        // compare these TODO how to assert more on lucene query
        assertThat(query.getBoost(), is(testQuery.getBoost()));
    }

    @Test
    public void testSerialization() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        testQuery.writeTo(output);

        BytesStreamInput bytesStreamInput = new BytesStreamInput(output.bytes());
        TermQueryBuilder deserializedQuery = new TermQueryBuilder();
        deserializedQuery.readFrom(bytesStreamInput);

        assertNotSame(testQuery, deserializedQuery);
        assertThat(testQuery, is(deserializedQuery));
    }

    public static TermQueryBuilder createTestQuery() {
        Object value;
        switch (randomIntBetween(0, 3)) {
            case 0: value = randomBoolean(); break;
            case 1: value = randomAsciiOfLength(8); break;
            case 2: value = randomInt(10000); break;
            case 3: value = randomDouble(); break;
            default: value = randomAsciiOfLength(8);
        }
        TermQueryBuilder query = new TermQueryBuilder(randomAsciiOfLength(8), value);
        if (randomBoolean()) {
            query.boost(2.0f / randomIntBetween(1, 20));
        }
        if (randomBoolean()) {
            query.queryName(randomAsciiOfLength(8));
        }
        return query;
    }
}
