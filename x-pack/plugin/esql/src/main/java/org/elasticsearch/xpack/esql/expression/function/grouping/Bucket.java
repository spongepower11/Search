/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.grouping;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.Rounding;
import org.elasticsearch.compute.operator.EvalOperator.ExpressionEvaluator;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xpack.esql.EsqlIllegalArgumentException;
import org.elasticsearch.xpack.esql.capabilities.Validatable;
import org.elasticsearch.xpack.esql.core.common.Failures;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.Foldables;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.expression.TypeResolutions;
import org.elasticsearch.xpack.esql.core.expression.function.TwoOptionalArguments;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataTypes;
import org.elasticsearch.xpack.esql.expression.function.Example;
import org.elasticsearch.xpack.esql.expression.function.FunctionInfo;
import org.elasticsearch.xpack.esql.expression.function.Param;
import org.elasticsearch.xpack.esql.expression.function.scalar.date.DateTrunc;
import org.elasticsearch.xpack.esql.expression.function.scalar.math.Floor;
import org.elasticsearch.xpack.esql.expression.predicate.operator.arithmetic.Div;
import org.elasticsearch.xpack.esql.expression.predicate.operator.arithmetic.Mul;
import org.elasticsearch.xpack.esql.type.EsqlDataTypes;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;

import static org.elasticsearch.common.logging.LoggerMessageFormat.format;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.FIRST;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.FOURTH;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.SECOND;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.ParamOrdinal.THIRD;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.isNumeric;
import static org.elasticsearch.xpack.esql.core.expression.TypeResolutions.isType;
import static org.elasticsearch.xpack.esql.expression.Validations.isFoldable;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter.dateTimeToLong;

/**
 * Splits dates and numbers into a given number of buckets. There are two ways to invoke
 * this function: with a user-provided span (explicit invocation mode), or a span derived
 * from a number of desired buckets (as a hint) and a range (auto mode).
 * In the former case, two parameters will be provided, in the latter four.
 */
public class Bucket extends GroupingFunction implements Validatable, TwoOptionalArguments {
    // TODO maybe we should just cover the whole of representable dates here - like ten years, 100 years, 1000 years, all the way up.
    // That way you never end up with more than the target number of buckets.
    private static final Rounding LARGEST_HUMAN_DATE_ROUNDING = Rounding.builder(Rounding.DateTimeUnit.YEAR_OF_CENTURY).build();
    private static final Rounding[] HUMAN_DATE_ROUNDINGS = new Rounding[] {
        Rounding.builder(Rounding.DateTimeUnit.MONTH_OF_YEAR).build(),
        Rounding.builder(Rounding.DateTimeUnit.WEEK_OF_WEEKYEAR).build(),
        Rounding.builder(Rounding.DateTimeUnit.DAY_OF_MONTH).build(),
        Rounding.builder(TimeValue.timeValueHours(12)).build(),
        Rounding.builder(TimeValue.timeValueHours(3)).build(),
        Rounding.builder(TimeValue.timeValueHours(1)).build(),
        Rounding.builder(TimeValue.timeValueMinutes(30)).build(),
        Rounding.builder(TimeValue.timeValueMinutes(10)).build(),
        Rounding.builder(TimeValue.timeValueMinutes(5)).build(),
        Rounding.builder(TimeValue.timeValueMinutes(1)).build(),
        Rounding.builder(TimeValue.timeValueSeconds(30)).build(),
        Rounding.builder(TimeValue.timeValueSeconds(10)).build(),
        Rounding.builder(TimeValue.timeValueSeconds(5)).build(),
        Rounding.builder(TimeValue.timeValueSeconds(1)).build(),
        Rounding.builder(TimeValue.timeValueMillis(100)).build(),
        Rounding.builder(TimeValue.timeValueMillis(50)).build(),
        Rounding.builder(TimeValue.timeValueMillis(10)).build(),
        Rounding.builder(TimeValue.timeValueMillis(1)).build(), };

    private static final ZoneId DEFAULT_TZ = ZoneOffset.UTC; // TODO: plug in the config

    private final Expression field;
    private final Expression buckets;
    private final Expression from;
    private final Expression to;

    @FunctionInfo(
        returnType = { "double", "date" },
        description = """
            Creates groups of values - buckets - out of a datetime or numeric input.
            The size of the buckets can either be provided directly, or chosen based on a recommended count and values range.""",
        examples = {
            @Example(
                description = """
                    `BUCKET` can work in two modes: one in which the size of the bucket is computed
                    based on a buckets count recommendation (four parameters) and a range, and
                    another in which the bucket size is provided directly (two parameters).

                    Using a target number of buckets, a start of a range, and an end of a range,
                    `BUCKET` picks an appropriate bucket size to generate the target number of buckets or fewer.
                    For example, asking for at most 20 buckets over a year results in monthly buckets:""",
                file = "bucket",
                tag = "docsBucketMonth",
                explanation = """
                    The goal isn't to provide *exactly* the target number of buckets,
                    it's to pick a range that people are comfortable with that provides at most the target number of buckets."""
            ),
            @Example(
                description = "Combine `BUCKET` with an <<esql-agg-functions,aggregation>> to create a histogram:",
                file = "bucket",
                tag = "docsBucketMonthlyHistogram",
                explanation = """
                    NOTE: `BUCKET` does not create buckets that don't match any documents.
                    That's why this example is missing `1985-03-01` and other dates."""
            ),
            @Example(
                description = """
                    Asking for more buckets can result in a smaller range.
                    For example, asking for at most 100 buckets in a year results in weekly buckets:""",
                file = "bucket",
                tag = "docsBucketWeeklyHistogram",
                explanation = """
                    NOTE: `BUCKET` does not filter any rows. It only uses the provided range to pick a good bucket size.
                    For rows with a value outside of the range, it returns a bucket value that corresponds to a bucket outside the range.
                    Combine`BUCKET` with <<esql-where>> to filter rows."""
            ),
            @Example(description = """
                If the desired bucket size is known in advance, simply provide it as the second
                argument, leaving the range out:""", file = "bucket", tag = "docsBucketWeeklyHistogramWithSpan", explanation = """
                NOTE: When providing the bucket size as the second parameter, it must be a time
                duration or date period."""),
            @Example(
                description = "`BUCKET` can also operate on numeric fields. For example, to create a salary histogram:",
                file = "bucket",
                tag = "docsBucketNumeric",
                explanation = """
                    Unlike the earlier example that intentionally filters on a date range, you rarely want to filter on a numeric range.
                    You have to find the `min` and `max` separately. {esql} doesn't yet have an easy way to do that automatically."""
            ),
            @Example(description = """
                The range can be omitted if the desired bucket size is known in advance. Simply
                provide it as the second argument:""", file = "bucket", tag = "docsBucketNumericWithSpan", explanation = """
                NOTE: When providing the bucket size as the second parameter, it must be
                of a floating point type."""),
            @Example(
                description = "Create hourly buckets for the last 24 hours, and calculate the number of events per hour:",
                file = "bucket",
                tag = "docsBucketLast24hr"
            ),
            @Example(
                description = "Create monthly buckets for the year 1985, and calculate the average salary by hiring month",
                file = "bucket",
                tag = "bucket_in_agg"
            ),
            @Example(
                description = """
                    `BUCKET` may be used in both the aggregating and grouping part of the
                    <<esql-stats-by, STATS ... BY ...>> command provided that in the aggregating
                    part the function is referenced by an alias defined in the
                    grouping part, or that it is invoked with the exact same expression:""",
                file = "bucket",
                tag = "reuseGroupingFunctionWithExpression"
            ) }
    )
    public Bucket(
        Source source,
        @Param(
            name = "field",
            type = { "integer", "long", "double", "date" },
            description = "Numeric or date expression from which to derive buckets."
        ) Expression field,
        @Param(
            name = "buckets",
            type = { "integer", "double", "date_period", "time_duration" },
            description = "Target number of buckets."
        ) Expression buckets,
        @Param(
            name = "from",
            type = { "integer", "long", "double", "date" },
            optional = true,
            description = "Start of the range. Can be a number or a date expressed as a string."
        ) Expression from,
        @Param(
            name = "to",
            type = { "integer", "long", "double", "date" },
            optional = true,
            description = "End of the range. Can be a number or a date expressed as a string."
        ) Expression to
    ) {
        super(source, from != null && to != null ? List.of(field, buckets, from, to) : List.of(field, buckets));
        this.field = field;
        this.buckets = buckets;
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean foldable() {
        return field.foldable() && buckets.foldable() && (from == null || from.foldable()) && (to == null || to.foldable());
    }

    @Override
    public ExpressionEvaluator.Factory toEvaluator(Function<Expression, ExpressionEvaluator.Factory> toEvaluator) {
        if (field.dataType() == DataTypes.DATETIME) {
            Rounding.Prepared preparedRounding;
            if (buckets.dataType().isInteger()) {
                int b = ((Number) buckets.fold()).intValue();
                long f = foldToLong(from);
                long t = foldToLong(to);
                preparedRounding = new DateRoundingPicker(b, f, t).pickRounding().prepareForUnknown();
            } else {
                assert EsqlDataTypes.isTemporalAmount(buckets.dataType()) : "Unexpected span data type [" + buckets.dataType() + "]";
                preparedRounding = DateTrunc.createRounding(buckets.fold(), DEFAULT_TZ);
            }
            return DateTrunc.evaluator(source(), toEvaluator.apply(field), preparedRounding);
        }
        if (field.dataType().isNumeric()) {
            double roundTo;
            if (from != null) {
                int b = ((Number) buckets.fold()).intValue();
                double f = ((Number) from.fold()).doubleValue();
                double t = ((Number) to.fold()).doubleValue();
                roundTo = pickRounding(b, f, t);
            } else {
                assert buckets.dataType().isRational() : "Unexpected rounding data type [" + buckets.dataType() + "]";
                roundTo = ((Number) buckets.fold()).doubleValue();
            }
            Literal rounding = new Literal(source(), roundTo, DataTypes.DOUBLE);

            // We could make this more efficient, either by generating the evaluators with byte code or hand rolling this one.
            Div div = new Div(source(), field, rounding);
            Floor floor = new Floor(source(), div);
            Mul mul = new Mul(source(), floor, rounding);
            return toEvaluator.apply(mul);
        }
        throw EsqlIllegalArgumentException.illegalDataType(field.dataType());
    }

    private record DateRoundingPicker(int buckets, long from, long to) {
        Rounding pickRounding() {
            Rounding prev = LARGEST_HUMAN_DATE_ROUNDING;
            for (Rounding r : HUMAN_DATE_ROUNDINGS) {
                if (roundingIsOk(r)) {
                    prev = r;
                } else {
                    return prev;
                }
            }
            return prev;
        }

        /**
         * True if the rounding produces less than or equal to the requested number of buckets.
         */
        boolean roundingIsOk(Rounding rounding) {
            Rounding.Prepared r = rounding.prepareForUnknown();
            long bucket = r.round(from);
            int used = 0;
            while (used < buckets) {
                bucket = r.nextRoundingValue(bucket);
                used++;
                if (bucket > to) {
                    return true;
                }
            }
            return false;
        }
    }

    private double pickRounding(int buckets, double from, double to) {
        double precise = (to - from) / buckets;
        double nextPowerOfTen = Math.pow(10, Math.ceil(Math.log10(precise)));
        double halfPower = nextPowerOfTen / 2;
        return precise < halfPower ? halfPower : nextPowerOfTen;
    }

    // supported parameter type combinations (1st, 2nd, 3rd, 4th):
    // datetime, integer, string/datetime, string/datetime
    // datetime, rounding/duration, -, -
    // numeric, integer, numeric, numeric
    // numeric, double, -, -
    @Override
    protected TypeResolution resolveType() {
        if (childrenResolved() == false) {
            return new TypeResolution("Unresolved children");
        }
        var fieldType = field.dataType();
        var bucketsType = buckets.dataType();
        if (fieldType == DataTypes.NULL || bucketsType == DataTypes.NULL) {
            return TypeResolution.TYPE_RESOLVED;
        }

        if (fieldType == DataTypes.DATETIME) {
            TypeResolution resolution = isType(
                buckets,
                dt -> dt.isInteger() || EsqlDataTypes.isTemporalAmount(dt),
                sourceText(),
                SECOND,
                "integral",
                "date_period",
                "time_duration"
            );
            return bucketsType.isInteger()
                ? resolution.and(checkArgsCount(4))
                    .and(() -> isStringOrDate(from, sourceText(), THIRD))
                    .and(() -> isStringOrDate(to, sourceText(), FOURTH))
                : resolution.and(checkArgsCount(2)); // temporal amount
        }
        if (fieldType.isNumeric()) {
            return bucketsType.isInteger()
                ? checkArgsCount(4).and(() -> isNumeric(from, sourceText(), THIRD)).and(() -> isNumeric(to, sourceText(), FOURTH))
                : isNumeric(buckets, sourceText(), SECOND).and(checkArgsCount(2));
        }
        return isType(field, e -> false, sourceText(), FIRST, "datetime", "numeric");
    }

    private TypeResolution checkArgsCount(int expectedCount) {
        String expected = null;
        if (expectedCount == 2 && (from != null || to != null)) {
            expected = "two";
        } else if (expectedCount == 4 && (from == null || to == null)) {
            expected = "four";
        } else if ((from == null && to != null) || (from != null && to == null)) {
            expected = "two or four";
        }

        return expected == null
            ? TypeResolution.TYPE_RESOLVED
            : new TypeResolution(
                format(
                    null,
                    "function expects exactly {} arguments when the first one is of type [{}] and the second of type [{}]",
                    expected,
                    field.dataType(),
                    buckets.dataType()
                )
            );
    }

    private static TypeResolution isStringOrDate(Expression e, String operationName, TypeResolutions.ParamOrdinal paramOrd) {
        return TypeResolutions.isType(
            e,
            exp -> DataTypes.isString(exp) || DataTypes.isDateTime(exp),
            operationName,
            paramOrd,
            "datetime",
            "string"
        );
    }

    @Override
    public void validate(Failures failures) {
        String operation = sourceText();

        failures.add(isFoldable(buckets, operation, SECOND))
            .add(from != null ? isFoldable(from, operation, THIRD) : null)
            .add(to != null ? isFoldable(to, operation, FOURTH) : null);
    }

    private long foldToLong(Expression e) {
        Object value = Foldables.valueOf(e);
        return DataTypes.isDateTime(e.dataType()) ? ((Number) value).longValue() : dateTimeToLong(((BytesRef) value).utf8ToString());
    }

    @Override
    public DataTypes dataType() {
        if (field.dataType().isNumeric()) {
            return DataTypes.DOUBLE;
        }
        return field.dataType();
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        Expression from = newChildren.size() > 2 ? newChildren.get(2) : null;
        Expression to = newChildren.size() > 3 ? newChildren.get(3) : null;
        return new Bucket(source(), newChildren.get(0), newChildren.get(1), from, to);
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, Bucket::new, field, buckets, from, to);
    }

    public Expression field() {
        return field;
    }

    public Expression buckets() {
        return buckets;
    }

    public Expression from() {
        return from;
    }

    public Expression to() {
        return to;
    }

    @Override
    public String toString() {
        return "Bucket{" + "field=" + field + ", buckets=" + buckets + ", from=" + from + ", to=" + to + '}';
    }
}
