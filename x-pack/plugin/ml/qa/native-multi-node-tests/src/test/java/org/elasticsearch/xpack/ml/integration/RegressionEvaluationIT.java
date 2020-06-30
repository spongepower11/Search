/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.integration;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.xpack.core.ml.action.EvaluateDataFrameAction;
import org.elasticsearch.xpack.core.ml.dataframe.evaluation.EvaluationMetricResult;
import org.elasticsearch.xpack.core.ml.dataframe.evaluation.regression.MeanSquaredError;
import org.elasticsearch.xpack.core.ml.dataframe.evaluation.regression.MeanSquaredLogarithmicError;
import org.elasticsearch.xpack.core.ml.dataframe.evaluation.regression.RSquared;
import org.elasticsearch.xpack.core.ml.dataframe.evaluation.regression.Regression;
import org.junit.After;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class RegressionEvaluationIT extends MlNativeDataFrameAnalyticsIntegTestCase {

    private static final String HOUSES_DATA_INDEX = "test-evaluate-houses-index";

    private static final String PRICE_FIELD = "price";
    private static final String PRICE_PREDICTION_FIELD = "price_prediction";

    @Before
    public void setup() {
        createHousesIndex(HOUSES_DATA_INDEX);
        indexHousesData(HOUSES_DATA_INDEX);
    }

    @After
    public void cleanup() {
        cleanUp();
    }

    public void testEvaluate_DefaultMetrics() {
        EvaluateDataFrameAction.Response evaluateDataFrameResponse =
            evaluateDataFrame(HOUSES_DATA_INDEX, new Regression(PRICE_FIELD, PRICE_PREDICTION_FIELD, null));

        assertThat(evaluateDataFrameResponse.getEvaluationName(), equalTo(Regression.NAME.getPreferredName()));
        assertThat(
            evaluateDataFrameResponse.getMetrics().stream().map(EvaluationMetricResult::getMetricName).collect(toList()),
            contains(MeanSquaredError.NAME.getPreferredName(), RSquared.NAME.getPreferredName()));
    }

    public void testEvaluate_AllMetrics() {
        EvaluateDataFrameAction.Response evaluateDataFrameResponse =
            evaluateDataFrame(
                HOUSES_DATA_INDEX,
                new Regression(
                    PRICE_FIELD,
                    PRICE_PREDICTION_FIELD,
                    Arrays.asList(new MeanSquaredError(), new MeanSquaredLogarithmicError((Double) null), new RSquared())));

        assertThat(evaluateDataFrameResponse.getEvaluationName(), equalTo(Regression.NAME.getPreferredName()));
        assertThat(
            evaluateDataFrameResponse.getMetrics().stream().map(EvaluationMetricResult::getMetricName).collect(toList()),
            contains(
                MeanSquaredError.NAME.getPreferredName(),
                MeanSquaredLogarithmicError.NAME.getPreferredName(),
                RSquared.NAME.getPreferredName()));
    }

    public void testEvaluate_MeanSquaredError() {
        EvaluateDataFrameAction.Response evaluateDataFrameResponse =
            evaluateDataFrame(
                HOUSES_DATA_INDEX,
                new Regression(PRICE_FIELD, PRICE_PREDICTION_FIELD, Collections.singletonList(new MeanSquaredError())));

        assertThat(evaluateDataFrameResponse.getEvaluationName(), equalTo(Regression.NAME.getPreferredName()));
        assertThat(evaluateDataFrameResponse.getMetrics(), hasSize(1));

        MeanSquaredError.Result mseResult = (MeanSquaredError.Result) evaluateDataFrameResponse.getMetrics().get(0);
        assertThat(mseResult.getMetricName(), equalTo(MeanSquaredError.NAME.getPreferredName()));
        assertThat(mseResult.getError(), equalTo(1000000.0));
    }

    public void testEvaluate_MeanSquaredLogarithmicError() {
        EvaluateDataFrameAction.Response evaluateDataFrameResponse =
            evaluateDataFrame(
                HOUSES_DATA_INDEX,
                new Regression(
                    PRICE_FIELD,
                    PRICE_PREDICTION_FIELD,
                    Collections.singletonList(new MeanSquaredLogarithmicError((Double) null))));

        assertThat(evaluateDataFrameResponse.getEvaluationName(), equalTo(Regression.NAME.getPreferredName()));
        assertThat(evaluateDataFrameResponse.getMetrics(), hasSize(1));

        MeanSquaredLogarithmicError.Result msleResult = (MeanSquaredLogarithmicError.Result) evaluateDataFrameResponse.getMetrics().get(0);
        assertThat(msleResult.getMetricName(), equalTo(MeanSquaredLogarithmicError.NAME.getPreferredName()));
        assertThat(msleResult.getError(), closeTo(Math.pow(Math.log(1001), 2), 10E-6));
    }

    public void testEvaluate_RSquared() {
        EvaluateDataFrameAction.Response evaluateDataFrameResponse =
            evaluateDataFrame(
                HOUSES_DATA_INDEX, new Regression(PRICE_FIELD, PRICE_PREDICTION_FIELD, Collections.singletonList(new RSquared())));

        assertThat(evaluateDataFrameResponse.getEvaluationName(), equalTo(Regression.NAME.getPreferredName()));
        assertThat(evaluateDataFrameResponse.getMetrics(), hasSize(1));

        RSquared.Result rSquaredResult = (RSquared.Result) evaluateDataFrameResponse.getMetrics().get(0);
        assertThat(rSquaredResult.getMetricName(), equalTo(RSquared.NAME.getPreferredName()));
        assertThat(rSquaredResult.getValue(), equalTo(0.0));
    }

    private static void createHousesIndex(String indexName) {
        client().admin().indices().prepareCreate(indexName)
            .setMapping("_doc",
                PRICE_FIELD, "type=double",
                PRICE_PREDICTION_FIELD, "type=double")
            .get();
    }

    private static void indexHousesData(String indexName) {
        BulkRequestBuilder bulkRequestBuilder = client().prepareBulk()
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        for (int i = 0; i < 100; i++) {
            bulkRequestBuilder.add(
                new IndexRequest(indexName)
                    .source(
                        PRICE_FIELD, 1000,
                        PRICE_PREDICTION_FIELD, 0));
        }
        BulkResponse bulkResponse = bulkRequestBuilder.get();
        if (bulkResponse.hasFailures()) {
            fail("Failed to index data: " + bulkResponse.buildFailureMessage());
        }
    }
}
