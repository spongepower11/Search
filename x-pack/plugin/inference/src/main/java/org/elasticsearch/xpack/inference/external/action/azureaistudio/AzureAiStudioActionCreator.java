/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.action.azureaistudio;

import org.elasticsearch.xpack.inference.external.action.ExecutableAction;
import org.elasticsearch.xpack.inference.external.http.sender.AzureAiStudioCompletionRequestManager;
import org.elasticsearch.xpack.inference.external.http.sender.AzureAiStudioEmbeddingsRequestManager;
import org.elasticsearch.xpack.inference.external.http.sender.Sender;
import org.elasticsearch.xpack.inference.services.ServiceComponents;
import org.elasticsearch.xpack.inference.services.azureaistudio.completion.AzureAiStudioCompletionModel;
import org.elasticsearch.xpack.inference.services.azureaistudio.embeddings.AzureAiStudioEmbeddingsModel;

import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.xpack.inference.external.action.ActionUtils.constructFailedToSendRequestMessage;

public class AzureAiStudioActionCreator implements AzureAiStudioActionVisitor {
    private final Sender sender;
    private final ServiceComponents serviceComponents;

    public AzureAiStudioActionCreator(Sender sender, ServiceComponents serviceComponents) {
        this.sender = Objects.requireNonNull(sender);
        this.serviceComponents = Objects.requireNonNull(serviceComponents);
    }

    @Override
    public ExecutableAction create(AzureAiStudioCompletionModel completionModel, Map<String, Object> taskSettings) {
        var overriddenModel = AzureAiStudioCompletionModel.of(completionModel, taskSettings);
        var requestManager = new AzureAiStudioCompletionRequestManager(overriddenModel, serviceComponents.threadPool());
        var errorMessage = constructFailedToSendRequestMessage(completionModel.uri(), "Azure AI Studio completion");
        return new AzureAiStudioAction(sender, requestManager, errorMessage);
    }

    @Override
    public ExecutableAction create(AzureAiStudioEmbeddingsModel embeddingsModel, Map<String, Object> taskSettings) {
        var overriddenModel = AzureAiStudioEmbeddingsModel.of(embeddingsModel, taskSettings);
        var requestManager = new AzureAiStudioEmbeddingsRequestManager(
            overriddenModel,
            serviceComponents.truncator(),
            serviceComponents.threadPool()
        );
        var errorMessage = constructFailedToSendRequestMessage(embeddingsModel.uri(), "Azure AI Studio embeddings");
        return new AzureAiStudioAction(sender, requestManager, errorMessage);
    }
}
