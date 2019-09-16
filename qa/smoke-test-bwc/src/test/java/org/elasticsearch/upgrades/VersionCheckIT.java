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

package org.elasticsearch.upgrades;

import org.elasticsearch.Version;
import org.elasticsearch.client.Request;

import org.elasticsearch.test.rest.ESRestTestCase;

import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;


public class VersionCheckIT extends ESRestTestCase {

    private static final String EXPECTED_VERSION = System.getProperty("tests.cluster_version");
    private static final String EXPECTED_DISTRO = System.getProperty("tests.cluster_distro");

    @SuppressWarnings("unchecked")
    public void testVersion() throws Exception {
        assertNotNull("Tests expects the tests.cluster_version to be passed in", EXPECTED_VERSION);
        assertNotNull("Tests expects the tests.cluster_distro to be passed in", EXPECTED_DISTRO);

        Map<String, Object> response = entityAsMap(
            client().performRequest(
                new Request("GET", "/_nodes?pretty")
            )
        );
        logger.info("_nodes: {}", response);
        Map<String, Map<String, Object> > nodes = (Map<String, Map<String, Object>>) response.get("nodes");
        for (Map.Entry<String, Map<String, Object>> entry : nodes.entrySet()) {
            String version = entry.getValue().get("version").toString();
            assertEquals(EXPECTED_VERSION, version);
            logger.info("Version of {} is {}", entry.getKey(), version);

            List<String> modules = ((List<Map<String, String>>) entry.getValue().get("modules")).stream()
                .map(each -> each.get("name"))
                .collect(Collectors.toList());

            if (EXPECTED_DISTRO.equals("DEFAULT")) {
                if (Version.fromString(EXPECTED_VERSION).before(Version.fromString("6.3.0"))) {
                    List<String> plugins = ((List<Map<String, String>>) entry.getValue().get("plugins")).stream()
                        .map(each -> each.get("name"))
                        .collect(Collectors.toList());
                    assertTrue(
                        "Default distribution should have x-pack plugins. Is this really the \"default\" distribution ?",
                        plugins.stream().anyMatch(plugin -> plugin.startsWith("x-pack"))
                    );
                } else {
                    assertTrue(
                        "Default distribution should have x-pack modules. Is this really the default distribution ?",
                        modules.stream().anyMatch(module -> module.startsWith("x-pack"))
                    );
                }
            } else if (EXPECTED_DISTRO.equals("OSS")) {
                assertFalse(
                    "The OOS distribution should not contain x-pack modules. Is this really the oss distribution ?",
                    modules.stream().anyMatch(module -> module.startsWith("x-pack"))
                );
            } else if (EXPECTED_DISTRO.equals("INMTEG_TEST_ZIP")) {
                if (modules.size() != 0) {
                    fail("Integ test distribution should have no modules but it had: " + modules);
                }
            } else {
                fail("Unknown distribution type");
            }
            modules.forEach((module) -> logger.info("module: {}", module));

        }
    }

    @Override
    public boolean preserveClusterUponCompletion() {
        // Test is read only, no need to spend time cleaning up
        return true;
    }

}
