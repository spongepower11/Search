/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.snapshots;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.Bits;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.Version;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.metadata.RepositoryMetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.routing.RecoverySource;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardRoutingState;
import org.elasticsearch.cluster.routing.TestShardRouting;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.lucene.uid.Versions;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.TestEnvironment;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.InternalEngineFactory;
import org.elasticsearch.index.fieldvisitor.FieldsVisitor;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.Uid;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.IndexShardState;
import org.elasticsearch.index.shard.IndexShardTestCase;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.snapshots.IndexShardSnapshotStatus;
import org.elasticsearch.indices.recovery.RecoveryState;
import org.elasticsearch.repositories.IndexId;
import org.elasticsearch.repositories.Repository;
import org.elasticsearch.repositories.fs.FsRepository;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static org.elasticsearch.index.mapper.SourceToParse.source;

public class SourceOnlySnapshotShardTests extends IndexShardTestCase {

    public void testSourceIncomplete() throws IOException {
        ShardRouting shardRouting = TestShardRouting.newShardRouting(new ShardId("index", "_na_", 0), randomAlphaOfLength(10), true,
            ShardRoutingState.INITIALIZING, RecoverySource.StoreRecoverySource.EMPTY_STORE_INSTANCE);
        Settings settings = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 0)
            .put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, 1)
            .build();
        IndexMetaData metaData = IndexMetaData.builder(shardRouting.getIndexName())
            .settings(settings)
            .primaryTerm(0, primaryTerm)
            .putMapping("_doc",
                "{\"_source\":{\"enabled\": false}}").build();
        IndexShard shard = newShard(shardRouting, metaData, new InternalEngineFactory());
        recoverShardFromStore(shard);

        for (int i = 0; i < 1; i++) {
            final String id = Integer.toString(i);
            indexDoc(shard, "_doc", id);
        }
        SnapshotId snapshotId = new SnapshotId("test", "test");
        IndexId indexId = new IndexId(shard.shardId().getIndexName(), shard.shardId().getIndex().getUUID());
        SourceOnlySnapshotRepository repository = new SourceOnlySnapshotRepository(createRepository());
        repository.start();
        try (Engine.IndexCommitRef snapshotRef = shard.acquireLastIndexCommit(true)) {
            IndexShardSnapshotStatus indexShardSnapshotStatus = IndexShardSnapshotStatus.newInitializing();
            IllegalStateException illegalStateException = expectThrows(IllegalStateException.class, () ->
                runAsSnapshot(shard.getThreadPool(),
                    () -> repository.snapshotShard(shard, shard.store(), snapshotId, indexId,
                        snapshotRef.getIndexCommit(), indexShardSnapshotStatus)));
            assertEquals("Can't snapshot _source only on an index that has incomplete source ie. has _source disabled or filters the source"
                , illegalStateException.getMessage());
        }
        closeShards(shard);
    }

    public void testIncrementalSnapshot() throws IOException {
        IndexShard shard = newStartedShard();
        for (int i = 0; i < 10; i++) {
            final String id = Integer.toString(i);
            indexDoc(shard, "_doc", id);
        }

        IndexId indexId = new IndexId(shard.shardId().getIndexName(), shard.shardId().getIndex().getUUID());
        SourceOnlySnapshotRepository repository = new SourceOnlySnapshotRepository(createRepository());
        repository.start();
        int totalFileCount = -1;
        try (Engine.IndexCommitRef snapshotRef = shard.acquireLastIndexCommit(true)) {
            IndexShardSnapshotStatus indexShardSnapshotStatus = IndexShardSnapshotStatus.newInitializing();
            SnapshotId snapshotId = new SnapshotId("test", "test");
            runAsSnapshot(shard.getThreadPool(), () -> repository.snapshotShard(shard, shard.store(), snapshotId, indexId, snapshotRef
                    .getIndexCommit(), indexShardSnapshotStatus));
            IndexShardSnapshotStatus.Copy copy = indexShardSnapshotStatus.asCopy();
            assertEquals(copy.getTotalFileCount(), copy.getIncrementalFileCount());
            totalFileCount = copy.getTotalFileCount();
            assertEquals(copy.getStage(), IndexShardSnapshotStatus.Stage.DONE);
        }

        indexDoc(shard, "_doc", Integer.toString(10));
        indexDoc(shard, "_doc", Integer.toString(11));
        try (Engine.IndexCommitRef snapshotRef = shard.acquireLastIndexCommit(true)) {
            SnapshotId snapshotId = new SnapshotId("test_1", "test_1");

            IndexShardSnapshotStatus indexShardSnapshotStatus = IndexShardSnapshotStatus.newInitializing();
            runAsSnapshot(shard.getThreadPool(), () -> repository.snapshotShard(shard, shard.store(), snapshotId, indexId, snapshotRef
                .getIndexCommit(), indexShardSnapshotStatus));
            IndexShardSnapshotStatus.Copy copy = indexShardSnapshotStatus.asCopy();
            // we processed the segments_N file plus _1.si, _1.fdx, _1.fnm, _1.fdt
            assertEquals(5, copy.getIncrementalFileCount());
            // in total we have 4 more files than the previous snap since we don't count the segments_N twice
            assertEquals(totalFileCount+4, copy.getTotalFileCount());
            assertEquals(copy.getStage(), IndexShardSnapshotStatus.Stage.DONE);
        }
        deleteDoc(shard, "_doc", Integer.toString(10));
        try (Engine.IndexCommitRef snapshotRef = shard.acquireLastIndexCommit(true)) {
            SnapshotId snapshotId = new SnapshotId("test_2", "test_2");

            IndexShardSnapshotStatus indexShardSnapshotStatus = IndexShardSnapshotStatus.newInitializing();
            runAsSnapshot(shard.getThreadPool(), () -> repository.snapshotShard(shard, shard.store(), snapshotId, indexId, snapshotRef
                .getIndexCommit(), indexShardSnapshotStatus));
            IndexShardSnapshotStatus.Copy copy = indexShardSnapshotStatus.asCopy();
            // we processed the segments_N file plus _1_1.liv
            assertEquals(2, copy.getIncrementalFileCount());
            // in total we have 5 more files than the previous snap since we don't count the segments_N twice
            assertEquals(totalFileCount+5, copy.getTotalFileCount());
            assertEquals(copy.getStage(), IndexShardSnapshotStatus.Stage.DONE);
        }
        closeShards(shard);
    }

    private String randomDoc() {
        return "{ \"value\" : \"" + randomAlphaOfLength(10) + "\"}";
    }

    public void testRestoreMinmal() throws IOException {
        IndexShard shard = newStartedShard(true);
        int numInitialDocs = randomIntBetween(10, 100);
        for (int i = 0; i < numInitialDocs; i++) {
            final String id = Integer.toString(i);
            indexDoc(shard, "_doc", id, randomDoc());
            if (randomBoolean()) {
                shard.refresh("test");
            }
        }
        for (int i = 0; i < numInitialDocs; i++) {
            final String id = Integer.toString(i);
            if (randomBoolean()) {
                if (rarely()) {
                    deleteDoc(shard, "_doc", id);
                } else {
                    indexDoc(shard, "_doc", id, randomDoc());
                }
            }
            if (frequently()) {
                shard.refresh("test");
            }
        }
        SnapshotId snapshotId = new SnapshotId("test", "test");
        IndexId indexId = new IndexId(shard.shardId().getIndexName(), shard.shardId().getIndex().getUUID());
        SourceOnlySnapshotRepository repository = new SourceOnlySnapshotRepository(createRepository());
        repository.start();
        try (Engine.IndexCommitRef snapshotRef = shard.acquireLastIndexCommit(true)) {
            IndexShardSnapshotStatus indexShardSnapshotStatus = IndexShardSnapshotStatus.newInitializing();
            runAsSnapshot(shard.getThreadPool(), () -> {
                repository.initializeSnapshot(snapshotId, Arrays.asList(indexId),
                    MetaData.builder().put(shard.indexSettings()
                    .getIndexMetaData(), false).build());
                repository.snapshotShard(shard, shard.store(), snapshotId, indexId, snapshotRef.getIndexCommit(), indexShardSnapshotStatus);
            });
            IndexShardSnapshotStatus.Copy copy = indexShardSnapshotStatus.asCopy();
            assertEquals(copy.getTotalFileCount(), copy.getIncrementalFileCount());
            assertEquals(copy.getStage(), IndexShardSnapshotStatus.Stage.DONE);
        }
        shard.refresh("test");
        ShardRouting shardRouting = TestShardRouting.newShardRouting(new ShardId("index", "_na_", 0), randomAlphaOfLength(10), true,
            ShardRoutingState.INITIALIZING,
            new RecoverySource.SnapshotRecoverySource(new Snapshot("src_only", snapshotId), Version.CURRENT, indexId.getId()));
        IndexMetaData metaData = runAsSnapshot(threadPool, () -> repository.getSnapshotIndexMetaData(snapshotId, indexId));
        IndexShard restoredShard = newShard(shardRouting, metaData, null, (c) -> new SourceOnlySnapshotEngine(c), () -> {});
        restoredShard.mapperService().merge(shard.indexSettings().getIndexMetaData(), MapperService.MergeReason.MAPPING_RECOVERY);
        DiscoveryNode discoveryNode = new DiscoveryNode("node_g", buildNewFakeTransportAddress(), Version.CURRENT);
        restoredShard.markAsRecovering("test from snap", new RecoveryState(restoredShard.routingEntry(), discoveryNode, null));
        runAsSnapshot(shard.getThreadPool(), () ->
            assertTrue(restoredShard.restoreFromRepository(repository)));
        assertEquals(restoredShard.recoveryState().getStage(), RecoveryState.Stage.DONE);
        assertEquals(restoredShard.recoveryState().getTranslog().recoveredOperations(), 0);
        assertEquals(IndexShardState.POST_RECOVERY, restoredShard.state());
        restoredShard.refresh("test");
        assertEquals(restoredShard.docStats().getCount(), shard.docStats().getCount());
        expectThrows(UnsupportedOperationException.class,
            () -> restoredShard.get(new Engine.Get(false, false, "_doc", Integer.toString(0),
                new Term("_id", Uid.encodeId(Integer.toString(0))))));
        final IndexShard targetShard;
        try (Engine.Searcher searcher = restoredShard.acquireSearcher("test")) {
            targetShard = reindex(searcher.getDirectoryReader(), new MappingMetaData("_doc",
                restoredShard.mapperService().documentMapper("_doc").meta()));
        }

        for (int i = 0; i < numInitialDocs; i++) {
            Engine.Get get = new Engine.Get(false, false, "_doc", Integer.toString(i), new Term("_id", Uid.encodeId(Integer.toString(i))));
            Engine.GetResult original = shard.get(get);
            Engine.GetResult restored = targetShard.get(get);
            assertEquals(original.exists(), restored.exists());

            if (original.exists()) {
                Document document = original.docIdAndVersion().reader.document(original.docIdAndVersion().docId);
                Document restoredDocument = restored.docIdAndVersion().reader.document(restored.docIdAndVersion().docId);
                for (IndexableField field : document) {
                    assertEquals(document.get(field.name()), restoredDocument.get(field.name()));
                }
            }
            IOUtils.close(original, restored);
        }

        closeShards(shard, restoredShard, targetShard);
    }

    public IndexShard reindex(DirectoryReader reader, MappingMetaData mapping) throws IOException {
        ShardRouting targetShardRouting = TestShardRouting.newShardRouting(new ShardId("target", "_na_", 0), randomAlphaOfLength(10), true,
            ShardRoutingState.INITIALIZING, RecoverySource.StoreRecoverySource.EMPTY_STORE_INSTANCE);
        Settings settings = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 0)
            .put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, 1)
            .build();
        IndexMetaData.Builder metaData = IndexMetaData.builder(targetShardRouting.getIndexName())
            .settings(settings)
            .primaryTerm(0, primaryTerm);
        metaData.putMapping(mapping);
        IndexShard targetShard = newShard(targetShardRouting, metaData.build(), new InternalEngineFactory());
        boolean success = false;
        try {
            recoverShardFromStore(targetShard);
            String index = targetShard.shardId().getIndexName();
            FieldsVisitor rootFieldsVisitor = new FieldsVisitor(true);
            for (LeafReaderContext ctx : reader.leaves()) {
                LeafReader leafReader = ctx.reader();
                Bits liveDocs = leafReader.getLiveDocs();
                for (int i = 0; i < leafReader.maxDoc(); i++) {
                    if (liveDocs == null || liveDocs.get(i)) {
                        rootFieldsVisitor.reset();
                        leafReader.document(i, rootFieldsVisitor);
                        rootFieldsVisitor.postProcess(targetShard.mapperService());
                        Uid uid = rootFieldsVisitor.uid();
                        BytesReference source = rootFieldsVisitor.source();
                        assert source != null : "_source is null but should have been filtered out at snapshot time";
                        Engine.Result result = targetShard.applyIndexOperationOnPrimary(Versions.MATCH_ANY, VersionType.INTERNAL, source
                            (index, uid.type(), uid.id(), source, XContentHelper.xContentType(source))
                            .routing(rootFieldsVisitor.routing()), 1, false);
                        if (result.getResultType() != Engine.Result.Type.SUCCESS) {
                            throw new IllegalStateException("failed applying post restore operation result: " + result
                                .getResultType(), result.getFailure());
                        }
                    }
                }
            }
            targetShard.refresh("test");
            success = true;
        } finally {
            if (success == false) {
                closeShards(targetShard);
            }
        }
        return targetShard;
    }


    /** Create a {@link Environment} with random path.home and path.repo **/
    private Environment createEnvironment() {
        Path home = createTempDir();
        return TestEnvironment.newEnvironment(Settings.builder()
            .put(Environment.PATH_HOME_SETTING.getKey(), home.toAbsolutePath())
            .put(Environment.PATH_REPO_SETTING.getKey(), home.resolve("repo").toAbsolutePath())
            .build());
    }

    /** Create a {@link Repository} with a random name **/
    private Repository createRepository() throws IOException {
        Settings settings = Settings.builder().put("location", randomAlphaOfLength(10)).build();
        RepositoryMetaData repositoryMetaData = new RepositoryMetaData(randomAlphaOfLength(10), FsRepository.TYPE, settings);
        return new FsRepository(repositoryMetaData, createEnvironment(), xContentRegistry());
    }

    private static void runAsSnapshot(ThreadPool pool, Runnable runnable) {
        runAsSnapshot(pool, (Callable<Void>) () -> {
            runnable.run();
            return null;
        });
    }

    private static <T> T runAsSnapshot(ThreadPool pool, Callable<T> runnable) {
        PlainActionFuture<T> future = new PlainActionFuture<>();
        pool.executor(ThreadPool.Names.SNAPSHOT).execute(() -> {
            try {
                future.onResponse(runnable.call());
            } catch (Exception e) {
                future.onFailure(e);
            }
        });
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw ExceptionsHelper.convertToRuntime((Exception) e.getCause());
            } else {
                throw new AssertionError(e.getCause());
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
