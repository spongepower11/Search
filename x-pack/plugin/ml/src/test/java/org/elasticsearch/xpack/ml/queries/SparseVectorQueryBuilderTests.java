/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.queries;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.extras.MapperExtrasPlugin;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.elasticsearch.xpack.core.ml.action.CoordinatedInferenceAction;
import org.elasticsearch.xpack.core.ml.action.InferModelAction;
import org.elasticsearch.xpack.core.ml.inference.TrainedModelPrefixStrings;
import org.elasticsearch.xpack.core.ml.inference.results.TextExpansionResults;
import org.elasticsearch.xpack.ml.MachineLearning;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.elasticsearch.xpack.ml.queries.SparseVectorQueryBuilder.MODEL_ID;
import static org.elasticsearch.xpack.ml.queries.VectorDimensionsQueryBuilder.TOKENS_FIELD;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.hasSize;

public class SparseVectorQueryBuilderTests extends AbstractQueryTestCase<SparseVectorQueryBuilder> {

    private static final String RANK_FEATURES_FIELD = "rank";
    private static final List<TextExpansionResults.VectorDimension> VECTOR_DIMENSIONS = List.of(
        new TextExpansionResults.VectorDimension("foo", .42f)
    );
    private static final int NUM_TOKENS = VECTOR_DIMENSIONS.size();

    @Override
    protected SparseVectorQueryBuilder doCreateTestQueryBuilder() {
        TokenPruningConfig tokenPruningConfig = randomBoolean()
            ? new TokenPruningConfig(randomIntBetween(1, 100), randomFloat(), randomBoolean())
            : null;
        String modelText = randomAlphaOfLength(4);
        String modelId = randomBoolean() ? randomAlphaOfLength(4) : null;
        List<TextExpansionResults.VectorDimension> vectorDimensions = modelId == null ? VECTOR_DIMENSIONS : null;

        var builder = new SparseVectorQueryBuilder(RANK_FEATURES_FIELD, modelText, modelId, vectorDimensions, tokenPruningConfig);
        if (randomBoolean()) {
            builder.boost((float) randomDoubleBetween(0.1, 10.0, true));
        }
        if (randomBoolean()) {
            builder.queryName(randomAlphaOfLength(4));
        }
        return builder;
    }

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return List.of(MachineLearning.class, MapperExtrasPlugin.class);
    }

    @Override
    public void testMustRewrite() {
        SearchExecutionContext context = createSearchExecutionContext();
        SparseVectorQueryBuilder builder = new SparseVectorQueryBuilder("foo", "bar", "baz", null);
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> builder.toQuery(context));
        assertEquals("sparse_vector should have been rewritten to another query type", e.getMessage());
    }

    @Override
    protected boolean canSimulateMethod(Method method, Object[] args) throws NoSuchMethodException {
        return method.equals(Client.class.getMethod("execute", ActionType.class, ActionRequest.class, ActionListener.class))
            && (args[0] instanceof CoordinatedInferenceAction);
    }

    @Override
    protected Object simulateMethod(Method method, Object[] args) {
        CoordinatedInferenceAction.Request request = (CoordinatedInferenceAction.Request) args[1];
        assertEquals(InferModelAction.Request.DEFAULT_TIMEOUT_FOR_API, request.getInferenceTimeout());
        assertEquals(TrainedModelPrefixStrings.PrefixType.SEARCH, request.getPrefixType());
        assertEquals(CoordinatedInferenceAction.Request.RequestModelType.NLP_MODEL, request.getRequestModelType());

        // Randomisation cannot be used here as {@code #doAssertLuceneQuery}
        // asserts that 2 rewritten queries are the same
        var tokens = new ArrayList<TextExpansionResults.VectorDimension>();
        for (int i = 0; i < NUM_TOKENS; i++) {
            tokens.add(new TextExpansionResults.VectorDimension(Integer.toString(i), (i + 1) * 1.0f));
        }

        var response = InferModelAction.Response.builder()
            .setId(request.getModelId())
            .addInferenceResults(List.of(new TextExpansionResults("foo", tokens, randomBoolean())))
            .build();
        @SuppressWarnings("unchecked")  // We matched the method above.
        ActionListener<InferModelAction.Response> listener = (ActionListener<InferModelAction.Response>) args[2];
        listener.onResponse(response);
        return null;
    }

    @Override
    protected void initializeAdditionalMappings(MapperService mapperService) throws IOException {
        mapperService.merge(
            "_doc",
            new CompressedXContent(Strings.toString(PutMappingRequest.simpleMapping(RANK_FEATURES_FIELD, "type=rank_features"))),
            MapperService.MergeReason.MAPPING_UPDATE
        );
    }

    @Override
    protected void doAssertLuceneQuery(SparseVectorQueryBuilder queryBuilder, Query query, SearchExecutionContext context) {
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery booleanQuery = (BooleanQuery) query;
        assertEquals(booleanQuery.getMinimumNumberShouldMatch(), 1);
        assertThat(booleanQuery.clauses(), hasSize(NUM_TOKENS));

        Class<?> featureQueryClass = FeatureField.newLinearQuery("", "", 0.5f).getClass();
        // if the weight is 1.0f a BoostQuery is returned
        Class<?> boostQueryClass = FeatureField.newLinearQuery("", "", 1.0f).getClass();

        for (var clause : booleanQuery.clauses()) {
            assertEquals(BooleanClause.Occur.SHOULD, clause.getOccur());
            assertThat(clause.getQuery(), either(instanceOf(featureQueryClass)).or(instanceOf(boostQueryClass)));
        }
    }

    /**
     * Overridden to ensure that {@link SearchExecutionContext} has a non-null {@link IndexReader}
     */
    @Override
    public void testCacheability() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            Document document = new Document();
            document.add(new FloatDocValuesField(RANK_FEATURES_FIELD, 1.0f));
            iw.addDocument(document);
            try (IndexReader reader = iw.getReader()) {
                SearchExecutionContext context = createSearchExecutionContext(newSearcher(reader));
                SparseVectorQueryBuilder queryBuilder = createTestQueryBuilder();
                QueryBuilder rewriteQuery = rewriteQuery(queryBuilder, new SearchExecutionContext(context));

                assertNotNull(rewriteQuery.toQuery(context));
                assertTrue("query should be cacheable: " + queryBuilder.toString(), context.isCacheable());
            }
        }
    }

    /**
    * Overridden to ensure that {@link SearchExecutionContext} has a non-null {@link IndexReader}; this query should always be rewritten
    */
    @Override
    public void testToQuery() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            Document document = new Document();
            document.add(new FloatDocValuesField(RANK_FEATURES_FIELD, 1.0f));
            iw.addDocument(document);
            try (IndexReader reader = iw.getReader()) {
                SearchExecutionContext context = createSearchExecutionContext(newSearcher(reader));
                SparseVectorQueryBuilder queryBuilder = createTestQueryBuilder();
                IllegalStateException e = expectThrows(IllegalStateException.class, () -> queryBuilder.toQuery(context));
                assertEquals("sparse_vector should have been rewritten to another query type", e.getMessage());
            }
        }
    }

    public void testIllegalValues() {
        {
            IllegalArgumentException e = expectThrows(
                IllegalArgumentException.class,
                () -> new SparseVectorQueryBuilder(null, "model text", "model id", null)
            );
            assertEquals("[sparse_vector] requires a fieldName", e.getMessage());
        }
        {
            IllegalArgumentException e = expectThrows(
                IllegalArgumentException.class,
                () -> new SparseVectorQueryBuilder("field name", null, "model id", null)
            );
            assertEquals("[sparse_vector] requires [model_text] when [model_id] is specified", e.getMessage());
        }
        {
            IllegalArgumentException e = expectThrows(
                IllegalArgumentException.class,
                () -> new SparseVectorQueryBuilder("field name", "model text", null, null)
            );
            assertEquals("[sparse_vector] requires one of [model_id], or [vector_dimensions]", e.getMessage());
        }
        {
            IllegalArgumentException e = expectThrows(
                IllegalArgumentException.class,
                () -> new SparseVectorQueryBuilder("field name", "model text", "baz", VECTOR_DIMENSIONS)
            );
            assertEquals("[sparse_vector] requires one of [model_id], or [vector_dimensions]", e.getMessage());
        }
    }

    public void testToXContentWithModelId() throws IOException {
        QueryBuilder query = new SparseVectorQueryBuilder("foo", "bar", "baz", null);
        checkGeneratedJson("""
            {
              "sparse_vector": {
                "foo": {
                  "model_text": "bar",
                  "model_id": "baz"
                }
              }
            }""", query);
    }

    public void testToXContentWithVectorDimensions() throws IOException {
        QueryBuilder query = new SparseVectorQueryBuilder("foo", "bar", null, VECTOR_DIMENSIONS);
        checkGeneratedJson("""
            {
              "sparse_vector": {
                "foo": {
                  "model_text": "bar",
                  "vector_dimensions": {
                    "foo": 0.42
                  }
                }
              }
            }""", query);
    }

    public void testToXContentWithThresholds() throws IOException {
        QueryBuilder query = new SparseVectorQueryBuilder("foo", "bar", "baz", null, new TokenPruningConfig(4, 0.3f, false));
        checkGeneratedJson("""
            {
              "sparse_vector": {
                "foo": {
                  "model_text": "bar",
                  "model_id": "baz",
                  "pruning_config": {
                    "tokens_freq_ratio_threshold": 4.0,
                    "tokens_weight_threshold": 0.3
                  }
                }
              }
            }""", query);
    }

    public void testToXContentWithThresholdsAndVectorDimensions() throws IOException {
        QueryBuilder query = new SparseVectorQueryBuilder("foo", "bar", null, VECTOR_DIMENSIONS, new TokenPruningConfig(4, 0.3f, false));
        checkGeneratedJson("""
            {
              "sparse_vector": {
                "foo": {
                  "model_text": "bar",
                  "pruning_config": {
                    "tokens_freq_ratio_threshold": 4.0,
                    "tokens_weight_threshold": 0.3
                  },
                  "vector_dimensions": {
                    "foo": 0.42
                  }
                }
              }
            }""", query);
    }

    public void testToXContentWithThresholdsAndOnlyScorePrunedTokens() throws IOException {
        QueryBuilder query = new SparseVectorQueryBuilder("foo", "bar", "baz", null, new TokenPruningConfig(4, 0.3f, true));
        checkGeneratedJson("""
            {
              "sparse_vector": {
                "foo": {
                  "model_text": "bar",
                  "model_id": "baz",
                  "pruning_config": {
                    "tokens_freq_ratio_threshold": 4.0,
                    "tokens_weight_threshold": 0.3,
                    "only_score_pruned_tokens": true
                  }
                }
              }
            }""", query);
    }

    public void testToXContentWithThresholdsAndOnlyScorePrunedTokensAndVectorDimensions() throws IOException {
        QueryBuilder query = new SparseVectorQueryBuilder("foo", "bar", null, VECTOR_DIMENSIONS, new TokenPruningConfig(4, 0.3f, true));
        checkGeneratedJson("""
            {
              "sparse_vector": {
                "foo": {
                  "model_text": "bar",
                  "pruning_config": {
                    "tokens_freq_ratio_threshold": 4.0,
                    "tokens_weight_threshold": 0.3,
                    "only_score_pruned_tokens": true
                  },
                  "vector_dimensions": {
                    "foo": 0.42
                  }
                }
              }
            }""", query);
    }

    @Override
    public void testValidOutput() {
        QueryBuilder query = new SparseVectorQueryBuilder("foo", "bar", null, VECTOR_DIMENSIONS, new TokenPruningConfig(4, 0.3f, true));
        assertEquals("""
            {
              "sparse_vector" : {
                "foo" : {
                  "model_text" : "bar",
                  "pruning_config" : {
                    "tokens_freq_ratio_threshold" : 4.0,
                    "tokens_weight_threshold" : 0.3,
                    "only_score_pruned_tokens" : true
                  },
                  "vector_dimensions" : {
                    "foo" : 0.42
                  }
                }
              }
            }""", query.toString());
    }

    @Override
    protected String[] shuffleProtectedFields() {
        return new String[] {
            TOKENS_FIELD.getPreferredName(),
            MODEL_ID.getPreferredName(),
            SparseVectorQueryBuilder.VECTOR_DIMENSIONS.getPreferredName() };
    }

    public void testThatTokensAreCorrectlyPruned() {
        SearchExecutionContext searchExecutionContext = createSearchExecutionContext();
        SparseVectorQueryBuilder queryBuilder = createTestQueryBuilder();
        QueryBuilder rewrittenQueryBuilder = rewriteAndFetch(queryBuilder, searchExecutionContext);
        if (queryBuilder.getTokenPruningConfig() == null) {
            assertTrue(rewrittenQueryBuilder instanceof BoolQueryBuilder);
        } else {
            assertTrue(rewrittenQueryBuilder instanceof VectorDimensionsQueryBuilder);
        }
    }
}
