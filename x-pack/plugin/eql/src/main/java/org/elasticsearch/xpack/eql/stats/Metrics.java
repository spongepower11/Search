/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.eql.stats;

import org.elasticsearch.common.metrics.CounterMetric;
import org.elasticsearch.xpack.core.watcher.common.stats.Counters;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Class encapsulating the metrics collected for EQL
 */
public class Metrics {

    private enum OperationType {
        FAILED, TOTAL;

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    // map that holds total/failed counters for all queries, atm
    private final Map<QueryMetric, Map<OperationType, CounterMetric>> opsByTypeMetrics;
    // map that holds counters for each eql "feature" (join, pipe, sequence...)
    private final Map<FeatureMetric, CounterMetric> featuresMetrics;
    protected static String QPREFIX = "queries.";
    protected static String FPREFIX = "features.";
    protected static String SEQUENCE_PREFIX = "sequences.";
    protected static String JOIN_PREFIX = "joins.";
    protected static String KEYS_PREFIX = "keys.";
    protected static String PIPES_PREFIX = "pipes.";

    public Metrics() {
        Map<QueryMetric, Map<OperationType, CounterMetric>> qMap = new LinkedHashMap<>();
        for (QueryMetric metric : QueryMetric.values()) {
            Map<OperationType, CounterMetric> metricsMap = new LinkedHashMap<>(OperationType.values().length);
            for (OperationType type : OperationType.values()) {
                metricsMap.put(type,  new CounterMetric());
            }

            qMap.put(metric, Collections.unmodifiableMap(metricsMap));
        }
        opsByTypeMetrics = Collections.unmodifiableMap(qMap);

        Map<FeatureMetric, CounterMetric> fMap = new LinkedHashMap<>(FeatureMetric.values().length);
        for (FeatureMetric featureMetric : FeatureMetric.values()) {
            fMap.put(featureMetric,  new CounterMetric());
        }
        featuresMetrics = Collections.unmodifiableMap(fMap);
    }

    /**
     * Increments the "total" counter for a metric
     * This method should be called only once per query.
     */
    public void total(QueryMetric metric) {
        inc(metric, OperationType.TOTAL);
    }

    /**
     * Increments the "failed" counter for a metric
     */
    public void failed(QueryMetric metric) {
        inc(metric, OperationType.FAILED);
    }

    private void inc(QueryMetric metric, OperationType op) {
        this.opsByTypeMetrics.get(metric).get(op).inc();
    }

    /**
     * Increments the counter for a "features" metric
     */
    public void inc(FeatureMetric metric) {
        this.featuresMetrics.get(metric).inc();
    }

    public Counters stats() {
        Counters counters = new Counters();

        // queries metrics
        for (Entry<QueryMetric, Map<OperationType, CounterMetric>> entry : opsByTypeMetrics.entrySet()) {
            String metricName = entry.getKey().toString();

            for (OperationType type : OperationType.values()) {
                long metricCounter = entry.getValue().get(type).count();
                String operationTypeName = type.toString();
                
                counters.inc(QPREFIX + metricName + "." + operationTypeName, metricCounter);
                counters.inc(QPREFIX + "_all." + operationTypeName, metricCounter);
            }
        }

        // features metrics
        for (Entry<FeatureMetric, CounterMetric> entry : featuresMetrics.entrySet()) {
            String featureName = entry.getKey().toString();
            String prefix = FPREFIX;

            if (featureName.startsWith("sequence_")) {
                prefix += SEQUENCE_PREFIX;
            } else if (featureName.startsWith("join_k")) {
                prefix += KEYS_PREFIX;
            } else if (featureName.startsWith("join_")) {
                prefix += JOIN_PREFIX;
            } else if (featureName.startsWith("pipe_")) {
                prefix += PIPES_PREFIX;
            }
            counters.inc(prefix + featureName, entry.getValue().count());
        }

        return counters;
    }
}
