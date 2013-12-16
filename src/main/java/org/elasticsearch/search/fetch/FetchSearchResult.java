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

package org.elasticsearch.search.fetch;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.search.SearchShardTarget;
import org.elasticsearch.search.internal.InternalSearchHits;
import org.elasticsearch.search.internal.InternalSearchHits.StreamContext;
import org.elasticsearch.transport.TransportResponse;

import java.io.IOException;

/**
 *
 */
public class FetchSearchResult extends TransportResponse implements FetchSearchResultProvider {

    private long id;
    private SearchShardTarget shardTarget;
    private InternalSearchHits hits;
    // client side counter
    private transient int counter;
    private boolean timedOut;

    public FetchSearchResult() {

    }

    public FetchSearchResult(long id, SearchShardTarget shardTarget) {
        this.id = id;
        this.shardTarget = shardTarget;
    }

    @Override
    public FetchSearchResult fetchResult() {
        return this;
    }

    public long id() {
        return this.id;
    }

    public SearchShardTarget shardTarget() {
        return this.shardTarget;
    }

    @Override
    public void shardTarget(SearchShardTarget shardTarget) {
        this.shardTarget = shardTarget;
    }

    public void hits(InternalSearchHits hits) {
        this.hits = hits;
    }

    public InternalSearchHits hits() {
        return hits;
    }

    public FetchSearchResult initCounter() {
        counter = 0;
        return this;
    }

    public int counterGetAndIncrement() {
        return counter++;
    }

    public static FetchSearchResult readFetchSearchResult(StreamInput in) throws IOException {
        FetchSearchResult result = new FetchSearchResult();
        result.readFrom(in);
        return result;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        id = in.readLong();
        timedOut = in.readBoolean();
        if (!timedOut) {
            hits = InternalSearchHits.readSearchHits(in,
                    InternalSearchHits.streamContext().streamShardTarget(StreamContext.ShardTargetType.NO_STREAM));
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeLong(id);
        out.writeBoolean(timedOut);
        if (!timedOut) {
            hits.writeTo(out, InternalSearchHits.streamContext().streamShardTarget(StreamContext.ShardTargetType.NO_STREAM));
        }
    }
    public boolean isTimedOut() {
        return timedOut;
    }

    public void setTimedOut(boolean timedOut) {
        this.timedOut=timedOut;
    }
}
