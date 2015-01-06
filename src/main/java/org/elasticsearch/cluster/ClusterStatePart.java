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

package org.elasticsearch.cluster;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;

/**
 * Part of the cluster state
 *
 * Each cluster state part is serializable in both binary and json formats and can calculate differences with the
 * previous version of the same part.
 */
public interface ClusterStatePart extends ToXContent {

    public static final String CONTEXT_MODE_PARAM = "context_mode";

    /**
     * Represents the context in which this part should be serialized as JSON
     */
    public enum XContentContext {
        /* A part should be returned as part of API call */
        API,

        /* A part should be stored by gateway as a persistent cluster state */
        GATEWAY,

        /* A part should be stored as part of a snapshot */
        SNAPSHOT;
    }

    public static EnumSet<XContentContext> NONE = EnumSet.noneOf(XContentContext.class);
    public static EnumSet<XContentContext> API = EnumSet.of(XContentContext.API);
    public static EnumSet<XContentContext> API_GATEWAY = EnumSet.of(XContentContext.API, XContentContext.GATEWAY);
    public static EnumSet<XContentContext> API_SNAPSHOT = EnumSet.of(XContentContext.API, XContentContext.SNAPSHOT);
    public static EnumSet<XContentContext> API_GATEWAY_SNAPSHOT = EnumSet.of(XContentContext.API, XContentContext.GATEWAY, XContentContext.SNAPSHOT);
    public static EnumSet<XContentContext> GATEWAY = EnumSet.of(XContentContext.GATEWAY);
    public static EnumSet<XContentContext> GATEWAY_SNAPSHOT = EnumSet.of(XContentContext.GATEWAY, XContentContext.SNAPSHOT);

    /**
     * Writes part to output stream
     */
    void writeTo(StreamOutput out) throws IOException;

    /**
     * Returns a set of contexts in which this part should be serialized as JSON
     */
    EnumSet<XContentContext> context();

    /**
     * Returns part type
     */
    String partType();

    interface Factory<T extends ClusterStatePart> {

        Diff<T> diff(T before, T after);

        Diff<T> readDiffFrom(StreamInput in, LocalContext context) throws IOException;

        T readFrom(StreamInput in, LocalContext context) throws IOException;

        T fromXContent(XContentParser parser, LocalContext context) throws IOException;

        T fromMap(Map<String, Object> map, LocalContext context) throws IOException;

        String partType();

        Version addedIn();

    }

    interface Diff<T extends ClusterStatePart> {

        T apply(T part);

        void writeTo(StreamOutput out) throws IOException;
    }
}
