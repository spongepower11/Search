/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.upgrades;

import org.elasticsearch.Version;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.xpack.test.SecuritySettingsSourceField;
import org.junit.Before;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.xpack.test.SecuritySettingsSourceField.basicAuthHeaderValue;

public abstract class AbstractUpgradeTestCase extends ESRestTestCase {

    private static final String BASIC_AUTH_VALUE =
            basicAuthHeaderValue("test_user", SecuritySettingsSourceField.TEST_PASSWORD);

    protected static final Version UPGRADE_FROM_VERSION =
        Version.fromString(System.getProperty("tests.upgrade_from_version"));

    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveReposUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveTemplatesUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveRollupJobsUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveILMPoliciesUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveDataStreamsUponCompletion() {
        return true;
    }

    enum ClusterType {
        OLD,
        MIXED,
        UPGRADED;

        public static ClusterType parse(String value) {
            switch (value) {
                case "old_cluster":
                    return OLD;
                case "mixed_cluster":
                    return MIXED;
                case "upgraded_cluster":
                    return UPGRADED;
                default:
                    throw new AssertionError("unknown cluster type: " + value);
            }
        }
    }

    protected static final ClusterType CLUSTER_TYPE = ClusterType.parse(System.getProperty("tests.rest.suite"));

    @Override
    protected Settings restClientSettings() {
        return Settings.builder()
                .put(ThreadContext.PREFIX + ".Authorization", BASIC_AUTH_VALUE)
                .build();
    }

    protected Collection<String> templatesToWaitFor() {
        return Collections.emptyList();
    }

    @Before
    public void setupForTests() throws Exception {
        final Collection<String> expectedTemplates = templatesToWaitFor();

        if (expectedTemplates.isEmpty()) {
            return;
        }

        assertBusy(() -> {
            final Request catRequest = new Request("GET", "_cat/templates?h=n&s=n");
            final Response catResponse = adminClient().performRequest(catRequest);

            final List<String> templates = Streams.readAllLines(catResponse.getEntity().getContent());

            final List<String> missingTemplates = expectedTemplates.stream()
                .filter(each -> templates.contains(each) == false)
                .collect(Collectors.toList());

            // While it's possible to use a Hamcrest matcher for this, the failure is much less legible.
            if (missingTemplates.isEmpty() == false) {
                fail("Some expected templates are missing: " + missingTemplates + ". The templates that exist are: " + templates + "");
            }
        });
    }

    /**
     * Compares the mappings from the template and the index and asserts they
     * are the same.
     *
     * The test is intended to catch cases where an index mapping has been
     * updated dynamically or a write occurred before the template was put.
     * The assertion error message details the differences in the mappings.
     *
     * The Mappings, which are maps of maps, are flattened with the keys built
     * from the keys of the sub-maps appended to the parent key.
     * This makes diffing the 2 maps easier and diffs more comprehensible.
     *
     * @param templateName The template
     * @param indexName The index
     * @throws IOException Yes
     */
    @SuppressWarnings("unchecked")
    public void assertLegacyTemplateMatchesIndexMappings(String templateName,
                                                         String indexName) throws IOException {

        Request getTemplate = new Request("GET", "_template/" + templateName);
        Response templateResponse = client().performRequest(getTemplate);
        assertEquals("missing template [" + templateName + "]", 200, templateResponse.getStatusLine().getStatusCode());

        Map<String, Object> templateMappings = (Map<String, Object>) XContentMapValues.extractValue(entityAsMap(templateResponse),
            templateName, "mappings");

        Request getIndexMapping = new Request("GET", indexName + "/_mapping");
        Response indexMappingResponse = client().performRequest(getIndexMapping);
        assertEquals("error getting mappings for index [" + indexName + "]",
            200, indexMappingResponse.getStatusLine().getStatusCode());

        Map<String, Object> indexMappings = (Map<String, Object>) XContentMapValues.extractValue(entityAsMap(indexMappingResponse),
            indexName, "mappings");

        // We cannot do a simple comparison of mappings e.g
        // Objects.equals(indexMappings, templateMappings) because some
        // templates use strings for the boolean values - "true" and "false"
        // which are automatically converted to Booleans causing the equality
        // to fail.
        boolean isEqual = true;

        // flatten the map of maps
        Map<String, Object> flatTemplateMap = flattenMap(templateMappings);
        Map<String, Object> flatIndexMap = flattenMap(indexMappings);

        Set<String> keysMissingFromIndex = new HashSet<>(flatTemplateMap.keySet());
        keysMissingFromIndex.removeAll(flatIndexMap.keySet());

        Set<String> keysMissingFromTemplate = new HashSet<>(flatIndexMap.keySet());
        keysMissingFromTemplate.removeAll(flatTemplateMap.keySet());

        // In the case of object fields the 'type: object' mapping is set by default.
        // If this does not explicitly appear in the template it is not an error
        // as ES has added the default to the index mappings
        keysMissingFromTemplate.removeIf(key -> key.endsWith(".type") && "object".equals(flatIndexMap.get(key)));

        StringBuilder errorMesssage = new StringBuilder("Error the template mappings [")
            .append(templateName)
            .append("] and index mappings [")
            .append(indexName)
            .append("] are not the same")
            .append(System.lineSeparator());

        if (keysMissingFromIndex.isEmpty() == false) {
            isEqual = false;
            errorMesssage.append("Keys in the template missing from the index mapping: ")
                .append(keysMissingFromIndex)
                .append(System.lineSeparator());
        }

        if (keysMissingFromTemplate.isEmpty() == false) {
            isEqual = false;
            errorMesssage.append("Keys in the index missing from the template mapping: ")
                .append(keysMissingFromTemplate)
                .append(System.lineSeparator());
        }

        // find values that are different for the same key
        Set<String> commonKeys = new TreeSet<>(flatIndexMap.keySet());
        commonKeys.retainAll(flatTemplateMap.keySet());
        for (String key : commonKeys) {
            Object template = flatTemplateMap.get(key);
            Object index = flatIndexMap.get(key);
            if (Objects.equals(template, index) ==  false) {
                // Both maybe be booleans but different representations
                if (index instanceof Boolean && template instanceof String) {
                    if (index.equals(Boolean.parseBoolean((String)template))) {
                        continue;
                    }
                }

                isEqual = false;

                errorMesssage.append("Values for key [").append(key).append("] are different").append(System.lineSeparator());
                errorMesssage.append("    template value [").append(template).append("] ").append(template.getClass().getSimpleName())
                    .append(System.lineSeparator());
                errorMesssage.append("    index value [").append(index).append("] ").append(index.getClass().getSimpleName())
                    .append(System.lineSeparator());
            }
        }

        if (isEqual == false) {
            fail(errorMesssage.toString());
        }
    }

    private Map<String, Object> flattenMap(Map<String, Object> map) {
        return new TreeMap<>(flatten("", map).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private Stream<Map.Entry<String, Object>> flatten(String path, Map<String, Object> map) {
        return map.entrySet()
            .stream()
            .flatMap((e) -> extractValue(path, e));
    }

    @SuppressWarnings("unchecked")
    private Stream<Map.Entry<String, Object>> extractValue(String path, Map.Entry<String, Object> entry) {
        if (entry.getValue() instanceof Map<?, ?>) {
            return flatten(path + "." + entry.getKey(), (Map<String, Object>) entry.getValue());
        } else {
            return Stream.of(new AbstractMap.SimpleEntry<>(path + "." + entry.getKey(), entry.getValue()));
        }
    }
}
