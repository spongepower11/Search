/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.indices.alias;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.rest.action.admin.indices.AliasesNotFoundException;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class IndicesAliasesResponse extends AcknowledgedResponse {

    public static final IndicesAliasesResponse NOT_ACKNOWLEDGED = new IndicesAliasesResponse(false, false, List.of());
    public static final IndicesAliasesResponse ACKNOWLEDGED_NO_ERRORS = new IndicesAliasesResponse(true, false, List.of());

    private static final String ACTION_RESULTS_FIELD = "action_results";
    private static final String ERRORS_FIELD = "errors";

    private final List<AliasActionResult> actionResults;
    private final boolean errors;

    protected IndicesAliasesResponse(StreamInput in) throws IOException {
        super(in);

        if (in.getTransportVersion().onOrAfter(TransportVersions.ALIAS_ACTION_RESULTS)) {
            this.errors = in.readBoolean();
            this.actionResults = in.readCollectionAsImmutableList(AliasActionResult::new);
        } else {
            this.errors = false;
            this.actionResults = List.of();
        }
    }

    public IndicesAliasesResponse(boolean acknowledged, boolean errors, final List<AliasActionResult> actionResults) {
        super(acknowledged);
        this.errors = errors;
        this.actionResults = actionResults;
    }

    public List<AliasActionResult> getActionResults() {
        return actionResults;
    }

    public boolean hasErrors() {
        return errors;
    }

    public static IndicesAliasesResponse build(final List<AliasActionResult> actionResults) {
        final boolean errors = actionResults.stream().anyMatch(a -> a.error != null);
        return new IndicesAliasesResponse(true, errors, actionResults);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (out.getTransportVersion().onOrAfter(TransportVersions.ALIAS_ACTION_RESULTS)) {
            out.writeBoolean(errors);
            out.writeCollection(actionResults);
        }
    }

    @Override
    protected void addCustomFields(XContentBuilder builder, Params params) throws IOException {
        builder.field(ERRORS_FIELD, errors);
        // if there are no errors, don't provide granular list of results
        if (errors) {
            builder.field(ACTION_RESULTS_FIELD, actionResults);
        }
    }

    @Override
    // Only used equals in tests
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (super.equals(o) == false) return false;
        IndicesAliasesResponse response = (IndicesAliasesResponse) o;
        return errors == response.errors && Objects.equals(actionResults, response.actionResults);
    }

    @Override
    // Only used hashCode in tests
    public int hashCode() {
        return Objects.hash(super.hashCode(), actionResults, errors);
    }

    public static class AliasActionResult implements Writeable, ToXContentObject {
        private final List<String> indices;
        private final AliasActions action;
        private final ElasticsearchException error;

        public static AliasActionResult build(List<String> indices, AliasActions action, int numAliasesRemoved) {
            if (action.actionType() == AliasActions.Type.REMOVE && numAliasesRemoved == 0) {
                return buildRemoveError(indices, action);
            }
            return buildSuccess(indices, action);
        }

        private static AliasActionResult buildRemoveError(List<String> indices, AliasActions action) {
            return new AliasActionResult(indices, action, new AliasesNotFoundException((action.getOriginalAliases())));
        }

        public static AliasActionResult buildSuccess(List<String> indices, AliasActions action) {
            return new AliasActionResult(indices, action, null);
        }

        private int getStatus() {
            return error == null ? 200 : error.status().getStatus();
        }

        private AliasActionResult(List<String> indices, AliasActions action, ElasticsearchException error) {
            assert indices.isEmpty() == false : "Alias action result must be instantiated with at least one index";
            this.indices = indices;
            this.action = action;
            this.error = error;
        }

        private AliasActionResult(StreamInput in) throws IOException {
            this.indices = in.readStringCollectionAsList();
            this.action = new AliasActions(in);
            this.error = in.readException();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeStringCollection(indices);
            action.writeTo(out);
            out.writeException(error);
        }

        public static final String ACTION_FIELD = "action";
        public static final String ACTION_TYPE_FIELD = "type";
        public static final String ACTION_INDICES_FIELD = "indices";
        public static final String ACTION_ALIASES_FIELD = "aliases";
        public static final String STATUS_FIELD = "status";
        public static final String ERROR_FIELD = "error";

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();

            // include subset of fields from action request
            builder.field(ACTION_FIELD);
            builder.startObject();
            builder.field(ACTION_TYPE_FIELD, action.actionType().getFieldName());
            builder.field(ACTION_INDICES_FIELD, indices.stream().sorted().collect(Collectors.toList()));
            builder.array(ACTION_ALIASES_FIELD, action.getOriginalAliases());
            builder.endObject();

            builder.field(STATUS_FIELD, getStatus());

            if (error != null) {
                builder.startObject(ERROR_FIELD);
                error.toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
            return builder;
        }

        @Override
        // Only used equals in tests
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AliasActionResult that = (AliasActionResult) o;
            return Objects.equals(indices, that.indices) && Objects.equals(action, that.action)
            // ElasticsearchException does not have equals() so assume errors are equal iff messages are equal
                && Objects.equals(error == null ? null : error.getMessage(), that.error == null ? null : that.error.getMessage());
        }

        @Override
        // Only used hashCode in tests
        public int hashCode() {
            return Objects.hash(
                indices,
                action,
                // ElasticsearchException does not have hashCode() so assume errors are equal iff messages are equal
                error == null ? null : error.getMessage()
            );
        }
    }
}
