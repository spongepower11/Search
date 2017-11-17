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

package org.elasticsearch.search.profile.query;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BulkScorer;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;
import org.elasticsearch.search.profile.Timer;

import java.io.IOException;
import java.util.Set;

/**
 * Weight wrapper that will compute how much time it takes to build the
 * {@link Scorer} and then return a {@link Scorer} that is wrapped in
 * order to compute timings as well.
 */
public final class ProfileWeight extends Weight {

    private final Weight subQueryWeight;
    private final QueryProfileBreakdown profile;

    public ProfileWeight(Query query, Weight subQueryWeight, QueryProfileBreakdown profile) throws IOException {
        super(query);
        this.subQueryWeight = subQueryWeight;
        this.profile = profile;
    }

    @Override
    public Scorer scorer(LeafReaderContext context) throws IOException {
        ScorerSupplier supplier = scorerSupplier(context);
        if (supplier == null) {
            return null;
        }
        return supplier.get(Long.MAX_VALUE);
    }

    @Override
    public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
        Timer timer = profile.getTimer(QueryTimingType.BUILD_SCORER);
        timer.start();
        final ScorerSupplier subQueryScorerSupplier;
        try {
            subQueryScorerSupplier = subQueryWeight.scorerSupplier(context);
        } finally {
            timer.stop();
        }
        if (subQueryScorerSupplier == null) {
            return null;
        }

        final ProfileWeight weight = this;
        return new ScorerSupplier() {

            @Override
            public Scorer get(long loadCost) throws IOException {
                timer.start();
                try {
                    return new ProfileScorer(weight, subQueryScorerSupplier.get(loadCost), profile);
                } finally {
                    timer.stop();
                }
            }

            @Override
            public long cost() {
                timer.start();
                try {
                    return subQueryScorerSupplier.cost();
                } finally {
                    timer.stop();
                }
            }
        };
    }

    @Override
    public BulkScorer bulkScorer(LeafReaderContext context) throws IOException {
        // We use the default bulk scorer instead of the specialized one. The reason
        // is that Lucene's BulkScorers do everything at once: finding matches,
        // scoring them and calling the collector, so they make it impossible to
        // see where time is spent, which is the purpose of query profiling.
        // The default bulk scorer will pull a scorer and iterate over matches,
        // this might be a significantly different execution path for some queries
        // like disjunctions, but in general this is what is done anyway
        return super.bulkScorer(context);
    }

    @Override
    public Explanation explain(LeafReaderContext context, int doc) throws IOException {
        return subQueryWeight.explain(context, doc);
    }

    @Override
    public void extractTerms(Set<Term> set) {
        subQueryWeight.extractTerms(set);
    }

}
