/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.ingest;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.test.ESTestCase;
import org.junit.Before;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;

public class RegisteredDomainProcessorFactoryTests extends ESTestCase {

    private RegisteredDomainProcessor.Factory factory;

    @Before
    public void init() {
        factory = new RegisteredDomainProcessor.Factory();
    }

    public void testCreate() throws Exception {
        Map<String, Object> config = new HashMap<>();

        String field = randomAlphaOfLength(6);
        config.put("field", field);
        String targetField = randomAlphaOfLength(6);
        config.put("target_field", targetField);
        String targetETLDField = randomAlphaOfLength(6);
        config.put("target_etld_field", targetETLDField);
        String targetSubdomainField = randomAlphaOfLength(6);
        config.put("target_subdomain_field", targetSubdomainField);
        boolean ignoreMissing = randomBoolean();
        config.put("ignore_missing", ignoreMissing);

        String processorTag = randomAlphaOfLength(10);
        RegisteredDomainProcessor publicSuffixProcessor = factory.create(null, processorTag, null, config);
        assertThat(publicSuffixProcessor.getTag(), equalTo(processorTag));
        assertThat(publicSuffixProcessor.getTargetField(), equalTo(targetField));
        assertThat(publicSuffixProcessor.getTargetETLDField(), equalTo(targetETLDField));
        assertThat(publicSuffixProcessor.getTargetSubdomainField(), equalTo(targetSubdomainField));
        assertThat(publicSuffixProcessor.getIgnoreMissing(), equalTo(ignoreMissing));
    }

    public void testFieldRequired() throws Exception {
        HashMap<String, Object> config = new HashMap<>();
        String processorTag = randomAlphaOfLength(10);
        try {
            factory.create(null, processorTag, null, config);
            fail("factory create should have failed");
        } catch (ElasticsearchParseException e) {
            assertThat(e.getMessage(), equalTo("[field] required property is missing"));
        }
    }

    public void testTargetFieldRequired() throws Exception {
        HashMap<String, Object> config = new HashMap<>();
        String field = randomAlphaOfLength(6);
        config.put("field", field);
        String processorTag = randomAlphaOfLength(10);
        try {
            factory.create(null, processorTag, null, config);
            fail("factory create should have failed");
        } catch (ElasticsearchParseException e) {
            assertThat(e.getMessage(), equalTo("[target_field] required property is missing"));
        }
    }
}
