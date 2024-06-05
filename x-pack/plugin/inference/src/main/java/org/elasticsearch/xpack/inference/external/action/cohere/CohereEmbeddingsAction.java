/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.action.cohere;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.external.action.ExecutableAction;
import org.elasticsearch.xpack.inference.external.http.sender.CohereEmbeddingsRequestManager;
import org.elasticsearch.xpack.inference.external.http.sender.InferenceInputs;
import org.elasticsearch.xpack.inference.external.http.sender.Sender;
import org.elasticsearch.xpack.inference.services.cohere.embeddings.CohereEmbeddingsModel;

import java.util.Objects;

import static org.elasticsearch.xpack.inference.external.action.ActionUtils.constructFailedToSendRequestMessage;
import static org.elasticsearch.xpack.inference.external.action.ActionUtils.createInternalServerError;
import static org.elasticsearch.xpack.inference.external.action.ActionUtils.wrapFailuresInElasticsearchException;

public class CohereEmbeddingsAction implements ExecutableAction {
    private final String failedToSendRequestErrorMessage;
    private final Sender sender;
    private final CohereEmbeddingsRequestManager requestCreator;

    public CohereEmbeddingsAction(Sender sender, CohereEmbeddingsModel model, ThreadPool threadPool) {
        Objects.requireNonNull(model);
        this.sender = Objects.requireNonNull(sender);
        this.failedToSendRequestErrorMessage = constructFailedToSendRequestMessage(
            model.getServiceSettings().getCommonSettings().uri(),
            "Cohere embeddings"
        );
        // TODO - Batching pass the batching class on to the CohereEmbeddingsRequestManager
        requestCreator = CohereEmbeddingsRequestManager.of(model, threadPool);
    }

    @Override
    public void execute(InferenceInputs inferenceInputs, TimeValue timeout, ActionListener<InferenceServiceResults> listener) {
        try {
            ActionListener<InferenceServiceResults> wrappedListener = wrapFailuresInElasticsearchException(
                failedToSendRequestErrorMessage,
                listener
            );
            sender.send(requestCreator, inferenceInputs, timeout, wrappedListener);
        } catch (ElasticsearchException e) {
            listener.onFailure(e);
        } catch (Exception e) {
            listener.onFailure(createInternalServerError(e, failedToSendRequestErrorMessage));
        }
    }
}
