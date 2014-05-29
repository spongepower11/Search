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
package org.elasticsearch.search.aggregations.metrics.valuecount;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.AggregationStreams;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation;

import java.io.IOException;
import java.util.List;

/**
 * An internal implementation of {@link ValueCount}.
 */
public class InternalValueCount extends InternalNumericMetricsAggregation implements ValueCount {

    public static final Type TYPE = new Type("value_count", "vcount");

    private static final AggregationStreams.Stream STREAM = new AggregationStreams.Stream() {
        @Override
        public InternalValueCount readResult(StreamInput in) throws IOException {
            InternalValueCount count = new InternalValueCount();
            count.readFrom(in);
            return count;
        }
    };

    public static void registerStreams() {
        AggregationStreams.registerStream(STREAM, TYPE.stream());
    }

    private long value;

    InternalValueCount() {} // for serialization

    public InternalValueCount(String name, long value) {
        super(name);
        this.value = value;
    }

    @Override
    public long getValue() {
        return value;
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public InternalAggregation reduce(ReduceContext reduceContext) {
        List<InternalAggregation> aggregations = reduceContext.aggregations();
        if (aggregations.size() == 1) {
            return aggregations.get(0);
        }
        InternalValueCount reduced = null;
        for (InternalAggregation aggregation : aggregations) {
            if (reduced == null) {
                reduced = (InternalValueCount) aggregation;
            } else {
                reduced.value += ((InternalValueCount) aggregation).value;
            }
        }
        return reduced;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        name = in.readString();
        value = in.readVLong();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeVLong(value);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.startObject(name)
                .field(CommonFields.VALUE, value)
                .endObject();
    }

    @Override
    public String toString() {
        return "count[" + value + "]";
    }
}
