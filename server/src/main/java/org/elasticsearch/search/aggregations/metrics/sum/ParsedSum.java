/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations.metrics.sum;

import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;

import java.io.IOException;

public class ParsedSum extends ParsedSingleValueNumericMetricsAggregation implements Sum {

    @Override
    public double getValue() {
        return value();
    }

    @Override
    public String getType() {
        return SumAggregationBuilder.NAME;
    }

    @Override
    protected XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        builder.field(CommonFields.VALUE.getPreferredName(), value);
        if (valueAsString != null) {
            builder.field(CommonFields.VALUE_AS_STRING.getPreferredName(), valueAsString);
        }
        return builder;
    }

    private static final ObjectParser<ParsedSum, Void> PARSER = new ObjectParser<>(ParsedSum.class.getSimpleName(), true, ParsedSum::new);

    static {
        declareSingleValueFields(PARSER, Double.NEGATIVE_INFINITY);
    }

    public static ParsedSum fromXContent(XContentParser parser, final String name) {
        ParsedSum sum = PARSER.apply(parser, null);
        sum.setName(name);
        return sum;
    }
}