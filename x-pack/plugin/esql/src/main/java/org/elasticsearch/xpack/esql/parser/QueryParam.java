/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.esql.parser;

import org.elasticsearch.xpack.esql.core.type.DataType;

/**
 * Represent a strongly typed parameter value
 */
public record QueryParam(String name, Object value, DataType type, boolean isField, boolean isPattern) {

    public QueryParam(String name, Object value, DataType type) {
        this(name, value, type, false, false);
    }

    public QueryParam(String name, Object value, DataType type, boolean isField) {
        this(name, value, type, isField, false);
    }

    public String nameValue() {
        return "{" + (this.name == null ? "" : this.name + ":") + this.value + "}";
    }

    @Override
    public String toString() {
        return value + " [" + name + "][" + type + "][" + isField + "][" + isPattern + "]";
    }

    public boolean isField() {
        return isField;
    }

    public boolean isPattern() {
        return isPattern;
    }

}
