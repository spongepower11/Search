/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.reservedstate.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.Randomness;
import org.elasticsearch.common.component.AbstractLifecycleComponent;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;

public class FileWatchService extends AbstractLifecycleComponent {

    private static final Logger logger = LogManager.getLogger(FileWatchService.class);
    final String watchedFileName;
    private static final int REGISTER_RETRY_COUNT = 5;

    final Path watchedDirectoryPath;
    private final String threadName;

    private WatchService watchService; // null;
    private Thread watcherThread;
    private FileUpdateState fileUpdateState;
    private WatchKey watchedDirectoryWatchKey;
    private WatchKey parentDirectoryWatchKey;

    private volatile boolean active = false;

    FileWatchService(Path watchedDirectoryPath, String watchedFileName, String threadName) {
        this.watchedDirectoryPath = watchedDirectoryPath;
        this.watchedFileName = watchedFileName;
        this.threadName = threadName;
    }

    public Path watchedDirectory() {
        return watchedDirectoryPath;
    }

    public Path watchedFile() {
        return watchedDirectoryPath.resolve(watchedFileName);
    }

    // platform independent way to tell if a file changed
    // we compare the file modified timestamp, the absolute path (symlinks), and file id on the system
    boolean watchedFileChanged(Path path) throws IOException {
        if (Files.exists(path) == false) {
            return false;
        }

        FileUpdateState previousUpdateState = fileUpdateState;

        BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
        fileUpdateState = new FileUpdateState(attr.lastModifiedTime().toMillis(), path.toRealPath().toString(), attr.fileKey());

        return (previousUpdateState == null || previousUpdateState.equals(fileUpdateState) == false);
    }

    @Override
    protected void doStart() {
        // We start the file watcher when we know we are master from a cluster state change notification.
        // We need the additional active flag, since cluster state can change after we've shutdown the service
        // causing the watcher to start again.
        active = Files.exists(watchedDirectory().getParent());
    }

    @Override
    protected void doStop() {
        active = false;
        logger.debug("Stopping file watch service");
        stopWatcher();
    }

    @Override
    protected void doClose() throws IOException {
        watchService = null;
    }

    boolean isActive() {
        return active;
    }

    public boolean watching() {
        return watcherThread != null;
    }

    synchronized void startWatcher(Runnable processFileAction, Runnable noInitialFileAction) {
        /*
         * We essentially watch for two things:
         *  - the creation of the watched directory (if it doesn't exist), symlink changes to the watched directory
         *  - any changes to files inside the watched directory if it exists, filtering for the particular watched file
         */
        try {
            Path watchedDirPath = watchedDirectory();
            this.watchService = watchedDirPath.getParent().getFileSystem().newWatchService();
            if (Files.exists(watchedDirPath)) {
                watchedDirectoryWatchKey = enableDirWatcher(watchedDirectoryWatchKey, watchedDirPath);
            } else {
                logger.debug("watched directory [{}] not found, will watch for its creation...", watchedDirPath);
            }
            // We watch the parent directory always, even if initially we had a watched directory
            // it can be deleted and created later. We assume the parent directory never goes away, we only
            // register it once for watching.
            parentDirectoryWatchKey = enableDirWatcher(parentDirectoryWatchKey, watchedDirPath.getParent());
        } catch (Exception e) {
            if (watchService != null) {
                try {
                    // this will also close any keys
                    this.watchService.close();
                } catch (Exception ce) {
                    e.addSuppressed(ce);
                } finally {
                    this.watchService = null;
                }
            }

            throw new IllegalStateException("unable to launch a new watch service", e);
        }

        watcherThread = new Thread(() -> watcherThread(processFileAction, noInitialFileAction), "elasticsearch[" + threadName + "]");
        watcherThread.start();
    }

    private void watcherThread(Runnable processFileAction, Runnable noInitialFileAction) {
        try {
            logger.info("file watch service up and running [tid={}]", Thread.currentThread().getId());

            Path filePath = watchedFile();

            if (Files.exists(filePath)) {
                logger.debug("found initial file to watch [{}], applying...", filePath);
                processFileAction.run();
            } else {
                // Notify everyone we don't have any initial file to watch
                noInitialFileAction.run();
            }

            WatchKey key;
            while ((key = watchService.take()) != null) {
                /*
                 * Reading and interpreting watch service events can vary from platform to platform. E.g:
                 * MacOS symlink delete and set (rm -rf operator && ln -s <path to>/file_settings/ operator):
                 *     ENTRY_MODIFY:operator
                 *     ENTRY_CREATE:settings.json
                 *     ENTRY_MODIFY:settings.json
                 * Linux in Docker symlink delete and set (rm -rf operator && ln -s <path to>/file_settings/ operator):
                 *     ENTRY_CREATE:operator
                 * Windows
                 *     ENTRY_CREATE:operator
                 *     ENTRY_MODIFY:operator
                 * After we get an indication that something has changed, we check the timestamp, file id,
                 * real path of our desired file. We don't actually care what changed, we just re-check ourselves.
                 */
                Path dirPath = watchedDirectory();
                if (Files.exists(dirPath)) {
                    try {
                        if (logger.isDebugEnabled()) {
                            key.pollEvents().forEach(e -> logger.debug("{}:{}", e.kind().toString(), e.context().toString()));
                        } else {
                            key.pollEvents();
                        }
                        key.reset();

                        // We re-register the watched directory watch key, because we don't know
                        // if the file name maps to the same native file system file id. Symlinks
                        // are one potential cause of inconsistency here, since their handling by
                        // the WatchService is platform dependent.
                        watchedDirectoryWatchKey = enableDirWatcher(watchedDirectoryWatchKey, dirPath);

                        if (watchedFileChanged(filePath)) {
                            processFileAction.run();
                        }
                    } catch (IOException e) {
                        logger.warn("encountered I/O error while watching file", e);
                    }
                } else {
                    key.pollEvents();
                    key.reset();
                }
            }
        } catch (ClosedWatchServiceException | InterruptedException expected) {
            logger.info("shutting down watcher thread");
        } catch (Exception e) {
            logger.error("shutting down watcher thread with exception", e);
        }
    }

    synchronized void stopWatcher() {
        if (watching()) {
            logger.debug("stopping watcher ...");
            // make sure watch service is closed whatever
            // this will also close any outstanding keys
            try (var ws = this.watchService) {
                watcherThread.interrupt();
                watcherThread.join();

                // make sure any keys are closed - if watchService.close() throws, it may not close the keys first
                if (parentDirectoryWatchKey != null) {
                    parentDirectoryWatchKey.cancel();
                }
                if (watchedDirectoryWatchKey != null) {
                    watchedDirectoryWatchKey.cancel();
                }
            } catch (IOException e) {
                logger.warn("encountered exception while closing watch service", e);
            } catch (InterruptedException interruptedException) {
                logger.info("interrupted while closing the watch service", interruptedException);
            } finally {
                watcherThread = null;
                watchedDirectoryWatchKey = null;
                parentDirectoryWatchKey = null;
                logger.info("watcher service stopped");
            }
        } else {
            logger.trace("file watch service already stopped");
        }
    }

    // package private for testing
    long retryDelayMillis(int failedCount) {
        assert failedCount < 31; // don't let the count overflow
        return 100 * (1 << failedCount) + Randomness.get().nextInt(10); // add a bit of jitter to avoid two processes in lockstep
    }

    // package private for testing
    WatchKey enableDirWatcher(WatchKey previousKey, Path watchedDir) throws IOException, InterruptedException {
        if (previousKey != null) {
            previousKey.cancel();
        }
        int retryCount = 0;

        do {
            try {
                return watchedDir.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE
                );
            } catch (IOException e) {
                if (retryCount == REGISTER_RETRY_COUNT - 1) {
                    throw e;
                }
                Thread.sleep(retryDelayMillis(retryCount));
                retryCount++;
            }
        } while (true);
    }

    /**
     * Holds information about the last known state of the file we watched. We use this
     * class to determine if a file has been changed.
     */
    record FileUpdateState(long timestamp, String path, Object fileKey) {}
}
