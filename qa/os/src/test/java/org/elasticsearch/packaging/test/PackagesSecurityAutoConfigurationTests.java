/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.packaging.test;

import org.elasticsearch.packaging.util.Installation;
import org.elasticsearch.packaging.util.Packages;
import org.junit.BeforeClass;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.elasticsearch.packaging.util.FileMatcher.Fileness.Directory;
import static org.elasticsearch.packaging.util.FileMatcher.Fileness.File;
import static org.elasticsearch.packaging.util.FileMatcher.file;
import static org.elasticsearch.packaging.util.FileMatcher.p660;
import static org.elasticsearch.packaging.util.FileMatcher.p750;
import static org.elasticsearch.packaging.util.Packages.assertInstalled;
import static org.elasticsearch.packaging.util.Packages.assertRemoved;
import static org.elasticsearch.packaging.util.Packages.installPackage;
import static org.elasticsearch.packaging.util.Packages.verifyPackageInstallation;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeTrue;

public class PackagesSecurityAutoConfigurationTests extends PackagingTestCase {

    @BeforeClass
    public static void filterDistros() {
        assumeTrue("rpm or deb", distribution.isPackage());
    }

    public void test10SecurityAutoConfiguredOnPackageInstall() throws Exception {
        assertRemoved(distribution());
        installation = installPackage(sh, distribution());
        assertInstalled(distribution());
        verifyPackageInstallation(installation, distribution(), sh);
        verifySecurityAutoConfigured(installation);
        assertNotNull(installation.getElasticPassword());
    }

    public void test20SecurityNotAutoConfiguredOnReInstallation() throws Exception {
        // we are testing force upgrading in the current version
        // In such a case, security remains configured from the initial installation, we don't run it again.
        Optional<String> autoConfigDir = getAutoConfigPathDir(installation);
        installation = Packages.forceUpgradePackage(sh, distribution);
        assertInstalled(distribution);
        verifyPackageInstallation(installation, distribution, sh);
        verifySecurityAutoConfigured(installation);
        // Since we did not auto-configure the second time, the directory name should be the same
        assertThat(autoConfigDir.isPresent(), is(true));
        assertThat(getAutoConfigPathDir(installation).isPresent(), is(true));
        assertThat(getAutoConfigPathDir(installation).get(), equalTo(autoConfigDir.get()));
    }

    private static void verifySecurityAutoConfigured(Installation es) throws IOException {
        Optional<String> autoConfigDirName = getAutoConfigPathDir(es);
        assertThat(autoConfigDirName.isPresent(), is(true));
        assertThat(es.config(autoConfigDirName.get()), file(Directory, "root", "elasticsearch", p750));
        Stream.of("http_keystore_local_node.p12", "http_ca.crt", "transport_keystore_all_nodes.p12")
            .forEach(file -> assertThat(es.config(autoConfigDirName.get()).resolve(file), file(File, "root", "elasticsearch", p660)));
        List<String> configLines = Files.readAllLines(es.config("elasticsearch.yml"));

        // This will change when we change all packaging tests to work with security enabled in a follow-up PR.
        // For now, we disable security _after_ installation in ServerUtils#disableSecurityFeatures
        // assertThat(configLines, contains("xpack.security.enabled: true"));
        // assertThat(configLines, contains("xpack.security.http.ssl.enabled: true"));
        // assertThat(configLines, contains("xpack.security.transport.ssl.enabled: true"));
        assertThat(configLines, hasItem("xpack.security.enabled: false"));
        assertThat(configLines, hasItem("xpack.security.http.ssl.enabled: false"));
        assertThat(configLines, hasItem("xpack.security.transport.ssl.enabled: false"));

        assertThat(configLines, hasItem("xpack.security.enrollment.enabled: true"));
        assertThat(configLines, hasItem("xpack.security.transport.ssl.verification_mode: certificate"));
        assertThat(
            configLines,
            hasItem(
                "xpack.security.transport.ssl.keystore.path: "
                    + es.config(autoConfigDirName.get()).resolve("transport_keystore_all_nodes.p12")
            )
        );
        assertThat(
            configLines,
            hasItem(
                "xpack.security.transport.ssl.truststore.path: "
                    + es.config(autoConfigDirName.get()).resolve("transport_keystore_all_nodes.p12")
            )
        );

        assertThat(
            configLines,
            hasItem("xpack.security.http.ssl.keystore.path: " + es.config(autoConfigDirName.get()).resolve("http_keystore_local_node.p12"))
        );
        assertThat(configLines, hasItem("http.host: [_local_, _site_]"));
        assertThat(sh.run(es.bin("elasticsearch-keystore") + " list").stdout, containsString("autoconfig.password_hash"));
    }

}
