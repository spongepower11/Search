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

package org.elasticsearch.search.aggregations.pipeline;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.CheckedConsumer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.KeywordFieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.NumberFieldMapper;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.script.MockScriptEngine;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorTestCase;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.InternalFilters;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.InternalDateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.InternalAvg;
import org.elasticsearch.search.aggregations.metrics.MetricInspectionHelper;
import org.elasticsearch.search.aggregations.support.ValueType;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.equalTo;

public class BucketScriptAggregatorTests extends AggregatorTestCase {
    private final String SCRIPT_NAME = "script_name";
    private final String GAP_SCRIPT = "gap_script";

    @Override
    protected ScriptService getMockScriptService() {
        Map<String, Function<Map<String, Object>, Object>> scripts = new HashMap<>(2);
        scripts.put(SCRIPT_NAME, script -> script.get("the_avg"));
        scripts.put(GAP_SCRIPT, script -> script.get("doc_count"));

        MockScriptEngine scriptEngine = new MockScriptEngine(MockScriptEngine.NAME, scripts, Collections.emptyMap());
        Map<String, ScriptEngine> engines = Collections.singletonMap(scriptEngine.getType(), scriptEngine);

        return new ScriptService(Settings.EMPTY, engines, ScriptModule.CORE_CONTEXTS);
    }

    public void testScript() throws IOException {
        MappedFieldType fieldType = new NumberFieldMapper.NumberFieldType(NumberFieldMapper.NumberType.INTEGER);
        fieldType.setName("number_field");
        fieldType.setHasDocValues(true);
        MappedFieldType fieldType1 = new KeywordFieldMapper.KeywordFieldType();
        fieldType1.setName("the_field");
        fieldType1.setHasDocValues(true);

        FiltersAggregationBuilder filters = new FiltersAggregationBuilder("placeholder", new MatchAllQueryBuilder())
            .subAggregation(new TermsAggregationBuilder("the_terms", ValueType.STRING).field("the_field")
                .subAggregation(new AvgAggregationBuilder("the_avg").field("number_field")))
            .subAggregation(new BucketScriptPipelineAggregationBuilder("bucket_script",
                Collections.singletonMap("the_avg", "the_terms['test1']>the_avg.value"),
                new Script(ScriptType.INLINE, MockScriptEngine.NAME, SCRIPT_NAME, Collections.emptyMap())));


        testCase(filters, new MatchAllDocsQuery(), iw -> {
            Document doc = new Document();
            doc.add(new SortedSetDocValuesField("the_field", new BytesRef("test1")));
            doc.add(new SortedNumericDocValuesField("number_field", 19));
            iw.addDocument(doc);

            doc = new Document();
            doc.add(new SortedSetDocValuesField("the_field", new BytesRef("test2")));
            doc.add(new SortedNumericDocValuesField("number_field", 55));
            iw.addDocument(doc);
        }, agg -> {
            InternalFilters f = (InternalFilters) agg;
           assertThat(((InternalSimpleValue)(f.getBuckets().get(0).getAggregations().get("bucket_script"))).value,
               equalTo(19.0));
        }, fieldType, fieldType1);
    }

    public void testGapPolicyNone() throws IOException {
        MappedFieldType fieldType = new NumberFieldMapper.NumberFieldType(NumberFieldMapper.NumberType.INTEGER);
        fieldType.setName("number_field");
        fieldType.setHasDocValues(true);

        DateHistogramAggregationBuilder dateHisto = new DateHistogramAggregationBuilder("the_histo")
            .field("number_field")
            .fixedInterval(new DateHistogramInterval("1ms"))
            .subAggregation(new AvgAggregationBuilder("the_avg").field("number_field"))
            .subAggregation(new BucketScriptPipelineAggregationBuilder("bucket_script",
                Collections.singletonMap("the_avg", "the_avg.value"),
                new Script(ScriptType.INLINE, MockScriptEngine.NAME, GAP_SCRIPT, Collections.emptyMap()))
                .gapPolicy(BucketHelpers.GapPolicy.NONE));


        testCase(dateHisto, new MatchAllDocsQuery(), iw -> {
            Document doc = new Document();
            doc.add(new SortedNumericDocValuesField("number_field", 19));
            iw.addDocument(doc);

            doc = new Document();
            doc.add(new SortedNumericDocValuesField("number_field", 21));
            iw.addDocument(doc);
        }, agg -> {
            InternalDateHistogram dh = (InternalDateHistogram) agg;
            assertThat(dh.getBuckets().size(), equalTo(3));
            assertThat(((InternalSimpleValue)(dh.getBuckets().get(0).getAggregations().get("bucket_script"))).value,
                equalTo(1.0));
            assertThat(((InternalAvg)(dh.getBuckets().get(0).getAggregations().get("the_avg"))).getValue(),
                equalTo(19.0));

            assertThat(((InternalSimpleValue)(dh.getBuckets().get(1).getAggregations().get("bucket_script"))).value,
                equalTo(0.0));
            assertFalse(MetricInspectionHelper.hasValue(((InternalAvg)(dh.getBuckets().get(1).getAggregations().get("the_avg")))));

            assertThat(((InternalSimpleValue)(dh.getBuckets().get(2).getAggregations().get("bucket_script"))).value,
                equalTo(1.0));
            assertThat(((InternalAvg)(dh.getBuckets().get(2).getAggregations().get("the_avg"))).getValue(),
                equalTo(21.0));

        }, fieldType);
    }

    private void testCase(AggregationBuilder aggregationBuilder, Query query,
                          CheckedConsumer<RandomIndexWriter, IOException> buildIndex,
                          Consumer<InternalAggregation> verify, MappedFieldType... fieldType) throws IOException {

        try (Directory directory = newDirectory()) {
            RandomIndexWriter indexWriter = new RandomIndexWriter(random(), directory);
            buildIndex.accept(indexWriter);
            indexWriter.close();

            try (IndexReader indexReader = DirectoryReader.open(directory)) {
                IndexSearcher indexSearcher = newIndexSearcher(indexReader);

                InternalAggregation agg;
                agg = searchAndReduce(indexSearcher, query, aggregationBuilder, fieldType);
                verify.accept(agg);
            }
        }
    }
}
