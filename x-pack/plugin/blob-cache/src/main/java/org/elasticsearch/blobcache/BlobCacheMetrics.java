/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.blobcache;

import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.telemetry.TelemetryProvider;
import org.elasticsearch.telemetry.metric.LongCounter;
import org.elasticsearch.telemetry.metric.LongHistogram;
import org.elasticsearch.telemetry.metric.MeterRegistry;

import java.util.Map;

public class BlobCacheMetrics {
    private static final String CACHE_POPULATION_REASON_ATTRIBUTE_KEY = "cachePopulationReason";
    private static final String SHARD_ID_ATTRIBUTE_KEY = "shardId";

    private final LongCounter cacheMissCounter;
    private final LongCounter evictedCountNonZeroFrequency;
    private final LongHistogram cacheMissLoadTimes;
    private final LongHistogram cachePopulateThroughput;

    public enum CachePopulationReason {
        /**
         * When warming the cache
         */
        Warming,
        /**
         * When the data we need is not in the cache
         */
        CacheMiss
    }

    public BlobCacheMetrics(MeterRegistry meterRegistry) {
        this(
            meterRegistry.registerLongCounter(
                "es.blob_cache.miss_that_triggered_read.total",
                "The number of times there was a cache miss that triggered a read from the blob store",
                "count"
            ),
            meterRegistry.registerLongCounter(
                "es.blob_cache.count_of_evicted_used_regions.total",
                "The number of times a cache entry was evicted where the frequency was not zero",
                "entries"
            ),
            meterRegistry.registerLongHistogram(
                "es.blob_cache.cache_miss_load_times.histogram",
                "The time in milliseconds for populating entries in the blob store resulting from a cache miss, expressed as a histogram.",
                "ms"
            ),
            meterRegistry.registerLongHistogram(
                "es.blob_cache.populate_throughput.histogram",
                "The throughput when populating the blob store from the cache",
                "bytes/second"
            )
        );
    }

    BlobCacheMetrics(
        LongCounter cacheMissCounter,
        LongCounter evictedCountNonZeroFrequency,
        LongHistogram cacheMissLoadTimes,
        LongHistogram cachePopulateThroughput
    ) {
        this.cacheMissCounter = cacheMissCounter;
        this.evictedCountNonZeroFrequency = evictedCountNonZeroFrequency;
        this.cacheMissLoadTimes = cacheMissLoadTimes;
        this.cachePopulateThroughput = cachePopulateThroughput;
    }

    public static BlobCacheMetrics NOOP = new BlobCacheMetrics(TelemetryProvider.NOOP.getMeterRegistry());

    public LongCounter getCacheMissCounter() {
        return cacheMissCounter;
    }

    public LongCounter getEvictedCountNonZeroFrequency() {
        return evictedCountNonZeroFrequency;
    }

    public LongHistogram getCacheMissLoadTimes() {
        return cacheMissLoadTimes;
    }

    /**
     * Record the various cache population metrics after a chunk is copied to the cache
     *
     * @param totalBytesCopied The total number of bytes read
     * @param totalCopyTimeNanos The time taken to read the bytes in nanoseconds
     * @param shardId The shard ID to which the chunk belonged
     * @param cachePopulationReason The reason for the cache being populated
     */
    public void recordCachePopulationMetrics(
        int totalBytesCopied,
        long totalCopyTimeNanos,
        ShardId shardId,
        CachePopulationReason cachePopulationReason
    ) {
        Map<String, Object> metricAttributes = Map.of(
            SHARD_ID_ATTRIBUTE_KEY,
            shardId,
            CACHE_POPULATION_REASON_ATTRIBUTE_KEY,
            cachePopulationReason
        );
        cachePopulateThroughput.record(toBytesPerSecond(totalBytesCopied, totalCopyTimeNanos), metricAttributes);
    }

    /**
     * Calculate throughput as bytes/second
     *
     * @param totalBytes The total number of bytes transferred
     * @param totalNanoseconds The time to transfer in nanoseconds
     * @return The throughput as bytes/second
     */
    private int toBytesPerSecond(int totalBytes, long totalNanoseconds) {
        double totalSeconds = totalNanoseconds / 1_000_000_000.0;
        return (int) (totalBytes / totalSeconds);
    }
}
