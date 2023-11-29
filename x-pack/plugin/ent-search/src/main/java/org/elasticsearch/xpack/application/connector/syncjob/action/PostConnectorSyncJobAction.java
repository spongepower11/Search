/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.connector.syncjob.action;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.application.connector.syncjob.ConnectorSyncJobTriggerMethod;
import org.elasticsearch.xpack.application.connector.syncjob.ConnectorSyncJobType;

import java.io.IOException;
import java.util.Objects;

import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;
import static org.elasticsearch.xpack.application.EnterpriseSearch.CONNECTOR_API_ENDPOINT;

public class PostConnectorSyncJobAction extends ActionType<PostConnectorSyncJobAction.Response> {

    public static final PostConnectorSyncJobAction INSTANCE = new PostConnectorSyncJobAction();

    public static final String NAME = "cluster:admin/xpack/connector/sync_job/post";

    public static final String CONNECTOR_SYNC_JOB_API_ENDPOINT = CONNECTOR_API_ENDPOINT + "/_sync_job";

    private PostConnectorSyncJobAction() {
        super(NAME, PostConnectorSyncJobAction.Response::new);
    }

    public static class Request extends ActionRequest implements ToXContentObject {
        public static final String EMPTY_CONNECTOR_ID_ERROR_MESSAGE = "[id] of the connector cannot be null or empty";
        private final String id;
        private final ConnectorSyncJobType jobType;
        private final ConnectorSyncJobTriggerMethod triggerMethod;

        public Request(String id, ConnectorSyncJobType jobType, ConnectorSyncJobTriggerMethod triggerMethod) {
            this.id = id;
            this.jobType = jobType;
            this.triggerMethod = triggerMethod;
        }

        public Request(StreamInput in) throws IOException {
            super(in);
            this.id = in.readString();
            this.jobType = in.readOptionalEnum(ConnectorSyncJobType.class);
            this.triggerMethod = in.readOptionalEnum(ConnectorSyncJobTriggerMethod.class);
        }

        private static final ConstructingObjectParser<Request, Void> PARSER = new ConstructingObjectParser<>(
            "connector_sync_job_put_request",
            false,
            ((args) -> new Request(
                (String) args[0],
                ConnectorSyncJobType.fromString((String) args[1]),
                ConnectorSyncJobTriggerMethod.fromString((String) args[2])
            ))
        );

        static {
            PARSER.declareString(constructorArg(), new ParseField("id"));
            PARSER.declareString(optionalConstructorArg(), new ParseField("job_type"));
            PARSER.declareString(optionalConstructorArg(), new ParseField("trigger_method"));
        }

        public String getId() {
            return id;
        }

        public ConnectorSyncJobType getJobType() {
            return jobType;
        }

        public ConnectorSyncJobTriggerMethod getTriggerMethod() {
            return triggerMethod;
        }

        public static Request fromXContentBytes(BytesReference source, XContentType xContentType) {
            try (XContentParser parser = XContentHelper.createParser(XContentParserConfiguration.EMPTY, source, xContentType)) {
                return Request.fromXContent(parser);
            } catch (IOException e) {
                throw new ElasticsearchParseException("Failed to parse: " + source.utf8ToString(), e);
            }
        }

        public static Request fromXContent(XContentParser parser) throws IOException {
            return PARSER.parse(parser, null);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            {
                builder.field("id", id);
                builder.field("job_type", jobType);
                builder.field("trigger_method", triggerMethod);
            }
            builder.endObject();
            return builder;
        }

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException validationException = null;

            // TODO: test explicitly
            if (Strings.isNullOrEmpty(getId())) {
                validationException = addValidationError(EMPTY_CONNECTOR_ID_ERROR_MESSAGE, validationException);
            }

            return validationException;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(id);
            out.writeOptionalEnum(jobType);
            out.writeOptionalEnum(triggerMethod);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Request request = (Request) o;
            return Objects.equals(id, request.id) && jobType == request.jobType && triggerMethod == request.triggerMethod;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, jobType, triggerMethod);
        }
    }

    public static class Response extends ActionResponse implements ToXContentObject {

        private final String id;

        public Response(StreamInput in) throws IOException {
            super(in);
            this.id = in.readString();
        }

        public Response(String id) {
            this.id = id;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(id);
        }

        public String getId() {
            return id;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field("id", id);
            builder.endObject();
            return builder;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Response response = (Response) o;
            return Objects.equals(id, response.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
