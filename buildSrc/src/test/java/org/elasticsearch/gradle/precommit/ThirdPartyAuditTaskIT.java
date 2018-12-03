package org.elasticsearch.gradle.precommit;

import org.elasticsearch.gradle.test.GradleIntegrationTestCase;
import org.gradle.testkit.runner.BuildResult;

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
public class ThirdPartyAuditTaskIT extends GradleIntegrationTestCase {

    public void testElasticsearchIgnored() {
        BuildResult result = getGradleRunner("thirdPartyAudit")
            .withArguments("clean", "empty", "-s",
                "-PcompileOnlyGroup=elasticsearch.gradle:broken-log4j", "-PcompileOnlyVersion=0.0.1",
                "-PcompileGroup=elasticsearch.gradle:dummy-io", "-PcompileVersion=0.0.1"
            )
            .build();
        assertTaskNoSource(result, ":empty");
    }

    public void testWithEmptyRules() {
        BuildResult result = getGradleRunner("thirdPartyAudit")
            .withArguments("clean", "empty", "-s",
                "-PcompileOnlyGroup=other.gradle:broken-log4j", "-PcompileOnlyVersion=0.0.1",
                "-PcompileGroup=other.gradle:dummy-io", "-PcompileVersion=0.0.1"
            )
            .build();

        assertTaskSuccessful(result, ":empty");

        result = getGradleRunner("thirdPartyAudit")
            .withArguments("empty", "-s",
                "-PcompileOnlyGroup=other.gradle:broken-log4j", "-PcompileOnlyVersion=0.0.1",
                "-PcompileGroup=other.gradle:dummy-io", "-PcompileVersion=0.0.1"
            )
            .build();

        assertTaskUpToDate(result, ":empty");

        result = getGradleRunner("thirdPartyAudit")
            .withArguments("empty", "-s",
                "-PcompileOnlyGroup=other.gradle:broken-log4j", "-PcompileOnlyVersion=0.0.1",
                "-PcompileGroup=other.gradle:dummy-io", "-PcompileVersion=0.0.2"
            )
            .build();

        assertTaskSuccessful(result, ":empty");
    }

    public void testViolationFoundAndCompileOnlyIgnored() {
        BuildResult result = getGradleRunner("thirdPartyAudit")
            .withArguments("clean", "absurd", "-s",
                "-PcompileOnlyGroup=other.gradle:broken-log4j", "-PcompileOnlyVersion=0.0.1",
                "-PcompileGroup=other.gradle:dummy-io", "-PcompileVersion=0.0.1"
            )
            .buildAndFail();

        assertTaskFailed(result, ":absurd");
        assertOutputContains(result.getOutput(),
            "> Audit of third party dependencies failed:",
            "  Classes with violations:",
            "    * TestingIoClass"
        );
        assertOutputDoesNotContain(result.getOutput(),"Missing classes:");
    }

    public void testClassNotFoundAndCompileOnlyIgnored() {
        BuildResult result = getGradleRunner("thirdPartyAudit")
            .withArguments("clean", "absurd", "-s",
                "-PcompileGroup=other.gradle:broken-log4j", "-PcompileVersion=0.0.1",
                "-PcompileOnlyGroup=other.gradle:dummy-io", "-PcompileOnlyVersion=0.0.1"
            )
            .buildAndFail();
        assertTaskFailed(result, ":absurd");

        assertOutputContains(result.getOutput(),
            "> Audit of third party dependencies failed:",
            "  Missing classes:",
            "    * org.apache.log4j.Logger"
        );
        assertOutputDoesNotContain(result.getOutput(), "Classes with violations:");
    }

    public void testJarHellWithJDK() {
        BuildResult result = getGradleRunner("thirdPartyAudit")
            .withArguments("clean", "absurd", "-s",
                "-PcompileGroup=other.gradle:jarhellJdk", "-PcompileVersion=0.0.1",
                "-PcompileOnlyGroup=other.gradle:dummy-io", "-PcompileOnlyVersion=0.0.1"
            )
            .buildAndFail();
        assertTaskFailed(result, ":absurd");

        assertOutputContains(result.getOutput(),
            "> Audit of third party dependencies failed:",
            "   Jar Hell with the JDK:",
            "    * java.lang.String"
        );
        assertOutputDoesNotContain(result.getOutput(), "Classes with violations:");
    }

    public void testElasticsearchIgnoredWithViolations() {
        BuildResult result = getGradleRunner("thirdPartyAudit")
            .withArguments("clean", "absurd", "-s",
                "-PcompileOnlyGroup=elasticsearch.gradle:broken-log4j", "-PcompileOnlyVersion=0.0.1",
                "-PcompileGroup=elasticsearch.gradle:dummy-io", "-PcompileVersion=0.0.1"
            )
            .build();
        assertTaskNoSource(result, ":absurd");
    }

}
