/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar;

import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.Literal;
import org.elasticsearch.xpack.esql.core.expression.TypeResolutions;
import org.elasticsearch.xpack.esql.core.tree.Location;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.core.type.DataTypes;
import org.elasticsearch.xpack.esql.expression.function.AbstractFunctionTestCase;
import org.elasticsearch.xpack.esql.type.EsqlDataTypes;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;

/**
 * Base class for function tests.
 * @deprecated extends from {@link AbstractFunctionTestCase} instead
 * and {@link AbstractFunctionTestCase#errorsForCasesWithoutExamples}.
 */
@Deprecated
public abstract class AbstractScalarFunctionTestCase extends AbstractFunctionTestCase {
    /**
     * Describe supported arguments. Build each argument with
     * {@link #required} or {@link #optional}.
     */
    protected abstract List<ArgumentSpec> argSpec();

    /**
     * The data type that applying this function to arguments of this type should produce.
     */
    protected abstract DataTypes expectedType(List<DataTypes> argTypes);

    /**
     * Define a required argument.
     */
    protected final ArgumentSpec required(DataTypes... validTypes) {
        return new ArgumentSpec(false, withNullAndSorted(validTypes));
    }

    /**
     * Define an optional argument.
     */
    protected final ArgumentSpec optional(DataTypes... validTypes) {
        return new ArgumentSpec(true, withNullAndSorted(validTypes));
    }

    private Set<DataTypes> withNullAndSorted(DataTypes[] validTypes) {
        Set<DataTypes> realValidTypes = new LinkedHashSet<>();
        Arrays.stream(validTypes).sorted(Comparator.comparing(DataTypes::nameUpper)).forEach(realValidTypes::add);
        realValidTypes.add(DataTypes.NULL);
        return realValidTypes;
    }

    public Set<DataTypes> sortedTypesSet(DataTypes[] validTypes, DataTypes... additionalTypes) {
        Set<DataTypes> mergedSet = new LinkedHashSet<>();
        Stream.concat(Stream.of(validTypes), Stream.of(additionalTypes))
            .sorted(Comparator.comparing(DataTypes::nameUpper))
            .forEach(mergedSet::add);
        return mergedSet;
    }

    /**
     * All integer types (long, int, short, byte). For passing to {@link #required} or {@link #optional}.
     */
    protected static DataTypes[] integers() {
        return DataTypes.types().stream().filter(DataTypes::isInteger).toArray(DataTypes[]::new);
    }

    /**
     * All rational types (double, float, whatever). For passing to {@link #required} or {@link #optional}.
     */
    protected static DataTypes[] rationals() {
        return DataTypes.types().stream().filter(DataTypes::isRational).toArray(DataTypes[]::new);
    }

    /**
     * All numeric types (integers and rationals.) For passing to {@link #required} or {@link #optional}.
     */
    protected static DataTypes[] numerics() {
        return DataTypes.types().stream().filter(DataTypes::isNumeric).toArray(DataTypes[]::new);
    }

    protected final DataTypes[] representableNumerics() {
        // TODO numeric should only include representable numbers but that is a change for a followup
        return DataTypes.types().stream().filter(DataTypes::isNumeric).filter(EsqlDataTypes::isRepresentable).toArray(DataTypes[]::new);
    }

    protected record ArgumentSpec(boolean optional, Set<DataTypes> validTypes) {}

    public final void testResolveType() {
        List<ArgumentSpec> specs = argSpec();
        for (int mutArg = 0; mutArg < specs.size(); mutArg++) {
            for (DataTypes mutArgType : DataTypes.types()) {
                List<Expression> args = new ArrayList<>(specs.size());
                for (int arg = 0; arg < specs.size(); arg++) {
                    if (mutArg == arg) {
                        args.add(new Literal(new Source(Location.EMPTY, "arg" + arg), "", mutArgType));
                    } else {
                        args.add(new Literal(new Source(Location.EMPTY, "arg" + arg), "", specs.get(arg).validTypes.iterator().next()));
                    }
                }
                assertResolution(specs, args, mutArg, mutArgType, specs.get(mutArg).validTypes.contains(mutArgType));
                int optionalIdx = specs.size() - 1;
                while (optionalIdx > 0 && specs.get(optionalIdx).optional()) {
                    args.remove(optionalIdx--);
                    assertResolution(
                        specs,
                        args,
                        mutArg,
                        mutArgType,
                        args.size() <= mutArg || specs.get(mutArg).validTypes.contains(mutArgType)
                    );
                }
            }
        }
    }

    private void assertResolution(List<ArgumentSpec> specs, List<Expression> args, int mutArg, DataTypes mutArgType, boolean shouldBeValid) {
        Expression exp = build(new Source(Location.EMPTY, "exp"), args);
        logger.info("checking {} is {}", exp.nodeString(), shouldBeValid ? "valid" : "invalid");
        if (shouldBeValid) {
            assertResolveTypeValid(exp, expectedType(args.stream().map(Expression::dataType).toList()));
            return;
        }
        Expression.TypeResolution resolution = exp.typeResolved();
        assertFalse(exp.nodeString(), resolution.resolved());
        assertThat(exp.nodeString(), resolution.message(), badTypeError(specs, mutArg, mutArgType));
    }

    protected Matcher<String> badTypeError(List<ArgumentSpec> spec, int badArgPosition, DataTypes badArgType) {
        String ordinal = spec.size() == 1
            ? ""
            : TypeResolutions.ParamOrdinal.fromIndex(badArgPosition).name().toLowerCase(Locale.ROOT) + " ";
        return equalTo(
            ordinal
                + "argument of [exp] must be ["
                + expectedTypeName(spec.get(badArgPosition).validTypes())
                + "], found value [arg"
                + badArgPosition
                + "] type ["
                + badArgType.typeName()
                + "]"
        );
    }

    private String expectedTypeName(Set<DataTypes> validTypes) {
        List<DataTypes> withoutNull = validTypes.stream().filter(t -> t != DataTypes.NULL).toList();
        if (withoutNull.equals(Arrays.asList(strings()))) {
            return "string";
        }
        if (withoutNull.equals(Arrays.asList(integers())) || withoutNull.equals(List.of(DataTypes.INTEGER))) {
            return "integer";
        }
        if (withoutNull.equals(Arrays.asList(rationals()))) {
            return "double";
        }
        if (withoutNull.equals(Arrays.asList(numerics())) || withoutNull.equals(Arrays.asList(representableNumerics()))) {
            return "numeric";
        }
        if (withoutNull.equals(List.of(DataTypes.DATETIME))) {
            return "datetime";
        }
        if (withoutNull.equals(List.of(DataTypes.IP))) {
            return "ip";
        }
        List<DataTypes> negations = Stream.concat(Stream.of(numerics()), Stream.of(DataTypes.DATE_PERIOD, DataTypes.TIME_DURATION))
            .sorted(Comparator.comparing(DataTypes::nameUpper))
            .toList();
        if (withoutNull.equals(negations)) {
            return "numeric, date_period or time_duration";
        }
        if (validTypes.equals(Set.copyOf(Arrays.asList(representableTypes())))) {
            return "representable";
        }
        if (validTypes.equals(Set.copyOf(Arrays.asList(representableNonSpatialTypes())))) {
            return "representableNonSpatial";
        }
        throw new IllegalArgumentException("can't guess expected type for " + validTypes);
    }
}
