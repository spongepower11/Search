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

package org.elasticsearch.search.aggregations.reducers.smooth;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.reducers.ReducerBuilder;
import org.elasticsearch.search.aggregations.reducers.smooth.models.SmoothingModelBuilder;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.search.aggregations.reducers.BucketHelpers.GapPolicy;

public class SmoothBuilder extends ReducerBuilder<SmoothBuilder> {

    private String format;
    private GapPolicy gapPolicy;
    private SmoothingModelBuilder modelBuilder;
    private Integer window;

    public SmoothBuilder(String name) {
        super(name, SmoothReducer.TYPE.name());
    }

    public SmoothBuilder format(String format) {
        this.format = format;
        return this;
    }

    public SmoothBuilder gapPolicy(GapPolicy gapPolicy) {
        this.gapPolicy = gapPolicy;
        return this;
    }

    public SmoothBuilder modelBuilder(SmoothingModelBuilder modelBuilder) {
        this.modelBuilder = modelBuilder;
        return this;
    }

    public SmoothBuilder window(int window) {
        this.window = window;
        return this;
    }


    @Override
    protected XContentBuilder internalXContent(XContentBuilder builder, Params params) throws IOException {
        if (format != null) {
            builder.field(SmoothParser.FORMAT.getPreferredName(), format);
        }
        if (gapPolicy != null) {
            builder.field(SmoothParser.GAP_POLICY.getPreferredName(), gapPolicy.getName());
        }
        if (modelBuilder != null) {
            modelBuilder.toXContent(builder);
        }
        if (window != null) {
            builder.field(SmoothParser.WINDOW.getPreferredName(), window);
        }
        return builder;
    }

}
