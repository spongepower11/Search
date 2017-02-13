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

package org.elasticsearch.rest;

import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.hamcrest.Matcher;

import java.io.IOException;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

public class Netty4HeadBodyIsEmptyIT extends ESRestTestCase {

    public void testHeadRoot() throws IOException {
        headTestCase("/", emptyMap(), greaterThan(0));
        headTestCase("/", singletonMap("pretty", ""), greaterThan(0));
        headTestCase("/", singletonMap("pretty", "true"), greaterThan(0));
    }

    private void createTestDoc() throws IOException {
        createTestDoc("test", "test");
    }

    private void createTestDoc(final String indexName, final String typeName) throws IOException {
        try (XContentBuilder builder = jsonBuilder()) {
            builder.startObject();
            {
                builder.field("test", "test");
            }
            builder.endObject();
            client().performRequest("PUT", "/" + indexName + "/" + typeName + "/" + "1", emptyMap(), new StringEntity(builder.string()));
        }
    }

    public void testDocumentExists() throws IOException {
        createTestDoc();
        headTestCase("test/test/1", emptyMap(), equalTo(0));
        headTestCase("test/test/1", singletonMap("pretty", "true"), equalTo(0));
    }

    public void testIndexExists() throws IOException {
        createTestDoc();
        headTestCase("test", emptyMap(), greaterThan(0));
        headTestCase("test", singletonMap("pretty", "true"), greaterThan(0));
    }

    public void testTypeExists() throws IOException {
        createTestDoc();
        headTestCase("test/test", emptyMap(), equalTo(0));
        headTestCase("test/test", singletonMap("pretty", "true"), equalTo(0));
    }

    public void testAliasExists() throws IOException {
        createTestDoc();
        try (XContentBuilder builder = jsonBuilder()) {
            builder.startObject();
            {
                builder.startArray("actions");
                {
                    builder.startObject();
                    {
                        builder.startObject("add");
                        {
                            builder.field("index", "test");
                            builder.field("alias", "test_alias");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endArray();
            }
            builder.endObject();

            client().performRequest("POST", "_aliases", emptyMap(), new StringEntity(builder.string()));
            headTestCase("/_alias/test_alias", emptyMap(), greaterThan(0));
            headTestCase("/test/_alias/test_alias", emptyMap(), greaterThan(0));
        }
    }

    public void testTemplateExists() throws IOException {
        try (XContentBuilder builder = jsonBuilder()) {
            builder.startObject();
            {
                builder.array("index_patterns", "*");
                builder.startObject("settings");
                {
                    builder.field("number_of_replicas", 0);
                }
                builder.endObject();
            }
            builder.endObject();

            client().performRequest("PUT", "/_template/template", emptyMap(), new StringEntity(builder.string()));
            headTestCase("/_template/template", emptyMap(), greaterThan(0));
        }
    }

    public void testGetSourceAction() throws IOException {
        createTestDoc();
        headTestCase("/test/test/1/_source", emptyMap(), greaterThan(0));
        headTestCase("/test/test/2/_source", emptyMap(), 404, equalTo(0));

        try (XContentBuilder builder = jsonBuilder()) {
            builder.startObject();
            {
                builder.startObject("mappings");
                {
                    builder.startObject("test-no-source");
                    {
                        builder.startObject("_source");
                        {
                            builder.field("enabled", false);
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            builder.endObject();
            client().performRequest("PUT", "/test-no-source", emptyMap(), new StringEntity(builder.string()));
            createTestDoc("test-no-source", "test-no-source");
            headTestCase("/test-no-source/test-no-source/1/_source", emptyMap(), 404, equalTo(0));
        }
    }

    private void headTestCase(final String url, final Map<String, String> params, final Matcher<Integer> matcher) throws IOException {
        headTestCase(url, params, 200, matcher);
    }

    private void headTestCase(
            final String url,
            final Map<String, String> params,
            final int expectedStatusCode,
            final Matcher<Integer> matcher) throws IOException {
        Response response = client().performRequest("HEAD", url, params);
        assertEquals(expectedStatusCode, response.getStatusLine().getStatusCode());
        assertThat(Integer.valueOf(response.getHeader("Content-Length")), matcher);
        assertNull("HEAD requests shouldn't have a response body but " + url + " did", response.getEntity());
    }

}
