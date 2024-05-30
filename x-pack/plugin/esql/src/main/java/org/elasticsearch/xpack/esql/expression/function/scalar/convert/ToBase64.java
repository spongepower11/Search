/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.convert;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.elasticsearch.compute.ann.Evaluator;
import org.elasticsearch.compute.ann.Fixed;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.xpack.esql.EsqlIllegalArgumentException;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.TypeResolutions;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataTypes;
import org.elasticsearch.xpack.esql.expression.function.Example;
import org.elasticsearch.xpack.esql.expression.function.FunctionInfo;
import org.elasticsearch.xpack.esql.expression.function.Param;
import org.elasticsearch.xpack.esql.expression.function.scalar.UnaryScalarFunction;
import org.elasticsearch.xpack.esql.planner.PlannerUtils;

import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.isString;
import static org.elasticsearch.xpack.esql.core.type.DataTypes.KEYWORD;

public class ToBase64 extends UnaryScalarFunction {

    @FunctionInfo(
        returnType = "keyword",
        description = "Encode a string to a base64 string.",
        examples = @Example(file = "string", tag = "to_base64")
    )
    public ToBase64(Source source, @Param(name = "string", type = { "keyword", "text" }, description = "A string.") Expression string) {
        super(source, string);

    }

    @Override
    protected TypeResolution resolveType() {
        if (childrenResolved() == false) {
            return new TypeResolution("Unresolved children");
        }
        return isString(field, sourceText(), TypeResolutions.ParamOrdinal.DEFAULT);
    }

    @Override
    public DataTypes dataType() {
        return KEYWORD;
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        return new ToBase64(source(), newChildren.get(0));
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, ToBase64::new, field);
    }

    @Evaluator(warnExceptions = { ArithmeticException.class })
    static BytesRef process(BytesRef field, @Fixed(includeInToString = false, build = true) BytesRefBuilder oScratch) {
        int outLength = Math.multiplyExact(4, (Math.addExact(field.length, 2) / 3));
        byte[] bytes = new byte[field.length];
        System.arraycopy(field.bytes, field.offset, bytes, 0, field.length);
        oScratch.grow(outLength);
        oScratch.clear();
        int encodedSize = Base64.getEncoder().encode(bytes, oScratch.bytes());
        return new BytesRef(oScratch.bytes(), 0, encodedSize);
    }

    @Override
    public EvalOperator.ExpressionEvaluator.Factory toEvaluator(
        Function<Expression, EvalOperator.ExpressionEvaluator.Factory> toEvaluator
    ) {
        return switch (PlannerUtils.toElementType(field.dataType())) {
            case BYTES_REF -> new ToBase64Evaluator.Factory(source(), toEvaluator.apply(field), context -> new BytesRefBuilder());
            case NULL -> EvalOperator.CONSTANT_NULL_FACTORY;
            default -> throw EsqlIllegalArgumentException.illegalDataType(field.dataType());
        };
    }
}
