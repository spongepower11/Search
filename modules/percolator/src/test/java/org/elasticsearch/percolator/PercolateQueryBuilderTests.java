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

import org.apache.lucene.search.Query;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.ingest.RandomDocumentPicks;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;

public class PercolateQueryBuilderTests extends AbstractQueryTestCase<PercolateQueryBuilder> {

    private static final String[] SHUFFLE_PROTECTED_FIELDS = new String[] {
        PercolateQueryBuilder.DOCUMENT_FIELD.getPreferredName(),
        PercolateQueryBuilder.DOCUMENTS_FIELD.getPreferredName()
    };

    protected static String queryField = "field";
    protected static String aliasField = "alias";
    private static String docType;

    private String indexedDocumentIndex;
    private String indexedDocumentType;
    private String indexedDocumentId;
    private String indexedDocumentRouting;
    private String indexedDocumentPreference;
    private Long indexedDocumentVersion;
    private List<BytesReference> documentSource;

    private boolean indexedDocumentExists = true;

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(PercolatorPlugin.class);
    }

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        queryField = randomAlphaOfLength(4);
        aliasField = randomAlphaOfLength(4);

        String docType = "_doc";
        mapperService.merge(docType, new CompressedXContent(Strings.toString(PutMappingRequest.buildFromSimplifiedDef(docType,
                queryField, "type=percolator", aliasField, "type=alias,path=" + queryField
        ))), MapperService.MergeReason.MAPPING_UPDATE);
        mapperService.merge(docType, new CompressedXContent(Strings.toString(PutMappingRequest.buildFromSimplifiedDef(docType,
                STRING_FIELD_NAME, "type=text"
        ))), MapperService.MergeReason.MAPPING_UPDATE);
    }

    @Override
    protected PercolateQueryBuilder doCreateTestQueryBuilder() {
        return doCreateTestQueryBuilder(randomBoolean());
    }

    private PercolateQueryBuilder doCreateTestQueryBuilder(boolean indexedDocument) {
        if (indexedDocument) {
            documentSource = Collections.singletonList(randomSource(new HashSet<>()));
        } else {
            int numDocs = randomIntBetween(1, 8);
            documentSource = new ArrayList<>(numDocs);
            Set<String> usedFields = new HashSet<>();
            for (int i = 0; i < numDocs; i++) {
                documentSource.add(randomSource(usedFields));
            }
        }

        PercolateQueryBuilder queryBuilder;
        if (indexedDocument) {
            indexedDocumentIndex = randomAlphaOfLength(4);
            indexedDocumentType = "doc";
            indexedDocumentId = randomAlphaOfLength(4);
            indexedDocumentRouting = randomAlphaOfLength(4);
            indexedDocumentPreference = randomAlphaOfLength(4);
            indexedDocumentVersion = (long) randomIntBetween(0, Integer.MAX_VALUE);
            queryBuilder = new PercolateQueryBuilder(queryField, docType, indexedDocumentIndex, indexedDocumentType, indexedDocumentId,
                    indexedDocumentRouting, indexedDocumentPreference, indexedDocumentVersion);
        } else {
            queryBuilder = new PercolateQueryBuilder(queryField, docType, documentSource, XContentType.JSON);
        }
        if (randomBoolean()) {
            queryBuilder.setName(randomAlphaOfLength(4));
        }
        return queryBuilder;
    }

    /**
     * we don't want to shuffle the "document" field internally in {@link #testFromXContent()} because even though the
     * documents would be functionally the same, their {@link BytesReference} representation isn't and thats what we
     * compare when check for equality of the original and the shuffled builder
     */
    @Override
    protected String[] shuffleProtectedFields() {
        return SHUFFLE_PROTECTED_FIELDS;
    }

    @Override
    protected GetResponse executeGet(GetRequest getRequest) {
        assertThat(getRequest.index(), Matchers.equalTo(indexedDocumentIndex));
        assertThat(getRequest.type(), Matchers.equalTo(indexedDocumentType));
        assertThat(getRequest.id(), Matchers.equalTo(indexedDocumentId));
        assertThat(getRequest.routing(), Matchers.equalTo(indexedDocumentRouting));
        assertThat(getRequest.preference(), Matchers.equalTo(indexedDocumentPreference));
        assertThat(getRequest.version(), Matchers.equalTo(indexedDocumentVersion));
        if (indexedDocumentExists) {
            return new GetResponse(
                    new GetResult(indexedDocumentIndex, indexedDocumentType, indexedDocumentId, 0L, true,
                            documentSource.iterator().next(), Collections.emptyMap())
            );
        } else {
            return new GetResponse(
                    new GetResult(indexedDocumentIndex, indexedDocumentType, indexedDocumentId, -1, false, null, Collections.emptyMap())
            );
        }
    }

    @Override
    protected void doAssertLuceneQuery(PercolateQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        assertThat(query, Matchers.instanceOf(PercolateQuery.class));
        PercolateQuery percolateQuery = (PercolateQuery) query;
        assertThat(docType, Matchers.equalTo(queryBuilder.getDocumentType()));
        assertThat(percolateQuery.getDocuments(), Matchers.equalTo(documentSource));
    }

    @Override
    public void testMustRewrite() throws IOException {
        PercolateQueryBuilder pqb = doCreateTestQueryBuilder(true);
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> pqb.toQuery(createShardContext()));
        assertThat(e.getMessage(), equalTo("query builder must be rewritten first"));
        QueryBuilder rewrite = rewriteAndFetch(pqb, createShardContext());
        PercolateQueryBuilder geoShapeQueryBuilder =
            new PercolateQueryBuilder(pqb.getField(), pqb.getDocumentType(), documentSource, XContentType.JSON);
        assertEquals(geoShapeQueryBuilder, rewrite);
    }

    public void testIndexedDocumentDoesNotExist() throws IOException {
        indexedDocumentExists = false;
        PercolateQueryBuilder pqb = doCreateTestQueryBuilder(true);
        ResourceNotFoundException e = expectThrows(ResourceNotFoundException.class, () -> rewriteAndFetch(pqb,
            createShardContext()));
        String expectedString = "indexed document [" + indexedDocumentIndex + "/" + indexedDocumentType + "/" +
                indexedDocumentId +  "] couldn't be found";
        assertThat(e.getMessage() , equalTo(expectedString));
    }

    @Override
    protected Set<String> getObjectsHoldingArbitraryContent() {
        //document contains arbitrary content, no error expected when an object is added to it
        return new HashSet<>(Arrays.asList(PercolateQueryBuilder.DOCUMENT_FIELD.getPreferredName(),
                PercolateQueryBuilder.DOCUMENTS_FIELD.getPreferredName()));
    }

    public void testRequiredParameters() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> {
            new PercolateQueryBuilder(null, new BytesArray("{}"), XContentType.JSON);
        });
        assertThat(e.getMessage(), equalTo("[field] is a required argument"));

        e = expectThrows(IllegalArgumentException.class,
            () -> new PercolateQueryBuilder("_field", "_document_type", null, null));
        assertThat(e.getMessage(), equalTo("[document] is a required argument"));

        e = expectThrows(IllegalArgumentException.class, () -> {
            new PercolateQueryBuilder(null, null, "_index", "_type", "_id", null, null, null);
        });
        assertThat(e.getMessage(), equalTo("[field] is a required argument"));

        e = expectThrows(IllegalArgumentException.class, () -> {
            new PercolateQueryBuilder("_field", "_document_type", null, "_type", "_id", null, null, null);
        });
        assertThat(e.getMessage(), equalTo("[index] is a required argument"));

        e = expectThrows(IllegalArgumentException.class, () -> {
            new PercolateQueryBuilder("_field", "_document_type", "_index", null, "_id", null, null, null);
        });
        assertThat(e.getMessage(), equalTo("[type] is a required argument"));

        e = expectThrows(IllegalArgumentException.class, () -> {
            new PercolateQueryBuilder("_field", "_document_type", "_index", "_type", null, null, null, null);
        });
        assertThat(e.getMessage(), equalTo("[id] is a required argument"));
    }

    public void testFromJsonNoDocumentType() throws IOException {
        QueryShardContext queryShardContext = createShardContext();
        QueryBuilder queryBuilder = parseQuery("{\"percolate\" : { \"document\": {}, \"field\":\"" + queryField + "\"}}");
        queryBuilder.toQuery(queryShardContext);
    }

    public void testBothDocumentAndDocumentsSpecified() throws IOException {
        expectThrows(IllegalArgumentException.class,
            () -> parseQuery("{\"percolate\" : { \"document\": {}, \"documents\": [{}, {}], \"field\":\"" + queryField + "\"}}"));
    }

    private static BytesReference randomSource(Set<String> usedFields) {
        try {
            // If we create two source that have the same field, but these fields have different kind of values (str vs. lng) then
            // when these source get indexed, indexing can fail. To solve this test issue, we should generate source that
            // always have unique fields:
            Map<String, ?> source;
            boolean duplicateField;
            do {
                duplicateField = false;
                source = RandomDocumentPicks.randomSource(random());
                for (String field : source.keySet()) {
                    if (usedFields.add(field) == false) {
                        duplicateField = true;
                        break;
                    }
                }
            } while (duplicateField);

            XContentBuilder xContent = XContentFactory.jsonBuilder();
            xContent.map(source);
            return BytesReference.bytes(xContent);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected boolean isCachable(PercolateQueryBuilder queryBuilder) {
        return false;
    }

    @Override
    protected boolean builderGeneratesCacheableQueries() {
        return false;
    }

    public void testSerializationFailsUnlessFetched() throws IOException {
        QueryBuilder builder = doCreateTestQueryBuilder(true);
        QueryBuilder queryBuilder = Rewriteable.rewrite(builder, createShardContext());
        IllegalStateException ise = expectThrows(IllegalStateException.class, () -> queryBuilder.writeTo(new BytesStreamOutput(10)));
        assertEquals(ise.getMessage(), "supplier must be null, can't serialize suppliers, missing a rewriteAndFetch?");
        builder = rewriteAndFetch(builder, createShardContext());
        builder.writeTo(new BytesStreamOutput(10));
    }

    public void testFieldAlias() throws IOException {
        QueryShardContext shardContext = createShardContext();

        PercolateQueryBuilder builder = doCreateTestQueryBuilder(false);
        QueryBuilder rewrittenBuilder = rewriteAndFetch(builder, shardContext);
        PercolateQuery query = (PercolateQuery) rewrittenBuilder.toQuery(shardContext);

        PercolateQueryBuilder aliasBuilder = new PercolateQueryBuilder(aliasField,
            builder.getDocuments(),
            builder.getXContentType());
        QueryBuilder rewrittenAliasBuilder = rewriteAndFetch(aliasBuilder, shardContext);
        PercolateQuery aliasQuery = (PercolateQuery) rewrittenAliasBuilder.toQuery(shardContext);

        assertEquals(query.getCandidateMatchesQuery(), aliasQuery.getCandidateMatchesQuery());
        assertEquals(query.getVerifiedMatchesQuery(), aliasQuery.getVerifiedMatchesQuery());
    }

}
