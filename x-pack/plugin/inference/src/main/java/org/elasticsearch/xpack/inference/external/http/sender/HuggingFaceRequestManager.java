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
import org.elasticsearch.xpack.inference.common.Truncator;
import org.elasticsearch.xpack.inference.external.http.retry.RequestSender;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseHandler;
import org.elasticsearch.xpack.inference.external.huggingface.HuggingFaceAccount;
import org.elasticsearch.xpack.inference.external.request.huggingface.HuggingFaceInferenceRequest;
import org.elasticsearch.xpack.inference.services.huggingface.HuggingFaceModel;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.inference.common.Truncator.truncate;

public class HuggingFaceRequestManager extends BaseRequestManager {
    private static final Logger logger = LogManager.getLogger(HuggingFaceRequestManager.class);

    public static HuggingFaceRequestManager of(
        HuggingFaceModel model,
        ResponseHandler responseHandler,
        Truncator truncator,
        ThreadPool threadPool
    ) {
        return new HuggingFaceRequestManager(
            Objects.requireNonNull(model),
            Objects.requireNonNull(responseHandler),
            Objects.requireNonNull(truncator),
            Objects.requireNonNull(threadPool)
        );
    }

    private final HuggingFaceModel model;
    private final ResponseHandler responseHandler;
    private final Truncator truncator;

    private HuggingFaceRequestManager(HuggingFaceModel model, ResponseHandler responseHandler, Truncator truncator, ThreadPool threadPool) {
        super(threadPool, model.getInferenceEntityId(), RateLimitGrouping.of(model));
        this.model = model;
        this.responseHandler = responseHandler;
        this.truncator = truncator;
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
        var truncatedInput = truncate(input, model.getTokenLimit());
        var request = new HuggingFaceInferenceRequest(truncator, truncatedInput, model);

        return new ExecutableInferenceRequest(
            requestSender,
            logger,
            request,
            context,
            responseHandler,
            hasRequestCompletedFunction,
            listener
        );
    }

    record RateLimitGrouping(HuggingFaceAccount account) {

        public static RateLimitGrouping of(HuggingFaceModel model) {
            return new RateLimitGrouping(new HuggingFaceAccount(model.rateLimitServiceSettings().uri(), model.apiKey()));
        }

        public RateLimitGrouping {
            Objects.requireNonNull(account);
        }
    }
}
