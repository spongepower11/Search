/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper.vectors;

import org.apache.lucene.util.hnsw.RandomVectorScorer;
import org.apache.lucene.util.hnsw.RandomVectorScorerSupplier;
import org.elasticsearch.vec.VectorScorer;

import java.io.IOException;

public final class VectorScorerSupplierAdapter implements RandomVectorScorerSupplier {

    private final VectorScorer scorer;

    public VectorScorerSupplierAdapter(VectorScorer scorer) {
        this.scorer = scorer;
    }

    @Override
    public RandomVectorScorer scorer(int ord) throws IOException {
        return new RandomVectorScorer() {
            final int firstOrd = ord;

            @Override
            public float score(int node) throws IOException {
                return scorer.score(firstOrd, ord);
            }

            @Override
            public int maxOrd() {
                return scorer.maxOrd();
            }
        };
    }

    @Override
    public RandomVectorScorerSupplier copy() throws IOException {
        return this; // no need to copy, thread-safe
    }
}
