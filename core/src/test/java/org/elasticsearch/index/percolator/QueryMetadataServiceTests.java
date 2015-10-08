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
package org.elasticsearch.index.percolator;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queries.TermsQuery;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.test.ESTestCase;

import java.util.*;

import static org.elasticsearch.index.percolator.QueryMetadataService.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;

public class QueryMetadataServiceTests extends ESTestCase {

    private QueryMetadataService queryMetadataService = new QueryMetadataService();

    public void testExtractQueryMetadata_termQuery() {
        TermQuery termQuery = new TermQuery(new Term("_field", "_term"));
        List<Term> terms = new ArrayList<>(queryMetadataService.extractQueryMetadata(termQuery));
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(termQuery.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_phraseQuery() {
        PhraseQuery phraseQuery = new PhraseQuery("_field", "_term1", "term2");
        List<Term> terms = new ArrayList<>(queryMetadataService.extractQueryMetadata(phraseQuery));
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(phraseQuery.getTerms()[0].field()));
        assertThat(terms.get(0).bytes(), equalTo(phraseQuery.getTerms()[0].bytes()));
    }

    public void testExtractQueryMetadata_booleanQuery() {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        PhraseQuery phraseQuery = new PhraseQuery("_field", "_term1", "term2");
        builder.add(phraseQuery, BooleanClause.Occur.SHOULD);

        BooleanQuery.Builder subBuilder = new BooleanQuery.Builder();
        TermQuery termQuery2 = new TermQuery(new Term("_field1", "_term"));
        subBuilder.add(termQuery2, BooleanClause.Occur.MUST);
        TermQuery termQuery3 = new TermQuery(new Term("_field3", "_long_term"));
        subBuilder.add(termQuery3, BooleanClause.Occur.MUST);
        builder.add(subBuilder.build(), BooleanClause.Occur.SHOULD);

        BooleanQuery booleanQuery = builder.build();
        List<Term> terms = new ArrayList<>(queryMetadataService.extractQueryMetadata(booleanQuery));
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(3));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
        assertThat(terms.get(1).field(), equalTo(phraseQuery.getTerms()[0].field()));
        assertThat(terms.get(1).bytes(), equalTo(phraseQuery.getTerms()[0].bytes()));
        assertThat(terms.get(2).field(), equalTo(termQuery3.getTerm().field()));
        assertThat(terms.get(2).bytes(), equalTo(termQuery3.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_booleanQuery_onlyShould() {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term1"));
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        TermQuery termQuery2 = new TermQuery(new Term("_field", "_term2"));
        builder.add(termQuery2, BooleanClause.Occur.SHOULD);

        BooleanQuery.Builder subBuilder = new BooleanQuery.Builder();
        TermQuery termQuery3 = new TermQuery(new Term("_field1", "_term"));
        subBuilder.add(termQuery3, BooleanClause.Occur.SHOULD);
        TermQuery termQuery4 = new TermQuery(new Term("_field3", "_long_term"));
        subBuilder.add(termQuery4, BooleanClause.Occur.SHOULD);
        builder.add(subBuilder.build(), BooleanClause.Occur.SHOULD);

        BooleanQuery booleanQuery = builder.build();
        List<Term> terms = new ArrayList<>(queryMetadataService.extractQueryMetadata(booleanQuery));
        Collections.sort(terms);
        assertThat(terms.size(), equalTo(4));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
        assertThat(terms.get(1).field(), equalTo(termQuery2.getTerm().field()));
        assertThat(terms.get(1).bytes(), equalTo(termQuery2.getTerm().bytes()));
        assertThat(terms.get(2).field(), equalTo(termQuery3.getTerm().field()));
        assertThat(terms.get(2).bytes(), equalTo(termQuery3.getTerm().bytes()));
        assertThat(terms.get(3).field(), equalTo(termQuery4.getTerm().field()));
        assertThat(terms.get(3).bytes(), equalTo(termQuery4.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_booleanQueryWithMustNot() {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        builder.add(termQuery1, BooleanClause.Occur.MUST_NOT);
        PhraseQuery phraseQuery = new PhraseQuery("_field", "_term1", "term2");
        builder.add(phraseQuery, BooleanClause.Occur.SHOULD);

        BooleanQuery booleanQuery = builder.build();
        List<Term> terms = new ArrayList<>(queryMetadataService.extractQueryMetadata(booleanQuery));
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(phraseQuery.getTerms()[0].field()));
        assertThat(terms.get(0).bytes(), equalTo(phraseQuery.getTerms()[0].bytes()));
    }

    public void testExtractQueryMetadata_constantScoreQuery() {
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        ConstantScoreQuery constantScoreQuery = new ConstantScoreQuery(termQuery1);
        List<Term> terms = new ArrayList<>(queryMetadataService.extractQueryMetadata(constantScoreQuery));
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_boostQuery() {
        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        BoostQuery constantScoreQuery = new BoostQuery(termQuery1, 1f);
        List<Term> terms = new ArrayList<>(queryMetadataService.extractQueryMetadata(constantScoreQuery));
        assertThat(terms.size(), equalTo(1));
        assertThat(terms.get(0).field(), equalTo(termQuery1.getTerm().field()));
        assertThat(terms.get(0).bytes(), equalTo(termQuery1.getTerm().bytes()));
    }

    public void testExtractQueryMetadata_unsupportedQuery() {
        TermRangeQuery termRangeQuery = new TermRangeQuery("_field", null, null, true, false);

        try {
            queryMetadataService.extractQueryMetadata(termRangeQuery);
            fail("UnsupportedQueryException expected");
        } catch (QueryMetadataService.UnsupportedQueryException e) {
            assertThat(e.getUnsupportedQuery(), sameInstance(termRangeQuery));
        }

        TermQuery termQuery1 = new TermQuery(new Term("_field", "_term"));
        BooleanQuery.Builder builder = new BooleanQuery.Builder();;
        builder.add(termQuery1, BooleanClause.Occur.SHOULD);
        builder.add(termRangeQuery, BooleanClause.Occur.SHOULD);
        BooleanQuery bq = builder.build();

        try {
            queryMetadataService.extractQueryMetadata(bq);
            fail("UnsupportedQueryException expected");
        } catch (QueryMetadataService.UnsupportedQueryException e) {
            assertThat(e.getUnsupportedQuery(), sameInstance(termRangeQuery));
        }
    }

    public void testCreateQueryMetadataQuery() throws Exception {
        MemoryIndex memoryIndex = new MemoryIndex(false);
        memoryIndex.addField("field1", "the quick brown fox jumps over the lazy dog", new WhitespaceAnalyzer());
        memoryIndex.addField("field2", "some more text", new WhitespaceAnalyzer());
        memoryIndex.addField("_field3", "unhide me", new WhitespaceAnalyzer());
        memoryIndex.addField("field4", "123", new WhitespaceAnalyzer());

        IndexReader indexReader = memoryIndex.createSearcher().getIndexReader();
        Query query = queryMetadataService.createQueryMetadataQuery(indexReader);
        assertThat(query, instanceOf(TermsQuery.class));

        // no easy way to get to the terms in TermsQuery,
        // if there a less then 16 terms then it gets rewritten to bq and then we can easily check the terms
        BooleanQuery booleanQuery = (BooleanQuery) ((ConstantScoreQuery) query.rewrite(indexReader)).getQuery();
        assertThat(booleanQuery.clauses().size(), equalTo(15));
        assertClause(booleanQuery, 0, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "_field3", "me");
        assertClause(booleanQuery, 1, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "_field3", "unhide");
        assertClause(booleanQuery, 2, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field1", "brown");
        assertClause(booleanQuery, 3, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field1", "dog");
        assertClause(booleanQuery, 4, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field1", "fox");
        assertClause(booleanQuery, 5, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field1", "jumps");
        assertClause(booleanQuery, 6, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field1", "lazy");
        assertClause(booleanQuery, 7, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field1", "over");
        assertClause(booleanQuery, 8, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field1", "quick");
        assertClause(booleanQuery, 9, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field1", "the");
        assertClause(booleanQuery, 10, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field2", "more");
        assertClause(booleanQuery, 11, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field2", "some");
        assertClause(booleanQuery, 12, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field2", "text");
        assertClause(booleanQuery, 13, QueryMetadataService.QUERY_METADATA_FIELD_PREFIX + "field4", "123");
        assertClause(booleanQuery, 14, QueryMetadataService.QUERY_METADATA_FIELD_UNKNOWN, "");
    }

    public void testSelectTermsListWithHighestSumOfTermLength() {
        Set<Term> terms1 = new HashSet<>();
        int sumTermLength = randomIntBetween(32, 64);
        while (sumTermLength > 0) {
            int length = randomInt(sumTermLength);
            terms1.add(new Term("field", randomAsciiOfLength(length)));
            sumTermLength -= length;
        }

        Set<Term> terms2 = new HashSet<>();
        sumTermLength = randomIntBetween(65, 128);
        while (sumTermLength > 0) {
            int length = randomInt(sumTermLength);
            terms2.add(new Term("field", randomAsciiOfLength(length)));
            sumTermLength -= length;
        }

        Set<Term> result = queryMetadataService.selectTermsListWithHighestSumOfTermLength(terms1, terms2);
        assertThat(result, sameInstance(terms2));
    }

    private void assertClause(BooleanQuery booleanQuery, int i, String expectedField, String expectedValue) {
        assertThat(booleanQuery.clauses().get(i).getOccur(), equalTo(BooleanClause.Occur.SHOULD));
        assertThat(((TermQuery) booleanQuery.clauses().get(i).getQuery()).getTerm().field(), equalTo(expectedField));
        assertThat(((TermQuery) booleanQuery.clauses().get(i).getQuery()).getTerm().bytes().utf8ToString(), equalTo(expectedValue));
    }

}
