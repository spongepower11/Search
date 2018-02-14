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

package org.elasticsearch.index.mapper;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.mapper.KeywordFieldMapper.KeywordFieldType;
import org.elasticsearch.index.mapper.MapperService.MergeReason;
import org.elasticsearch.index.mapper.NumberFieldMapper.NumberFieldType;
import org.elasticsearch.indices.InvalidTypeNameException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.test.InternalSettingsPlugin;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;

public class MapperServiceTests extends ESSingleNodeTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(InternalSettingsPlugin.class);
    }

    public void testUpgradeIndexMetaData() throws IOException {
        Settings settings = Settings.builder()
                .put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, 1)
                .put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 0)
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_6_1_2)
                .build();
        IndexMetaData meta1 = new IndexMetaData.Builder("index")
                .settings(settings)
                .putMapping("_doc", "{\"properties\": {\"foo\": {\"type\": \"keyword\"}}}")
                .build();
        IndexMetaData upgradedMeta1 = MapperService.MAPPINGS_METADATA_6x_UPGRADER.apply(meta1);
        assertSame(meta1, upgradedMeta1);

        IndexMetaData meta2 = new IndexMetaData.Builder("index")
                .settings(settings)
                .putMapping("_default_", "{\"properties\": {\"foo\": {\"type\": \"keyword\"}}}")
                .build();
        IndexMetaData upgradedMeta2 = MapperService.MAPPINGS_METADATA_6x_UPGRADER.apply(meta2);
        assertEquals(meta1, upgradedMeta2);

        IndexMetaData meta3 = new IndexMetaData.Builder("index")
                .settings(settings)
                .putMapping("_doc", "{\"properties\": {\"foo\": {\"type\": \"keyword\"}}}")
                .putMapping("_default_", "{\"properties\": {\"bar\": {\"type\": \"keyword\"}}}")
                .build();
        IndexMetaData upgradedMeta3 = MapperService.MAPPINGS_METADATA_6x_UPGRADER.apply(meta3);
        assertEquals(meta1, upgradedMeta3);
    }

    public void testTypeNameStartsWithIllegalDot() {
        String index = "test-index";
        String type = ".test-type";
        String field = "field";
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> {
            client().admin().indices().prepareCreate(index)
                    .addMapping(type, field, "type=text")
                    .execute().actionGet();
        });
        assertTrue(e.getMessage(), e.getMessage().contains("mapping type name [.test-type] must not start with a '.'"));
    }

    public void testTypeNameTooLong() {
        String index = "text-index";
        String field = "field";
        String type = new String(new char[256]).replace("\0", "a");

        MapperException e = expectThrows(MapperException.class, () -> {
            client().admin().indices().prepareCreate(index)
                    .addMapping(type, field, "type=text")
                    .execute().actionGet();
        });
        assertTrue(e.getMessage(), e.getMessage().contains("mapping type name [" + type + "] is too long; limit is length 255 but was [256]"));
    }

    public void testTypes() throws Exception {
        IndexService indexService1 = createIndex("index1", Settings.builder().put("index.version.created", Version.V_5_6_0) // multi types
            .build());
        MapperService mapperService = indexService1.mapperService();
        assertEquals(Collections.emptySet(), mapperService.types());

        mapperService.merge("type1", new CompressedXContent("{\"type1\":{}}"), MapperService.MergeReason.MAPPING_UPDATE);
        assertEquals(Collections.singleton("type1"), mapperService.types());

        mapperService.merge("type2", new CompressedXContent("{\"type2\":{}}"), MapperService.MergeReason.MAPPING_UPDATE);
        assertEquals(new HashSet<>(Arrays.asList("type1", "type2")), mapperService.types());
    }

    public void testTypeValidation() {
        InvalidTypeNameException e = expectThrows(InvalidTypeNameException.class, () -> MapperService.validateTypeName("_type"));
        assertEquals("mapping type name [_type] can't start with '_' unless it is called [_doc]", e.getMessage());

        e = expectThrows(InvalidTypeNameException.class, () -> MapperService.validateTypeName("_document"));
        assertEquals("mapping type name [_document] can't start with '_' unless it is called [_doc]", e.getMessage());

        MapperService.validateTypeName("_doc"); // no exception
    }

    public void testTotalFieldsExceedsLimit() throws Throwable {
        Function<String, String> mapping = type -> {
            try {
                return XContentFactory.jsonBuilder().startObject().startObject(type).startObject("properties")
                    .startObject("field1").field("type", "keyword")
                    .endObject().endObject().endObject().endObject().string();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        createIndex("test1").mapperService().merge("type", new CompressedXContent(mapping.apply("type")), MergeReason.MAPPING_UPDATE);
        //set total number of fields to 1 to trigger an exception
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> {
            createIndex("test2", Settings.builder().put(MapperService.INDEX_MAPPING_TOTAL_FIELDS_LIMIT_SETTING.getKey(), 1).build())
                .mapperService().merge("type", new CompressedXContent(mapping.apply("type")), MergeReason.MAPPING_UPDATE);
        });
        assertTrue(e.getMessage(), e.getMessage().contains("Limit of total fields [1] in index [test2] has been exceeded"));
    }

    public void testMappingDepthExceedsLimit() throws Throwable {
        CompressedXContent simpleMapping = new CompressedXContent(XContentFactory.jsonBuilder().startObject()
                .startObject("properties")
                    .startObject("field")
                        .field("type", "text")
                    .endObject()
                .endObject().endObject().bytes());
        IndexService indexService1 = createIndex("test1", Settings.builder().put(MapperService.INDEX_MAPPING_DEPTH_LIMIT_SETTING.getKey(), 1).build());
        // no exception
        indexService1.mapperService().merge("type", simpleMapping, MergeReason.MAPPING_UPDATE);

        CompressedXContent objectMapping = new CompressedXContent(XContentFactory.jsonBuilder().startObject()
                .startObject("properties")
                    .startObject("object1")
                        .field("type", "object")
                    .endObject()
                .endObject().endObject().bytes());

        IndexService indexService2 = createIndex("test2");
        // no exception
        indexService2.mapperService().merge("type", objectMapping, MergeReason.MAPPING_UPDATE);

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> indexService1.mapperService().merge("type2", objectMapping, MergeReason.MAPPING_UPDATE));
        assertThat(e.getMessage(), containsString("Limit of mapping depth [1] in index [test1] has been exceeded"));
    }

    public void testUnmappedFieldType() {
        MapperService mapperService = createIndex("index").mapperService();
        assertThat(mapperService.unmappedFieldType("keyword"), instanceOf(KeywordFieldType.class));
        assertThat(mapperService.unmappedFieldType("long"), instanceOf(NumberFieldType.class));
        // back compat
        assertThat(mapperService.unmappedFieldType("string"), instanceOf(KeywordFieldType.class));
        assertWarnings("[unmapped_type:string] should be replaced with [unmapped_type:keyword]");
    }

    public void testMergeWithMap() throws Throwable {
        IndexService indexService1 = createIndex("index1");
        MapperService mapperService = indexService1.mapperService();
        Map<String, Map<String, Object>> mappings = new HashMap<>();

        mappings.put("type1", MapperService.parseMapping(xContentRegistry(), "{}"));

        MapperParsingException e = expectThrows( MapperParsingException.class,
            () -> mapperService.merge(mappings, MergeReason.MAPPING_UPDATE));
        assertThat(e.getMessage(), startsWith("Failed to parse mapping [type1]: "));
    }

    public void testMergeParentTypesSame() {
        // Verifies that a merge (absent a DocumentMapper change)
        // doesn't change the parentTypes reference.
        // The collection was being rewrapped with each merge
        // in v5.2 resulting in eventual StackOverflowErrors.
        // https://github.com/elastic/elasticsearch/issues/23604

        IndexService indexService1 = createIndex("index1");
        MapperService mapperService = indexService1.mapperService();
        Set<String> parentTypes = mapperService.getParentTypes();

        Map<String, Map<String, Object>> mappings = new HashMap<>();
        mapperService.merge(mappings, MergeReason.MAPPING_UPDATE);
        assertSame(parentTypes, mapperService.getParentTypes());
    }

    public void testOtherDocumentMappersOnlyUpdatedWhenChangingFieldType() throws IOException {
        IndexService indexService = createIndex("test",
            Settings.builder().put("index.version.created", Version.V_5_6_0).build()); // multiple types

        CompressedXContent simpleMapping = new CompressedXContent(XContentFactory.jsonBuilder().startObject()
            .startObject("properties")
            .startObject("field")
            .field("type", "text")
            .endObject()
            .endObject().endObject().bytes());

        indexService.mapperService().merge("type1", simpleMapping, MergeReason.MAPPING_UPDATE);
        DocumentMapper documentMapper = indexService.mapperService().documentMapper("type1");

        indexService.mapperService().merge("type2", simpleMapping, MergeReason.MAPPING_UPDATE);
        assertSame(indexService.mapperService().documentMapper("type1"), documentMapper);

        CompressedXContent normsDisabledMapping = new CompressedXContent(XContentFactory.jsonBuilder().startObject()
            .startObject("properties")
            .startObject("field")
            .field("type", "text")
            .field("norms", false)
            .endObject()
            .endObject().endObject().bytes());

        indexService.mapperService().merge("type3", normsDisabledMapping, MergeReason.MAPPING_UPDATE);
        assertNotSame(indexService.mapperService().documentMapper("type1"), documentMapper);
    }

     public void testPartitionedConstraints() {
        // partitioned index must have routing
         IllegalArgumentException noRoutingException = expectThrows(IllegalArgumentException.class, () -> {
            client().admin().indices().prepareCreate("test-index")
                    .addMapping("type", "{\"type\":{}}", XContentType.JSON)
                    .setSettings(Settings.builder()
                        .put("index.number_of_shards", 4)
                        .put("index.routing_partition_size", 2))
                    .execute().actionGet();
        });
        assertTrue(noRoutingException.getMessage(), noRoutingException.getMessage().contains("must have routing"));

        // partitioned index cannot have parent/child relationships
        IllegalArgumentException parentException = expectThrows(IllegalArgumentException.class, () -> {
            client().admin().indices().prepareCreate("test-index")
                    .addMapping("parent", "{\"parent\":{\"_routing\":{\"required\":true}}}", XContentType.JSON)
                    .addMapping("child", "{\"child\": {\"_routing\":{\"required\":true}, \"_parent\": {\"type\": \"parent\"}}}",
                        XContentType.JSON)
                    .setSettings(Settings.builder()
                        .put("index.number_of_shards", 4)
                        .put("index.routing_partition_size", 2))
                    .execute().actionGet();
        });
        assertTrue(parentException.getMessage(), parentException.getMessage().contains("cannot have a _parent field"));

        // valid partitioned index
        assertTrue(client().admin().indices().prepareCreate("test-index")
            .addMapping("type", "{\"type\":{\"_routing\":{\"required\":true}}}", XContentType.JSON)
            .setSettings(Settings.builder()
                .put("index.number_of_shards", 4)
                .put("index.routing_partition_size", 2))
            .execute().actionGet().isAcknowledged());
    }

    public void testIndexSortWithNestedFields() throws IOException {
        Settings settings = Settings.builder()
            .put("index.sort.field", "foo")
            .build();
        IllegalArgumentException invalidNestedException = expectThrows(IllegalArgumentException.class,
           () -> createIndex("test", settings, "t", "nested_field", "type=nested", "foo", "type=keyword"));
        assertThat(invalidNestedException.getMessage(),
            containsString("cannot have nested fields when index sort is activated"));
        IndexService indexService =  createIndex("test", settings, "t", "foo", "type=keyword");
        CompressedXContent nestedFieldMapping = new CompressedXContent(XContentFactory.jsonBuilder().startObject()
            .startObject("properties")
            .startObject("nested_field")
            .field("type", "nested")
            .endObject()
            .endObject().endObject().bytes());
        invalidNestedException = expectThrows(IllegalArgumentException.class,
            () -> indexService.mapperService().merge("t", nestedFieldMapping,
                MergeReason.MAPPING_UPDATE));
        assertThat(invalidNestedException.getMessage(),
            containsString("cannot have nested fields when index sort is activated"));
    }

    public void testForbidMultipleTypes() throws IOException {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type").endObject().endObject().string();
        MapperService mapperService = createIndex("test").mapperService();
        mapperService.merge("type", new CompressedXContent(mapping), MergeReason.MAPPING_UPDATE);

        String mapping2 = XContentFactory.jsonBuilder().startObject().startObject("type2").endObject().endObject().string();
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> mapperService.merge("type2", new CompressedXContent(mapping2), MergeReason.MAPPING_UPDATE));
        assertThat(e.getMessage(), Matchers.startsWith("Rejecting mapping update to [test] as the final mapping would have more than 1 type: "));
    }

}
