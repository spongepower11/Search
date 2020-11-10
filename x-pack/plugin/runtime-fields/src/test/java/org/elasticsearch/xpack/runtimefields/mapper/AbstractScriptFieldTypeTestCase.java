/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.runtimefields.mapper;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.CheckedConsumer;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AnalyzerScope;
import org.elasticsearch.index.analysis.IndexAnalyzers;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.indices.mapper.MapperRegistry;
import org.elasticsearch.plugins.MapperPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.runtimefields.RuntimeFields;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractScriptFieldTypeTestCase extends ESTestCase {

    private static final ToXContent.Params INCLUDE_DEFAULTS = new ToXContent.MapParams(Map.of("include_defaults", "true"));

    protected abstract MappedFieldType simpleMappedFieldType() throws IOException;

    protected abstract MappedFieldType loopFieldType() throws IOException;

    protected abstract String typeName();

    public final void testMinimalSerializesToItself() throws IOException {
        XContentBuilder orig = JsonXContent.contentBuilder().startObject();
        createMapperService(runtimeFieldMapping(this::minimalMapping)).documentMapper().mapping().toXContent(orig, ToXContent.EMPTY_PARAMS);
        orig.endObject();
        XContentBuilder parsedFromOrig = JsonXContent.contentBuilder().startObject();
        createMapperService(orig).documentMapper().mapping().toXContent(parsedFromOrig, ToXContent.EMPTY_PARAMS);
        parsedFromOrig.endObject();
        assertEquals(Strings.toString(orig), Strings.toString(parsedFromOrig));
    }

    public final void testMeta() throws IOException {
        XContentBuilder mapping = runtimeFieldMapping(b -> {
            minimalMapping(b);
            b.field("meta", Collections.singletonMap("foo", "bar"));
        });
        MapperService mapperService = createMapperService(mapping);
        assertEquals(
            XContentHelper.convertToMap(BytesReference.bytes(mapping), false, mapping.contentType()).v2(),
            XContentHelper.convertToMap(mapperService.documentMapper().mappingSource().uncompressed(), false, mapping.contentType()).v2()
        );

        mapping = runtimeFieldMapping(this::minimalMapping);
        merge(mapperService, mapping);
        assertEquals(
            XContentHelper.convertToMap(BytesReference.bytes(mapping), false, mapping.contentType()).v2(),
            XContentHelper.convertToMap(mapperService.documentMapper().mappingSource().uncompressed(), false, mapping.contentType()).v2()
        );

        mapping = runtimeFieldMapping(b -> {
            minimalMapping(b);
            b.field("meta", Collections.singletonMap("baz", "quux"));
        });
        merge(mapperService, mapping);
        assertEquals(
            XContentHelper.convertToMap(BytesReference.bytes(mapping), false, mapping.contentType()).v2(),
            XContentHelper.convertToMap(mapperService.documentMapper().mappingSource().uncompressed(), false, mapping.contentType()).v2()
        );
    }

    public final void testMinimalMappingToMaximal() throws IOException {
        XContentBuilder orig = JsonXContent.contentBuilder().startObject();
        createMapperService(runtimeFieldMapping(this::minimalMapping)).documentMapper().mapping().toXContent(orig, INCLUDE_DEFAULTS);
        orig.endObject();
        XContentBuilder parsedFromOrig = JsonXContent.contentBuilder().startObject();
        createMapperService(orig).documentMapper().mapping().toXContent(parsedFromOrig, INCLUDE_DEFAULTS);
        parsedFromOrig.endObject();
        assertEquals(Strings.toString(orig), Strings.toString(parsedFromOrig));
    }

    public void testScriptIsRequired() throws Exception {
        XContentBuilder mapping = runtimeFieldMapping(b -> { b.field("type", typeName()); });
        MapperParsingException exception = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: script must be specified for runtime field [field]", exception.getMessage());
    }

    public void testCopyToIsNotSupported() throws IOException {
        XContentBuilder mapping = runtimeFieldMapping(b -> {
            minimalMapping(b);
            b.field("copy_to", "target");
        });
        MapperParsingException exception = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: runtime field [field] does not support [copy_to]", exception.getMessage());
    }

    public void testMultiFieldsIsNotSupported() throws IOException {
        XContentBuilder mapping = runtimeFieldMapping(b -> {
            minimalMapping(b);
            b.startObject("fields").startObject("test").field("type", "keyword").endObject().endObject();
        });
        MapperParsingException exception = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: runtime field [field] does not support [fields]", exception.getMessage());
    }

    public void testStoredScriptsAreNotSupported() throws Exception {
        XContentBuilder mapping = runtimeFieldMapping(b -> {
            b.field("type", typeName());
            b.startObject("script").field("id", "test").endObject();
        });
        MapperParsingException exception = expectThrows(MapperParsingException.class, () -> createMapperService(mapping));
        assertEquals("Failed to parse mapping: stored scripts are not supported for runtime field [field]", exception.getMessage());
    }

    public void testFieldCaps() throws Exception {
        MapperService scriptIndexMapping = createMapperService(runtimeFieldMapping(this::minimalMapping));
        MapperService concreteIndexMapping;
        {
            XContentBuilder mapping = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("_doc")
                .startObject("properties")
                .startObject("field")
                .field("type", typeName())
                .endObject()
                .endObject()
                .endObject()
                .endObject();
            concreteIndexMapping = createMapperService(mapping);
        }
        MappedFieldType scriptFieldType = scriptIndexMapping.fieldType("field");
        MappedFieldType concreteIndexType = concreteIndexMapping.fieldType("field");
        assertEquals(concreteIndexType.familyTypeName(), scriptFieldType.familyTypeName());
        assertEquals(concreteIndexType.isSearchable(), scriptFieldType.isSearchable());
        assertEquals(concreteIndexType.isAggregatable(), scriptFieldType.isAggregatable());
    }

    @SuppressWarnings("unused")
    public abstract void testDocValues() throws IOException;

    @SuppressWarnings("unused")
    public abstract void testSort() throws IOException;

    @SuppressWarnings("unused")
    public abstract void testUsedInScript() throws IOException;

    @SuppressWarnings("unused")
    public abstract void testExistsQuery() throws IOException;

    @SuppressWarnings("unused")
    public abstract void testRangeQuery() throws IOException;

    protected abstract Query randomRangeQuery(MappedFieldType ft, QueryShardContext ctx);

    @SuppressWarnings("unused")
    public abstract void testTermQuery() throws IOException;

    protected abstract Query randomTermQuery(MappedFieldType ft, QueryShardContext ctx);

    @SuppressWarnings("unused")
    public abstract void testTermsQuery() throws IOException;

    protected abstract Query randomTermsQuery(MappedFieldType ft, QueryShardContext ctx);

    protected static QueryShardContext mockContext() {
        return mockContext(true);
    }

    protected static QueryShardContext mockContext(boolean allowExpensiveQueries) {
        return mockContext(allowExpensiveQueries, null);
    }

    protected boolean supportsTermQueries() {
        return true;
    }

    protected boolean supportsRangeQueries() {
        return true;
    }

    protected static QueryShardContext mockContext(boolean allowExpensiveQueries, MappedFieldType mappedFieldType) {
        QueryShardContext context = mock(QueryShardContext.class);
        if (mappedFieldType != null) {
            when(context.getFieldType(anyString())).thenReturn(mappedFieldType);
        }
        when(context.allowExpensiveQueries()).thenReturn(allowExpensiveQueries);
        SearchLookup lookup = new SearchLookup(
            context::getFieldType,
            (mft, lookupSupplier) -> mft.fielddataBuilder("test", lookupSupplier).build(null, null)
        );
        when(context.lookup()).thenReturn(lookup);
        return context;
    }

    public void testExistsQueryIsExpensive() {
        checkExpensiveQuery(MappedFieldType::existsQuery);
    }

    public void testExistsQueryInLoop() {
        checkLoop(MappedFieldType::existsQuery);
    }

    public void testRangeQueryWithShapeRelationIsError() {
        Exception e = expectThrows(
            IllegalArgumentException.class,
            () -> simpleMappedFieldType().rangeQuery(1, 2, true, true, ShapeRelation.DISJOINT, null, null, null)
        );
        assertThat(e.getMessage(), equalTo("Runtime field [test] of type [" + typeName() + "] does not support DISJOINT ranges"));
    }

    public void testRangeQueryIsExpensive() {
        assumeTrue("Impl does not support range queries", supportsRangeQueries());
        checkExpensiveQuery(this::randomRangeQuery);
    }

    public void testRangeQueryInLoop() {
        assumeTrue("Impl does not support range queries", supportsRangeQueries());
        checkLoop(this::randomRangeQuery);
    }

    public void testTermQueryIsExpensive() {
        assumeTrue("Impl does not support term queries", supportsTermQueries());
        checkExpensiveQuery(this::randomTermQuery);
    }

    public void testTermQueryInLoop() {
        assumeTrue("Impl does not support term queries", supportsTermQueries());
        checkLoop(this::randomTermQuery);
    }

    public void testTermsQueryIsExpensive() {
        assumeTrue("Impl does not support term queries", supportsTermQueries());
        checkExpensiveQuery(this::randomTermsQuery);
    }

    public void testTermsQueryInLoop() {
        assumeTrue("Impl does not support term queries", supportsTermQueries());
        checkLoop(this::randomTermsQuery);
    }

    public void testPhraseQueryIsError() {
        assumeTrue("Impl does not support term queries", supportsTermQueries());
        assertQueryOnlyOnText("phrase", () -> simpleMappedFieldType().phraseQuery(null, 1, false));
    }

    public void testPhrasePrefixQueryIsError() {
        assumeTrue("Impl does not support term queries", supportsTermQueries());
        assertQueryOnlyOnText("phrase prefix", () -> simpleMappedFieldType().phrasePrefixQuery(null, 1, 1));
    }

    public void testMultiPhraseQueryIsError() {
        assumeTrue("Impl does not support term queries", supportsTermQueries());
        assertQueryOnlyOnText("phrase", () -> simpleMappedFieldType().multiPhraseQuery(null, 1, false));
    }

    public void testSpanPrefixQueryIsError() {
        assumeTrue("Impl does not support term queries", supportsTermQueries());
        assertQueryOnlyOnText("span prefix", () -> simpleMappedFieldType().spanPrefixQuery(null, null, null));
    }

    private void assertQueryOnlyOnText(String queryName, ThrowingRunnable buildQuery) {
        Exception e = expectThrows(IllegalArgumentException.class, buildQuery);
        assertThat(
            e.getMessage(),
            equalTo(
                "Can only use "
                    + queryName
                    + " queries on text fields - not on [test] which is a runtime field of type ["
                    + typeName()
                    + "]"
            )
        );
    }

    protected final String readSource(IndexReader reader, int docId) throws IOException {
        return reader.document(docId).getBinaryValue("_source").utf8ToString();
    }

    protected final void checkExpensiveQuery(BiConsumer<MappedFieldType, QueryShardContext> queryBuilder) {
        Exception e = expectThrows(ElasticsearchException.class, () -> queryBuilder.accept(simpleMappedFieldType(), mockContext(false)));
        assertThat(
            e.getMessage(),
            equalTo("queries cannot be executed against runtime fields while [search.allow_expensive_queries] is set to [false].")
        );
    }

    protected final void checkLoop(BiConsumer<MappedFieldType, QueryShardContext> queryBuilder) {
        Exception e = expectThrows(IllegalArgumentException.class, () -> queryBuilder.accept(loopFieldType(), mockContext()));
        assertThat(e.getMessage(), equalTo("Cyclic dependency detected while resolving runtime fields: test -> test"));
    }

    private Collection<? extends Plugin> getPlugins() {
        return List.of(new RuntimeFields(), new TestScriptPlugin());
    }

    protected final void minimalMapping(XContentBuilder b) throws IOException {
        b.field("type", typeName());
        b.startObject("script").field("source", "dummy_source").field("lang", "test").endObject();
    }

    protected final XContentBuilder runtimeFieldMapping(CheckedConsumer<XContentBuilder, IOException> buildField) throws IOException {
        return runtimeMapping(b -> {
            b.startObject("field");
            buildField.accept(b);
            b.endObject();
        });
    }

    private XContentBuilder runtimeMapping(CheckedConsumer<XContentBuilder, IOException> buildFields) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject().startObject("_doc").startObject("runtime");
        buildFields.accept(builder);
        return builder.endObject().endObject().endObject();
    }

    protected final MapperService createMapperService(XContentBuilder mapping) throws IOException {
        Settings settings = Settings.builder()
            .put("index.number_of_replicas", 0)
            .put("index.number_of_shards", 1)
            .put("index.version.created", Version.CURRENT)
            .build();
        IndexMetadata meta = IndexMetadata.builder("index").settings(settings).build();
        IndexSettings indexSettings = new IndexSettings(meta, settings);
        MapperRegistry mapperRegistry = new IndicesModule(
            getPlugins().stream().filter(p -> p instanceof MapperPlugin).map(p -> (MapperPlugin) p).collect(toList())
        ).getMapperRegistry();
        ScriptModule scriptModule = new ScriptModule(
            Settings.EMPTY,
            getPlugins().stream().filter(p -> p instanceof ScriptPlugin).map(p -> (ScriptPlugin) p).collect(toList())
        );
        ScriptService scriptService = new ScriptService(Settings.EMPTY, scriptModule.engines, scriptModule.contexts);
        SimilarityService similarityService = new SimilarityService(indexSettings, scriptService, Map.of());
        MapperService mapperService = new MapperService(
            indexSettings,
            createIndexAnalyzers(),
            xContentRegistry(),
            similarityService,
            mapperRegistry,
            () -> { throw new UnsupportedOperationException(); },
            () -> true,
            scriptService
        );
        merge(mapperService, mapping);
        return mapperService;
    }

    private void merge(MapperService mapperService, XContentBuilder mapping) throws IOException {
        mapperService.merge(null, new CompressedXContent(BytesReference.bytes(mapping)), MapperService.MergeReason.MAPPING_UPDATE);
    }

    private IndexAnalyzers createIndexAnalyzers() {
        return new IndexAnalyzers(
            Map.of("default", new NamedAnalyzer("default", AnalyzerScope.INDEX, new StandardAnalyzer())),
            Map.of(),
            Map.of()
        );
    }

    private static class TestScriptPlugin extends Plugin implements ScriptPlugin {
        @Override
        public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
            return new TestScriptEngine() {
                @Override
                protected Object buildScriptFactory(ScriptContext<?> context) {
                    if (context == BooleanFieldScript.CONTEXT) {
                        return BooleanFieldScriptTests.DUMMY;
                    }
                    if (context == DateFieldScript.CONTEXT) {
                        return DateFieldScriptTests.DUMMY;
                    }
                    if (context == DoubleFieldScript.CONTEXT) {
                        return DoubleFieldScriptTests.DUMMY;
                    }
                    if (context == IpFieldScript.CONTEXT) {
                        return IpFieldScriptTests.DUMMY;
                    }
                    if (context == LongFieldScript.CONTEXT) {
                        return LongFieldScriptTests.DUMMY;
                    }
                    if (context == StringFieldScript.CONTEXT) {
                        return StringFieldScriptTests.DUMMY;
                    }
                    if (context == GeoPointFieldScript.CONTEXT) {
                        return GeoPointFieldScriptTests.DUMMY;
                    }
                    throw new IllegalArgumentException("Unsupported context: " + context);
                }

                public Set<ScriptContext<?>> getSupportedContexts() {
                    return Set.copyOf(new RuntimeFields().getContexts());
                }
            };
        }
    }
}
