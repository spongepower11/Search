/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.analytics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.ClearScrollAction;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollAction;
import org.elasticsearch.action.search.SearchScrollRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.ml.datafeed.extractor.ExtractorUtils;
import org.elasticsearch.xpack.ml.datafeed.extractor.fields.ExtractedField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * An implementation that extracts data from elasticsearch using search and scroll on a client.
 * It supports safe and responsive cancellation by continuing the scroll until a new timestamp
 * is seen.
 * Note that this class is NOT thread-safe.
 */
public class DataFrameDataExtractor {

    private static final Logger LOGGER = LogManager.getLogger(DataFrameDataExtractor.class);
    private static final TimeValue SCROLL_TIMEOUT = new TimeValue(30, TimeUnit.MINUTES);

    private final Client client;
    private final DataFrameDataExtractorContext context;
    private String scrollId;
    private boolean isCancelled;
    private boolean hasNext;
    private boolean searchHasShardFailure;

    DataFrameDataExtractor(Client client, DataFrameDataExtractorContext context) {
        this.client = Objects.requireNonNull(client);
        this.context = Objects.requireNonNull(context);
        hasNext = true;
        searchHasShardFailure = false;
    }

    public boolean hasNext() {
        return hasNext;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void cancel() {
        isCancelled = true;
    }

    public Optional<List<String[]>> next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Optional<List<String[]>> records = scrollId == null ? Optional.ofNullable(initScroll()) : Optional.ofNullable(continueScroll());
        if (!records.isPresent()) {
            hasNext = false;
        }
        return records;
    }

    protected List<String[]> initScroll() throws IOException {
        LOGGER.debug("[{}] Initializing scroll", "analytics");
        SearchResponse searchResponse = executeSearchRequest(buildSearchRequest());
        LOGGER.debug("[{}] Search response was obtained", context.jobId);
        return processSearchResponse(searchResponse);
    }

    protected SearchResponse executeSearchRequest(SearchRequestBuilder searchRequestBuilder) {
        return ClientHelper.executeWithHeaders(context.headers, ClientHelper.ML_ORIGIN, client, searchRequestBuilder::get);
    }

    private SearchRequestBuilder buildSearchRequest() {
        SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client, SearchAction.INSTANCE)
                .setScroll(SCROLL_TIMEOUT)
                .addSort(DataFrameFields.ID, SortOrder.ASC)
                .setIndices(context.indices)
                .setSize(context.scrollSize)
                .setQuery(context.query)
                .setFetchSource(false);

        for (ExtractedField docValueField : context.extractedFields.getDocValueFields()) {
            searchRequestBuilder.addDocValueField(docValueField.getName(), docValueField.getDocValueFormat());
        }

        return searchRequestBuilder;
    }

    private List<String[]> processSearchResponse(SearchResponse searchResponse) throws IOException {

        if (searchResponse.getFailedShards() > 0 && searchHasShardFailure == false) {
            LOGGER.debug("[{}] Resetting scroll search after shard failure", context.jobId);
            markScrollAsErrored();
            return initScroll();
        }

        ExtractorUtils.checkSearchWasSuccessful(context.jobId, searchResponse);
        scrollId = searchResponse.getScrollId();
        if (searchResponse.getHits().getHits().length == 0) {
            hasNext = false;
            clearScroll(scrollId);
            return null;
        }

        SearchHit[] hits = searchResponse.getHits().getHits();
        List<String[]> records = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            if (isCancelled) {
                hasNext = false;
                clearScroll(scrollId);
                break;
            }
            records.add(toStringArray(hit));
        }
        return records;
    }

    private String[] toStringArray(SearchHit hit) {
        String[] result = new String[context.extractedFields.getAllFields().size()];
        for (int i = 0; i < result.length; ++i) {
            ExtractedField field = context.extractedFields.getAllFields().get(i);
            Object[] values = field.value(hit);
            result[i] = (values.length == 1) ? Objects.toString(values[0]) : "";
        }
        return result;
    }

    private List<String[]> continueScroll() throws IOException {
        LOGGER.debug("[{}] Continuing scroll with id [{}]", context.jobId, scrollId);
        SearchResponse searchResponse = executeSearchScrollRequest(scrollId);
        LOGGER.debug("[{}] Search response was obtained", context.jobId);
        return processSearchResponse(searchResponse);
    }

    private void markScrollAsErrored() {
        // This could be a transient error with the scroll Id.
        // Reinitialise the scroll and try again but only once.
        resetScroll();
        searchHasShardFailure = true;
    }

    protected SearchResponse executeSearchScrollRequest(String scrollId) {
        return ClientHelper.executeWithHeaders(context.headers, ClientHelper.ML_ORIGIN, client,
                () -> new SearchScrollRequestBuilder(client, SearchScrollAction.INSTANCE)
                .setScroll(SCROLL_TIMEOUT)
                .setScrollId(scrollId)
                .get());
    }

    private void resetScroll() {
        clearScroll(scrollId);
        scrollId = null;
    }

    private void clearScroll(String scrollId) {
        if (scrollId != null) {
            ClearScrollRequest request = new ClearScrollRequest();
            request.addScrollId(scrollId);
            ClientHelper.executeWithHeaders(context.headers, ClientHelper.ML_ORIGIN, client,
                    () -> client.execute(ClearScrollAction.INSTANCE, request).actionGet());
        }
    }

    public List<String> getFieldNames() {
        return context.extractedFields.getAllFields().stream().map(ExtractedField::getAlias).collect(Collectors.toList());
    }

    public String[] getFieldNamesArray() {
        List<String> fieldNames = getFieldNames();
        return fieldNames.toArray(new String[fieldNames.size()]);
    }
}
