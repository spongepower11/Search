package org.elasticsearch.script.expression;

import org.apache.lucene.expressions.Bindings;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.elasticsearch.index.fielddata.IndexFieldData;

import java.util.HashMap;
import java.util.Map;


class ExpressionScriptBindings extends Bindings {

    Map<String, ValueSource> variables = new HashMap<>();

    void addConstant(String variable, double value) {
        variables.put(variable, new DoubleConstValueSource(value));
    }

    void addField(String variable, IndexFieldData<?> fieldData) {
        variables.put(variable, new ExpressionScriptValueSource(fieldData));
    }

    @Override
    public ValueSource getValueSource(String variable) {
        if (variable.equals("_score")) {
            return getScoreValueSource();
        } else {
            return variables.get(variable);
        }
    }
}
