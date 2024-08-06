/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.aggregation;

import org.elasticsearch.compute.operator.DriverContext;
import static org.elasticsearch.common.logging.LoggerMessageFormat.format;

import static org.elasticsearch.common.logging.HeaderWarning.addWarning;

/**
 * Utilities to collect warnings for running an executor.
 */
public class Warnings {
    static final int MAX_ADDED_WARNINGS = 20;

    private final String location;
    private final String first;

    private int addedWarnings;

    public static final Warnings NOOP_WARNINGS = new Warnings(-1, -2, "") {
        @Override
        public void registerException(Exception exception) {
            // this space intentionally left blank
        }
    };

    /**
     * Create a new warnings object based on the given mode
     * @param warningsMode The warnings collection strategy to use
     * @param lineNumber The line number of the source text. Same as `source.getLineNumber()`
     * @param columnNumber The column number of the source text. Same as `source.getColumnNumber()`
     * @param sourceText The source text that caused the warning. Same as `source.text()`
     * @return A warnings collector object
     */
    public static Warnings createWarnings(DriverContext.WarningsMode warningsMode, int lineNumber, int columnNumber, String sourceText) {
        switch (warningsMode) {
            case COLLECT -> {
                return new Warnings(lineNumber, columnNumber, sourceText);
            }
            case IGNORE -> {
                return NOOP_WARNINGS;
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    public Warnings(int lineNumber, int columnNumber, String sourceText) {
        location = format("Line {}:{}: ", lineNumber, columnNumber);
        first = format(
            null,
            "{}evaluation of [{}] failed, treating result as null. Only first {} failures recorded.",
            location,
            sourceText,
            MAX_ADDED_WARNINGS
        );
    }

    public void registerException(Exception exception) {
        if (addedWarnings < MAX_ADDED_WARNINGS) {
            if (addedWarnings == 0) {
                addWarning(first);
            }
            // location needs to be added to the exception too, since the headers are deduplicated
            addWarning(location + exception.getClass().getName() + ": " + exception.getMessage());
            addedWarnings++;
        }
    }
}
