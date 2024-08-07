/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.convert;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.expression.function.AbstractScalarFunctionTestCase;
import org.elasticsearch.xpack.esql.expression.function.FunctionName;
import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.esql.EsqlTestUtils.randomLiteral;
import static org.hamcrest.Matchers.equalTo;

@FunctionName("to_base64")
public class ToBase64Tests extends AbstractScalarFunctionTestCase {
    public ToBase64Tests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        List<TestCaseSupplier> suppliers = new ArrayList<>();
        suppliers.add(new TestCaseSupplier(List.of(DataType.KEYWORD), () -> {
            BytesRef input = (BytesRef) randomLiteral(DataType.KEYWORD).value();
            return new TestCaseSupplier.TestCase(
                List.of(new TestCaseSupplier.TypedData(input, DataType.KEYWORD, "string")),
                "ToBase64Evaluator[field=Attribute[channel=0]]",
                DataType.KEYWORD,
                equalTo(new BytesRef(Base64.getEncoder().encode(input.utf8ToString().getBytes(StandardCharsets.UTF_8))))
            );
        }));

        suppliers.add(new TestCaseSupplier(List.of(DataType.TEXT), () -> {
            BytesRef input = (BytesRef) randomLiteral(DataType.TEXT).value();
            return new TestCaseSupplier.TestCase(
                List.of(new TestCaseSupplier.TypedData(input, DataType.TEXT, "string")),
                "ToBase64Evaluator[field=Attribute[channel=0]]",
                DataType.KEYWORD,
                equalTo(new BytesRef(Base64.getEncoder().encode(input.utf8ToString().getBytes(StandardCharsets.UTF_8))))
            );
        }));

        return parameterSuppliersFromTypedDataWithDefaultChecks(true, suppliers, (v, p) -> "string");
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new ToBase64(source, args.get(0));
    }
}
