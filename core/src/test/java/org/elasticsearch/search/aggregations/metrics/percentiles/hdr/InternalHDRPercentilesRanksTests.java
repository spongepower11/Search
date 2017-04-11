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

package org.elasticsearch.search.aggregations.metrics.percentiles.hdr;

import org.HdrHistogram.DoubleHistogram;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.Writeable.Reader;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.InternalAggregationTestCase;
import org.elasticsearch.search.aggregations.metrics.percentiles.Percentile;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;

import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

public class InternalHDRPercentilesRanksTests extends InternalAggregationTestCase<InternalHDRPercentileRanks> {

    @Override
    protected InternalHDRPercentileRanks createTestInstance(String name, List<PipelineAggregator> pipelineAggregators,
            Map<String, Object> metaData) {
        double[] cdfValues = new double[] { 0.5 };
        int numberOfSignificantValueDigits = 3;
        DoubleHistogram state = new DoubleHistogram(numberOfSignificantValueDigits);
        int numValues = randomInt(100);
        for (int i = 0; i < numValues; ++i) {
            state.recordValue(randomDouble());
        }
        boolean keyed = randomBoolean();
        DocValueFormat format = DocValueFormat.RAW;
        return new InternalHDRPercentileRanks(name, cdfValues, state, keyed, format, pipelineAggregators, metaData);
    }

    @Override
    protected void assertReduced(InternalHDRPercentileRanks reduced, List<InternalHDRPercentileRanks> inputs) {
        // it is hard to check the values due to the inaccuracy of the algorithm
        long totalCount = 0;
        for (InternalHDRPercentileRanks ranks : inputs) {
            totalCount += ranks.state.getTotalCount();
        }
        assertEquals(totalCount, reduced.state.getTotalCount());
    }

    @Override
    protected Reader<InternalHDRPercentileRanks> instanceReader() {
        return InternalHDRPercentileRanks::new;
    }

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        return new NamedXContentRegistry(singletonList(
                new NamedXContentRegistry.Entry(
                        Aggregation.class,
                        new ParseField(InternalHDRPercentileRanks.NAME),
                        (parser, context) -> ParsedHDRPercentileRanks.fromXContent(parser, (String) context))));
    }

    @Override
    protected void assertFromXContent(InternalHDRPercentileRanks aggregation, Aggregation parsedAggregation) {
        super.assertFromXContent(aggregation, parsedAggregation);

        assertTrue(parsedAggregation instanceof ParsedHDRPercentileRanks);
        ParsedHDRPercentileRanks hdrPercentileRanks = (ParsedHDRPercentileRanks) parsedAggregation;
        for (Percentile percentile : aggregation) {
            assertEquals(percentile.getPercent(), hdrPercentileRanks.percentile(percentile.getValue()), 0);
        }
        for (Percentile percentile : hdrPercentileRanks) {
            assertEquals(percentile.getValue(), aggregation.percent(percentile.getPercent()), 0);
        }
    }
}
