/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ml.action;

import org.elasticsearch.Version;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.core.ml.action.util.PageParams;
import org.elasticsearch.xpack.core.ml.action.util.QueryPage;
import org.elasticsearch.xpack.core.ml.job.config.Job;
import org.elasticsearch.xpack.core.ml.job.results.Bucket;
import org.elasticsearch.xpack.core.ml.job.results.Result;
import org.elasticsearch.xpack.core.ml.utils.ExceptionsHelper;

import java.io.IOException;
import java.util.Objects;

public class GetBucketsAction extends Action<GetBucketsAction.Response> {

    public static final GetBucketsAction INSTANCE = new GetBucketsAction();
    public static final String NAME = "cluster:monitor/xpack/ml/job/results/buckets/get";

    private GetBucketsAction() {
        super(NAME);
    }

    @Override
    public Response newResponse() {
        return new Response();
    }

    public static class Request extends ActionRequest implements ToXContentObject {

        public static final ParseField EXPAND = new ParseField("expand");
        public static final ParseField EXCLUDE_INTERIM = new ParseField("exclude_interim");
        public static final ParseField START = new ParseField("start");
        public static final ParseField END = new ParseField("end");
        public static final ParseField ANOMALY_SCORE = new ParseField("anomaly_score");
        public static final ParseField TIMESTAMP = new ParseField("timestamp");
        public static final ParseField SORT = new ParseField("sort");
        public static final ParseField DESCENDING = new ParseField("desc");

        private static final ObjectParser<Request, Void> PARSER = new ObjectParser<>(NAME, Request::new);

        static {
            PARSER.declareString((request, jobId) -> request.jobId = jobId, Job.ID);
            PARSER.declareString(Request::setTimestamp, Result.TIMESTAMP);
            PARSER.declareBoolean(Request::setExpand, EXPAND);
            PARSER.declareBoolean(Request::setExcludeInterim, EXCLUDE_INTERIM);
            PARSER.declareStringOrNull(Request::setStart, START);
            PARSER.declareStringOrNull(Request::setEnd, END);
            PARSER.declareObject(Request::setPageParams, PageParams.PARSER, PageParams.PAGE);
            PARSER.declareDouble(Request::setAnomalyScore, ANOMALY_SCORE);
            PARSER.declareString(Request::setSort, SORT);
            PARSER.declareBoolean(Request::setDescending, DESCENDING);
        }

        public static Request parseRequest(String jobId, XContentParser parser) {
            Request request = PARSER.apply(parser, null);
            if (jobId != null) {
                request.jobId = jobId;
            }
            return request;
        }

        private String jobId;
        private String timestamp;
        private boolean expand = false;
        private boolean excludeInterim = false;
        private String start;
        private String end;
        private PageParams pageParams;
        private Double anomalyScore;
        private String sort = Result.TIMESTAMP.getPreferredName();
        private boolean descending = false;

        public Request() {
        }

        public Request(String jobId) {
            this.jobId = ExceptionsHelper.requireNonNull(jobId, Job.ID.getPreferredName());
        }

        public String getJobId() {
            return jobId;
        }

        public void setTimestamp(String timestamp) {
            if (pageParams != null || start != null || end != null || anomalyScore != null) {
                throw new IllegalArgumentException("Param [" + TIMESTAMP.getPreferredName() + "] is incompatible with ["
                                + PageParams.FROM.getPreferredName() + ","
                                + PageParams.SIZE.getPreferredName() + ","
                                + START.getPreferredName() + ","
                                + END.getPreferredName() + ","
                                + ANOMALY_SCORE.getPreferredName() + "]");
            }
            this.timestamp = ExceptionsHelper.requireNonNull(timestamp, Result.TIMESTAMP.getPreferredName());
        }

        public String getTimestamp() {
            return timestamp;
        }

        public boolean isExpand() {
            return expand;
        }

        public void setExpand(boolean expand) {
            this.expand = expand;
        }

        public boolean isExcludeInterim() {
            return excludeInterim;
        }

        public void setExcludeInterim(boolean excludeInterim) {
            this.excludeInterim = excludeInterim;
        }

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            if (timestamp != null) {
                throw new IllegalArgumentException("Param [" + START.getPreferredName() + "] is incompatible with ["
                        + TIMESTAMP.getPreferredName() + "].");
            }
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            if (timestamp != null) {
                throw new IllegalArgumentException("Param [" + END.getPreferredName() + "] is incompatible with ["
                        + TIMESTAMP.getPreferredName() + "].");
            }
            this.end = end;
        }

        public PageParams getPageParams() {
            return pageParams;
        }

        public void setPageParams(PageParams pageParams) {
            if (timestamp != null) {
                throw new IllegalArgumentException("Param [" + PageParams.FROM.getPreferredName() 
                        + ", " + PageParams.SIZE.getPreferredName() + "] is incompatible with [" + TIMESTAMP.getPreferredName() + "].");
            }
            this.pageParams = ExceptionsHelper.requireNonNull(pageParams, PageParams.PAGE.getPreferredName());
        }

        public Double getAnomalyScore() {
            return anomalyScore;
        }

        public void setAnomalyScore(double anomalyScore) {
            if (timestamp != null) {
                throw new IllegalArgumentException("Param [" + ANOMALY_SCORE.getPreferredName() + "] is incompatible with ["
                        + TIMESTAMP.getPreferredName() + "].");
            }
            this.anomalyScore = anomalyScore;
        }

        public String getSort() {
            return sort;
        }

        public void setSort(String sort) {
            this.sort = sort;
        }

        public boolean isDescending() {
            return descending;
        }

        public void setDescending(boolean descending) {
            this.descending = descending;
        }

        @Override
        public ActionRequestValidationException validate() {
            return null;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            jobId = in.readString();
            timestamp = in.readOptionalString();
            expand = in.readBoolean();
            excludeInterim = in.readBoolean();
            start = in.readOptionalString();
            end = in.readOptionalString();
            anomalyScore = in.readOptionalDouble();
            pageParams = in.readOptionalWriteable(PageParams::new);
            if (in.getVersion().onOrAfter(Version.V_5_5_0)) {
                sort = in.readString();
                descending = in.readBoolean();
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(jobId);
            out.writeOptionalString(timestamp);
            out.writeBoolean(expand);
            out.writeBoolean(excludeInterim);
            out.writeOptionalString(start);
            out.writeOptionalString(end);
            out.writeOptionalDouble(anomalyScore);
            out.writeOptionalWriteable(pageParams);
            if (out.getVersion().onOrAfter(Version.V_5_5_0)) {
                out.writeString(sort);
                out.writeBoolean(descending);
            }
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field(Job.ID.getPreferredName(), jobId);
            if (timestamp != null) {
                builder.field(Result.TIMESTAMP.getPreferredName(), timestamp);
            }
            builder.field(EXPAND.getPreferredName(), expand);
            builder.field(EXCLUDE_INTERIM.getPreferredName(), excludeInterim);
            if (start != null) {
                builder.field(START.getPreferredName(), start);
            }
            if (end != null) {
                builder.field(END.getPreferredName(), end);
            }
            if (pageParams != null) {
                builder.field(PageParams.PAGE.getPreferredName(), pageParams);
            }
            if (anomalyScore != null) {
                builder.field(ANOMALY_SCORE.getPreferredName(), anomalyScore);
            }
            builder.field(SORT.getPreferredName(), sort);
            builder.field(DESCENDING.getPreferredName(), descending);
            builder.endObject();
            return builder;
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobId, timestamp, expand, excludeInterim, anomalyScore, pageParams, start, end, sort, descending);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Request other = (Request) obj;
            return Objects.equals(jobId, other.jobId) &&
                    Objects.equals(timestamp, other.timestamp) &&
                    Objects.equals(expand, other.expand) &&
                    Objects.equals(excludeInterim, other.excludeInterim) &&
                    Objects.equals(anomalyScore, other.anomalyScore) &&
                    Objects.equals(pageParams, other.pageParams) &&
                    Objects.equals(start, other.start) &&
                    Objects.equals(end, other.end) &&
                    Objects.equals(sort, other.sort) &&
                    Objects.equals(descending, other.descending);
        }
    }

    static class RequestBuilder extends ActionRequestBuilder<Request, Response> {

        RequestBuilder(ElasticsearchClient client) {
            super(client, INSTANCE, new Request());
        }
    }

    public static class Response extends ActionResponse implements ToXContentObject {

        private QueryPage<Bucket> buckets;

        public Response() {
        }

        public Response(QueryPage<Bucket> buckets) {
            this.buckets = buckets;
        }

        public QueryPage<Bucket> getBuckets() {
            return buckets;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            buckets = new QueryPage<>(in, Bucket::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            buckets.writeTo(out);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            buckets.doXContentBody(builder, params);
            builder.endObject();
            return builder;
        }

        @Override
        public int hashCode() {
            return Objects.hash(buckets);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Response other = (Response) obj;
            return Objects.equals(buckets, other.buckets);
        }

        @Override
        public final String toString() {
            return Strings.toString(this);
        }
    }

}
