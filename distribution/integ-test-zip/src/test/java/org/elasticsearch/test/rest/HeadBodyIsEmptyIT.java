/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.test.rest;

import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

/**
 * Tests that HTTP HEAD requests don't respond with a body.
 */
public class HeadBodyIsEmptyIT extends ESRestTestCase {
    public void testHeadRoot() throws IOException {
        headTestCase("/", emptyMap(), 350);
        headTestCase("/", singletonMap("pretty", ""), 350);
        headTestCase("/", singletonMap("pretty", "true"), 350);
    }

    private void createTestDoc() throws UnsupportedEncodingException, IOException {
        client().performRequest("PUT", "test/test/1", emptyMap(), new StringEntity("{\"test\": \"test\"}"));
    }

    public void testDocumentExists() throws IOException {
        createTestDoc();
        headTestCase("test/test/1", emptyMap(), 0);
        headTestCase("test/test/1", singletonMap("pretty", "true"), 0);
    }

    public void testIndexExists() throws IOException {
        createTestDoc();
        headTestCase("test", emptyMap(), 0);
        headTestCase("test", singletonMap("pretty", "true"), 0);
    }

    public void testTypeExists() throws IOException {
        createTestDoc();
        headTestCase("test/test", emptyMap(), 0);
        headTestCase("test/test", singletonMap("pretty", "true"), 0);
    }

    private void headTestCase(String url, Map<String, String> params, int length) throws IOException {
        Response response = client().performRequest("HEAD", url, params);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(
            "We expect HEAD requests to have " + length + " + Content-Length but " + url + " didn't",
            Integer.toString(length),
            response.getHeader("Content-Length"));
        assertNull("HEAD requests shouldn't have a response body but " + url + " did", response.getEntity());
    }
}
