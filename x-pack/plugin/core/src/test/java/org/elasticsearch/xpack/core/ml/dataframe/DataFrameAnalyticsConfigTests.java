/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ml.dataframe;

import com.carrotsearch.randomizedtesting.generators.CodepointSetGenerator;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParseException;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.test.AbstractSerializingTestCase;
import org.elasticsearch.xpack.core.ml.utils.ToXContentParams;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class DataFrameAnalyticsConfigTests extends AbstractSerializingTestCase<DataFrameAnalyticsConfig> {

    @Override
    protected DataFrameAnalyticsConfig doParseInstance(XContentParser parser) throws IOException {
        return DataFrameAnalyticsConfig.STRICT_PARSER.apply(parser, null).build();
    }

    @Override
    protected NamedWriteableRegistry getNamedWriteableRegistry() {
        SearchModule searchModule = new SearchModule(Settings.EMPTY, false, Collections.emptyList());
        return new NamedWriteableRegistry(searchModule.getNamedWriteables());
    }

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        SearchModule searchModule = new SearchModule(Settings.EMPTY, false, Collections.emptyList());
        return new NamedXContentRegistry(searchModule.getNamedXContents());
    }

    @Override
    protected DataFrameAnalyticsConfig createTestInstance() {
        return createRandom(randomValidId());
    }

    @Override
    protected Writeable.Reader<DataFrameAnalyticsConfig> instanceReader() {
        return DataFrameAnalyticsConfig::new;
    }

    public static DataFrameAnalyticsConfig createRandom(String id) {
        return createRandomBuilder(id).build();
    }

    public static DataFrameAnalyticsConfig.Builder createRandomBuilder(String id) {
        String source = randomAlphaOfLength(10);
        String dest = randomAlphaOfLength(10);
        List<DataFrameAnalysisConfig> analyses = Collections.singletonList(DataFrameAnalysisConfigTests.randomConfig());
        DataFrameAnalyticsConfig.Builder builder = new DataFrameAnalyticsConfig.Builder()
            .setId(id)
            .setAnalyses(analyses)
            .setSource(source)
            .setDest(dest);
        if (randomBoolean()) {
            builder.setQuery(
                Collections.singletonMap(TermQueryBuilder.NAME,
                    Collections.singletonMap(randomAlphaOfLength(10), randomAlphaOfLength(10))), true);
        }
        return builder;
    }

    public static String randomValidId() {
        CodepointSetGenerator generator = new CodepointSetGenerator("abcdefghijklmnopqrstuvwxyz".toCharArray());
        return generator.ofCodePointsLength(random(), 10, 10);
    }

    private static final String ANACHRONISTIC_QUERY_DATA_FRAME_ANALYTICS = "{\n" +
        "    \"id\": \"old-data-frame\",\n" +
        "    \"source\": \"my-index\",\n" +
        "    \"dest\": \"dest-index\",\n" +
        "    \"analyses\": {\"outlier_detection\": {\"number_neighbours\": 10}},\n" +
        //query:match:type stopped being supported in 6.x
        "    \"query\": {\"match\" : {\"query\":\"fieldName\", \"type\": \"phrase\"}}\n" +
        "}";

    private static final String MODERN_QUERY_DATA_FRAME_ANALYTICS = "{\n" +
        "    \"id\": \"data-frame\",\n" +
        "    \"source\": \"my-index\",\n" +
        "    \"dest\": \"dest-index\",\n" +
        "    \"analyses\": {\"outlier_detection\": {\"number_neighbours\": 10}},\n" +
        // match_all if parsed, adds default values in the options
        "    \"query\": {\"match_all\" : {}}\n" +
        "}";

    public void testQueryConfigStoresUserInputOnly() throws IOException {
        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON)
            .createParser(NamedXContentRegistry.EMPTY,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                MODERN_QUERY_DATA_FRAME_ANALYTICS)) {

            DataFrameAnalyticsConfig config = DataFrameAnalyticsConfig.LENIENT_PARSER.apply(parser, null).build();
            assertThat(config.getQuery(), equalTo(Collections.singletonMap(MatchAllQueryBuilder.NAME, Collections.emptyMap())));
        }

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON)
            .createParser(NamedXContentRegistry.EMPTY,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                MODERN_QUERY_DATA_FRAME_ANALYTICS)) {

            DataFrameAnalyticsConfig config = DataFrameAnalyticsConfig.STRICT_PARSER.apply(parser, null).build();
            assertThat(config.getQuery(), equalTo(Collections.singletonMap(MatchAllQueryBuilder.NAME, Collections.emptyMap())));
        }
    }

    public void testPastQueryConfigParse() throws IOException {
        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON)
            .createParser(NamedXContentRegistry.EMPTY,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                ANACHRONISTIC_QUERY_DATA_FRAME_ANALYTICS)) {

            DataFrameAnalyticsConfig config = DataFrameAnalyticsConfig.LENIENT_PARSER.apply(parser, null).build();
            ElasticsearchException e = expectThrows(ElasticsearchException.class, () -> config.getParsedQuery());
            assertEquals("[match] query doesn't support multiple fields, found [query] and [type]", e.getMessage());
        }

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON)
            .createParser(NamedXContentRegistry.EMPTY,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                ANACHRONISTIC_QUERY_DATA_FRAME_ANALYTICS)) {

            XContentParseException e = expectThrows(XContentParseException.class,
                () -> DataFrameAnalyticsConfig.STRICT_PARSER.apply(parser, null).build());
            assertEquals("[6:64] [data_frame_analytics_config] failed to parse field [query]", e.getMessage());
        }
    }

    public void testToXContentForInternalStorage() throws IOException {
        DataFrameAnalyticsConfig.Builder builder = createRandomBuilder("foo");

        // headers are only persisted to cluster state
        Map<String, String> headers = new HashMap<>();
        headers.put("header-name", "header-value");
        builder.setHeaders(headers);
        DataFrameAnalyticsConfig config = builder.build();

        ToXContent.MapParams params = new ToXContent.MapParams(Collections.singletonMap(ToXContentParams.FOR_INTERNAL_STORAGE, "true"));

        BytesReference forClusterstateXContent = XContentHelper.toXContent(config, XContentType.JSON, params, false);
        XContentParser parser = XContentFactory.xContent(XContentType.JSON)
            .createParser(xContentRegistry(), LoggingDeprecationHandler.INSTANCE, forClusterstateXContent.streamInput());

        DataFrameAnalyticsConfig parsedConfig = DataFrameAnalyticsConfig.LENIENT_PARSER.apply(parser, null).build();
        assertThat(parsedConfig.getHeaders(), hasEntry("header-name", "header-value"));

        // headers are not written without the FOR_INTERNAL_STORAGE param
        BytesReference nonClusterstateXContent = XContentHelper.toXContent(config, XContentType.JSON, ToXContent.EMPTY_PARAMS, false);
        parser = XContentFactory.xContent(XContentType.JSON)
            .createParser(xContentRegistry(), LoggingDeprecationHandler.INSTANCE, nonClusterstateXContent.streamInput());

        parsedConfig = DataFrameAnalyticsConfig.LENIENT_PARSER.apply(parser, null).build();
        assertThat(parsedConfig.getHeaders().entrySet(), hasSize(0));
    }

    public void testGetQueryDeprecations() {
        DataFrameAnalyticsConfig dataFrame = createTestInstance();
        String deprecationWarning = "Warning";
        List<String> deprecations = dataFrame.getQueryDeprecations((map, id, deprecationlist) -> {
            deprecationlist.add(deprecationWarning);
            return new BoolQueryBuilder();
        });
        assertThat(deprecations, hasItem(deprecationWarning));

        DataFrameAnalyticsConfig spiedConfig = spy(dataFrame);
        spiedConfig.getQueryDeprecations();
        verify(spiedConfig).getQueryDeprecations(DataFrameAnalyticsConfig.lazyQueryParser);
    }
}
