/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.function.aggregate;

import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.Expressions.ParamOrdinal;
import org.elasticsearch.xpack.ql.expression.Foldables;
import org.elasticsearch.xpack.ql.tree.NodeInfo;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;
import org.elasticsearch.xpack.sql.type.SqlDataTypeConverter;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.elasticsearch.xpack.ql.expression.TypeResolutions.isFoldable;
import static org.elasticsearch.xpack.ql.expression.TypeResolutions.isNumeric;

public class Percentile extends HasPercentilesConfig {

    private final Expression percent;

    public Percentile(Source source, Expression field, Expression percent, Expression method, Expression methodParameter) {
        super(source, field, singletonList(percent), method, methodParameter);
        this.percent = percent;
    }

    @Override
    protected NodeInfo<Percentile> info() {
        return NodeInfo.create(this, Percentile::new, field(), percent, method(), methodParameter());
    }

    @Override
    public Percentile replaceChildren(List<Expression> newChildren) {
        if (newChildren.size() != 2) {
            throw new IllegalArgumentException("expected [2] children but received [" + newChildren.size() + "]");
        }
        return new Percentile(source(), newChildren.get(0), newChildren.get(1), method(), methodParameter());
    }

    @Override
    protected TypeResolution resolveType() {
        TypeResolution resolution = isFoldable(percent, sourceText(), ParamOrdinal.SECOND);
        if (resolution.unresolved()) {
            return resolution;
        }

        resolution = super.resolveType();
        if (resolution.unresolved()) {
            return resolution;
        }

        return isNumeric(percent, sourceText(), ParamOrdinal.DEFAULT);
    }

    public Expression percent() {
        return percent;
    }

    @Override
    public DataType dataType() {
        return DataTypes.DOUBLE;
    }

    @Override
    public String innerName() {
        Double value = (Double) SqlDataTypeConverter.convert(Foldables.valueOf(percent), DataTypes.DOUBLE);
        return Double.toString(value);
    }


}
