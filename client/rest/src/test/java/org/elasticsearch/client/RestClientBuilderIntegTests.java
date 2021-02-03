/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.apache.http.HttpHost;
import org.elasticsearch.mocksocket.MockHttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Integration test to validate the builder builds a client with the correct configuration
 */
public class RestClientBuilderIntegTests extends RestClientTestCase {

    private static HttpsServer httpsServer;

    @BeforeClass
    public static void startHttpServer() throws Exception {
        httpsServer = MockHttpServer.createHttps(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(getSslContext()));
        httpsServer.createContext("/", new ResponseHandler());
        httpsServer.start();
    }

    private static class ResponseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            httpExchange.sendResponseHeaders(200, -1);
            httpExchange.close();
        }
    }

    @AfterClass
    public static void stopHttpServers() throws IOException {
        httpsServer.stop(0);
        httpsServer = null;
    }

    public void testBuilderUsesDefaultSSLContext() throws Exception {
        assumeFalse("https://github.com/elastic/elasticsearch/issues/49094", inFipsJvm());
        final SSLContext defaultSSLContext = SSLContext.getDefault();
        try {
            try (RestClient client = buildRestClient()) {
                try {
                    client.performRequest(new Request("GET", "/"));
                    fail("connection should have been rejected due to SSL handshake");
                } catch (Exception e) {
                    assertThat(e, instanceOf(SSLHandshakeException.class));
                }
            }

            SSLContext.setDefault(getSslContext());
            try (RestClient client = buildRestClient()) {
                Response response = client.performRequest(new Request("GET", "/"));
                assertEquals(200, response.getStatusLine().getStatusCode());
            }
        } finally {
            SSLContext.setDefault(defaultSSLContext);
        }
    }

    private RestClient buildRestClient() {
        InetSocketAddress address = httpsServer.getAddress();
        return RestClient.builder(new HttpHost(address.getHostString(), address.getPort(), "https")).build();
    }

    private static SSLContext getSslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance(getProtocol());
        try (InputStream certFile = RestClientBuilderIntegTests.class.getResourceAsStream("/test.crt");
             InputStream keyStoreFile = RestClientBuilderIntegTests.class.getResourceAsStream("/test_truststore.jks")) {
            // Build a keystore of default type programmatically since we can't use JKS keystores to
            // init a KeyManagerFactory in FIPS 140 JVMs.
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, "password".toCharArray());
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Files.readAllBytes(Paths.get(RestClientBuilderIntegTests.class
                .getResource("/test.der").toURI())));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            keyStore.setKeyEntry("mykey", keyFactory.generatePrivate(privateKeySpec), "password".toCharArray(),
                new Certificate[]{certFactory.generateCertificate(certFile)});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "password".toCharArray());
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(keyStoreFile, "password".toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        }
        return sslContext;
    }

    /**
     * The {@link HttpsServer} in the JDK has issues with TLSv1.3 when running in a JDK that supports TLSv1.3 prior to
     * 12.0.1 so we pin to TLSv1.2 when running on an earlier JDK.
     */
    private static String getProtocol() {
        String version = AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty("java.version"));
        String[] parts = version.split("-");
        String[] numericComponents;
        if (parts.length == 1) {
            numericComponents = version.split("\\.");
        } else if (parts.length == 2) {
            numericComponents = parts[0].split("\\.");
        } else {
            throw new IllegalArgumentException("Java version string [" + version + "] could not be parsed.");
        }
        if (numericComponents.length > 0) {
            final int major = Integer.valueOf(numericComponents[0]);
            if (major < 11) {
                return "TLS";
            }
            if (major > 12) {
                return "TLS";
            } else if (major == 12 && numericComponents.length > 2) {
                final int minor = Integer.valueOf(numericComponents[1]);
                if (minor > 0) {
                    return "TLS";
                } else {
                    String patch = numericComponents[2];
                    final int index = patch.indexOf("_");
                    if (index > -1) {
                        patch = patch.substring(0, index);
                    }

                    if (Integer.valueOf(patch) >= 1) {
                        return "TLS";
                    }
                }
            }
        }
        return "TLSv1.2";
    }
}
