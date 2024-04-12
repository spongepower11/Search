/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.apache.http.client.protocol.HttpClientContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.external.cohere.CohereResponseHandler;
import org.elasticsearch.xpack.inference.external.http.retry.RequestSender;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseHandler;
import org.elasticsearch.xpack.inference.external.request.cohere.CohereRerankRequest;
import org.elasticsearch.xpack.inference.external.response.cohere.CohereRankedResponseEntity;
import org.elasticsearch.xpack.inference.services.cohere.rerank.CohereRerankModel;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class CohereRerankRequestManager extends CohereRequestManager {
    private static final Logger logger = LogManager.getLogger(CohereRerankRequestManager.class);
    private static final ResponseHandler HANDLER = createCohereResponseHandler();

    private static ResponseHandler createCohereResponseHandler() {
        return new CohereResponseHandler("cohere rerank", (request, response) -> CohereRankedResponseEntity.fromResponse(response));
    }

    public static CohereRerankRequestManager of(CohereRerankModel model, ThreadPool threadPool) {
        return new CohereRerankRequestManager(Objects.requireNonNull(model), Objects.requireNonNull(threadPool));
    }

    private final CohereRerankModel model;

    private CohereRerankRequestManager(CohereRerankModel model, ThreadPool threadPool) {
        super(threadPool, model, CohereRerankRequest::buildDefaultUri);
        this.model = model;
    }

    @Override
    public Runnable create(
        String query,
        List<String> input,
        RequestSender requestSender,
        Supplier<Boolean> hasRequestCompletedFunction,
        HttpClientContext context,
        ActionListener<InferenceServiceResults> listener
    ) {
        CohereRerankRequest request = new CohereRerankRequest(query, input, model);

        return new ExecutableInferenceRequest(requestSender, logger, request, context, HANDLER, hasRequestCompletedFunction, listener);
    }
}
