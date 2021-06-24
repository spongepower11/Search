/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.search.stats;

import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.util.CollectionUtils;
import org.elasticsearch.core.Releasable;
import org.elasticsearch.index.search.stats.FieldUsageStats.PerFieldUsageStats;
import org.elasticsearch.search.internal.FieldUsageTrackingDirectoryReader;
import org.elasticsearch.search.internal.FieldUsageTrackingDirectoryReader.FieldUsageNotifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Records and provides field usage stats
 */
public class ShardFieldUsageTracker {

    private final Map<String, InternalFieldStats> perFieldStats = new ConcurrentHashMap<>();

    /**
     * Returns a new session which can be passed to a {@link FieldUsageTrackingDirectoryReader}
     * to track field usage of a shard. Fields tracked as part of a session are only counted
     * as a single use. The stats are then recorded for this shard when the corresponding
     * session is closed.
     */
    public FieldUsageStatsTrackingSession createSession() {
        return new FieldUsageStatsTrackingSession();
    }

    /**
     * Returns field usage stats for the given fields. If no subset of fields is specified,
     * returns information for all fields.
     */
    public FieldUsageStats stats(String... fields) {
        final Map<String, PerFieldUsageStats> stats = new HashMap<>(perFieldStats.size());
        for (Map.Entry<String, InternalFieldStats> entry : perFieldStats.entrySet()) {
            InternalFieldStats ifs = entry.getValue();
            if (CollectionUtils.isEmpty(fields) || Regex.simpleMatch(fields, entry.getKey())) {
                PerFieldUsageStats pf = new PerFieldUsageStats();
                pf.terms = ifs.terms.longValue();
                pf.termFrequencies = ifs.termFrequencies.longValue();
                pf.positions = ifs.positions.longValue();
                pf.offsets = ifs.offsets.longValue();
                pf.docValues = ifs.docValues.longValue();
                pf.storedFields = ifs.storedFields.longValue();
                pf.norms = ifs.norms.longValue();
                pf.payloads = ifs.payloads.longValue();
                pf.termVectors = ifs.termVectors.longValue();
                pf.points = ifs.points.longValue();
                stats.put(entry.getKey(), pf);
            }
        }
        return new FieldUsageStats(Collections.unmodifiableMap(stats));
    }

    static class InternalFieldStats {
        final LongAdder terms = new LongAdder();
        final LongAdder termFrequencies = new LongAdder();
        final LongAdder positions = new LongAdder();
        final LongAdder offsets = new LongAdder();
        final LongAdder docValues = new LongAdder();
        final LongAdder storedFields = new LongAdder();
        final LongAdder norms = new LongAdder();
        final LongAdder payloads = new LongAdder();
        final LongAdder termVectors = new LongAdder();
        final LongAdder points = new LongAdder();
    }

    static class PerField {
        boolean terms;
        boolean termFrequencies;
        boolean positions;
        boolean offsets;
        boolean docValues;
        boolean storedFields;
        boolean norms;
        boolean payloads;
        boolean termVectors;
        boolean points;
    }

    public class FieldUsageStatsTrackingSession implements FieldUsageNotifier, Releasable {

        private final Map<String, PerField> usages = new HashMap<>();

        @Override
        public void close() {
            usages.entrySet().stream().forEach(e -> {
                InternalFieldStats fieldStats = perFieldStats.computeIfAbsent(e.getKey(), f -> new InternalFieldStats());
                PerField pf = e.getValue();
                if (pf.terms) {
                    fieldStats.terms.increment();
                }
                if (pf.termFrequencies) {
                    fieldStats.termFrequencies.increment();
                }
                if (pf.positions) {
                    fieldStats.positions.increment();
                }
                if (pf.offsets) {
                    fieldStats.offsets.increment();
                }
                if (pf.docValues) {
                    fieldStats.docValues.increment();
                }
                if (pf.storedFields) {
                    fieldStats.storedFields.increment();
                }
                if (pf.norms) {
                    fieldStats.norms.increment();
                }
                if (pf.payloads) {
                    fieldStats.payloads.increment();
                }
                if (pf.points) {
                    fieldStats.points.increment();
                }
                if (pf.termVectors) {
                    fieldStats.termVectors.increment();
                }
            });
        }

        private PerField getOrAdd(String fieldName) {
            Objects.requireNonNull(fieldName, "fieldName must be non-null");
            return usages.computeIfAbsent(fieldName, k -> new PerField());
        }

        @Override
        public void onTermsUsed(String field) {
            getOrAdd(field).terms = true;
        }

        @Override
        public void onTermFrequenciesUsed(String field) {
            getOrAdd(field).termFrequencies = true;
        }

        @Override
        public void onPositionsUsed(String field) {
            getOrAdd(field).positions = true;
        }

        @Override
        public void onOffsetsUsed(String field) {
            getOrAdd(field).offsets = true;
        }

        @Override
        public void onDocValuesUsed(String field) {
            getOrAdd(field).docValues = true;
        }

        @Override
        public void onStoredFieldsUsed(String field) {
            getOrAdd(field).storedFields = true;
        }

        @Override
        public void onNormsUsed(String field) {
            getOrAdd(field).norms = true;
        }

        @Override
        public void onPayloadsUsed(String field) {
            getOrAdd(field).payloads = true;
        }

        @Override
        public void onPointsUsed(String field) {
            getOrAdd(field).points = true;
        }

        @Override
        public void onTermVectorsUsed(String field) {
            getOrAdd(field).termVectors = true;
        }
    }
}
