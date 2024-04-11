/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.azureopenai.embeddings;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.test.AbstractWireSerializingTestCase;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.services.ConfigurationParseContext;
import org.elasticsearch.xpack.inference.services.ServiceFields;
import org.elasticsearch.xpack.inference.services.azureopenai.AzureOpenAiServiceFields;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.xpack.inference.services.azureopenai.embeddings.AzureOpenAiEmbeddingsServiceSettings.DIMENSIONS_SET_BY_USER;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class AzureOpenAiEmbeddingsServiceSettingsTests extends AbstractWireSerializingTestCase<AzureOpenAiEmbeddingsServiceSettings> {

    private static AzureOpenAiEmbeddingsServiceSettings createRandom() {
        var resourceName = randomAlphaOfLength(8);
        var deploymentId = randomAlphaOfLength(8);
        var apiVersion = randomAlphaOfLength(8);
        Integer dims = randomBoolean() ? 1536 : null;
        Integer maxInputTokens = randomBoolean() ? null : randomIntBetween(128, 256);
        return new AzureOpenAiEmbeddingsServiceSettings(resourceName, deploymentId, apiVersion, dims, randomBoolean(), maxInputTokens);
    }

    public void testFromMap_Request_CreatesSettingsCorrectly() {
        var resourceName = "this-resource";
        var deploymentId = "this-deployment";
        var apiVersion = "2024-01-01";
        var dims = 1536;
        var maxInputTokens = 512;
        var serviceSettings = AzureOpenAiEmbeddingsServiceSettings.fromMap(
            new HashMap<>(
                Map.of(
                    AzureOpenAiServiceFields.RESOURCE_NAME,
                    resourceName,
                    AzureOpenAiServiceFields.DEPLOYMENT_ID,
                    deploymentId,
                    AzureOpenAiServiceFields.API_VERSION,
                    apiVersion,
                    ServiceFields.DIMENSIONS,
                    dims,
                    ServiceFields.MAX_INPUT_TOKENS,
                    maxInputTokens
                )
            ),
            ConfigurationParseContext.REQUEST
        );

        assertThat(
            serviceSettings,
            is(new AzureOpenAiEmbeddingsServiceSettings(resourceName, deploymentId, apiVersion, dims, true, maxInputTokens))
        );
    }

    public void testFromMap_Request_DimensionsSetByUser_IsFalse_WhenDimensionsAreNotPresent() {
        var resourceName = "this-resource";
        var deploymentId = "this-deployment";
        var apiVersion = "2024-01-01";
        var maxInputTokens = 512;
        var serviceSettings = AzureOpenAiEmbeddingsServiceSettings.fromMap(
            new HashMap<>(
                Map.of(
                    AzureOpenAiServiceFields.RESOURCE_NAME,
                    resourceName,
                    AzureOpenAiServiceFields.DEPLOYMENT_ID,
                    deploymentId,
                    AzureOpenAiServiceFields.API_VERSION,
                    apiVersion,
                    ServiceFields.MAX_INPUT_TOKENS,
                    maxInputTokens
                )
            ),
            ConfigurationParseContext.REQUEST
        );

        assertThat(
            serviceSettings,
            is(new AzureOpenAiEmbeddingsServiceSettings(resourceName, deploymentId, apiVersion, null, false, maxInputTokens))
        );
    }

    public void testFromMap_Request_DimensionsSetByUser_ShouldThrowWhenPresent() {
        var resourceName = "this-resource";
        var deploymentId = "this-deployment";
        var apiVersion = "2024-01-01";
        var maxInputTokens = 512;
        var thrownException = expectThrows(
            ValidationException.class,
            () -> AzureOpenAiEmbeddingsServiceSettings.fromMap(
                new HashMap<>(
                    Map.of(
                        AzureOpenAiServiceFields.RESOURCE_NAME,
                        resourceName,
                        AzureOpenAiServiceFields.DEPLOYMENT_ID,
                        deploymentId,
                        AzureOpenAiServiceFields.API_VERSION,
                        apiVersion,
                        ServiceFields.MAX_INPUT_TOKENS,
                        maxInputTokens,
                        ServiceFields.DIMENSIONS,
                        1024,
                        DIMENSIONS_SET_BY_USER,
                        false
                    )
                ),
                ConfigurationParseContext.REQUEST
            )
        );

        MatcherAssert.assertThat(
            thrownException.getMessage(),
            containsString(
                Strings.format("Validation Failed: 1: [service_settings] does not allow the setting [%s];", DIMENSIONS_SET_BY_USER)
            )
        );
    }

    public void testFromMap_Persistent_CreatesSettingsCorrectly() {
        var resourceName = "this-resource";
        var deploymentId = "this-deployment";
        var apiVersion = "2024-01-01";
        var encodingFormat = "float";
        var dims = 1536;
        var maxInputTokens = 512;

        var serviceSettings = AzureOpenAiEmbeddingsServiceSettings.fromMap(
            new HashMap<>(
                Map.of(
                    AzureOpenAiServiceFields.RESOURCE_NAME,
                    resourceName,
                    AzureOpenAiServiceFields.DEPLOYMENT_ID,
                    deploymentId,
                    AzureOpenAiServiceFields.API_VERSION,
                    apiVersion,
                    ServiceFields.DIMENSIONS,
                    dims,
                    DIMENSIONS_SET_BY_USER,
                    false,
                    ServiceFields.MAX_INPUT_TOKENS,
                    maxInputTokens
                )
            ),
            ConfigurationParseContext.PERSISTENT
        );

        assertThat(
            serviceSettings,
            is(new AzureOpenAiEmbeddingsServiceSettings(resourceName, deploymentId, apiVersion, dims, false, maxInputTokens))
        );
    }

    public void testFromMap_PersistentContext_DoesNotThrowException_WhenDimensionsIsNull() {
        var resourceName = "this-resource";
        var deploymentId = "this-deployment";
        var apiVersion = "2024-01-01";

        var settings = AzureOpenAiEmbeddingsServiceSettings.fromMap(
            new HashMap<>(
                Map.of(
                    AzureOpenAiServiceFields.RESOURCE_NAME,
                    resourceName,
                    AzureOpenAiServiceFields.DEPLOYMENT_ID,
                    deploymentId,
                    AzureOpenAiServiceFields.API_VERSION,
                    apiVersion,
                    DIMENSIONS_SET_BY_USER,
                    true
                )
            ),
            ConfigurationParseContext.PERSISTENT
        );

        assertThat(settings, is(new AzureOpenAiEmbeddingsServiceSettings(resourceName, deploymentId, apiVersion, null, true, null)));
    }

    public void testFromMap_PersistentContext_ThrowsException_WhenDimensionsSetByUserIsNull() {
        var resourceName = "this-resource";
        var deploymentId = "this-deployment";
        var apiVersion = "2024-01-01";

        var exception = expectThrows(
            ValidationException.class,
            () -> AzureOpenAiEmbeddingsServiceSettings.fromMap(
                new HashMap<>(
                    Map.of(
                        AzureOpenAiServiceFields.RESOURCE_NAME,
                        resourceName,
                        AzureOpenAiServiceFields.DEPLOYMENT_ID,
                        deploymentId,
                        AzureOpenAiServiceFields.API_VERSION,
                        apiVersion,
                        ServiceFields.DIMENSIONS,
                        1
                    )
                ),
                ConfigurationParseContext.PERSISTENT
            )
        );

        assertThat(
            exception.getMessage(),
            containsString("Validation Failed: 1: [service_settings] does not contain the required setting [dimensions_set_by_user];")
        );
    }

    public void testToXContent_WritesDimensionsSetByUserTrue() throws IOException {
        var entity = new AzureOpenAiEmbeddingsServiceSettings("resource", "deployment", "apiVersion", null, true, null);

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        entity.toXContent(builder, null);
        String xContentResult = Strings.toString(builder);

        assertThat(xContentResult, CoreMatchers.is("""
            {"resource_name":"resource","deployment_id":"deployment","api_version":"apiVersion",""" + """
            "dimensions_set_by_user":true}"""));
    }

    public void testToXContent_WritesAllValues() throws IOException {
        var entity = new AzureOpenAiEmbeddingsServiceSettings("resource", "deployment", "apiVersion", 1024, false, 512);

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        entity.toXContent(builder, null);
        String xContentResult = Strings.toString(builder);

        assertThat(xContentResult, CoreMatchers.is("""
            {"resource_name":"resource","deployment_id":"deployment","api_version":"apiVersion",""" + """
            "dimensions":1024,"max_input_tokens":512,"dimensions_set_by_user":false}"""));
    }

    public void testToFilteredXContent_WritesAllValues_ExceptDimensionsSetByUser() throws IOException {
        var entity = new AzureOpenAiEmbeddingsServiceSettings("resource", "deployment", "apiVersion", 1024, false, 512);

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        var filteredXContent = entity.getFilteredXContentObject();
        filteredXContent.toXContent(builder, null);
        String xContentResult = Strings.toString(builder);

        assertThat(xContentResult, CoreMatchers.is("""
            {"resource_name":"resource","deployment_id":"deployment","api_version":"apiVersion",""" + """
            "dimensions":1024,"max_input_tokens":512}"""));
    }

    @Override
    protected Writeable.Reader<AzureOpenAiEmbeddingsServiceSettings> instanceReader() {
        return AzureOpenAiEmbeddingsServiceSettings::new;
    }

    @Override
    protected AzureOpenAiEmbeddingsServiceSettings createTestInstance() {
        return createRandom();
    }

    @Override
    protected AzureOpenAiEmbeddingsServiceSettings mutateInstance(AzureOpenAiEmbeddingsServiceSettings instance) throws IOException {
        return createRandom();
    }

    public static Map<String, Object> getPersistentAzureOpenAiServiceSettingsMap(
        String resourceName,
        String deploymentId,
        String apiVersion,
        @Nullable Integer dimensions,
        @Nullable Integer maxInputTokens
    ) {
        var map = new HashMap<String, Object>();

        map.put(AzureOpenAiServiceFields.RESOURCE_NAME, resourceName);
        map.put(AzureOpenAiServiceFields.DEPLOYMENT_ID, deploymentId);
        map.put(AzureOpenAiServiceFields.API_VERSION, apiVersion);

        if (dimensions != null) {
            map.put(ServiceFields.DIMENSIONS, dimensions);
            map.put(DIMENSIONS_SET_BY_USER, true);
        } else {
            map.put(DIMENSIONS_SET_BY_USER, false);
        }

        if (maxInputTokens != null) {
            map.put(ServiceFields.MAX_INPUT_TOKENS, maxInputTokens);
        }

        return map;
    }

    public static Map<String, Object> getRequestAzureOpenAiServiceSettingsMap(
        String resourceName,
        String deploymentId,
        String apiVersion,
        @Nullable Integer dimensions,
        @Nullable Integer maxInputTokens
    ) {
        var map = new HashMap<String, Object>();

        map.put(AzureOpenAiServiceFields.RESOURCE_NAME, resourceName);
        map.put(AzureOpenAiServiceFields.DEPLOYMENT_ID, deploymentId);
        map.put(AzureOpenAiServiceFields.API_VERSION, apiVersion);

        if (dimensions != null) {
            map.put(ServiceFields.DIMENSIONS, dimensions);
        }

        if (maxInputTokens != null) {
            map.put(ServiceFields.MAX_INPUT_TOKENS, maxInputTokens);
        }

        return map;
    }
}
