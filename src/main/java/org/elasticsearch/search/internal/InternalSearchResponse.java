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

package org.elasticsearch.search.internal;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.InternalFacets;
import org.elasticsearch.search.profile.Profile;
import org.elasticsearch.search.suggest.Suggest;

import java.io.IOException;

import static org.elasticsearch.search.internal.InternalSearchHits.readSearchHits;

/**
 *
 */
public class InternalSearchResponse implements Streamable, ToXContent {

    public static InternalSearchResponse empty() {
        return new InternalSearchResponse(InternalSearchHits.empty(), null, null, null, null, false, null);
    }

    private InternalSearchHits hits;

    private InternalFacets facets;

    private InternalAggregations aggregations;

    private Suggest suggest;

    private Profile profile;

    private boolean timedOut;

    private Boolean terminatedEarly = null;

    private InternalSearchResponse() {
    }

    public InternalSearchResponse(InternalSearchHits hits, InternalFacets facets, InternalAggregations aggregations, Suggest suggest, Profile profile, boolean timedOut, Boolean terminatedEarly) {
        this.hits = hits;
        this.facets = facets;
        this.aggregations = aggregations;
        this.suggest = suggest;
        this.timedOut = timedOut;
        this.terminatedEarly = terminatedEarly;
        this.profile = profile;
    }

    public boolean timedOut() {
        return this.timedOut;
    }

    public Boolean terminatedEarly() {
        return this.terminatedEarly;
    }

    public SearchHits hits() {
        return hits;
    }

    public Facets facets() {
        return facets;
    }

    public Aggregations aggregations() {
        return aggregations;
    }

    public Suggest suggest() {
        return suggest;
    }

    public Profile profile() {
        return profile;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        hits.toXContent(builder, params);
        if (facets != null) {
            facets.toXContent(builder, params);
        }
        if (aggregations != null) {
            aggregations.toXContent(builder, params);
        }
        if (suggest != null) {
            suggest.toXContent(builder, params);
        }
        if (profile != null) {
            builder.startArray("profile");
            profile.toXContent(builder, params);
            builder.endArray();
        }
        return builder;
    }

    public static InternalSearchResponse readInternalSearchResponse(StreamInput in) throws IOException {
        InternalSearchResponse response = new InternalSearchResponse();
        response.readFrom(in);
        return response;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        hits = readSearchHits(in);
        if (in.readBoolean()) {
            facets = InternalFacets.readFacets(in);
        }
        if (in.readBoolean()) {
            aggregations = InternalAggregations.readAggregations(in);
        }
        if (in.readBoolean()) {
            suggest = Suggest.readSuggest(Suggest.Fields.SUGGEST, in);
        }

        timedOut = in.readBoolean();

        if (in.getVersion().onOrAfter(Version.V_1_4_0_Beta1)) {
            terminatedEarly = in.readOptionalBoolean();
        }

        if (in.getVersion().onOrAfter(Version.V_1_5_0)) {
            if (in.readBoolean()) {
                profile = Profile.readProfile(in);
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        hits.writeTo(out);
        if (facets == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            facets.writeTo(out);
        }
        if (aggregations == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            aggregations.writeTo(out);
        }
        if (suggest == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            suggest.writeTo(out);
        }

        out.writeBoolean(timedOut);

        if (out.getVersion().onOrAfter(Version.V_1_4_0_Beta1)) {
            out.writeOptionalBoolean(terminatedEarly);

        }

        if (out.getVersion().onOrAfter(Version.V_1_5_0)) {
            if (profile == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                profile.writeTo(out);
            }
        }
    }
}
