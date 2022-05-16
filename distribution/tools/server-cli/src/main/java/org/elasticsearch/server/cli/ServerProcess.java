/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.server.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.bootstrap.BootstrapInfo;
import org.elasticsearch.bootstrap.ServerArgs;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.ProcessInfo;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput;
import org.elasticsearch.core.IOUtils;
import org.elasticsearch.core.PathUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.server.cli.ProcessUtil.nonInterruptible;

/**
 * A helper to control a {@link Process} running the main Elasticsearch server.
 *
 * <p> The process can be started by calling {@link #start(Terminal, ProcessInfo, ServerArgs, Path)}.
 * The process is controlled by internally sending arguments and control signals on stdin,
 * and receiving control signals on stderr. The start method does not return until the
 * server is ready to process requests and has exited the bootstrap thread.
 *
 * <p> The caller starting a {@link ServerProcess} can do one of several things:
 * <ul>
 *     <li>Block on the server process exiting, by calling {@link #waitFor()}</li>
 *     <li>Detach from the server process by calling {@link #detach()}</li>
 *     <li>Tell the server process to shutdown and wait for it by calling {@link #stop()}</li>
 * </ul>
 */
public class ServerProcess {
    private static final Logger logger = LogManager.getLogger(ServerProcess.class);

    // the actual java process of the server
    private final Process jvmProcess;

    // the thread pumping stderr watching for state change messages
    private final ErrorPumpThread errorPump;

    // a flag marking whether the java process has been detached from
    private volatile boolean detached = false;

    ServerProcess(Process jvmProcess, ErrorPumpThread errorPump) {
        this.jvmProcess = jvmProcess;
        this.errorPump = errorPump;
    }

    interface OptionsBuilder {
        List<String> getJvmOptions(Path configDir, Path pluginsDir, Path tmpDir, String envOptions) throws InterruptedException,
            IOException, UserException;
    }

    interface ProcessStarter {
        Process start(ProcessBuilder pb) throws IOException;
    }

    /**
     * Start a server in a new process.
     *
     * @param terminal A terminal to connect the standard inputs and outputs to for the new process.
     * @param processInfo Info about the current process, for passing through to the subprocess.
     * @param args Arguments to the server process.
     * @param pluginsDir The directory in which plugins can be found
     * @return A running server process that is ready for requests
     * @throws UserException If the process failed during bootstrap
     */
    public static ServerProcess start(Terminal terminal, ProcessInfo processInfo, ServerArgs args, Path pluginsDir) throws UserException {
        return start(terminal, processInfo, args, pluginsDir, JvmOptionsParser::determineJvmOptions, ProcessBuilder::start);
    }

    // package private so tests can mock options building and process starting
    static ServerProcess start(
        Terminal terminal,
        ProcessInfo processInfo,
        ServerArgs args,
        Path pluginsDir,
        OptionsBuilder optionsBuilder,
        ProcessStarter processStarter
    ) throws UserException {
        Process jvmProcess = null;
        ErrorPumpThread errorPump;

        boolean success = false;
        try {
            jvmProcess = createProcess(processInfo, args.configDir(), pluginsDir, optionsBuilder, processStarter);
            errorPump = new ErrorPumpThread(terminal.getErrorWriter(), jvmProcess.getErrorStream());
            errorPump.start();
            logger.info("ES PID: " + jvmProcess.pid());
            sendArgs(args, jvmProcess.getOutputStream());

            String errorMsg = errorPump.waitUntilReady();
            if (errorMsg != null) {
                // something bad happened, wait for the process to exit then rethrow
                int exitCode = jvmProcess.waitFor();
                throw new UserException(exitCode, errorMsg);
            }
            success = true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (success == false && jvmProcess != null && jvmProcess.isAlive()) {
                jvmProcess.destroyForcibly();
            }
        }

        return new ServerProcess(jvmProcess, errorPump);
    }

    /**
     * Detaches the server process from the current process, enabling the current process to exit.
     *
     * @throws IOException If an I/O error occured while reading stderr or closing any of the standard streams
     */
    public synchronized void detach() throws IOException {
        errorPump.drain();
        IOUtils.close(jvmProcess.getOutputStream(), jvmProcess.getInputStream(), jvmProcess.getErrorStream());
    }

    /**
     * Waits for the subprocess to exit.
     */
    public void waitFor() {
        int exitCode = nonInterruptible(jvmProcess::waitFor);
        if (exitCode != ExitCodes.OK) {
            throw new RuntimeException("server process exited with status code " + exitCode);
        }
    }

    public synchronized void stop() {
        if (detached) {
            return;
        }

        sendShutdownMarker();
        errorPump.drain();
        waitFor();
    }

    private static void sendArgs(ServerArgs args, OutputStream processStdin) {
        // DO NOT close the underlying process stdin, since we need to be able to write to it to signal exit
        var out = new OutputStreamStreamOutput(processStdin);
        try {
            args.writeTo(out);
            out.flush();
        } catch (IOException ignore) {
            // A failure to write here means the process has problems, and it will die anyways. We let this fall through
            // so the pump thread can complete, writing out the actual error. All we get here is the failure to write to
            // the process pipe, which isn't helpful to print.
        }
        args.keystorePassword().close();
    }

    private void sendShutdownMarker() {
        try {
            OutputStream os = jvmProcess.getOutputStream();
            os.write(BootstrapInfo.SERVER_SHUTDOWN_MARKER);
            os.flush();
        } catch (IOException e) {
            // process is already effectively dead, fall through to wait for it, or should we SIGKILL?
        }
    }

    private static Process createProcess(
        ProcessInfo processInfo,
        Path configDir,
        Path pluginsDir,
        OptionsBuilder optionsBuilder,
        ProcessStarter processStarter
    ) throws InterruptedException, IOException, UserException {
        Map<String, String> envVars = new HashMap<>(processInfo.envVars());
        Path tempDir = TempDirectory.setup(envVars);
        List<String> jvmOptions = optionsBuilder.getJvmOptions(configDir, pluginsDir, tempDir, envVars.get("ES_JAVA_OPTS"));
        // jvmOptions.add("-Des.path.conf=" + env.configFile());
        jvmOptions.add("-Des.distribution.type=" + processInfo.sysprops().get("es.distribution.type"));

        Path esHome = processInfo.workingDir();
        Path javaHome = PathUtils.get(processInfo.sysprops().get("java.home"));
        List<String> command = new ArrayList<>();
        boolean isWindows = processInfo.sysprops().get("os.name").startsWith("Windows");
        command.add(javaHome.resolve("bin").resolve("java" + (isWindows ? ".exe" : "")).toString());
        command.addAll(jvmOptions);
        command.add("-cp");
        // The '*' isn't allows by the windows filesystem, so we need to force it into the classpath after converting to a string.
        // Thankfully this will all go away when switching to modules, which take the directory instead of a glob.
        command.add(esHome.resolve("lib") + (isWindows ? "\\" : "/") + "*");
        command.add("org.elasticsearch.bootstrap.Elasticsearch");

        var builder = new ProcessBuilder(command);
        builder.environment().putAll(envVars);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        return processStarter.start(builder);
    }
}
