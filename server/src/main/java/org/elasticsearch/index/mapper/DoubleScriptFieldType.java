/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.time.DateMathParser;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.index.fielddata.DoubleScriptFieldData;
import org.elasticsearch.index.mapper.NumberFieldMapper.NumberType;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.script.CompositeFieldScript;
import org.elasticsearch.script.DoubleFieldScript;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.field.DoubleDocValuesField;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.search.runtime.DoubleScriptFieldExistsQuery;
import org.elasticsearch.search.runtime.DoubleScriptFieldRangeQuery;
import org.elasticsearch.search.runtime.DoubleScriptFieldTermQuery;
import org.elasticsearch.search.runtime.DoubleScriptFieldTermsQuery;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DoubleScriptFieldType extends AbstractScriptFieldType<DoubleFieldScript.LeafFactory> {

    public static final RuntimeField.Parser PARSER = new RuntimeField.Parser(Builder::new);

    private static class Builder extends AbstractScriptFieldType.Builder<DoubleFieldScript.Factory> {
        Builder(String name) {
            super(name, DoubleFieldScript.CONTEXT);
        }

        @Override
        AbstractScriptFieldType<?> createFieldType(DoubleFieldScript.Factory factory, Script script, Map<String, String> meta) {
            return new DoubleScriptFieldType(factory, script, meta);
        }

        @Override
        DoubleFieldScript.Factory getParseFromSourceFactory() {
            return DoubleFieldScript.PARSE_FROM_SOURCE;
        }

        @Override
        DoubleFieldScript.Factory getCompositeLeafFactory(Function<SearchLookup, CompositeFieldScript.LeafFactory> parentScriptFactory) {
            return DoubleFieldScript.leafAdapter(parentScriptFactory);
        }
    }

    public static RuntimeField sourceOnly(String name) {
        return new Builder(name).createRuntimeField(DoubleFieldScript.PARSE_FROM_SOURCE);
    }

    DoubleScriptFieldType(DoubleFieldScript.Factory scriptFactory, Script script, Map<String, String> meta) {
        super(
            (name, searchLookup) -> scriptFactory.newFactory(name, script.getParams(), searchLookup),
            script,
            scriptFactory.isResultDeterministic(),
            meta
        );
    }

    @Override
    public String typeName() {
        return NumberType.DOUBLE.typeName();
    }

    @Override
    public Object valueForDisplay(Object value) {
        return value; // These should come back as a Double
    }

    @Override
    public DocValueFormat docValueFormat(String name, String format, ZoneId timeZone) {
        checkNoTimeZone(name, timeZone);
        if (format == null) {
            return DocValueFormat.RAW;
        }
        return new DocValueFormat.Decimal(format);
    }

    @Override
    public DoubleScriptFieldData.Builder fielddataBuilder(
        String name,
        String fullyQualifiedIndexName,
        Supplier<SearchLookup> searchLookup
    ) {
        return new DoubleScriptFieldData.Builder(name, leafFactory(name, searchLookup.get()), DoubleDocValuesField::new);
    }

    @Override
    public Query existsQuery(String name, SearchExecutionContext context) {
        applyScriptContext(context);
        return new DoubleScriptFieldExistsQuery(script, leafFactory(name, context), name);
    }

    @Override
    public Query rangeQuery(
        String name,
        Object lowerTerm,
        Object upperTerm,
        boolean includeLower,
        boolean includeUpper,
        ZoneId timeZone,
        DateMathParser parser,
        SearchExecutionContext context
    ) {
        applyScriptContext(context);
        return NumberType.doubleRangeQuery(
            lowerTerm,
            upperTerm,
            includeLower,
            includeUpper,
            (l, u) -> new DoubleScriptFieldRangeQuery(script, leafFactory(name, context), name, l, u)
        );
    }

    @Override
    public Query termQuery(String name, Object value, SearchExecutionContext context) {
        applyScriptContext(context);
        return new DoubleScriptFieldTermQuery(script, leafFactory(name, context), name, NumberType.objectToDouble(value));
    }

    @Override
    public Query termsQuery(String name, Collection<?> values, SearchExecutionContext context) {
        if (values.isEmpty()) {
            return Queries.newMatchAllQuery();
        }
        Set<Long> terms = Sets.newHashSetWithExpectedSize(values.size());
        for (Object value : values) {
            terms.add(Double.doubleToLongBits(NumberType.objectToDouble(value)));
        }
        applyScriptContext(context);
        return new DoubleScriptFieldTermsQuery(script, leafFactory(name, context), name, terms);
    }
}
