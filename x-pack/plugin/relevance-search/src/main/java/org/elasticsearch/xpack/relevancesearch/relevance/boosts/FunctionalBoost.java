/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.relevancesearch.relevance.boosts;

import java.util.Objects;

public class FunctionalBoost extends ScriptScoreBoost {
    private final FunctionType function;
    private final Float factor;

    public static final String TYPE = "functional";

    public enum FunctionType {
        logarithmic,
        exponential,
        linear
    }

    public FunctionalBoost(String function, String operation, Float factor) {
        super(TYPE, operation);
        this.function = FunctionType.valueOf(function);
        this.factor = factor;
    }

    public FunctionType getFunction() {
        return function;
    }

    public Float getFactor() {
        return factor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionalBoost that = (FunctionalBoost) o;
        return (this.type.equals(that.getType())
            && this.function.equals(that.getFunction())
            && this.operation.equals(that.getOperation())
            && this.factor.equals(that.getFactor()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, function, operation, factor);
    }

    @Override
    public String getSource(String field) {
        return switch (function) {
            case logarithmic -> format("{0} * Math.max(0.0001, Math.log(Math.max(0.0001, {1})))", factor, safeLogValue(field));
            case exponential -> format("{0} * Math.exp({1})", factor, safeValue(field));
            case linear -> format("{0} * ({1})", factor, safeValue(field));
        };
    }

    private String safeLogValue(String field) {
        return format("(doc[''{0}''].size() > 0) ? (doc[''{0}''].value + 1) : {1}", field, constantFactor());
    }
}
