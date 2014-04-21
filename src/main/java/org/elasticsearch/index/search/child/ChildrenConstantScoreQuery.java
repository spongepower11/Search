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

package org.elasticsearch.index.search.child;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermFilter;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.OpenBitSet;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.lucene.docset.DocIdSets;
import org.elasticsearch.common.lucene.search.ApplyAcceptedDocsFilter;
import org.elasticsearch.common.lucene.search.NoopCollector;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.util.BytesRefHash;
import org.elasticsearch.index.fielddata.BytesValues;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.ordinals.Ordinals;
import org.elasticsearch.index.fielddata.plain.ParentChildIndexFieldData;
import org.elasticsearch.index.mapper.Uid;
import org.elasticsearch.index.mapper.internal.UidFieldMapper;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Set;

/**
 *
 */
public class ChildrenConstantScoreQuery extends Query {

    private final ParentChildIndexFieldData parentChildIndexFieldData;
    private Query originalChildQuery;
    private final String parentType;
    private final String childType;
    private final Filter parentFilter;
    private final int shortCircuitParentDocSet;
    private final Filter nonNestedDocsFilter;

    private Query rewrittenChildQuery;
    private IndexReader rewriteIndexReader;

    public ChildrenConstantScoreQuery(ParentChildIndexFieldData parentChildIndexFieldData, Query childQuery, String parentType, String childType, Filter parentFilter, int shortCircuitParentDocSet, Filter nonNestedDocsFilter) {
        this.parentChildIndexFieldData = parentChildIndexFieldData;
        this.parentFilter = parentFilter;
        this.parentType = parentType;
        this.childType = childType;
        this.originalChildQuery = childQuery;
        this.shortCircuitParentDocSet = shortCircuitParentDocSet;
        this.nonNestedDocsFilter = nonNestedDocsFilter;
    }

    @Override
    // See TopChildrenQuery#rewrite
    public Query rewrite(IndexReader reader) throws IOException {
        if (rewrittenChildQuery == null) {
            rewrittenChildQuery = originalChildQuery.rewrite(reader);
            rewriteIndexReader = reader;
        }
        return this;
    }

    @Override
    public void extractTerms(Set<Term> terms) {
        rewrittenChildQuery.extractTerms(terms);
    }

    @Override
    public Query clone() {
        ChildrenConstantScoreQuery q = (ChildrenConstantScoreQuery) super.clone();
        q.originalChildQuery = originalChildQuery.clone();
        if (q.rewrittenChildQuery != null) {
            q.rewrittenChildQuery = rewrittenChildQuery.clone();
        }
        return q;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher) throws IOException {
        final SearchContext searchContext = SearchContext.current();
        ParentChildIndexFieldData.WithOrdinals globalIfd = parentChildIndexFieldData.getGlobalParentChild(
                parentType, searcher.getIndexReader()
        );
        assert rewrittenChildQuery != null;
        assert rewriteIndexReader == searcher.getIndexReader()  : "not equal, rewriteIndexReader=" + rewriteIndexReader + " searcher.getIndexReader()=" + searcher.getIndexReader();

        final Query childQuery = rewrittenChildQuery;
        IndexSearcher indexSearcher = new IndexSearcher(searcher.getIndexReader());
        indexSearcher.setSimilarity(searcher.getSimilarity());
        ParentOrdCollector collector = new ParentOrdCollector(globalIfd);
        indexSearcher.search(childQuery, collector);

        long remaining = collector.foundParents();
        if (remaining == 0) {
            return Queries.newMatchNoDocsQuery().createWeight(searcher);
        }

        Filter shortCircuitFilter = null;
        if (remaining == 1) {
            BytesRef id = collector.values.getValueByOrd(collector.parentOrds.nextSetBit(0));
            shortCircuitFilter = new TermFilter(new Term(UidFieldMapper.NAME, Uid.createUidAsBytes(parentType, id)));
        } else if (remaining <= shortCircuitParentDocSet) {
            BytesRefHash parentIds= null;
            boolean constructed = false;
            try {
                parentIds = new BytesRefHash(remaining, searchContext.bigArrays());
                for (long parentOrd = collector.parentOrds.nextSetBit(0l); parentOrd != -1; parentOrd = collector.parentOrds.nextSetBit(parentOrd + 1)) {
                    parentIds.add(collector.values.getValueByOrd(parentOrd));
                }
                constructed = true;
            } finally {
                if (!constructed) {
                    Releasables.close(parentIds);
                }
            }
            searchContext.addReleasable(parentIds, SearchContext.Lifetime.COLLECTION);
            shortCircuitFilter = new ParentIdsFilter(parentType, nonNestedDocsFilter, parentIds);
        }
        return new ParentWeight(parentFilter, globalIfd, shortCircuitFilter, collector);
    }

    private final class ParentWeight extends Weight  {

        private final Filter parentFilter;
        private final Filter shortCircuitFilter;
        private final ParentOrdCollector collector;
        private final IndexFieldData.WithOrdinals globalIfd;

        private long remaining;
        private float queryNorm;
        private float queryWeight;

        public ParentWeight(Filter parentFilter, IndexFieldData.WithOrdinals globalIfd, Filter shortCircuitFilter, ParentOrdCollector collector) {
            this.parentFilter = new ApplyAcceptedDocsFilter(parentFilter);
            this.globalIfd = globalIfd;
            this.shortCircuitFilter = shortCircuitFilter;
            this.collector = collector;
            this.remaining = collector.foundParents();
        }

        @Override
        public Explanation explain(AtomicReaderContext context, int doc) throws IOException {
            return new Explanation(getBoost(), "not implemented yet...");
        }

        @Override
        public Query getQuery() {
            return ChildrenConstantScoreQuery.this;
        }

        @Override
        public float getValueForNormalization() throws IOException {
            queryWeight = getBoost();
            return queryWeight * queryWeight;
        }

        @Override
        public void normalize(float norm, float topLevelBoost) {
            this.queryNorm = norm * topLevelBoost;
            queryWeight *= this.queryNorm;
        }

        @Override
        public Scorer scorer(AtomicReaderContext context, boolean scoreDocsInOrder, boolean topScorer, Bits acceptDocs) throws IOException {
            if (remaining == 0) {
                return null;
            }

            if (shortCircuitFilter != null) {
                DocIdSet docIdSet = shortCircuitFilter.getDocIdSet(context, acceptDocs);
                if (!DocIdSets.isEmpty(docIdSet)) {
                    DocIdSetIterator iterator = docIdSet.iterator();
                    if (iterator != null) {
                        return ConstantScorer.create(iterator, this, queryWeight);
                    }
                }
                return null;
            }

            DocIdSet parentDocIdSet = this.parentFilter.getDocIdSet(context, acceptDocs);
            if (!DocIdSets.isEmpty(parentDocIdSet)) {
                // We can't be sure of the fact that liveDocs have been applied, so we apply it here. The "remaining"
                // count down (short circuit) logic will then work as expected.
                parentDocIdSet = BitsFilteredDocIdSet.wrap(parentDocIdSet, context.reader().getLiveDocs());
                DocIdSetIterator innerIterator = parentDocIdSet.iterator();
                if (innerIterator != null) {
                    OpenBitSet parentOrds = collector.parentOrds;
                    BytesValues.WithOrdinals globalValues = globalIfd.load(context).getBytesValues(false);
                    if (globalValues != null) {
                        Ordinals.Docs globalOrdinals = globalValues.ordinals();
                        DocIdSetIterator parentIdIterator = new ParentOrdIterator(innerIterator, parentOrds, globalOrdinals, this);
                        return ConstantScorer.create(parentIdIterator, this, queryWeight);
                    }
                }
            }
            return null;
        }

    }

    private final static class ParentOrdCollector extends NoopCollector {

        private final OpenBitSet parentOrds;
        private final ParentChildIndexFieldData.WithOrdinals indexFieldData;

        private BytesValues.WithOrdinals values;
        private Ordinals.Docs ordinals;

        private ParentOrdCollector(ParentChildIndexFieldData.WithOrdinals indexFieldData) {
            // TODO: look into setting it to maxOrd
            this.parentOrds = new OpenBitSet(512);
            this.indexFieldData = indexFieldData;
        }

        @Override
        public void collect(int doc) throws IOException {
            if (ordinals != null) {
                long globalOrd = ordinals.getOrd(doc);
                if (globalOrd != Ordinals.MISSING_ORDINAL) {
                    parentOrds.set(globalOrd);
                }
            }
        }

        @Override
        public void setNextReader(AtomicReaderContext context) throws IOException {
            values = indexFieldData.load(context).getBytesValues(false);
            if (values != null) {
                ordinals = values.ordinals();
            } else {
                ordinals = null;
            }
        }

        long foundParents() {
            return parentOrds.cardinality();
        }

    }

    private final static class ParentOrdIterator extends FilteredDocIdSetIterator {

        private final OpenBitSet parentOrds;
        private final Ordinals.Docs ordinals;
        private final ParentWeight parentWeight;

        private ParentOrdIterator(DocIdSetIterator innerIterator, OpenBitSet parentOrds, Ordinals.Docs ordinals, ParentWeight parentWeight) {
            super(innerIterator);
            this.parentOrds = parentOrds;
            this.ordinals = ordinals;
            this.parentWeight = parentWeight;
        }

        @Override
        protected boolean match(int doc) {
            if (parentWeight.remaining == 0) {
                try {
                    advance(DocIdSetIterator.NO_MORE_DOCS);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }

            long parentOrd = ordinals.getOrd(doc);
            if (parentOrd != Ordinals.MISSING_ORDINAL) {
                boolean match = parentOrds.get(parentOrd);
                if (match) {
                    parentWeight.remaining--;
                }
                return match;
            }
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        ChildrenConstantScoreQuery that = (ChildrenConstantScoreQuery) obj;
        if (!originalChildQuery.equals(that.originalChildQuery)) {
            return false;
        }
        if (!childType.equals(that.childType)) {
            return false;
        }
        if (shortCircuitParentDocSet != that.shortCircuitParentDocSet) {
            return false;
        }
        if (getBoost() != that.getBoost()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = originalChildQuery.hashCode();
        result = 31 * result + childType.hashCode();
        result = 31 * result + shortCircuitParentDocSet;
        result = 31 * result + Float.floatToIntBits(getBoost());
        return result;
    }

    @Override
    public String toString(String field) {
        StringBuilder sb = new StringBuilder();
        sb.append("child_filter[").append(childType).append("/").append(parentType).append("](").append(originalChildQuery).append(')');
        return sb.toString();
    }

}
