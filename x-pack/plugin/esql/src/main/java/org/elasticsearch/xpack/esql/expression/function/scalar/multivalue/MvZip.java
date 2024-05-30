/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.multivalue;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.elasticsearch.compute.ann.Evaluator;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.expression.function.OptionalArgument;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataTypes;
import org.elasticsearch.xpack.esql.evaluator.mapper.EvaluatorMapper;
import org.elasticsearch.xpack.esql.expression.function.Example;
import org.elasticsearch.xpack.esql.expression.function.FunctionInfo;
import org.elasticsearch.xpack.esql.expression.function.Param;
import org.elasticsearch.xpack.esql.expression.function.scalar.EsqlScalarFunction;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.FIRST;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.SECOND;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.THIRD;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.isString;

/**
 * Combines the values from two multivalued fields with a delimiter that joins them together.
 */
public class MvZip extends EsqlScalarFunction implements OptionalArgument, EvaluatorMapper {
    private final Expression mvLeft, mvRight, delim;
    private static final Literal COMMA = new Literal(Source.EMPTY, ",", DataTypes.TEXT);

    @FunctionInfo(
        returnType = { "keyword" },
        description = "Combines the values from two multivalued fields with a delimiter that joins them together.",
        examples = @Example(file = "string", tag = "mv_zip")
    )
    public MvZip(
        Source source,
        @Param(name = "string1", type = { "keyword", "text" }, description = "Multivalue expression.") Expression mvLeft,
        @Param(name = "string2", type = { "keyword", "text" }, description = "Multivalue expression.") Expression mvRight,
        @Param(
            name = "delim",
            type = { "keyword", "text" },
            description = "Delimiter. Optional; if omitted, `,` is used as a default delimiter.",
            optional = true
        ) Expression delim
    ) {
        super(source, delim == null ? Arrays.asList(mvLeft, mvRight, COMMA) : Arrays.asList(mvLeft, mvRight, delim));
        this.mvLeft = mvLeft;
        this.mvRight = mvRight;
        this.delim = delim == null ? COMMA : delim;
    }

    @Override
    protected TypeResolution resolveType() {
        if (childrenResolved() == false) {
            return new TypeResolution("Unresolved children");
        }

        TypeResolution resolution = isString(mvLeft, sourceText(), FIRST);
        if (resolution.unresolved()) {
            return resolution;
        }

        resolution = isString(mvRight, sourceText(), SECOND);
        if (resolution.unresolved()) {
            return resolution;
        }

        if (delim != null) {
            resolution = isString(delim, sourceText(), THIRD);
            if (resolution.unresolved()) {
                return resolution;
            }
        }

        return resolution;
    }

    @Override
    public boolean foldable() {
        return mvLeft.foldable() && mvRight.foldable() && (delim == null || delim.foldable());
    }

    @Override
    public EvalOperator.ExpressionEvaluator.Factory toEvaluator(
        Function<Expression, EvalOperator.ExpressionEvaluator.Factory> toEvaluator
    ) {
        return new MvZipEvaluator.Factory(source(), toEvaluator.apply(mvLeft), toEvaluator.apply(mvRight), toEvaluator.apply(delim));
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        return new MvZip(source(), newChildren.get(0), newChildren.get(1), newChildren.size() > 2 ? newChildren.get(2) : null);
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, MvZip::new, mvLeft, mvRight, delim);
    }

    @Override
    public DataTypes dataType() {
        return DataTypes.KEYWORD;
    }

    private static void buildOneSide(BytesRefBlock.Builder builder, int start, int end, BytesRefBlock field, BytesRef fieldScratch) {
        builder.beginPositionEntry();
        for (int i = start; i < end; i++) {
            builder.appendBytesRef(field.getBytesRef(i, fieldScratch));
        }
        builder.endPositionEntry();
    }

    @Evaluator
    static void process(BytesRefBlock.Builder builder, int position, BytesRefBlock leftField, BytesRefBlock rightField, BytesRef delim) {
        int leftFieldValueCount = leftField.getValueCount(position);
        int rightFieldValueCount = rightField.getValueCount(position);

        int leftFirst = leftField.getFirstValueIndex(position);
        int rightFirst = rightField.getFirstValueIndex(position);

        BytesRef fieldScratch = new BytesRef();

        // nulls
        if (leftField.isNull(position)) {
            if (rightFieldValueCount == 1) {
                builder.appendBytesRef(rightField.getBytesRef(rightFirst, fieldScratch));
                return;
            }
            buildOneSide(builder, rightFirst, rightFirst + rightFieldValueCount, rightField, fieldScratch);
            return;
        }

        if (rightField.isNull(position)) {
            if (leftFieldValueCount == 1) {
                builder.appendBytesRef(leftField.getBytesRef(leftFirst, fieldScratch));
                return;
            }
            buildOneSide(builder, leftFirst, leftFirst + leftFieldValueCount, leftField, fieldScratch);
            return;
        }

        BytesRefBuilder work = new BytesRefBuilder();
        // single value
        if (leftFieldValueCount == 1 && rightFieldValueCount == 1) {
            work.append(leftField.getBytesRef(leftFirst, fieldScratch));
            work.append(delim);
            work.append(rightField.getBytesRef(rightFirst, fieldScratch));
            builder.appendBytesRef(work.get());
            return;
        }
        // multiple values
        int leftIndex = 0, rightIndex = 0;
        builder.beginPositionEntry();
        while (leftIndex < leftFieldValueCount && rightIndex < rightFieldValueCount) {
            // concat
            work.clear();
            work.append(leftField.getBytesRef(leftIndex + leftFirst, fieldScratch));
            work.append(delim);
            work.append(rightField.getBytesRef(rightIndex + rightFirst, fieldScratch));
            builder.appendBytesRef(work.get());
            leftIndex++;
            rightIndex++;
        }
        while (leftIndex < leftFieldValueCount) {
            work.clear();
            work.append(leftField.getBytesRef(leftIndex + leftFirst, fieldScratch));
            builder.appendBytesRef(work.get());
            leftIndex++;
        }
        while (rightIndex < rightFieldValueCount) {
            work.clear();
            work.append(rightField.getBytesRef(rightIndex + rightFirst, fieldScratch));
            builder.appendBytesRef(work.get());
            rightIndex++;
        }
        builder.endPositionEntry();
    }
}
