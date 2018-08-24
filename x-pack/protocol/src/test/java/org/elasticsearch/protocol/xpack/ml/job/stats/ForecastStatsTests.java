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
package org.elasticsearch.protocol.xpack.ml.job.stats;

import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.test.AbstractXContentTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ForecastStatsTests extends AbstractXContentTestCase<ForecastStats> {

    @Override
    public ForecastStats createTestInstance() {
        return createForecastStats(1, 22);
    }

    @Override
    protected ForecastStats doParseInstance(XContentParser parser) throws IOException {
        return ForecastStats.PARSER.parse(parser, null);
    }

    @Override
    protected boolean supportsUnknownFields() {
        return false;
    }

    public static ForecastStats createForecastStats(long minTotal, long maxTotal) {
        return new ForecastStats(randomLongBetween(minTotal, maxTotal), createSimpleStats(),
            createSimpleStats(), createSimpleStats(), createCountStats());
    }

    private static SimpleStats createSimpleStats() {
        return new SimpleStatsTests().createTestInstance();
    }

    private static Map<String, Long> createCountStats() {
        Map<String, Long> countStats = new HashMap<>();
        for (int i = 0; i < randomInt(10); ++i) {
            countStats.put(randomAlphaOfLengthBetween(1, 20), randomLongBetween(1L, 100L));
        }
        return countStats;
    }
}
