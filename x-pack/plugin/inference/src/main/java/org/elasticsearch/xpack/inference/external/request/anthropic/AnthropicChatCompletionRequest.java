/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.request.anthropic;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.external.anthropic.AnthropicAccount;
import org.elasticsearch.xpack.inference.external.request.HttpRequest;
import org.elasticsearch.xpack.inference.external.request.Request;
import org.elasticsearch.xpack.inference.external.request.openai.OpenAiRequest;
import org.elasticsearch.xpack.inference.services.anthropic.completion.AnthropicChatCompletionModel;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.xpack.inference.external.request.anthropic.AnthropicRequestUtils.createVersionHeader;

public class AnthropicChatCompletionRequest implements OpenAiRequest {

    private final AnthropicAccount account;
    private final List<String> input;
    private final AnthropicChatCompletionModel model;

    public AnthropicChatCompletionRequest(List<String> input, AnthropicChatCompletionModel model) {
        this.account = AnthropicAccount.of(model, AnthropicChatCompletionRequest::buildDefaultUri);
        this.input = Objects.requireNonNull(input);
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public HttpRequest createHttpRequest() {
        HttpPost httpPost = new HttpPost(account.uri());

        ByteArrayEntity byteEntity = new ByteArrayEntity(
            Strings.toString(new AnthropicChatCompletionRequestEntity(input, model.getServiceSettings(), model.getTaskSettings()))
                .getBytes(StandardCharsets.UTF_8)
        );
        httpPost.setEntity(byteEntity);

        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, XContentType.JSON.mediaType());
        httpPost.setHeader(AnthropicRequestUtils.createAuthBearerHeader(account.apiKey()));
        httpPost.setHeader(createVersionHeader());

        return new HttpRequest(httpPost, getInferenceEntityId());
    }

    @Override
    public URI getURI() {
        return account.uri();
    }

    @Override
    public Request truncate() {
        // No truncation for Anthropic completions
        return this;
    }

    @Override
    public boolean[] getTruncationInfo() {
        // No truncation for Anthropic completions
        return null;
    }

    @Override
    public String getInferenceEntityId() {
        return model.getInferenceEntityId();
    }

    public static URI buildDefaultUri() throws URISyntaxException {
        return new URIBuilder().setScheme("https")
            .setHost(AnthropicRequestUtils.HOST)
            .setPathSegments(AnthropicRequestUtils.API_VERSION_1, AnthropicRequestUtils.MESSAGES_PATH)
            .build();
    }
}
