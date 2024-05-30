/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.spatial;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataTypes;
import org.elasticsearch.xpack.esql.expression.function.FunctionName;
import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@FunctionName("st_intersects")
public class SpatialIntersectsTests extends SpatialRelatesFunctionTestCase {
    public SpatialIntersectsTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        List<TestCaseSupplier> suppliers = new ArrayList<>();
        DataTypes[] geoDataTypes = { DataTypes.GEO_POINT, DataTypes.GEO_SHAPE };
        SpatialRelatesFunctionTestCase.addSpatialCombinations(suppliers, geoDataTypes);
        DataTypes[] cartesianDataTypes = { DataTypes.CARTESIAN_POINT, DataTypes.CARTESIAN_SHAPE };
        SpatialRelatesFunctionTestCase.addSpatialCombinations(suppliers, cartesianDataTypes);
        return parameterSuppliersFromTypedData(
            errorsForCasesWithoutExamples(anyNullIsNull(true, suppliers), SpatialIntersectsTests::typeErrorMessage)
        );
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new SpatialIntersects(source, args.get(0), args.get(1));
    }
}
