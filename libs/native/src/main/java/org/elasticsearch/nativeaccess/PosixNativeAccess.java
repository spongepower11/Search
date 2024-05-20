/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess;

import org.elasticsearch.nativeaccess.lib.NativeLibraryProvider;
import org.elasticsearch.nativeaccess.lib.PosixCLibrary;
import org.elasticsearch.nativeaccess.lib.VectorLibrary;

import java.util.Optional;

abstract class PosixNativeAccess extends AbstractNativeAccess {

    protected final PosixCLibrary libc;
    protected final VectorSimilarityFunctions vectorDistance;
    protected final PosixConstants constants;
    protected final ProcessLimits processLimits;

    PosixNativeAccess(String name, NativeLibraryProvider libraryProvider, PosixConstants constants) {
        super(name, libraryProvider);
        this.libc = libraryProvider.getLibrary(PosixCLibrary.class);
        this.vectorDistance = vectorSimilarityFunctionsOrNull(libraryProvider);
        this.constants = constants;
        this.processLimits = new ProcessLimits(
            getMaxThreads(),
            getRLimit(constants.RLIMIT_AS(), "max size virtual memory"),
            getRLimit(constants.RLIMIT_FSIZE(), "max file size")
        );
    }

    /**
     * Return the maximum number of threads this process may start, or {@link ProcessLimits#UNKNOWN}.
     */
    protected abstract long getMaxThreads();

    /**
     * Return the current rlimit for the given resource.
     * If getrlimit fails, returns {@link ProcessLimits#UNKNOWN}.
     * If the rlimit is unlimited, returns {@link ProcessLimits#UNLIMITED}.
     * */
    protected long getRLimit(int resource, String description) {
        var rlimit = libc.newRLimit();
        if (libc.getrlimit(resource, rlimit) == 0) {
            long value = rlimit.rlim_cur();
            return value == constants.RLIMIT_INFINITY() ? ProcessLimits.UNLIMITED : value;
        } else {
            logger.warn("unable to retrieve " + description + " [" + libc.strerror(libc.errno()) + "]");
            return ProcessLimits.UNKNOWN;
        }
    }

    static VectorSimilarityFunctions vectorSimilarityFunctionsOrNull(NativeLibraryProvider libraryProvider) {
        if (isNativeVectorLibSupported()) {
            var lib = libraryProvider.getLibrary(VectorLibrary.class).getVectorSimilarityFunctions();
            logger.info("Using native vector library; to disable start with -D" + ENABLE_JDK_VECTOR_LIBRARY + "=false");
            return lib;
        }
        return null;
    }

    @Override
    public boolean definitelyRunningAsRoot() {
        return libc.geteuid() == 0;
    }

    @Override
    public ProcessLimits getProcessLimits() {
        return processLimits;
    }

    @Override
    public Optional<VectorSimilarityFunctions> getVectorSimilarityFunctions() {
        return Optional.ofNullable(vectorDistance);
    }

    static boolean isNativeVectorLibSupported() {
        return Runtime.version().feature() >= 21 && (isMacOrLinuxAarch64() || isLinuxAmd64()) && checkEnableSystemProperty();
    }

    /**
     * Returns true iff the architecture is x64 (amd64) and the OS Linux (the OS we currently support for the native lib).
     */
    static boolean isLinuxAmd64() {
        String name = System.getProperty("os.name");
        return (name.startsWith("Linux")) && System.getProperty("os.arch").equals("amd64");
    }

    /** Returns true iff the OS is Mac or Linux, and the architecture is aarch64. */
    static boolean isMacOrLinuxAarch64() {
        String name = System.getProperty("os.name");
        return (name.startsWith("Mac") || name.startsWith("Linux")) && System.getProperty("os.arch").equals("aarch64");
    }

    /** -Dorg.elasticsearch.nativeaccess.enableVectorLibrary=false to disable.*/
    static final String ENABLE_JDK_VECTOR_LIBRARY = "org.elasticsearch.nativeaccess.enableVectorLibrary";

    static boolean checkEnableSystemProperty() {
        return Optional.ofNullable(System.getProperty(ENABLE_JDK_VECTOR_LIBRARY)).map(Boolean::valueOf).orElse(Boolean.TRUE);
    }
}
