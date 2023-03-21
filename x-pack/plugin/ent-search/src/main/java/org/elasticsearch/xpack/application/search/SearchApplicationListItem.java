/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.search;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * This class is used for returning information for lists of search applications, to avoid including all
 * {@link SearchApplication} information which can be retrieved using subsequent Get Search Application requests.
 */
public class SearchApplicationListItem implements Writeable, ToXContentObject {

    public static final ParseField NAME_FIELD = new ParseField("name");
    public static final ParseField INDICES_FIELD = new ParseField("indices");
    public static final ParseField ANALYTICS_COLLECTION_NAME_FIELD = new ParseField("analytics_collection_name");

    public static final ParseField UPDATED_AT_MILLIS_FIELD = new ParseField("updated_at_millis");
    private final String name;
    private final String[] indices;
    private final String analyticsCollectionName;

    private final long updatedAtMillis;

    public SearchApplicationListItem(String name, String[] indices, @Nullable String analyticsCollectionName, long updatedAtMillis) {
        this.name = name;
        this.indices = indices;
        this.analyticsCollectionName = analyticsCollectionName;
        this.updatedAtMillis = updatedAtMillis;
    }

    public SearchApplicationListItem(StreamInput in) throws IOException {
        this.name = in.readString();
        this.indices = in.readStringArray();
        this.analyticsCollectionName = in.readOptionalString();
        this.updatedAtMillis = in.readLong();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD.getPreferredName(), name);
        builder.field(INDICES_FIELD.getPreferredName(), indices);
        if (analyticsCollectionName != null) {
            builder.field(ANALYTICS_COLLECTION_NAME_FIELD.getPreferredName(), analyticsCollectionName);
        }
        builder.field(UPDATED_AT_MILLIS_FIELD.getPreferredName(), updatedAtMillis);
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeStringArray(indices);
        out.writeOptionalString(analyticsCollectionName);
        out.writeLong(updatedAtMillis);
    }

    public String name() {
        return name;
    }

    public String[] indices() {
        return indices;
    }

    public String analyticsCollectionName() {
        return analyticsCollectionName;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchApplicationListItem item = (SearchApplicationListItem) o;
        return name.equals(item.name)
            && Arrays.equals(indices, item.indices)
            && Objects.equals(analyticsCollectionName, item.analyticsCollectionName)
            && updatedAtMillis == item.updatedAtMillis;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, analyticsCollectionName, updatedAtMillis);
        result = 31 * result + Arrays.hashCode(indices);
        return result;
    }
}
