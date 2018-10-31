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

package org.elasticsearch.packaging.test;

import junit.framework.TestCase;
import org.elasticsearch.packaging.util.FileUtils;
import org.elasticsearch.packaging.util.Platforms;
import org.elasticsearch.packaging.util.ServerUtils;
import org.elasticsearch.packaging.util.Shell;
import org.elasticsearch.packaging.util.Shell.Result;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static com.carrotsearch.randomizedtesting.RandomizedTest.assumeTrue;
import static java.util.stream.Collectors.joining;
import static org.elasticsearch.packaging.util.Archives.installArchive;
import static org.elasticsearch.packaging.util.Archives.verifyArchiveInstallation;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public abstract class WindowsServiceTestCase extends PackagingTestCase {

    private static final String DEFAULT_ID = "elasticsearch-service-x64";
    private static final String DEFAULT_DISPLAY_NAME = "Elasticsearch " + FileUtils.getCurrentVersion() + " (elasticsearch-service-x64)";
    private static String serviceScript;

    private Shell sh;

    @Before
    public void createShell() {
        sh = new Shell();
    }

    @BeforeClass
    public static void ensureWindows() {
        assumeTrue(Platforms.WINDOWS);
    }

    @After
    public void uninstallService() {
        sh.runIgnoreExitCode(serviceScript + " remove");
    }

    private Result runWithoutJava(String script) {
        // on windows, removing java from PATH and removing JAVA_HOME is less involved than changing the permissions of the java
        // executable. we also don't check permissions in the windows scripts anyway
        final String originalPath = sh.run("$Env:PATH").stdout.trim();
        final String newPath = Arrays.stream(originalPath.split(";"))
            .filter(path -> path.contains("Java") == false)
            .collect(joining(";"));

        // note the lack of a $ when clearing the JAVA_HOME env variable - with a $ it deletes the java home directory
        // https://docs.microsoft.com/en-us/powershell/module/microsoft.powershell.core/providers/environment-provider?view=powershell-6
        //
        // this won't persist to another session so we don't have to reset anything
        return sh.runIgnoreExitCode(
            "$Env:PATH = '" + newPath + "'; " +
                "Remove-Item Env:JAVA_HOME; " +
                script
        );
    }

    private void assertService(String id, String status, String displayName) {
        Result result = sh.run("Get-Service " + id + " | Format-List -Property Name, Status, DisplayName");
        assertThat(result.stdout, containsString("Name        : " + id));
        assertThat(result.stdout, containsString("Status      : " + status));
        assertThat(result.stdout, containsString("DisplayName : " + displayName));
    }

    // runs the service command, dumping all log files on failure
    private void assertCommand(String script) {
        Result result = sh.runIgnoreExitCode(script);
        if (result.exitCode != 0) {
            logger.error("---- Failed to run script: " + script);
            logger.error(result);
            logger.error("Dumping log files\n");
            Result logs = sh.run("$files = Get-ChildItem \"" + installation.logs + "\\elasticsearch.log\"; " +
                "Write-Output $files; " +
                "foreach ($file in $files) {" +
                    "Write-Output \"$file\"; " +
                    "Get-Content \"$file\" " +
                "}");
            logger.error(logs.stdout);
            fail();
        }
    }

    public void test10InstallArchive() {
        installation = installArchive(distribution());
        verifyArchiveInstallation(installation, distribution());
        serviceScript = installation.bin("elasticsearch-service.bat").toString();
    }

    public void test11InstallServiceExeMissing() throws IOException {
        Path serviceExe = installation.bin("elasticsearch-service-x64.exe");
        Path tmpServiceExe = serviceExe.getParent().resolve(serviceExe.getFileName() + ".tmp");
        Files.move(serviceExe, tmpServiceExe);
        Result result =  sh.runIgnoreExitCode(serviceScript + " install");
        assertThat(result.exitCode, equalTo(1));
        assertThat(result.stdout, containsString("elasticsearch-service-x64.exe was not found..."));
        Files.move(tmpServiceExe, serviceExe);
    }

    public void test12InstallService() {
        sh.run(serviceScript + " install");
        assertService(DEFAULT_ID, "Stopped", DEFAULT_DISPLAY_NAME);
        sh.run(serviceScript + " remove");
    }

    public void test13InstallMissingJava() throws IOException {
        Result result = runWithoutJava(serviceScript + " install");
        assertThat(result.exitCode, equalTo(1));
        assertThat(result.stderr, containsString("could not find java; set JAVA_HOME or ensure java is in PATH"));
    }

    public void test14RemoveNotInstalled() {
        Result result = sh.runIgnoreExitCode(serviceScript + " remove");
        assertThat(result.stdout, result.exitCode, equalTo(1));
        assertThat(result.stdout, containsString("Failed removing '" + DEFAULT_ID + "' service"));
    }

    public void test20CustomizeServiceId() {
        String serviceId = "my-es-service";
        String displayName = DEFAULT_DISPLAY_NAME.replace(DEFAULT_ID, serviceId);
        sh.getEnv().put("SERVICE_ID", serviceId);
        sh.run(serviceScript + " install");
        assertService(serviceId, "Stopped", displayName);
        sh.run(serviceScript + " remove");
    }

    public void test21CustomizeServiceDisplayName() {
        String displayName = "my es service display name";
        sh.getEnv().put("SERVICE_DISPLAY_NAME", displayName);
        sh.run(serviceScript + " install");
        assertService(DEFAULT_ID, "Stopped", displayName);
        sh.run(serviceScript + " remove");
    }

    // NOTE: service description is not attainable through any powershell api, so checking it is not possible...

    public void test30StartStop() throws IOException {
        sh.run(serviceScript + " install");
        assertCommand(serviceScript + " start");
        ServerUtils.waitForElasticsearch();
        ServerUtils.runElasticsearchTests();

        assertCommand(serviceScript + " stop");
        assertService(DEFAULT_ID, "Stopped", DEFAULT_DISPLAY_NAME);
        assertCommand(serviceScript + " remove");
        assertCommand("$p = Get-Service -Name \"elasticsearch-service-x64\" -ErrorAction SilentlyContinue;" +
            "echo \"$p\";" +
            "if ($p -eq $Null) {" +
            "  exit 0;" +
            "} else {" +
            "  exit 1;" +
            "}");
    }

    public void test31StartNotInstalled() throws IOException {
        Result result = sh.runIgnoreExitCode(serviceScript + " start");
        assertThat(result.stdout, result.exitCode, equalTo(1));
        assertThat(result.stdout, containsString("Failed starting '" + DEFAULT_ID + "' service"));
    }

    public void test32StopNotStarted() throws IOException {
        sh.run(serviceScript + " install");
        Result result = sh.run(serviceScript + " stop"); // stop is ok when not started
        assertThat(result.stdout, containsString("The service '" + DEFAULT_ID + "' has been stopped"));
    }

    /*
    // TODO: need to make JAVA_HOME resolve at install time for this to work
    // see https://github.com/elastic/elasticsearch/issues/23097
    public void test33JavaChanged() throws IOException {
        sh.run(serviceScript + " install");
        runWithoutJava(serviceScript + "start");
        ServerUtils.waitForElasticsearch();
        sh.run(serviceScript + " stop");
        sh.runIgnoreExitCode("Wait-Process -Name \"elasticsearch-service-x64\" -Timeout 10");
        sh.run(serviceScript + " remove");
    }*/

    public void test60Manager() throws IOException {
        Path serviceMgr = installation.bin("elasticsearch-service-mgr.exe");
        Path tmpServiceMgr = serviceMgr.getParent().resolve(serviceMgr.getFileName() + ".tmp");
        Files.move(serviceMgr, tmpServiceMgr);
        Path fakeServiceMgr = serviceMgr.getParent().resolve("elasticsearch-service-mgr.bat");
        Files.write(fakeServiceMgr, Arrays.asList("echo \"Fake Service Manager GUI\""));
        Shell sh = new Shell();
        Result result =  sh.run(serviceScript + " manager");
        assertThat(result.stdout, containsString("Fake Service Manager GUI"));

        // check failure too
        Files.write(fakeServiceMgr, Arrays.asList("echo \"Fake Service Manager GUI Failure\"", "exit 1"));
        result =  sh.runIgnoreExitCode(serviceScript + " manager");
        TestCase.assertEquals(1, result.exitCode);
        TestCase.assertTrue(result.stdout, result.stdout.contains("Fake Service Manager GUI Failure"));
        Files.move(tmpServiceMgr, serviceMgr);
    }

    public void test70UnknownCommand() {
        Result result = sh.runIgnoreExitCode(serviceScript + " bogus");
        assertThat(result.exitCode, equalTo(1));
        assertThat(result.stdout, containsString("Unknown option \"bogus\""));
    }

    // TODO:
    // custom SERVICE_USERNAME/SERVICE_PASSWORD
    // custom SERVICE_LOG_DIR
    // custom LOG_OPTS (looks like it currently conflicts with setting custom log dir)
    // install and run with java opts
    // install and run java opts Xmx/s (each data size type)
}
