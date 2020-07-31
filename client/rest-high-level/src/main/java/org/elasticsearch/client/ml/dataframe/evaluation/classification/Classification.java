/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.client.ml.dataframe.evaluation.classification;

import org.elasticsearch.client.ml.dataframe.evaluation.Evaluation;
import org.elasticsearch.client.ml.dataframe.evaluation.EvaluationMetric;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.client.ml.dataframe.evaluation.MlEvaluationNamedXContentProvider.registeredMetricName;
import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.common.xcontent.ConstructingObjectParser.optionalConstructorArg;

/**
 * Evaluation of classification results.
 */
public class Classification implements Evaluation {

    public static final String NAME = "classification";

    private static final ParseField ACTUAL_FIELD = new ParseField("actual_field");
    private static final ParseField PREDICTED_FIELD = new ParseField("predicted_field");
    private static final ParseField RESULTS_NESTED_FIELD = new ParseField("results_nested_field");
    private static final ParseField PREDICTED_CLASS_NAME_FIELD = new ParseField("predicted_class_name_field");
    private static final ParseField PREDICTED_PROBABILITY_FIELD = new ParseField("predicted_probability_field");

    private static final ParseField METRICS = new ParseField("metrics");

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<Classification, Void> PARSER = new ConstructingObjectParser<>(
        NAME,
        true,
        a -> new Classification((String) a[0], (String) a[1], (String) a[2], (String) a[3], (String) a[4], (List<EvaluationMetric>) a[5]));

    static {
        PARSER.declareString(constructorArg(), ACTUAL_FIELD);
        PARSER.declareString(optionalConstructorArg(), PREDICTED_FIELD);
        PARSER.declareString(optionalConstructorArg(), RESULTS_NESTED_FIELD);
        PARSER.declareString(optionalConstructorArg(), PREDICTED_CLASS_NAME_FIELD);
        PARSER.declareString(optionalConstructorArg(), PREDICTED_PROBABILITY_FIELD);
        PARSER.declareNamedObjects(
            optionalConstructorArg(), (p, c, n) -> p.namedObject(EvaluationMetric.class, registeredMetricName(NAME, n), c), METRICS);
    }

    public static Classification fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    /**
     * The field containing the actual value
     */
    private final String actualField;

    /**
     * The field containing the predicted value
     */
    private final String predictedField;

    /**
     * The field containing the array of prediction results
     */
    private final String resultsNestedField;

    /**
     * The field containing the predicted class name value
     */
    private final String predictedClassNameField;

    /**
     * The field containing the predicted probability value in [0.0, 1.0]
     */
    private final String predictedProbabilityField;

    /**
     * The list of metrics to calculate
     */
    private final List<EvaluationMetric> metrics;

    public Classification(String actualField,
                          String predictedField,
                          String resultsNestedField,
                          String predictedClassNameField,
                          String predictedProbabilityField) {
        this(
            actualField,
            predictedField,
            resultsNestedField,
            predictedClassNameField,
            predictedProbabilityField,
            (List<EvaluationMetric>)null);
    }

    public Classification(String actualField,
                          String predictedField,
                          String resultsNestedField,
                          String predictedClassNameField,
                          String predictedProbabilityField,
                          EvaluationMetric... metrics) {
        this(actualField, predictedField, resultsNestedField, predictedClassNameField, predictedProbabilityField, Arrays.asList(metrics));
    }

    public Classification(String actualField,
                          @Nullable String predictedField,
                          @Nullable String resultsNestedField,
                          @Nullable String predictedClassNameField,
                          @Nullable String predictedProbabilityField,
                          @Nullable List<EvaluationMetric> metrics) {
        this.actualField = Objects.requireNonNull(actualField);
        this.predictedField = predictedField;
        this.resultsNestedField = resultsNestedField;
        this.predictedClassNameField = predictedClassNameField;
        this.predictedProbabilityField = predictedProbabilityField;
        if (metrics != null) {
            metrics.sort(Comparator.comparing(EvaluationMetric::getName));
        }
        this.metrics = metrics;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ACTUAL_FIELD.getPreferredName(), actualField);
        if (predictedField != null) {
            builder.field(PREDICTED_FIELD.getPreferredName(), predictedField);
        }
        if (resultsNestedField != null) {
            builder.field(RESULTS_NESTED_FIELD.getPreferredName(), resultsNestedField);
        }
        if (predictedClassNameField != null) {
            builder.field(PREDICTED_CLASS_NAME_FIELD.getPreferredName(), predictedClassNameField);
        }
        if (predictedProbabilityField != null) {
            builder.field(PREDICTED_PROBABILITY_FIELD.getPreferredName(), predictedProbabilityField);
        }
        if (metrics != null) {
           builder.startObject(METRICS.getPreferredName());
           for (EvaluationMetric metric : metrics) {
               builder.field(metric.getName(), metric);
           }
           builder.endObject();
        }

        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Classification that = (Classification) o;
        return Objects.equals(that.actualField, this.actualField)
            && Objects.equals(that.predictedField, this.predictedField)
            && Objects.equals(that.resultsNestedField, this.resultsNestedField)
            && Objects.equals(that.predictedClassNameField, this.predictedClassNameField)
            && Objects.equals(that.predictedProbabilityField, this.predictedProbabilityField)
            && Objects.equals(that.metrics, this.metrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actualField, predictedField, resultsNestedField, predictedClassNameField, predictedProbabilityField, metrics);
    }
}
