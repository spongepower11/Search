/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.connector.syncjob;

import org.elasticsearch.test.ESTestCase;

import static org.hamcrest.Matchers.equalTo;

public class ConnectorSyncJobTriggerMethodTests extends ESTestCase {

    public void testFromString_WithValidTriggerMethodString() {
        ConnectorSyncJobTriggerMethod triggerMethod = ConnectorSyncJobTestUtils.getRandomTriggerMethod();

        assertThat(triggerMethod, equalTo(ConnectorSyncJobTriggerMethod.fromString(triggerMethod.toString())));
    }

    public void testFromString_WithInvalidTriggerMethodString_ExpectException() {
        expectThrows(IllegalArgumentException.class, () -> ConnectorSyncJobTriggerMethod.fromString("invalid string"));
    }

}
