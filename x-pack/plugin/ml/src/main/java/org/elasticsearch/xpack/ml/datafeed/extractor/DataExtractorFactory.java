/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.datafeed.extractor;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.ml.datafeed.DatafeedConfig;
import org.elasticsearch.xpack.core.ml.datafeed.extractor.DataExtractor;
import org.elasticsearch.xpack.core.ml.job.config.Job;
import org.elasticsearch.xpack.core.rollup.action.GetRollupIndexCapsAction;
import org.elasticsearch.xpack.ml.datafeed.extractor.aggregation.AggregationDataExtractorFactory;
import org.elasticsearch.xpack.ml.datafeed.extractor.chunked.ChunkedDataExtractorFactory;
import org.elasticsearch.xpack.ml.datafeed.extractor.aggregation.RollupDataExtractorFactory;
import org.elasticsearch.xpack.ml.datafeed.extractor.scroll.ScrollDataExtractorFactory;

public interface DataExtractorFactory {
    DataExtractor newExtractor(long start, long end);

    /**
     * Creates a {@code DataExtractorFactory} for the given datafeed-job combination.
     */
    static void create(Client client, DatafeedConfig datafeed, Job job, ActionListener<DataExtractorFactory> listener) {
        ActionListener<DataExtractorFactory> factoryHandler = ActionListener.wrap(
            factory -> {
                if (datafeed.getChunkingConfig().isEnabled()) {
                    listener.onResponse(new ChunkedDataExtractorFactory(client, datafeed, job, factory));
                } else {
                    listener.onResponse(factory);
                }
            },
            listener::onFailure
        );

        ActionListener<GetRollupIndexCapsAction.Response> getRollupIndexCapsActionHandler = ActionListener.wrap(
            response -> {
                if (response.getJobs().isEmpty()) { // This means no rollup indexes are in the config
                    if (datafeed.hasAggregations()) {
                        factoryHandler.onResponse(new AggregationDataExtractorFactory(client, datafeed, job));
                    } else {
                        ScrollDataExtractorFactory.create(client, datafeed, job, factoryHandler);
                    }
                } else {
                    if (datafeed.hasAggregations()) { // Rollup indexes require aggregations
                        RollupDataExtractorFactory.create(client, datafeed, job, response.getJobs(), factoryHandler);
                    } else {
                        throw new IllegalArgumentException("Aggregations are required when using Rollup indices");
                    }
                }
            },
            e -> {
                if (e instanceof IndexNotFoundException) {
                    listener.onFailure(new ResourceNotFoundException("datafeed [" + datafeed.getId()
                        + "] cannot retrieve data because index " + ((IndexNotFoundException)e).getIndex() + " does not exist"));
                } else {
                    listener.onFailure(e);
                }
            }
        );

        GetRollupIndexCapsAction.Request request = new GetRollupIndexCapsAction.Request(datafeed.getIndices().toArray(new String[0]));

        ClientHelper.executeAsyncWithOrigin(
                client,
                ClientHelper.ML_ORIGIN,
                GetRollupIndexCapsAction.INSTANCE,
                request,
                getRollupIndexCapsActionHandler);
    }
}
