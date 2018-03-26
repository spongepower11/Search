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

package org.elasticsearch.client;

import com.carrotsearch.randomizedtesting.generators.RandomNumbers;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.elasticsearch.client.Node.Roles;
import org.junit.After;
import org.junit.Before;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.elasticsearch.client.RestClientTestUtil.randomErrorNoRetryStatusCode;
import static org.elasticsearch.client.RestClientTestUtil.randomErrorRetryStatusCode;
import static org.elasticsearch.client.RestClientTestUtil.randomHttpMethod;
import static org.elasticsearch.client.RestClientTestUtil.randomOkStatusCode;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RestClient} behaviour against multiple hosts: fail-over, blacklisting etc.
 * Relies on a mock http client to intercept requests and return desired responses based on request path.
 */
public class RestClientMultipleHostsTests extends RestClientTestCase {

    private ExecutorService exec = Executors.newFixedThreadPool(1);
    private RestClient restClient;
    private Node[] nodes;
    private HostsTrackingFailureListener failureListener;

    @Before
    @SuppressWarnings("unchecked")
    public void createRestClient() throws IOException {
        CloseableHttpAsyncClient httpClient = mock(CloseableHttpAsyncClient.class);
        when(httpClient.<HttpResponse>execute(any(HttpAsyncRequestProducer.class), any(HttpAsyncResponseConsumer.class),
               any(HttpClientContext.class), any(FutureCallback.class))).thenAnswer(new Answer<Future<HttpResponse>>() {
            @Override
            public Future<HttpResponse> answer(InvocationOnMock invocationOnMock) throws Throwable {
                HttpAsyncRequestProducer requestProducer = (HttpAsyncRequestProducer) invocationOnMock.getArguments()[0];
                final HttpUriRequest request = (HttpUriRequest)requestProducer.generateRequest();
                final HttpHost httpHost = requestProducer.getTarget();
                HttpClientContext context = (HttpClientContext) invocationOnMock.getArguments()[2];
                assertThat(context.getAuthCache().get(httpHost), instanceOf(BasicScheme.class));
                final FutureCallback<HttpResponse> futureCallback = (FutureCallback<HttpResponse>) invocationOnMock.getArguments()[3];
                //return the desired status code or exception depending on the path
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (request.getURI().getPath().equals("/soe")) {
                            futureCallback.failed(new SocketTimeoutException(httpHost.toString()));
                        } else if (request.getURI().getPath().equals("/coe")) {
                            futureCallback.failed(new ConnectTimeoutException(httpHost.toString()));
                        } else if (request.getURI().getPath().equals("/ioe")) {
                            futureCallback.failed(new IOException(httpHost.toString()));
                        } else {
                            int statusCode = Integer.parseInt(request.getURI().getPath().substring(1));
                            StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("http", 1, 1), statusCode, "");
                            futureCallback.completed(new BasicHttpResponse(statusLine));
                        }
                    }
                });
                return null;
            }
        });
        int numNodes = RandomNumbers.randomIntBetween(getRandom(), 2, 5);
        nodes = new Node[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodes[i] = new Node(new HttpHost("localhost", 9200 + i));
        }
        failureListener = new HostsTrackingFailureListener();
        restClient = new RestClient(httpClient, 10000, new Header[0], nodes, null, failureListener);
    }

    /**
     * Shutdown the executor so we don't leak threads into other test runs.
     */
    @After
    public void shutdownExec() {
        exec.shutdown();
    }

    public void testRoundRobinOkStatusCodes() throws IOException {
        int numIters = RandomNumbers.randomIntBetween(getRandom(), 1, 5);
        for (int i = 0; i < numIters; i++) {
            Set<HttpHost> hostsSet = hostsSet();
            for (int j = 0; j < nodes.length; j++) {
                int statusCode = randomOkStatusCode(getRandom());
                Response response = restClient.performRequest(randomHttpMethod(getRandom()), "/" + statusCode);
                assertEquals(statusCode, response.getStatusLine().getStatusCode());
                assertTrue("host not found: " + response.getHost(), hostsSet.remove(response.getHost()));
            }
            assertEquals("every host should have been used but some weren't: " + hostsSet, 0, hostsSet.size());
        }
        failureListener.assertNotCalled();
    }

    public void testRoundRobinNoRetryErrors() throws IOException {
        int numIters = RandomNumbers.randomIntBetween(getRandom(), 1, 5);
        for (int i = 0; i < numIters; i++) {
            Set<HttpHost> hostsSet = hostsSet();
            for (int j = 0; j < nodes.length; j++) {
                String method = randomHttpMethod(getRandom());
                int statusCode = randomErrorNoRetryStatusCode(getRandom());
                try {
                    Response response = restClient.performRequest(method, "/" + statusCode);
                    if (method.equals("HEAD") && statusCode == 404) {
                        //no exception gets thrown although we got a 404
                        assertEquals(404, response.getStatusLine().getStatusCode());
                        assertEquals(statusCode, response.getStatusLine().getStatusCode());
                        assertTrue("host not found: " + response.getHost(), hostsSet.remove(response.getHost()));
                    } else {
                        fail("request should have failed");
                    }
                } catch (ResponseException e) {
                    if (method.equals("HEAD") && statusCode == 404) {
                        throw e;
                    }
                    Response response = e.getResponse();
                    assertEquals(statusCode, response.getStatusLine().getStatusCode());
                    assertTrue("host not found: " + response.getHost(), hostsSet.remove(response.getHost()));
                    assertEquals(0, e.getSuppressed().length);
                }
            }
            assertEquals("every host should have been used but some weren't: " + hostsSet, 0, hostsSet.size());
        }
        failureListener.assertNotCalled();
    }

    public void testRoundRobinRetryErrors() throws IOException {
        String retryEndpoint = randomErrorRetryEndpoint();
        try  {
            restClient.performRequest(randomHttpMethod(getRandom()), retryEndpoint);
            fail("request should have failed");
        } catch (ResponseException e) {
            /*
             * Unwrap the top level failure that was added so the stack trace contains
             * the caller. It wraps the exception that contains the failed hosts.
             */
            e = (ResponseException) e.getCause();
            Set<HttpHost> hostsSet = hostsSet();
            //first request causes all the hosts to be blacklisted, the returned exception holds one suppressed exception each
            failureListener.assertCalled(nodes);
            do {
                Response response = e.getResponse();
                assertEquals(Integer.parseInt(retryEndpoint.substring(1)), response.getStatusLine().getStatusCode());
                assertTrue("host [" + response.getHost() + "] not found, most likely used multiple times",
                        hostsSet.remove(response.getHost()));
                if (e.getSuppressed().length > 0) {
                    assertEquals(1, e.getSuppressed().length);
                    Throwable suppressed = e.getSuppressed()[0];
                    assertThat(suppressed, instanceOf(ResponseException.class));
                    e = (ResponseException)suppressed;
                } else {
                    e = null;
                }
            } while(e != null);
            assertEquals("every host should have been used but some weren't: " + hostsSet, 0, hostsSet.size());
        } catch (IOException e) {
            /*
             * Unwrap the top level failure that was added so the stack trace contains
             * the caller. It wraps the exception that contains the failed hosts.
             */
            e = (IOException) e.getCause();
            Set<HttpHost> hostsSet = hostsSet();
            //first request causes all the hosts to be blacklisted, the returned exception holds one suppressed exception each
            failureListener.assertCalled(nodes);
            do {
                HttpHost httpHost = HttpHost.create(e.getMessage());
                assertTrue("host [" + httpHost + "] not found, most likely used multiple times", hostsSet.remove(httpHost));
                if (e.getSuppressed().length > 0) {
                    assertEquals(1, e.getSuppressed().length);
                    Throwable suppressed = e.getSuppressed()[0];
                    assertThat(suppressed, instanceOf(IOException.class));
                    e = (IOException) suppressed;
                } else {
                    e = null;
                }
            } while(e != null);
            assertEquals("every host should have been used but some weren't: " + hostsSet, 0, hostsSet.size());
        }

        int numIters = RandomNumbers.randomIntBetween(getRandom(), 2, 5);
        for (int i = 1; i <= numIters; i++) {
            //check that one different host is resurrected at each new attempt
            Set<HttpHost> hostsSet = hostsSet();
            for (int j = 0; j < nodes.length; j++) {
                retryEndpoint = randomErrorRetryEndpoint();
                try  {
                    restClient.performRequest(randomHttpMethod(getRandom()), retryEndpoint);
                    fail("request should have failed");
                } catch (ResponseException e) {
                    Response response = e.getResponse();
                    assertThat(response.getStatusLine().getStatusCode(), equalTo(Integer.parseInt(retryEndpoint.substring(1))));
                    assertTrue("host [" + response.getHost() + "] not found, most likely used multiple times",
                            hostsSet.remove(response.getHost()));
                    //after the first request, all hosts are blacklisted, a single one gets resurrected each time
                    failureListener.assertCalled(response.getHost());
                    assertEquals(0, e.getSuppressed().length);
                } catch (IOException e) {
                    /*
                     * Unwrap the top level failure that was added so the stack trace contains
                     * the caller. It wraps the exception that contains the failed hosts.
                     */
                    e = (IOException) e.getCause();
                    HttpHost httpHost = HttpHost.create(e.getMessage());
                    assertTrue("host [" + httpHost + "] not found, most likely used multiple times", hostsSet.remove(httpHost));
                    //after the first request, all hosts are blacklisted, a single one gets resurrected each time
                    failureListener.assertCalled(httpHost);
                    assertEquals(0, e.getSuppressed().length);
                }
            }
            assertEquals("every host should have been used but some weren't: " + hostsSet, 0, hostsSet.size());
            if (getRandom().nextBoolean()) {
                //mark one host back alive through a successful request and check that all requests after that are sent to it
                HttpHost selectedHost = null;
                int iters = RandomNumbers.randomIntBetween(getRandom(), 2, 10);
                for (int y = 0; y < iters; y++) {
                    int statusCode = randomErrorNoRetryStatusCode(getRandom());
                    Response response;
                    try {
                        response = restClient.performRequest(randomHttpMethod(getRandom()), "/" + statusCode);
                    } catch (ResponseException e) {
                        response = e.getResponse();
                    }
                    assertThat(response.getStatusLine().getStatusCode(), equalTo(statusCode));
                    if (selectedHost == null) {
                        selectedHost = response.getHost();
                    } else {
                        assertThat(response.getHost(), equalTo(selectedHost));
                    }
                }
                failureListener.assertNotCalled();
                //let the selected host catch up on number of failures, it gets selected a consecutive number of times as it's the one
                //selected to be retried earlier (due to lower number of failures) till all the hosts have the same number of failures
                for (int y = 0; y < i + 1; y++) {
                    retryEndpoint = randomErrorRetryEndpoint();
                    try {
                        restClient.performRequest(randomHttpMethod(getRandom()), retryEndpoint);
                        fail("request should have failed");
                    } catch (ResponseException e) {
                        Response response = e.getResponse();
                        assertThat(response.getStatusLine().getStatusCode(), equalTo(Integer.parseInt(retryEndpoint.substring(1))));
                        assertThat(response.getHost(), equalTo(selectedHost));
                        failureListener.assertCalled(selectedHost);
                    } catch(IOException e) {
                        /*
                         * Unwrap the top level failure that was added so the stack trace contains
                         * the caller. It wraps the exception that contains the failed hosts.
                         */
                        e = (IOException) e.getCause();
                        HttpHost httpHost = HttpHost.create(e.getMessage());
                        assertThat(httpHost, equalTo(selectedHost));
                        failureListener.assertCalled(selectedHost);
                    }
                }
            }
        }
    }

    public void testWithNodeSelector() throws IOException {
        NodeSelector firstPositionOnly = new NodeSelector() {
            @Override
            public boolean select(Node node) {
                return nodes[0] == node;
            }
        };
        RestClientActions withNodeSelector = restClient.withNodeSelector(firstPositionOnly);
        Response response = withNodeSelector.performRequest("GET", "/200");
        assertEquals(nodes[0].getHost(), response.getHost());
        restClient.close();
    }

    public void testSetNodes() throws IOException {
        Node[] newNodes = new Node[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            Roles roles = i == 0 ? new Roles(false, true, true) : new Roles(true, false, false);
            newNodes[i] = new Node(nodes[i].getHost(), null, null, roles);
        }
        restClient.setNodes(newNodes);
        Response response = restClient
                .withNodeSelector(NodeSelector.NOT_MASTER_ONLY)
                .performRequest("GET", "/200");
        assertEquals(newNodes[0].getHost(), response.getHost());
    }

    private static String randomErrorRetryEndpoint() {
        switch(RandomNumbers.randomIntBetween(getRandom(), 0, 3)) {
            case 0:
                return "/" + randomErrorRetryStatusCode(getRandom());
            case 1:
                return "/coe";
            case 2:
                return "/soe";
            case 3:
                return "/ioe";
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Build a mutable {@link Set} containing all the {@link Node#getHost() hosts}
     * in use by the test.
     */
    private Set<HttpHost> hostsSet() {
        Set<HttpHost> hosts = new HashSet<>();
        for (Node node : nodes) {
            hosts.add(node.getHost());
        }
        return hosts;
    }
}
