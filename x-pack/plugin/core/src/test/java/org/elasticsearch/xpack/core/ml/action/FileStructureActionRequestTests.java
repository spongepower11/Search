/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ml.action;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.test.AbstractStreamableTestCase;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

public class FileStructureActionRequestTests extends AbstractStreamableTestCase<FileStructureAction.Request> {

    @Override
    protected FileStructureAction.Request createTestInstance() {

        FileStructureAction.Request request = new FileStructureAction.Request();

        if (randomBoolean()) {
            request.setLinesToSample(randomIntBetween(10, 2000));
        }
        request.setSample(new BytesArray(randomByteArrayOfLength(randomIntBetween(1000, 20000))));

        return request;
    }

    @Override
    protected FileStructureAction.Request createBlankInstance() {
        return new FileStructureAction.Request();
    }

    public void testValidateLinesToSample() {

        FileStructureAction.Request request = new FileStructureAction.Request();
        request.setLinesToSample(randomFrom(-1, 0));
        request.setSample(new BytesArray("foo\n"));

        ActionRequestValidationException e = request.validate();
        assertNotNull(e);
        assertThat(e.getMessage(), startsWith("Validation Failed: "));
        assertThat(e.getMessage(), containsString(" lines_to_sample must be positive if specified"));
    }

    public void testValidateSample() {

        FileStructureAction.Request request = new FileStructureAction.Request();
        if (randomBoolean()) {
            request.setSample(BytesArray.EMPTY);
        }

        ActionRequestValidationException e = request.validate();
        assertNotNull(e);
        assertThat(e.getMessage(), startsWith("Validation Failed: "));
        assertThat(e.getMessage(), containsString(" sample must be specified"));
    }
}
