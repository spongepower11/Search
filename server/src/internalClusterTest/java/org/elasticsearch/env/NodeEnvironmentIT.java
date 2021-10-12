/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.env;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.node.DiscoveryNodeRole;
import org.elasticsearch.core.CheckedConsumer;
import org.elasticsearch.core.PathUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.gateway.PersistedClusterStateService;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.InternalTestCluster;
import org.elasticsearch.test.NodeRoles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.test.NodeRoles.nonDataNode;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST, numDataNodes = 0)
public class NodeEnvironmentIT extends ESIntegTestCase {
    public void testStartFailureOnDataForNonDataNode() throws Exception {
        final String indexName = "test-fail-on-data";

        logger.info("--> starting one node");
        final boolean writeDanglingIndices = randomBoolean();
        String node = internalCluster().startNode(Settings.builder()
            .put(IndicesService.WRITE_DANGLING_INDICES_INFO_SETTING.getKey(), writeDanglingIndices).build());
        Settings dataPathSettings = internalCluster().dataPathSettings(node);

        logger.info("--> creating index");
        prepareCreate(indexName, Settings.builder()
            .put("index.number_of_shards", 1)
            .put("index.number_of_replicas", 0)
        ).get();
        final String indexUUID = resolveIndex(indexName).getUUID();
        if (writeDanglingIndices) {
            assertBusy(() -> internalCluster().getInstances(IndicesService.class).forEach(
                indicesService -> assertTrue(indicesService.allPendingDanglingIndicesWritten())));
        }

        logger.info("--> restarting the node without the data and master roles");
        IllegalStateException ex = expectThrows(IllegalStateException.class,
            "node not having the data and master roles while having existing index metadata must fail",
            () ->
                internalCluster().restartRandomDataNode(new InternalTestCluster.RestartCallback() {
                    @Override
                    public Settings onNodeStopped(String nodeName) {
                        return NodeRoles.removeRoles(nonDataNode(), Set.of(DiscoveryNodeRole.MASTER_ROLE));
                    }
                }));
        if (writeDanglingIndices) {
            assertThat(ex.getMessage(), startsWith("node does not have the data and master roles but has index metadata"));
        } else {
            assertThat(ex.getMessage(), startsWith("node does not have the data role but has shard data"));
        }

        logger.info("--> start the node again with data and master roles");
        internalCluster().startNode(dataPathSettings);

        logger.info("--> indexing a simple document");
        client().prepareIndex(indexName).setId("1").setSource("field1", "value1").get();

        logger.info("--> restarting the node without the data role");
        ex = expectThrows(IllegalStateException.class,
            "node not having the data role while having existing shard data must fail",
            () ->
                internalCluster().restartRandomDataNode(new InternalTestCluster.RestartCallback() {
                    @Override
                    public Settings onNodeStopped(String nodeName) {
                        return nonDataNode();
                    }
                }));
        assertThat(ex.getMessage(), containsString(indexUUID));
        assertThat(ex.getMessage(), startsWith("node does not have the data role but has shard data"));
    }

    private IllegalStateException expectThrowsOnRestart(CheckedConsumer<Path[], Exception> onNodeStopped) {
        internalCluster().startNode();
        final Path[] dataPaths = internalCluster().getInstance(NodeEnvironment.class).nodeDataPaths();
        return expectThrows(IllegalStateException.class,
            () -> internalCluster().restartRandomDataNode(new InternalTestCluster.RestartCallback() {
                @Override
                public Settings onNodeStopped(String nodeName) {
                    try {
                        onNodeStopped.accept(dataPaths);
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    return Settings.EMPTY;
                }
            }));
    }

    public void testFailsToStartIfDowngraded() {
        final IllegalStateException illegalStateException = expectThrowsOnRestart(dataPaths ->
            PersistedClusterStateService.overrideVersion(NodeMetadataTests.tooNewVersion(), dataPaths));
        assertThat(illegalStateException.getMessage(),
            allOf(startsWith("cannot downgrade a node from version ["), endsWith("] to version [" + Version.CURRENT + "]")));
    }

    public void testFailsToStartIfUpgradedTooFar() {
        final IllegalStateException illegalStateException = expectThrowsOnRestart(dataPaths ->
            PersistedClusterStateService.overrideVersion(NodeMetadataTests.tooOldVersion(), dataPaths));
        assertThat(illegalStateException.getMessage(),
            allOf(startsWith("cannot upgrade a node from version ["), endsWith("] directly to version [" + Version.CURRENT + "]")));
    }

    public void testUpgradeDataFolder() throws IOException, InterruptedException {
        String node = internalCluster().startNode();
        prepareCreate("test").get();
        indexRandom(true, client().prepareIndex("test").setId("1").setSource("{}", XContentType.JSON));
        String nodeId = client().admin().cluster().prepareState().get().getState().nodes().getMasterNodeId();

        final Settings dataPathSettings = internalCluster().dataPathSettings(node);
        internalCluster().stopRandomDataNode();

        // simulate older data path layout by moving data under "nodes/0" folder
        final List<Path> dataPaths = List.of(PathUtils.get(Environment.PATH_DATA_SETTING.get(dataPathSettings)));
        dataPaths.forEach(path -> {
                final Path targetPath = path.resolve("nodes").resolve("0");
                try {
                    Files.createDirectories(targetPath);

                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                        for (Path subPath : stream) {
                            String fileName = subPath.getFileName().toString();
                            Path targetSubPath = targetPath.resolve(fileName);
                            if (fileName.equals("nodes") == false) {
                                Files.move(subPath, targetSubPath, StandardCopyOption.ATOMIC_MOVE);
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

        dataPaths.forEach(path -> assertTrue(Files.exists(path.resolve("nodes"))));

        // create extra file/folder, and check that upgrade fails
        if (dataPaths.isEmpty() == false) {
            final Path badFileInNodesDir = Files.createTempFile(randomFrom(dataPaths).resolve("nodes"), "bad", "file");
            IllegalStateException ise = expectThrows(IllegalStateException.class, () -> internalCluster().startNode(dataPathSettings));
            assertThat(ise.getMessage(), containsString("unexpected file/folder encountered during data folder upgrade"));
            Files.delete(badFileInNodesDir);

            final Path badFolderInNodesDir = Files.createDirectories(randomFrom(dataPaths).resolve("nodes").resolve("bad-folder"));
            ise = expectThrows(IllegalStateException.class, () -> internalCluster().startNode(dataPathSettings));
            assertThat(ise.getMessage(), containsString("unexpected file/folder encountered during data folder upgrade"));
            Files.delete(badFolderInNodesDir);

            final Path badFile = Files.createTempFile(randomFrom(dataPaths).resolve("nodes").resolve("0"), "bad", "file");
            ise = expectThrows(IllegalStateException.class, () -> internalCluster().startNode(dataPathSettings));
            assertThat(ise.getMessage(), containsString("unexpected file/folder encountered during data folder upgrade"));
            Files.delete(badFile);

            final Path badFolder = Files.createDirectories(randomFrom(dataPaths).resolve("nodes").resolve("0").resolve("bad-folder"));
            ise = expectThrows(IllegalStateException.class, () -> internalCluster().startNode(dataPathSettings));
            assertThat(ise.getMessage(), containsString("unexpected folder encountered during data folder upgrade"));
            Files.delete(badFolder);

            final Path randomDataPath = randomFrom(dataPaths);
            final Path conflictingFolder = randomDataPath.resolve("indices");
            final Path sourceFolder = randomDataPath.resolve("nodes").resolve("0").resolve("indices");
            if (Files.exists(sourceFolder) && Files.exists(conflictingFolder) == false) {
                Files.createDirectories(conflictingFolder);
                ise = expectThrows(IllegalStateException.class, () -> internalCluster().startNode(dataPathSettings));
                assertThat(ise.getMessage(), containsString("target folder already exists during data folder upgrade"));
                Files.delete(conflictingFolder);
            }
        }

        // check that upgrade works
        dataPaths.forEach(path -> assertTrue(Files.exists(path.resolve("nodes"))));
        internalCluster().startNode(dataPathSettings);
        dataPaths.forEach(path -> assertFalse(Files.exists(path.resolve("nodes"))));
        assertEquals(nodeId, client().admin().cluster().prepareState().get().getState().nodes().getMasterNodeId());
        assertTrue(indexExists("test"));
        ensureYellow("test");
        assertHitCount(client().prepareSearch().setQuery(matchAllQuery()).get(), 1L);
    }
}
