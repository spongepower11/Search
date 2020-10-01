/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.eql.expression.predicate.operator.comparison;

import org.elasticsearch.common.logging.LoggerMessageFormat;
import org.elasticsearch.xpack.eql.expression.predicate.operator.comparison.InsensitiveBinaryComparisonProcessor.InsensitiveBinaryComparisonOperation;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.Expressions;
import org.elasticsearch.xpack.ql.expression.Expressions.ParamOrdinal;
import org.elasticsearch.xpack.ql.expression.TypeResolutions;
import org.elasticsearch.xpack.ql.expression.gen.pipeline.Pipe;
import org.elasticsearch.xpack.ql.expression.predicate.BinaryOperator;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.time.ZoneId;

// marker class to indicate operations that rely on values
public abstract class InsensitiveBinaryComparison extends BinaryOperator<Object, Object, Boolean, InsensitiveBinaryComparisonOperation> {

    private final ZoneId zoneId;

    protected InsensitiveBinaryComparison(Source source, Expression left, Expression right, InsensitiveBinaryComparisonOperation operation,
                                          ZoneId zoneId) {
        super(source, left, right, operation);
        this.zoneId = zoneId;
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    @Override
    protected TypeResolution resolveInputType(Expression e, ParamOrdinal paramOrdinal) {
        String op = function().symbol();
        TypeResolution resolution = TypeResolutions.isString(e, op, paramOrdinal);
        if (resolution.unresolved()) {
            String message = LoggerMessageFormat.format(null, "{}; considering using [{}] instead", resolution.message(),
                regularOperatorSymbol());
            resolution = new TypeResolution(message);
        }
        return resolution;
    }

    /**
     * Symbol of the regular
     */
    protected abstract String regularOperatorSymbol();

    @Override
    protected Expression canonicalize() {
        return left().hashCode() > right().hashCode() ? swapLeftAndRight() : this;
    }

    @Override
    public DataType dataType() {
        return DataTypes.BOOLEAN;
    }

    @Override
    protected Pipe makePipe() {
        return new InsensitiveBinaryComparisonPipe(source(), this, Expressions.pipe(left()), Expressions.pipe(right()), function());
    }
}
