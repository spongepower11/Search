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

package org.elasticsearch.cluster.factory;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContent.Params;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.text.ParseException;
import java.util.EnumSet;

public class LongClusterStatePartFactory extends ClusterStatePartFactory<Long> {
    public LongClusterStatePartFactory(String type) {
        super(type);
    }

    public LongClusterStatePartFactory(String type, EnumSet<XContentContext> context) {
        super(type, context);
    }

    @Override
    public Long readFrom(StreamInput in, String partName, @Nullable DiscoveryNode localNode) throws IOException {
        return in.readLong();
    }

    @Override
    public void writeTo(Long part, StreamOutput out) throws IOException {
        out.writeLong(part);
    }

    @Override
    public Long fromXContent(XContentParser parser, String partName, @Nullable DiscoveryNode localNode) throws IOException {
        return parser.longValue();
    }

    @Override
    public void toXContent(Long part, XContentBuilder builder, Params params) throws IOException {
        builder.value(part);
    }
}