/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.bulk;

import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class SimulateBulkRequestTests extends ESTestCase {

    public void testSerialization() throws Exception {
        testSerialization(getTestPipelineSubstitutions(), getTestTemplateSubstitutions());
        testSerialization(getTestPipelineSubstitutions(), null);
        testSerialization(null, getTestTemplateSubstitutions());
        testSerialization(null, null);
        testSerialization(Map.of(), Map.of());
    }

    private void testSerialization(
        Map<String, Map<String, Object>> pipelineSubstitutions,
        Map<String, Map<String, Object>> templateSubstitutions
    ) throws IOException {
        SimulateBulkRequest simulateBulkRequest = new SimulateBulkRequest(pipelineSubstitutions, templateSubstitutions);
        /*
         * Note: SimulateBulkRequest does not implement equals or hashCode, so we can't test serialization in the usual way for a
         * Writable
         */
        SimulateBulkRequest copy = copyWriteable(simulateBulkRequest, null, SimulateBulkRequest::new);
        assertThat(copy.getPipelineSubstitutions(), equalTo(simulateBulkRequest.getPipelineSubstitutions()));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testGetComponentTemplateSubstitutions() throws IOException {
        SimulateBulkRequest simulateBulkRequest = new SimulateBulkRequest(Map.of(), Map.of());
        assertThat(simulateBulkRequest.getComponentTemplateSubstitutions(), equalTo(Map.of()));
        String substituteComponentTemplatesString = """
              {
                  "mappings_template": {
                    "mappings": {
                      "dynamic": "true",
                      "properties": {
                        "foo": {
                          "type": "keyword"
                        }
                      }
                    }
                  },
                  "settings_template": {
                    "settings": {
                      "index": {
                        "default_pipeline": "bar-pipeline"
                      }
                    }
                  }
              }
            """;

        Map tempMap = XContentHelper.convertToMap(
            new BytesArray(substituteComponentTemplatesString.getBytes(StandardCharsets.UTF_8)),
            randomBoolean(),
            XContentType.JSON
        ).v2();
        Map<String, Map<String, Object>> substituteComponentTemplates = (Map<String, Map<String, Object>>) tempMap;
        simulateBulkRequest = new SimulateBulkRequest(Map.of(), substituteComponentTemplates);
        Map<String, ComponentTemplate> componentTemplateSubstitutions = simulateBulkRequest.getComponentTemplateSubstitutions();
        assertThat(componentTemplateSubstitutions.size(), equalTo(2));
        assertThat(
            XContentHelper.convertToMap(
                componentTemplateSubstitutions.get("mappings_template").template().mappings().uncompressed(),
                randomBoolean(),
                XContentType.JSON
            ).v2(),
            equalTo(substituteComponentTemplates.get("mappings_template").get("mappings"))
        );
        assertNull(componentTemplateSubstitutions.get("mappings_template").template().settings());
        assertNull(componentTemplateSubstitutions.get("settings_template").template().mappings());
        assertThat(componentTemplateSubstitutions.get("settings_template").template().settings().size(), equalTo(1));
        assertThat(
            componentTemplateSubstitutions.get("settings_template").template().settings().get("index.default_pipeline"),
            equalTo("bar-pipeline")
        );
    }

    public void testShallowClone() throws IOException {
        SimulateBulkRequest simulateBulkRequest = new SimulateBulkRequest(getTestPipelineSubstitutions(), getTestTemplateSubstitutions());
        BulkRequest shallowCopy = simulateBulkRequest.shallowClone();
        assertThat(shallowCopy, instanceOf(SimulateBulkRequest.class));
        SimulateBulkRequest simulateBulkRequestCopy = (SimulateBulkRequest) shallowCopy;
        assertThat(simulateBulkRequestCopy.requests, equalTo(List.of()));
        assertThat(
            simulateBulkRequestCopy.getComponentTemplateSubstitutions(),
            equalTo(simulateBulkRequest.getComponentTemplateSubstitutions())
        );
        assertThat(simulateBulkRequestCopy.getPipelineSubstitutions(), equalTo(simulateBulkRequest.getPipelineSubstitutions()));
        assertThat(simulateBulkRequestCopy.getRefreshPolicy(), equalTo(simulateBulkRequest.getRefreshPolicy()));
        assertThat(simulateBulkRequestCopy.waitForActiveShards(), equalTo(simulateBulkRequest.waitForActiveShards()));
        assertThat(simulateBulkRequestCopy.timeout(), equalTo(simulateBulkRequest.timeout()));
    }

    private static Map<String, Map<String, Object>> getTestPipelineSubstitutions() {
        return Map.of(
            "pipeline1",
            Map.of("processors", List.of(Map.of("processor2", Map.of()), Map.of("processor3", Map.of()))),
            "pipeline2",
            Map.of("processors", List.of(Map.of("processor3", Map.of())))
        );
    }

    private static Map<String, Map<String, Object>> getTestTemplateSubstitutions() {
        return Map.of(
            "template1",
            Map.of("mappings", Map.of("_source", Map.of("enabled", false), "properties", Map.of()), "settings", Map.of()),
            "template2",
            Map.of("mappings", Map.of(), "settings", Map.of())
        );
    }
}
