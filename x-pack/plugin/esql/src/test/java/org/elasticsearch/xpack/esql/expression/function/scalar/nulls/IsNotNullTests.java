/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.nulls;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.predicate.nulls.IsNotNull;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataTypes;
import org.elasticsearch.xpack.esql.expression.function.AbstractFunctionTestCase;
import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;
import org.elasticsearch.xpack.esql.type.EsqlDataTypes;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.equalTo;

public class IsNotNullTests extends AbstractFunctionTestCase {
    public IsNotNullTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        List<TestCaseSupplier> suppliers = new ArrayList<>();
        for (DataTypes type : DataTypes.types()) {
            if (false == EsqlDataTypes.isRepresentable(type)) {
                continue;
            }
            if (type != DataTypes.NULL) {
                suppliers.add(
                    new TestCaseSupplier(
                        "non-null " + type.typeName(),
                        List.of(type),
                        () -> new TestCaseSupplier.TestCase(
                            List.of(new TestCaseSupplier.TypedData(randomLiteral(type).value(), type, "v")),
                            "IsNotNullEvaluator[field=Attribute[channel=0]]",
                            DataTypes.BOOLEAN,
                            equalTo(true)
                        )
                    )
                );
            }
            suppliers.add(
                new TestCaseSupplier(
                    "null " + type.typeName(),
                    List.of(type),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(new TestCaseSupplier.TypedData(null, type, "v")),
                        "IsNotNullEvaluator[field=Attribute[channel=0]]",
                        DataTypes.BOOLEAN,
                        equalTo(false)
                    )
                )
            );
        }
        return parameterSuppliersFromTypedData(failureForCasesWithoutExamples(suppliers));
    }

    @Override
    protected void assertSimpleWithNulls(List<Object> data, Block value, int nullBlock) {
        assertFalse(((BooleanBlock) value).asVector().getBoolean(0));
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new IsNotNull(Source.EMPTY, args.get(0));
    }

    @Override
    protected Matcher<Object> allNullsMatcher() {
        return equalTo(false);
    }
}
