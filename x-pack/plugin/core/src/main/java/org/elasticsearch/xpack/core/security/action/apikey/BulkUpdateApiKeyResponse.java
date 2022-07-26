/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.security.action.apikey;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BulkUpdateApiKeyResponse extends ActionResponse implements ToXContentObject, Writeable {
    private final List<String> updated;
    private final List<String> noops;
    private final Map<String, ElasticsearchException> errorDetails;

    public BulkUpdateApiKeyResponse(
        final List<String> updated,
        final List<String> noops,
        final Map<String, ElasticsearchException> errorDetails
    ) {
        this.updated = updated;
        this.noops = noops;
        this.errorDetails = errorDetails;
    }

    public BulkUpdateApiKeyResponse(StreamInput in) throws IOException {
        super(in);
        this.updated = in.readStringList();
        this.noops = in.readStringList();
        this.errorDetails = in.readMap(StreamInput::readString, StreamInput::readException);
    }

    public List<String> getUpdated() {
        return updated;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject().stringListField("updated", updated).stringListField("noops", noops);
        builder.startObject("errors");
        {
            builder.field("count", errorDetails.size());
            if (errorDetails.isEmpty() == false) {
                // TODO will this work?
                builder.field("error_details", errorDetails);
            }
        }
        return builder.endObject() // errors
            .endObject();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeStringCollection(updated);
        out.writeStringCollection(noops);
        out.writeMap(errorDetails, StreamOutput::writeString, StreamOutput::writeException);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<String> updated;
        private final List<String> noops;
        private final Map<String, ElasticsearchException> errorDetails;

        public Builder() {
            updated = new ArrayList<>();
            noops = new ArrayList<>();
            errorDetails = new HashMap<>();
        }

        public Builder update(final String id) {
            updated.add(id);
            return this;
        }

        public Builder noop(final String id) {
            noops.add(id);
            return this;
        }

        public Builder error(final String id, final ElasticsearchException ex) {
            errorDetails.put(id, ex);
            return this;
        }

        public BulkUpdateApiKeyResponse build() {
            return new BulkUpdateApiKeyResponse(updated, noops, errorDetails);
        }
    }

}
