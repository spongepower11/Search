/*
 * Licensed to Elasticsearch under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.elasticsearch.http;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/**
 * Refer to
 * <a href="https://github.com/elastic/elasticsearch/issues/15335">Unsupported
 * methods on REST endpoints should respond with status code 405</a> for more
 * information.
 */
public class RestHttpResponseHeadersIT extends HttpSmokeTestCase {

    /**
     * For an OPTIONS request to a valid REST endpoint, verify that a 200 HTTP
     * response code is returned, and that the response 'Allow' header includes
     * a list of valid HTTP methods for the endpoint (see
     * <a href="https://tools.ietf.org/html/rfc2616#section-9.2">HTTP/1.1 - 9.2
     * - Options</a>).
     */
    public void testValidEndpointOptionsResponseHttpHeader() throws Exception {
        Response response = getRestClient().performRequest("OPTIONS", "/_tasks");
        assertThat(response.getStatusLine().getStatusCode(), is(200));
        assertThat(response.getHeader("Allow"), notNullValue());
        List<String> responseAllowHeaderStringArray =
                Arrays.asList(response.getHeader("Allow").split(","));
        assertThat(responseAllowHeaderStringArray, containsInAnyOrder("GET"));
    }

    /**
     * For requests to a valid REST endpoint using an unsupported HTTP method,
     * verify that a 405 HTTP response code is returned, and that the response
     * 'Allow' header includes a list of valid HTTP methods for the endpoint
     * (see
     * <a href="https://tools.ietf.org/html/rfc2616#section-10.4.6">HTTP/1.1 -
     * 10.4.6 - 405 Method Not Allowed</a>).
     */
    public void testUnsupportedMethodResponseHttpHeader() throws Exception {
        try {
            getRestClient().performRequest("DELETE", "/_tasks");
            fail("Request should have failed with 405 error");
        } catch (ResponseException e) {
            Response response = e.getResponse();
            assertThat(response.getStatusLine().getStatusCode(), is(405));
            assertThat(response.getHeader("Allow"), notNullValue());
            List<String> responseAllowHeaderStringArray =
                    Arrays.asList(response.getHeader("Allow").split(","));
            assertThat(responseAllowHeaderStringArray, containsInAnyOrder("GET"));
        }
    }
    
    /**
     * Test if a POST request to /{index}/_settings matches the update settings
     * handler for /{index}/_settings, and returns a 405 error (see
     * <a href="https://github.com/elastic/elasticsearch/issues/17853">Issue
     * 17853</a> for more information).
     */
    public void testIndexSettingsPostRequest() throws Exception {
        try {
            createIndex("testindex");
            getRestClient().performRequest("POST", "/testindex/_settings");
            fail("Request should have failed with 405 error");
        } catch (ResponseException e) {
            Response response = e.getResponse();
            assertThat(response.getStatusLine().getStatusCode(), is(405));
            assertThat(response.getHeader("Allow"), notNullValue());
            List<String> responseAllowHeaderStringArray =
                    Arrays.asList(response.getHeader("Allow").split(","));
            assertThat(responseAllowHeaderStringArray, containsInAnyOrder("PUT", "GET"));
        }
    }

}
