/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.testclusters;

import org.elasticsearch.gradle.internal.test.GradleUnitTestCase;

import java.net.URL;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;

public class WaitForHttpResourceTests extends GradleUnitTestCase {

    public void testBuildTrustStoreFromFile() throws Exception {
        final WaitForHttpResource http = new WaitForHttpResource(new URL("https://localhost/"));
        final URL ca = getClass().getResource("/ca.p12");
        assertThat(ca, notNullValue());
        http.setTrustStoreFile(Paths.get(ca.toURI()).toFile());
        http.setTrustStorePassword("password");
        final KeyStore store = http.buildTrustStore();
        final Certificate certificate = store.getCertificate("ca");
        assertThat(certificate, notNullValue());
        assertThat(certificate, instanceOf(X509Certificate.class));
        assertThat(
            ((X509Certificate) certificate).getSubjectX500Principal().toString(),
            equalTo("CN=Elastic Certificate Tool Autogenerated CA")
        );
    }

    public void testBuildTrustStoreFromCA() throws Exception {
        final WaitForHttpResource http = new WaitForHttpResource(new URL("https://localhost/"));
        final URL ca = getClass().getResource("/ca.pem");
        assertThat(ca, notNullValue());
        http.setCertificateAuthorities(Paths.get(ca.toURI()).toFile());
        final KeyStore store = http.buildTrustStore();
        final Certificate certificate = store.getCertificate("cert-0");
        assertThat(certificate, notNullValue());
        assertThat(certificate, instanceOf(X509Certificate.class));
        assertThat(
            ((X509Certificate) certificate).getSubjectX500Principal().toString(),
            equalTo("CN=Elastic Certificate Tool Autogenerated CA")
        );
    }
}
