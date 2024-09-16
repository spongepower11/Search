/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.blobcache.shared;

import org.apache.lucene.store.AlreadyClosedException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.IOUtils;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.env.TestEnvironment;
import org.elasticsearch.node.Node;
import org.elasticsearch.test.ESTestCase;

import java.nio.ByteBuffer;
import java.nio.file.Files;

public class SharedBytesTests extends ESTestCase {

    public void testReleasesFileCorrectly() throws Exception {
        int regions = randomIntBetween(1, 10);
        var nodeSettings = Settings.builder()
            .put(Node.NODE_NAME_SETTING.getKey(), "node")
            .put("path.home", createTempDir())
            .putList(Environment.PATH_DATA_SETTING.getKey(), createTempDir().toString())
            .build();
        try (var nodeEnv = new NodeEnvironment(nodeSettings, TestEnvironment.newEnvironment(nodeSettings))) {
            boolean mmap = IOUtils.WINDOWS == false && randomBoolean();
            final SharedBytes sharedBytes = new SharedBytes(
                regions,
                randomIntBetween(1, 16) * 4096,
                nodeEnv,
                ignored -> {},
                ignored -> {},
                mmap
            );
            final var sharedBytesPath = nodeEnv.nodeDataPaths()[0].resolve("shared_snapshot_cache");
            assertTrue(Files.exists(sharedBytesPath));
            sharedBytes.decRef();
            assertFalse(Files.exists(sharedBytesPath));

            SharedBytes.IO fileChannel = sharedBytes.getFileChannel(0);
            if (mmap == false) {
                assertThrows(AlreadyClosedException.class, () -> fileChannel.read(ByteBuffer.allocate(4096), 0));
            }
            assertThrows(AlreadyClosedException.class, () -> fileChannel.write(ByteBuffer.allocate(4096), 0));
        }
    }
}
