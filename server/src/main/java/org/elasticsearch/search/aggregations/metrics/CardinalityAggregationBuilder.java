/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.aggregations.metrics;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.support.AggregationContext;
import org.elasticsearch.search.aggregations.support.CoreValuesSourceType;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.ValuesSourceRegistry;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class CardinalityAggregationBuilder extends ValuesSourceAggregationBuilder.SingleMetricAggregationBuilder<
    ValuesSource,
    CardinalityAggregationBuilder> {

    public static final String NAME = "cardinality";
    public static final ValuesSourceRegistry.RegistryKey<CardinalityAggregatorSupplier> REGISTRY_KEY =
        new ValuesSourceRegistry.RegistryKey<>(NAME, CardinalityAggregatorSupplier.class);

    private static final ParseField REHASH = new ParseField("rehash").withAllDeprecated("no replacement - values will always be rehashed");
    public static final ParseField PRECISION_THRESHOLD_FIELD = new ParseField("precision_threshold");
    public static final ParseField EXECUTION_HINT_FIELD_NAME = new ParseField("execution_hint");

    public static final ObjectParser<CardinalityAggregationBuilder, String> PARSER = ObjectParser.fromBuilder(
        NAME,
        CardinalityAggregationBuilder::new
    );
    static {
        ValuesSourceAggregationBuilder.declareFields(PARSER, true, false, false);
        PARSER.declareLong(CardinalityAggregationBuilder::precisionThreshold, CardinalityAggregationBuilder.PRECISION_THRESHOLD_FIELD);
        PARSER.declareLong((b, v) -> {/*ignore*/}, REHASH);
        PARSER.declareString(CardinalityAggregationBuilder::executionHint, EXECUTION_HINT_FIELD_NAME);
    }

    public static void registerAggregators(ValuesSourceRegistry.Builder builder) {
        CardinalityAggregatorFactory.registerAggregators(builder);
    }

    private Long precisionThreshold = null;

    private String executionHint = null;

    public CardinalityAggregationBuilder(String name) {
        super(name);
    }

    public CardinalityAggregationBuilder(
        CardinalityAggregationBuilder clone,
        AggregatorFactories.Builder factoriesBuilder,
        Map<String, Object> metadata
    ) {
        super(clone, factoriesBuilder, metadata);
        this.precisionThreshold = clone.precisionThreshold;
        this.executionHint = clone.executionHint;
    }

    @Override
    protected ValuesSourceType defaultValueSourceType() {
        return CoreValuesSourceType.KEYWORD;
    }

    /**
     * Read from a stream.
     */
    public CardinalityAggregationBuilder(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            precisionThreshold = in.readLong();
        }
        if (in.getVersion().onOrAfter(Version.V_8_3_0)) {
            executionHint = in.readOptionalString();
        }
    }

    @Override
    protected AggregationBuilder shallowCopy(AggregatorFactories.Builder factoriesBuilder, Map<String, Object> metadata) {
        return new CardinalityAggregationBuilder(this, factoriesBuilder, metadata);
    }

    @Override
    protected void innerWriteTo(StreamOutput out) throws IOException {
        boolean hasPrecisionThreshold = precisionThreshold != null;
        out.writeBoolean(hasPrecisionThreshold);
        if (hasPrecisionThreshold) {
            out.writeLong(precisionThreshold);
        }
        if (out.getVersion().onOrAfter(Version.V_8_3_0)) {
            out.writeOptionalString(executionHint);
        }
    }

    @Override
    protected boolean serializeTargetValueType(Version version) {
        return true;
    }

    /**
     * Set a precision threshold. Higher values improve accuracy but also
     * increase memory usage.
     */
    public CardinalityAggregationBuilder precisionThreshold(long precisionThreshold) {
        if (precisionThreshold < 0) {
            throw new IllegalArgumentException(
                "[precisionThreshold] must be greater than or equal to 0. Found [" + precisionThreshold + "] in [" + name + "]"
            );
        }
        this.precisionThreshold = precisionThreshold;
        return this;
    }

    /**
     * Get the precision threshold. Higher values improve accuracy but also
     * increase memory usage. Will return <code>null</code> if the
     * precisionThreshold has not been set yet.
     */
    public Long precisionThreshold() {
        return precisionThreshold;
    }

    /**
     * Get the execution hint.  This is an optional user specified hint that
     * will be used to decide on the specific collection algorithm.  Since this
     * is a hint, the implementation may choose to ignore it (typically when
     * the specified method is not applicable to the given field type)
     */
    public String ExecutionHint() {
        return executionHint;
    }

    /**
     * Set the execution hint.  This is an optional user specified hint that
     * will be used to decide on the specific collection algorithm.  Since this
     * is a hint, the implementation may choose to ignore it (typically when
     * the specified method is not applicable to the given field type)
     */
    public void executionHint(String executionHint) {
        this.executionHint = executionHint;
    }

    @Override
    protected CardinalityAggregatorFactory innerBuild(
        AggregationContext context,
        ValuesSourceConfig config,
        AggregatorFactory parent,
        AggregatorFactories.Builder subFactoriesBuilder
    ) throws IOException {
        CardinalityAggregatorSupplier aggregatorSupplier = context.getValuesSourceRegistry().getAggregator(REGISTRY_KEY, config);

        return new CardinalityAggregatorFactory(
            name,
            config,
            precisionThreshold,
            executionHint,
            context,
            parent,
            subFactoriesBuilder,
            metadata,
            aggregatorSupplier
        );
    }

    @Override
    public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        if (precisionThreshold != null) {
            builder.field(PRECISION_THRESHOLD_FIELD.getPreferredName(), precisionThreshold);
        }
        return builder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), precisionThreshold, executionHint);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (super.equals(obj) == false) return false;
        CardinalityAggregationBuilder other = (CardinalityAggregationBuilder) obj;
        return Objects.equals(precisionThreshold, other.precisionThreshold) && Objects.equals(executionHint, other.executionHint);
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    protected ValuesSourceRegistry.RegistryKey<?> getRegistryKey() {
        return REGISTRY_KEY;
    }

    @Override
    public Version getMinimalSupportedVersion() {
        return Version.V_EMPTY;
    }
}
