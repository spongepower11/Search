/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.function.scalar.string;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.sql.SqlIllegalArgumentException;
import org.elasticsearch.xpack.sql.expression.function.scalar.processor.runtime.Processor;
import org.elasticsearch.xpack.sql.expression.function.scalar.string.BinaryStringStringProcessor.BinaryStringStringOperation;

import java.io.IOException;
import java.util.function.BiFunction;

/**
 * Processor class covering string manipulating functions that have two string parameters and a numeric result.
 */
public class BinaryStringStringProcessor extends BinaryStringProcessor<BinaryStringStringOperation, String, Number> {
    
    public static final String NAME = "ss";
    
    public BinaryStringStringProcessor(StreamInput in) throws IOException {
        super(in, i -> i.readEnum(BinaryStringStringOperation.class));
    }

    public BinaryStringStringProcessor(Processor left, Processor right, BinaryStringStringOperation operation) {
        super(left, right, operation);
    }

    public enum BinaryStringStringOperation implements BiFunction<String, String, Number> {
        POSITION((sub,str) -> {
            if (sub == null || str == null) return null;
            int pos = str.indexOf(sub);
            return pos < 0 ? 0 : pos+1;
        });

        BinaryStringStringOperation(BiFunction<String, String, Number> op) {
            this.op = op;
        }
        
        private final BiFunction<String, String, Number> op;
        
        @Override
        public Number apply(String stringExpLeft, String stringExpRight) {
            return op.apply(stringExpLeft, stringExpRight);
        }
    }

    @Override
    protected void doWrite(StreamOutput out) throws IOException {
        out.writeEnum(operation());
    }

    @Override
    protected Object doProcess(Object left, Object right) {
        if (left == null || right == null) {
            return null;
        }
        if (!(left instanceof String || left instanceof Character)) {
            throw new SqlIllegalArgumentException("A string/char is required; received [{}]", left);
        }
        if (!(right instanceof String || right instanceof Character)) {
            throw new SqlIllegalArgumentException("A string/char is required; received [{}]", right);
        }

        return operation().apply(left instanceof Character ? left.toString() : (String) left, 
                right instanceof Character ? right.toString() : (String) right);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

}
