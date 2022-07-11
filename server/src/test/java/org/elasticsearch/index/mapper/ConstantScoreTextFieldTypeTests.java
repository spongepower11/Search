/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.index.mapper;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.common.lucene.search.AutomatonQueries;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.mapper.TextFieldMapper.ConstantScoreTextFieldType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.lucene.search.MultiTermQuery.CONSTANT_SCORE_REWRITE;
import static org.hamcrest.Matchers.equalTo;

public class ConstantScoreTextFieldTypeTests extends FieldTypeTestCase {

    private static ConstantScoreTextFieldType createFieldType() {
        return new ConstantScoreTextFieldType();
    }

    public void testTermQuery() {
        MappedFieldType ft = createFieldType();
        assertEquals(new ConstantScoreQuery(new TermQuery(new Term("field", "foo"))), ft.termQuery("field", "foo", null));
        assertEquals(AutomatonQueries.caseInsensitiveTermQuery(new Term("field", "fOo")),
            ft.termQueryCaseInsensitive("field", "fOo", null));

        MappedFieldType unsearchable = new ConstantScoreTextFieldType(false, false, Collections.emptyMap());
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> unsearchable.termQuery("field", "bar", null));
        assertEquals("Cannot search on field [field] since it is not indexed.", e.getMessage());
    }

    public void testTermsQuery() {
        MappedFieldType ft = createFieldType();
        List<BytesRef> terms = new ArrayList<>();
        terms.add(new BytesRef("foo"));
        terms.add(new BytesRef("bar"));
        assertEquals(new TermInSetQuery("field", terms), ft.termsQuery("field", Arrays.asList("foo", "bar"), null));

        MappedFieldType unsearchable = new ConstantScoreTextFieldType(false, false, Collections.emptyMap());
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> unsearchable.termsQuery("field", Arrays.asList("foo", "bar"), null)
        );
        assertEquals("Cannot search on field [field] since it is not indexed.", e.getMessage());
    }

    public void testRangeQuery() {
        MappedFieldType ft = createFieldType();
        assertEquals(
            new TermRangeQuery("field", BytesRefs.toBytesRef("foo"), BytesRefs.toBytesRef("bar"), true, false),
            ft.rangeQuery("field", "foo", "bar", true, false, null, null, null, MOCK_CONTEXT)
        );

        ElasticsearchException ee = expectThrows(
            ElasticsearchException.class,
            () -> ft.rangeQuery("field", "foo", "bar", true, false, null, null, null, MOCK_CONTEXT_DISALLOW_EXPENSIVE)
        );
        assertEquals(
            "[range] queries on [text] or [keyword] fields cannot be executed when " + "'search.allow_expensive_queries' is set to false.",
            ee.getMessage()
        );
    }

    public void testRegexpQuery() {
        MappedFieldType ft = createFieldType();
        assertEquals(new RegexpQuery(new Term("field", "foo.*")), ft.regexpQuery("field", "foo.*", 0, 0, 10, null, MOCK_CONTEXT));

        MappedFieldType unsearchable = new ConstantScoreTextFieldType(false, false, Collections.emptyMap());
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> unsearchable.regexpQuery("field", "foo.*", 0, 0, 10, null, MOCK_CONTEXT)
        );
        assertEquals("Cannot search on field [field] since it is not indexed.", e.getMessage());

        ElasticsearchException ee = expectThrows(
            ElasticsearchException.class,
            () -> ft.regexpQuery("field", "foo.*", randomInt(10), 0, randomInt(10) + 1, null, MOCK_CONTEXT_DISALLOW_EXPENSIVE)
        );
        assertEquals("[regexp] queries cannot be executed when 'search.allow_expensive_queries' is set to false.", ee.getMessage());
    }

    public void testFuzzyQuery() {
        MappedFieldType ft = createFieldType();
        assertEquals(
            new ConstantScoreQuery(new FuzzyQuery(new Term("field", "foo"), 2, 1, 50, true)),
            ft.fuzzyQuery("field", "foo", Fuzziness.fromEdits(2), 1, 50, true, MOCK_CONTEXT)
        );

        MappedFieldType unsearchable = new ConstantScoreTextFieldType(false, false, Collections.emptyMap());
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> unsearchable.fuzzyQuery("field", "foo", Fuzziness.fromEdits(2), 1, 50, true, MOCK_CONTEXT)
        );
        assertEquals("Cannot search on field [field] since it is not indexed.", e.getMessage());

        ElasticsearchException ee = expectThrows(
            ElasticsearchException.class,
            () -> ft.fuzzyQuery(
                "field",
                "foo",
                Fuzziness.AUTO,
                randomInt(10) + 1,
                randomInt(10) + 1,
                randomBoolean(),
                MOCK_CONTEXT_DISALLOW_EXPENSIVE
            )
        );
        assertEquals("[fuzzy] queries cannot be executed when 'search.allow_expensive_queries' is set to false.", ee.getMessage());
    }

    public void testIndexPrefixes() {
        ConstantScoreTextFieldType ft = createFieldType();
        ft.setIndexPrefixes(2, 10);

        Query q = ft.prefixQuery("field", "goin", CONSTANT_SCORE_REWRITE, false, randomMockContext());
        assertEquals(new ConstantScoreQuery(new TermQuery(new Term("field._index_prefix", "goin"))), q);

        q = ft.prefixQuery("field", "internationalisatio", CONSTANT_SCORE_REWRITE, false, MOCK_CONTEXT);
        assertEquals(new PrefixQuery(new Term("field", "internationalisatio")), q);

        q = ft.prefixQuery("field", "Internationalisatio", CONSTANT_SCORE_REWRITE, true, MOCK_CONTEXT);
        assertEquals(AutomatonQueries.caseInsensitivePrefixQuery(new Term("field", "Internationalisatio")), q);

        ElasticsearchException ee = expectThrows(
            ElasticsearchException.class,
            () -> ft.prefixQuery("field", "internationalisatio", null, false, MOCK_CONTEXT_DISALLOW_EXPENSIVE)
        );
        assertEquals(
            "[prefix] queries cannot be executed when 'search.allow_expensive_queries' is set to false. "
                + "For optimised prefix queries on text fields please enable [index_prefixes].",
            ee.getMessage()
        );

        q = ft.prefixQuery("field", "g", CONSTANT_SCORE_REWRITE, false, randomMockContext());
        Automaton automaton = Operations.concatenate(Arrays.asList(Automata.makeChar('g'), Automata.makeAnyChar()));

        Query expected = new ConstantScoreQuery(
            new BooleanQuery.Builder().add(new AutomatonQuery(new Term("field._index_prefix", "g*"), automaton), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("field", "g")), BooleanClause.Occur.SHOULD)
                .build()
        );

        assertThat(q, equalTo(expected));
    }

    public void testFetchSourceValue() throws IOException {
        MappedField mappedField = new MappedField("field", createFieldType());

        assertEquals(List.of("value"), fetchSourceValue(mappedField, "value"));
        assertEquals(List.of("42"), fetchSourceValue(mappedField, 42L));
        assertEquals(List.of("true"), fetchSourceValue(mappedField, true));
    }

    public void testWildcardQuery() {
        ConstantScoreTextFieldType ft = createFieldType();

        // case sensitive
        AutomatonQuery actual = (AutomatonQuery) ft.wildcardQuery("field", "*Butterflies*", null, false, MOCK_CONTEXT);
        AutomatonQuery expected = new WildcardQuery(new Term("field", new BytesRef("*Butterflies*")));
        assertEquals(expected, actual);
        assertFalse(new CharacterRunAutomaton(actual.getAutomaton()).run("some butterflies somewhere"));

        // case insensitive
        actual = (AutomatonQuery) ft.wildcardQuery("field", "*Butterflies*", null, true, MOCK_CONTEXT);
        expected = AutomatonQueries.caseInsensitiveWildcardQuery(new Term("field", new BytesRef("*Butterflies*")));
        assertEquals(expected, actual);
        assertTrue(new CharacterRunAutomaton(actual.getAutomaton()).run("some butterflies somewhere"));
        assertTrue(new CharacterRunAutomaton(actual.getAutomaton()).run("some Butterflies somewhere"));

        ElasticsearchException ee = expectThrows(
            ElasticsearchException.class,
            () -> ft.wildcardQuery("field", "valu*", null, MOCK_CONTEXT_DISALLOW_EXPENSIVE)
        );
        assertEquals("[wildcard] queries cannot be executed when 'search.allow_expensive_queries' is set to false.", ee.getMessage());
    }

    /**
     * we use this e.g. in query string query parser to normalize terms on text fields
     */
    public void testNormalizedWildcardQuery() {
        ConstantScoreTextFieldType ft = createFieldType();

        AutomatonQuery actual = (AutomatonQuery) ft.normalizedWildcardQuery("field", "*Butterflies*", null, MOCK_CONTEXT);
        AutomatonQuery expected = new WildcardQuery(new Term("field", new BytesRef("*butterflies*")));
        assertEquals(expected, actual);
        assertTrue(new CharacterRunAutomaton(actual.getAutomaton()).run("some butterflies somewhere"));
        assertFalse(new CharacterRunAutomaton(actual.getAutomaton()).run("some Butterflies somewhere"));

        ElasticsearchException ee = expectThrows(
            ElasticsearchException.class,
            () -> ft.wildcardQuery("field", "valu*", null, MOCK_CONTEXT_DISALLOW_EXPENSIVE)
        );
        assertEquals("[wildcard] queries cannot be executed when 'search.allow_expensive_queries' is set to false.", ee.getMessage());
    }

    public void testTermIntervals() throws IOException {
        MappedFieldType ft = createFieldType();
        IntervalsSource termIntervals = ft.termIntervals("field", new BytesRef("foo"), MOCK_CONTEXT);
        assertEquals(Intervals.term(new BytesRef("foo")), termIntervals);
    }

    public void testPrefixIntervals() throws IOException {
        MappedFieldType ft = createFieldType();
        IntervalsSource prefixIntervals = ft.prefixIntervals("field", new BytesRef("foo"), MOCK_CONTEXT);
        assertEquals(Intervals.prefix(new BytesRef("foo")), prefixIntervals);
    }

    public void testWildcardIntervals() throws IOException {
        MappedFieldType ft = createFieldType();
        IntervalsSource wildcardIntervals = ft.wildcardIntervals("field", new BytesRef("foo"), MOCK_CONTEXT);
        assertEquals(Intervals.wildcard(new BytesRef("foo")), wildcardIntervals);
    }

    public void testFuzzyIntervals() throws IOException {
        MappedFieldType ft = createFieldType();
        IntervalsSource fuzzyIntervals = ft.fuzzyIntervals("field", "foo", 1, 2, true, MOCK_CONTEXT);
        FuzzyQuery fq = new FuzzyQuery(new Term("field", "foo"), 1, 2, 128, true);
        IntervalsSource expectedIntervals = Intervals.multiterm(fq.getAutomata(), "foo");
        assertEquals(expectedIntervals, fuzzyIntervals);
    }

    public void testPrefixIntervalsWithIndexedPrefixes() {
        ConstantScoreTextFieldType ft = createFieldType();
        ft.setIndexPrefixes(1, 4);
        IntervalsSource prefixIntervals = ft.prefixIntervals("field", new BytesRef("foo"), MOCK_CONTEXT);
        assertEquals(Intervals.fixField("field._index_prefix", Intervals.term(new BytesRef("foo"))), prefixIntervals);
    }

    public void testWildcardIntervalsWithIndexedPrefixes() {
        ConstantScoreTextFieldType ft = createFieldType();
        ft.setIndexPrefixes(1, 4);
        IntervalsSource wildcardIntervals = ft.wildcardIntervals("field", new BytesRef("foo"), MOCK_CONTEXT);
        assertEquals(Intervals.wildcard(new BytesRef("foo")), wildcardIntervals);
    }
}
