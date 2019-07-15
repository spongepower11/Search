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

package org.elasticsearch.repositories.blobstore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.RateLimiter;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRunnable;
import org.elasticsearch.action.support.GroupedActionListener;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.metadata.RepositoryMetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.Numbers;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.blobstore.BlobContainer;
import org.elasticsearch.common.blobstore.BlobMetaData;
import org.elasticsearch.common.blobstore.BlobPath;
import org.elasticsearch.common.blobstore.BlobStore;
import org.elasticsearch.common.blobstore.fs.FsBlobContainer;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.compress.NotXContentException;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.common.lucene.store.InputStreamIndexInput;
import org.elasticsearch.common.metrics.CounterMetric;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.snapshots.IndexShardRestoreFailedException;
import org.elasticsearch.index.snapshots.IndexShardSnapshotException;
import org.elasticsearch.index.snapshots.IndexShardSnapshotFailedException;
import org.elasticsearch.index.snapshots.IndexShardSnapshotStatus;
import org.elasticsearch.index.snapshots.blobstore.BlobStoreIndexShardSnapshot;
import org.elasticsearch.index.snapshots.blobstore.BlobStoreIndexShardSnapshots;
import org.elasticsearch.index.snapshots.blobstore.RateLimitingInputStream;
import org.elasticsearch.index.snapshots.blobstore.SlicedInputStream;
import org.elasticsearch.index.snapshots.blobstore.SnapshotFiles;
import org.elasticsearch.index.store.Store;
import org.elasticsearch.index.store.StoreFileMetaData;
import org.elasticsearch.indices.recovery.RecoveryState;
import org.elasticsearch.repositories.IndexId;
import org.elasticsearch.repositories.Repository;
import org.elasticsearch.repositories.RepositoryData;
import org.elasticsearch.repositories.RepositoryException;
import org.elasticsearch.repositories.RepositoryVerificationException;
import org.elasticsearch.snapshots.InvalidSnapshotNameException;
import org.elasticsearch.snapshots.SnapshotCreationException;
import org.elasticsearch.snapshots.SnapshotException;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotMissingException;
import org.elasticsearch.snapshots.SnapshotShardFailure;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.index.snapshots.blobstore.BlobStoreIndexShardSnapshot.FileInfo.canonicalName;

/**
 * BlobStore - based implementation of Snapshot Repository
 * <p>
 * This repository works with any {@link BlobStore} implementation. The blobStore could be (and preferred) lazy initialized in
 * {@link #createBlobStore()}.
 * <p>
 * BlobStoreRepository maintains the following structure in the blob store
 * <pre>
 * {@code
 *   STORE_ROOT
 *   |- index-N           - JSON serialized {@link RepositoryData} containing a list of all snapshot ids and the indices belonging to
 *   |                      each snapshot, N is the generation of the file
 *   |- index.latest      - contains the numeric value of the latest generation of the index file (i.e. N from above)
 *   |- incompatible-snapshots - list of all snapshot ids that are no longer compatible with the current version of the cluster
 *   |- snap-20131010.dat - SMILE serialized {@link SnapshotInfo} for snapshot "20131010"
 *   |- meta-20131010.dat - SMILE serialized {@link MetaData} for snapshot "20131010" (includes only global metadata)
 *   |- snap-20131011.dat - SMILE serialized {@link SnapshotInfo} for snapshot "20131011"
 *   |- meta-20131011.dat - SMILE serialized {@link MetaData} for snapshot "20131011"
 *   .....
 *   |- indices/ - data for all indices
 *      |- Ac1342-B_x/ - data for index "foo" which was assigned the unique id of Ac1342-B_x in the repository
 *      |  |- meta-20131010.dat - JSON Serialized {@link IndexMetaData} for index "foo"
 *      |  |- 0/ - data for shard "0" of index "foo"
 *      |  |  |- __1                      \  (files with numeric names were created by older ES versions)
 *      |  |  |- __2                      |
 *      |  |  |- __VPO5oDMVT5y4Akv8T_AO_A |- files from different segments see snap-* for their mappings to real segment files
 *      |  |  |- __1gbJy18wS_2kv1qI7FgKuQ |
 *      |  |  |- __R8JvZAHlSMyMXyZc2SS8Zg /
 *      |  |  .....
 *      |  |  |- snap-20131010.dat - SMILE serialized {@link BlobStoreIndexShardSnapshot} for snapshot "20131010"
 *      |  |  |- snap-20131011.dat - SMILE serialized {@link BlobStoreIndexShardSnapshot} for snapshot "20131011"
 *      |  |  |- index-123 - SMILE serialized {@link BlobStoreIndexShardSnapshots} for the shard
 *      |  |
 *      |  |- 1/ - data for shard "1" of index "foo"
 *      |  |  |- __1
 *      |  |  .....
 *      |  |
 *      |  |-2/
 *      |  ......
 *      |
 *      |- 1xB0D8_B3y/ - data for index "bar" which was assigned the unique id of 1xB0D8_B3y in the repository
 *      ......
 * }
 * </pre>
 */
public abstract class BlobStoreRepository extends AbstractLifecycleComponent implements Repository {
    private static final Logger logger = LogManager.getLogger(BlobStoreRepository.class);

    protected final RepositoryMetaData metadata;

    protected final NamedXContentRegistry namedXContentRegistry;

    protected final ThreadPool threadPool;

    private static final int BUFFER_SIZE = 4096;

    private static final String SNAPSHOT_PREFIX = "snap-";

    private static final String SNAPSHOT_CODEC = "snapshot";

    private static final String INDEX_FILE_PREFIX = "index-";

    private static final String INDEX_LATEST_BLOB = "index.latest";

    private static final String TESTS_FILE = "tests-";

    private static final String METADATA_PREFIX = "meta-";

    public static final String METADATA_NAME_FORMAT = METADATA_PREFIX + "%s.dat";

    private static final String METADATA_CODEC = "metadata";

    private static final String INDEX_METADATA_CODEC = "index-metadata";

    public static final String SNAPSHOT_NAME_FORMAT = SNAPSHOT_PREFIX + "%s.dat";

    private static final String SNAPSHOT_INDEX_PREFIX = "index-";

    private static final String SNAPSHOT_INDEX_NAME_FORMAT = SNAPSHOT_INDEX_PREFIX + "%s";

    private static final String SNAPSHOT_INDEX_CODEC = "snapshots";

    private static final String DATA_BLOB_PREFIX = "__";

    private final Settings settings;

    private final boolean compress;

    private final RateLimiter snapshotRateLimiter;

    private final RateLimiter restoreRateLimiter;

    private final CounterMetric snapshotRateLimitingTimeInNanos = new CounterMetric();

    private final CounterMetric restoreRateLimitingTimeInNanos = new CounterMetric();

    private ChecksumBlobStoreFormat<MetaData> globalMetaDataFormat;

    private ChecksumBlobStoreFormat<IndexMetaData> indexMetaDataFormat;

    private ChecksumBlobStoreFormat<SnapshotInfo> snapshotFormat;

    private final boolean readOnly;

    private final ChecksumBlobStoreFormat<BlobStoreIndexShardSnapshot> indexShardSnapshotFormat;

    private final ChecksumBlobStoreFormat<BlobStoreIndexShardSnapshots> indexShardSnapshotsFormat;

    private final Object lock = new Object();

    private final SetOnce<BlobContainer> blobContainer = new SetOnce<>();

    private final SetOnce<BlobStore> blobStore = new SetOnce<>();

    /**
     * Constructs new BlobStoreRepository
     * @param metadata   The metadata for this repository including name and settings
     * @param settings   Settings for the node this repository object is created on
     * @param threadPool Threadpool to run long running repository manipulations on asynchronously
     */
    protected BlobStoreRepository(RepositoryMetaData metadata, Settings settings, boolean compress,
                                  NamedXContentRegistry namedXContentRegistry, ThreadPool threadPool) {
        this.settings = settings;
        this.compress = compress;
        this.metadata = metadata;
        this.namedXContentRegistry = namedXContentRegistry;
        this.threadPool = threadPool;
        snapshotRateLimiter = getRateLimiter(metadata.settings(), "max_snapshot_bytes_per_sec", new ByteSizeValue(40, ByteSizeUnit.MB));
        restoreRateLimiter = getRateLimiter(metadata.settings(), "max_restore_bytes_per_sec", new ByteSizeValue(40, ByteSizeUnit.MB));
        readOnly = metadata.settings().getAsBoolean("readonly", false);


        indexShardSnapshotFormat = new ChecksumBlobStoreFormat<>(SNAPSHOT_CODEC, SNAPSHOT_NAME_FORMAT,
            BlobStoreIndexShardSnapshot::fromXContent, namedXContentRegistry, compress);
        indexShardSnapshotsFormat = new ChecksumBlobStoreFormat<>(SNAPSHOT_INDEX_CODEC, SNAPSHOT_INDEX_NAME_FORMAT,
            BlobStoreIndexShardSnapshots::fromXContent, namedXContentRegistry, compress);
    }

    @Override
    protected void doStart() {
        ByteSizeValue chunkSize = chunkSize();
        if (chunkSize != null && chunkSize.getBytes() <= 0) {
            throw new IllegalArgumentException("the chunk size cannot be negative: [" + chunkSize + "]");
        }
        globalMetaDataFormat = new ChecksumBlobStoreFormat<>(METADATA_CODEC, METADATA_NAME_FORMAT,
            MetaData::fromXContent, namedXContentRegistry, compress);
        indexMetaDataFormat = new ChecksumBlobStoreFormat<>(INDEX_METADATA_CODEC, METADATA_NAME_FORMAT,
            IndexMetaData::fromXContent, namedXContentRegistry, compress);
        snapshotFormat = new ChecksumBlobStoreFormat<>(SNAPSHOT_CODEC, SNAPSHOT_NAME_FORMAT,
            SnapshotInfo::fromXContentInternal, namedXContentRegistry, compress);
    }

    @Override
    protected void doStop() {}

    @Override
    protected void doClose() {
        BlobStore store;
        // to close blobStore if blobStore initialization is started during close
        synchronized (lock) {
            store = blobStore.get();
        }
        if (store != null) {
            try {
                store.close();
            } catch (Exception t) {
                logger.warn("cannot close blob store", t);
            }
        }
    }

    public ThreadPool threadPool() {
        return threadPool;
    }

    // package private, only use for testing
    BlobContainer getBlobContainer() {
        return blobContainer.get();
    }

    // for test purposes only
    protected BlobStore getBlobStore() {
        return blobStore.get();
    }

    /**
     * maintains single lazy instance of {@link BlobContainer}
     */
    protected BlobContainer blobContainer() {
        assertSnapshotOrGenericThread();

        BlobContainer blobContainer = this.blobContainer.get();
        if (blobContainer == null) {
           synchronized (lock) {
               blobContainer = this.blobContainer.get();
               if (blobContainer == null) {
                   blobContainer = blobStore().blobContainer(basePath());
                   this.blobContainer.set(blobContainer);
               }
           }
        }

        return blobContainer;
    }

    /**
     * Maintains single lazy instance of {@link BlobStore}.
     * Public for testing.
     */
    public BlobStore blobStore() {
        assertSnapshotOrGenericThread();

        BlobStore store = blobStore.get();
        if (store == null) {
            synchronized (lock) {
                store = blobStore.get();
                if (store == null) {
                    if (lifecycle.started() == false) {
                        throw new RepositoryException(metadata.name(), "repository is not in started state");
                    }
                    try {
                        store = createBlobStore();
                    } catch (RepositoryException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RepositoryException(metadata.name(), "cannot create blob store" , e);
                    }
                    blobStore.set(store);
                }
            }
        }
        return store;
    }

    /**
     * Creates new BlobStore to read and write data.
     */
    protected abstract BlobStore createBlobStore() throws Exception;

    /**
     * Returns base path of the repository
     */
    public abstract BlobPath basePath();

    /**
     * Returns true if metadata and snapshot files should be compressed
     *
     * @return true if compression is needed
     */
    protected final boolean isCompress() {
        return compress;
    }

    /**
     * Returns data file chunk size.
     * <p>
     * This method should return null if no chunking is needed.
     *
     * @return chunk size
     */
    protected ByteSizeValue chunkSize() {
        return null;
    }

    @Override
    public RepositoryMetaData getMetadata() {
        return metadata;
    }

    @Override
    public void initializeSnapshot(SnapshotId snapshotId, List<IndexId> indices, MetaData clusterMetaData) {
        if (isReadOnly()) {
            throw new RepositoryException(metadata.name(), "cannot create snapshot in a readonly repository");
        }
        try {
            final String snapshotName = snapshotId.getName();
            // check if the snapshot name already exists in the repository
            final RepositoryData repositoryData = getRepositoryData();
            if (repositoryData.getSnapshotIds().stream().anyMatch(s -> s.getName().equals(snapshotName))) {
                throw new InvalidSnapshotNameException(metadata.name(), snapshotId.getName(), "snapshot with the same name already exists");
            }

            // Write Global MetaData
            globalMetaDataFormat.write(clusterMetaData, blobContainer(), snapshotId.getUUID());

            // write the index metadata for each index in the snapshot
            for (IndexId index : indices) {
                indexMetaDataFormat.write(clusterMetaData.index(index.getName()), indexContainer(index), snapshotId.getUUID());
            }
        } catch (IOException ex) {
            throw new SnapshotCreationException(metadata.name(), snapshotId, ex);
        }
    }

    @Override
    public void deleteSnapshot(SnapshotId snapshotId, long repositoryStateId, ActionListener<Void> listener) {
        if (isReadOnly()) {
            listener.onFailure(new RepositoryException(metadata.name(), "cannot delete snapshot from a readonly repository"));
        } else {
            SnapshotInfo snapshot = null;
            try {
                snapshot = getSnapshotInfo(snapshotId);
            } catch (SnapshotMissingException ex) {
                listener.onFailure(ex);
                return;
            } catch (IllegalStateException | SnapshotException | ElasticsearchParseException ex) {
                logger.warn(() -> new ParameterizedMessage("cannot read snapshot file [{}]", snapshotId), ex);
            }
            // Delete snapshot from the index file, since it is the maintainer of truth of active snapshots
            final RepositoryData updatedRepositoryData;
            final Map<String, BlobContainer> foundIndices;
            final Set<String> rootBlobs;
            try {
                rootBlobs = blobContainer().listBlobs().keySet();
                final RepositoryData repositoryData = getRepositoryData(latestGeneration(rootBlobs));
                updatedRepositoryData = repositoryData.removeSnapshot(snapshotId);
                // Cache the indices that were found before writing out the new index-N blob so that a stuck master will never
                // delete an index that was created by another master node after writing this index-N blob.
                foundIndices = blobStore().blobContainer(basePath().add("indices")).children();
                writeIndexGen(updatedRepositoryData, repositoryStateId);
            } catch (Exception ex) {
                listener.onFailure(new RepositoryException(metadata.name(), "failed to delete snapshot [" + snapshotId + "]", ex));
                return;
            }
            final SnapshotInfo finalSnapshotInfo = snapshot;
            final List<String> snapMetaFilesToDelete =
                Arrays.asList(snapshotFormat.blobName(snapshotId.getUUID()), globalMetaDataFormat.blobName(snapshotId.getUUID()));
            try {
                blobContainer().deleteBlobsIgnoringIfNotExists(snapMetaFilesToDelete);
            } catch (IOException e) {
                logger.warn(() -> new ParameterizedMessage("[{}] Unable to delete global metadata files", snapshotId), e);
            }
            final Map<String, IndexId> survivingIndices = updatedRepositoryData.getIndices();
            deleteIndices(
                updatedRepositoryData,
                Optional.ofNullable(finalSnapshotInfo)
                    .map(info -> info.indices().stream().filter(survivingIndices::containsKey)
                        .map(updatedRepositoryData::resolveIndexId).collect(Collectors.toList()))
                    .orElse(Collections.emptyList()),
                snapshotId,
                ActionListener.map(listener, v -> {
                    cleanupStaleIndices(foundIndices, survivingIndices);
                    cleanupStaleRootFiles(Sets.difference(rootBlobs, new HashSet<>(snapMetaFilesToDelete)), updatedRepositoryData);
                    return null;
                })
            );
        }
    }

    private void cleanupStaleRootFiles(Set<String> rootBlobNames, RepositoryData repositoryData) {
        final Set<String> allSnapshotIds =
            repositoryData.getSnapshotIds().stream().map(SnapshotId::getUUID).collect(Collectors.toSet());
        final List<String> blobsToDelete = rootBlobNames.stream().filter(
            blob -> {
                if (FsBlobContainer.isTempBlobName(blob)) {
                    return true;
                }
                if (blob.endsWith(".dat")) {
                    final String foundUUID;
                    if (blob.startsWith(SNAPSHOT_PREFIX)) {
                        foundUUID = blob.substring(SNAPSHOT_PREFIX.length(), blob.length() - ".dat".length());
                        assert snapshotFormat.blobName(foundUUID).equals(blob);
                    } else if (blob.startsWith(METADATA_PREFIX)) {
                        foundUUID = blob.substring(METADATA_PREFIX.length(), blob.length() - ".dat".length());
                        assert globalMetaDataFormat.blobName(foundUUID).equals(blob);
                    } else {
                        return false;
                    }
                    return allSnapshotIds.contains(foundUUID) == false;
                }
                return false;
            }
        ).collect(Collectors.toList());
        if (blobsToDelete.isEmpty()) {
            return;
        }
        try {
            logger.info("[{}] Found stale root level blobs {}. Cleaning them up", metadata.name(), blobsToDelete);
            blobContainer().deleteBlobsIgnoringIfNotExists(blobsToDelete);
        } catch (IOException e) {
            logger.warn(() -> new ParameterizedMessage(
                "[{}] The following blobs are no longer part of any snapshot [{}] but failed to remove them",
                metadata.name(), blobsToDelete), e);
        } catch (Exception e) {
            // TODO: We shouldn't be blanket catching and suppressing all exceptions here and instead handle them safely upstream.
            //       Currently this catch exists as a stop gap solution to tackle unexpected runtime exceptions from implementations
            //       bubbling up and breaking the snapshot functionality.
            assert false : e;
            logger.warn(new ParameterizedMessage("[{}] Exception during cleanup of root level blobs", metadata.name()), e);
        }
    }

    private void cleanupStaleIndices(Map<String, BlobContainer> foundIndices, Map<String, IndexId> survivingIndices) {
        try {
            final Set<String> survivingIndexIds = survivingIndices.values().stream()
                .map(IndexId::getId).collect(Collectors.toSet());
            for (Map.Entry<String, BlobContainer> indexEntry : foundIndices.entrySet()) {
                final String indexSnId = indexEntry.getKey();
                try {
                    if (survivingIndexIds.contains(indexSnId) == false) {
                        logger.debug("[{}] Found stale index [{}]. Cleaning it up", metadata.name(), indexSnId);
                        indexEntry.getValue().delete();
                        logger.debug("[{}] Cleaned up stale index [{}]", metadata.name(), indexSnId);
                    }
                } catch (IOException e) {
                    logger.warn(() -> new ParameterizedMessage(
                        "[{}] index {} is no longer part of any snapshots in the repository, " +
                            "but failed to clean up their index folders", metadata.name(), indexSnId), e);
                }
            }
        } catch (Exception e) {
            // TODO: We shouldn't be blanket catching and suppressing all exceptions here and instead handle them safely upstream.
            //       Currently this catch exists as a stop gap solution to tackle unexpected runtime exceptions from implementations
            //       bubbling up and breaking the snapshot functionality.
            assert false : e;
            logger.warn(new ParameterizedMessage("[{}] Exception during cleanup of stale indices", metadata.name()), e);
        }
    }

    private void deleteIndices(RepositoryData repositoryData, List<IndexId> indices, SnapshotId snapshotId,
                               ActionListener<Void> listener) {
        if (indices.isEmpty()) {
            listener.onResponse(null);
            return;
        }
        final ActionListener<Void> groupedListener = new GroupedActionListener<>(ActionListener.map(listener, v -> null), indices.size());
        for (IndexId indexId: indices) {
            threadPool.executor(ThreadPool.Names.SNAPSHOT).execute(new ActionRunnable<Void>(groupedListener) {

                @Override
                protected void doRun() {
                    IndexMetaData indexMetaData = null;
                    try {
                        indexMetaData = getSnapshotIndexMetaData(snapshotId, indexId);
                    } catch (Exception ex) {
                        logger.warn(() ->
                            new ParameterizedMessage("[{}] [{}] failed to read metadata for index", snapshotId, indexId.getName()), ex);
                    }
                    deleteIndexMetaDataBlobIgnoringErrors(snapshotId, indexId);
                    if (indexMetaData != null) {
                        for (int shardId = 0; shardId < indexMetaData.getNumberOfShards(); shardId++) {
                            try {
                                deleteShardSnapshot(repositoryData, indexId, new ShardId(indexMetaData.getIndex(), shardId), snapshotId);
                            } catch (SnapshotException ex) {
                                final int finalShardId = shardId;
                                logger.warn(() -> new ParameterizedMessage("[{}] failed to delete shard data for shard [{}][{}]",
                                    snapshotId, indexId.getName(), finalShardId), ex);
                            }
                        }
                    }
                    groupedListener.onResponse(null);
                }
            });
        }
    }

    private void deleteIndexMetaDataBlobIgnoringErrors(SnapshotId snapshotId, IndexId indexId) {
        try {
            indexMetaDataFormat.delete(indexContainer(indexId), snapshotId.getUUID());
        } catch (IOException ex) {
            logger.warn(() -> new ParameterizedMessage("[{}] failed to delete metadata for index [{}]",
                snapshotId, indexId.getName()), ex);
        }
    }

    @Override
    public SnapshotInfo finalizeSnapshot(final SnapshotId snapshotId,
                                         final List<IndexId> indices,
                                         final long startTime,
                                         final String failure,
                                         final int totalShards,
                                         final List<SnapshotShardFailure> shardFailures,
                                         final long repositoryStateId,
                                         final boolean includeGlobalState,
                                         final Map<String, Object> userMetadata) {
        SnapshotInfo blobStoreSnapshot = new SnapshotInfo(snapshotId,
            indices.stream().map(IndexId::getName).collect(Collectors.toList()),
            startTime, failure, threadPool.absoluteTimeInMillis(), totalShards, shardFailures,
            includeGlobalState, userMetadata);
        try {
            final RepositoryData updatedRepositoryData = getRepositoryData().addSnapshot(snapshotId, blobStoreSnapshot.state(), indices);
            snapshotFormat.write(blobStoreSnapshot, blobContainer(), snapshotId.getUUID());
            writeIndexGen(updatedRepositoryData, repositoryStateId);
        } catch (FileAlreadyExistsException ex) {
            // if another master was elected and took over finalizing the snapshot, it is possible
            // that both nodes try to finalize the snapshot and write to the same blobs, so we just
            // log a warning here and carry on
            throw new RepositoryException(metadata.name(), "Blob already exists while " +
                "finalizing snapshot, assume the snapshot has already been saved", ex);
        } catch (IOException ex) {
            throw new RepositoryException(metadata.name(), "failed to update snapshot in repository", ex);
        }
        return blobStoreSnapshot;
    }

    @Override
    public SnapshotInfo getSnapshotInfo(final SnapshotId snapshotId) {
        try {
            return snapshotFormat.read(blobContainer(), snapshotId.getUUID());
        } catch (NoSuchFileException ex) {
            throw new SnapshotMissingException(metadata.name(), snapshotId, ex);
        } catch (IOException | NotXContentException ex) {
            throw new SnapshotException(metadata.name(), snapshotId, "failed to get snapshots", ex);
        }
    }

    @Override
    public MetaData getSnapshotGlobalMetaData(final SnapshotId snapshotId) {
        try {
            return globalMetaDataFormat.read(blobContainer(), snapshotId.getUUID());
        } catch (NoSuchFileException ex) {
            throw new SnapshotMissingException(metadata.name(), snapshotId, ex);
        } catch (IOException ex) {
            throw new SnapshotException(metadata.name(), snapshotId, "failed to read global metadata", ex);
        }
    }

    @Override
    public IndexMetaData getSnapshotIndexMetaData(final SnapshotId snapshotId, final IndexId index) throws IOException {
        return indexMetaDataFormat.read(indexContainer(index), snapshotId.getUUID());
    }

    private BlobPath indicesPath() {
        return basePath().add("indices");
    }

    private BlobContainer indexContainer(IndexId indexId) {
        return blobStore().blobContainer(indicesPath().add(indexId.getId()));
    }

    private BlobContainer shardContainer(IndexId indexId, ShardId shardId) {
        return blobStore().blobContainer(indicesPath().add(indexId.getId()).add(Integer.toString(shardId.getId())));
    }

    /**
     * Configures RateLimiter based on repository and global settings
     *
     * @param repositorySettings repository settings
     * @param setting            setting to use to configure rate limiter
     * @param defaultRate        default limiting rate
     * @return rate limiter or null of no throttling is needed
     */
    private RateLimiter getRateLimiter(Settings repositorySettings, String setting, ByteSizeValue defaultRate) {
        ByteSizeValue maxSnapshotBytesPerSec = repositorySettings.getAsBytesSize(setting,
                settings.getAsBytesSize(setting, defaultRate));
        if (maxSnapshotBytesPerSec.getBytes() <= 0) {
            return null;
        } else {
            return new RateLimiter.SimpleRateLimiter(maxSnapshotBytesPerSec.getMbFrac());
        }
    }

    @Override
    public long getSnapshotThrottleTimeInNanos() {
        return snapshotRateLimitingTimeInNanos.count();
    }

    @Override
    public long getRestoreThrottleTimeInNanos() {
        return restoreRateLimitingTimeInNanos.count();
    }

    protected void assertSnapshotOrGenericThread() {
        assert Thread.currentThread().getName().contains(ThreadPool.Names.SNAPSHOT)
            || Thread.currentThread().getName().contains(ThreadPool.Names.GENERIC) :
            "Expected current thread [" + Thread.currentThread() + "] to be the snapshot or generic thread.";
    }

    @Override
    public String startVerification() {
        try {
            if (isReadOnly()) {
                // It's readonly - so there is not much we can do here to verify it apart from reading the blob store metadata
                latestIndexBlobId();
                return "read-only";
            } else {
                String seed = UUIDs.randomBase64UUID();
                byte[] testBytes = Strings.toUTF8Bytes(seed);
                BlobContainer testContainer = blobStore().blobContainer(basePath().add(testBlobPrefix(seed)));
                BytesArray bytes = new BytesArray(testBytes);
                try (InputStream stream = bytes.streamInput()) {
                    testContainer.writeBlobAtomic("master.dat", stream, bytes.length(), true);
                }
                return seed;
            }
        } catch (IOException exp) {
            throw new RepositoryVerificationException(metadata.name(), "path " + basePath() + " is not accessible on master node", exp);
        }
    }

    @Override
    public void endVerification(String seed) {
        if (isReadOnly() == false) {
            try {
                final String testPrefix = testBlobPrefix(seed);
                final BlobContainer container = blobStore().blobContainer(basePath().add(testPrefix));
                container.deleteBlobsIgnoringIfNotExists(new ArrayList<>(container.listBlobs().keySet()));
                blobStore().blobContainer(basePath()).deleteBlobIgnoringIfNotExists(testPrefix);
            } catch (IOException exp) {
                throw new RepositoryVerificationException(metadata.name(), "cannot delete test data at " + basePath(), exp);
            }
        }
    }

    @Override
    public RepositoryData getRepositoryData() {
        try {
            return getRepositoryData(latestIndexBlobId());
        } catch (NoSuchFileException ex) {
            // repository doesn't have an index blob, its a new blank repo
            return RepositoryData.EMPTY;
        } catch (IOException ioe) {
            throw new RepositoryException(metadata.name(), "Could not determine repository generation from root blobs", ioe);
        }
    }

    private RepositoryData getRepositoryData(long indexGen) {
        if (indexGen == RepositoryData.EMPTY_REPO_GEN) {
            return RepositoryData.EMPTY;
        }
        try {
            final String snapshotsIndexBlobName = INDEX_FILE_PREFIX + Long.toString(indexGen);

            RepositoryData repositoryData;
            // EMPTY is safe here because RepositoryData#fromXContent calls namedObject
            try (InputStream blob = blobContainer().readBlob(snapshotsIndexBlobName);
                 XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY,
                     LoggingDeprecationHandler.INSTANCE, blob)) {
                repositoryData = RepositoryData.snapshotsFromXContent(parser, indexGen);
            }
            return repositoryData;
        } catch (NoSuchFileException ex) {
            // repository doesn't have an index blob, its a new blank repo
            return RepositoryData.EMPTY;
        } catch (IOException ioe) {
            throw new RepositoryException(metadata.name(), "could not read repository data from index blob", ioe);
        }
    }

    private static String testBlobPrefix(String seed) {
        return TESTS_FILE + seed;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    protected void writeIndexGen(final RepositoryData repositoryData, final long expectedGen) throws IOException {
        assert isReadOnly() == false; // can not write to a read only repository
        final long currentGen = repositoryData.getGenId();
        if (currentGen != expectedGen) {
            // the index file was updated by a concurrent operation, so we were operating on stale
            // repository data
            throw new RepositoryException(metadata.name(), "concurrent modification of the index-N file, expected current generation [" +
                                              expectedGen + "], actual current generation [" + currentGen +
                                              "] - possibly due to simultaneous snapshot deletion requests");
        }
        final long newGen = currentGen + 1;
        // write the index file
        final String indexBlob = INDEX_FILE_PREFIX + Long.toString(newGen);
        logger.debug("Repository [{}] writing new index generational blob [{}]", metadata.name(), indexBlob);
        writeAtomic(indexBlob, BytesReference.bytes(repositoryData.snapshotsToXContent(XContentFactory.jsonBuilder())), true);
        // write the current generation to the index-latest file
        final BytesReference genBytes;
        try (BytesStreamOutput bStream = new BytesStreamOutput()) {
            bStream.writeLong(newGen);
            genBytes = bStream.bytes();
        }
        logger.debug("Repository [{}] updating index.latest with generation [{}]", metadata.name(), newGen);
        writeAtomic(INDEX_LATEST_BLOB, genBytes, false);
        // delete the N-2 index file if it exists, keep the previous one around as a backup
        if (newGen - 2 >= 0) {
            final String oldSnapshotIndexFile = INDEX_FILE_PREFIX + Long.toString(newGen - 2);
            try {
                blobContainer().deleteBlobIgnoringIfNotExists(oldSnapshotIndexFile);
            } catch (IOException e) {
                logger.warn("Failed to clean up old index blob [{}]", oldSnapshotIndexFile);
            }
        }
    }

    /**
     * Get the latest snapshot index blob id.  Snapshot index blobs are named index-N, where N is
     * the next version number from when the index blob was written.  Each individual index-N blob is
     * only written once and never overwritten.  The highest numbered index-N blob is the latest one
     * that contains the current snapshots in the repository.
     *
     * Package private for testing
     */
    long latestIndexBlobId() throws IOException {
        try {
            // First, try listing all index-N blobs (there should only be two index-N blobs at any given
            // time in a repository if cleanup is happening properly) and pick the index-N blob with the
            // highest N value - this will be the latest index blob for the repository.  Note, we do this
            // instead of directly reading the index.latest blob to get the current index-N blob because
            // index.latest is not written atomically and is not immutable - on every index-N change,
            // we first delete the old index.latest and then write the new one.  If the repository is not
            // read-only, it is possible that we try deleting the index.latest blob while it is being read
            // by some other operation (such as the get snapshots operation).  In some file systems, it is
            // illegal to delete a file while it is being read elsewhere (e.g. Windows).  For read-only
            // repositories, we read for index.latest, both because listing blob prefixes is often unsupported
            // and because the index.latest blob will never be deleted and re-written.
            return listBlobsToGetLatestIndexId();
        } catch (UnsupportedOperationException e) {
            // If its a read-only repository, listing blobs by prefix may not be supported (e.g. a URL repository),
            // in this case, try reading the latest index generation from the index.latest blob
            try {
                return readSnapshotIndexLatestBlob();
            } catch (NoSuchFileException nsfe) {
                return RepositoryData.EMPTY_REPO_GEN;
            }
        }
    }

    // package private for testing
    long readSnapshotIndexLatestBlob() throws IOException {
        return Numbers.bytesToLong(Streams.readFully(blobContainer().readBlob(INDEX_LATEST_BLOB)).toBytesRef());
    }

    private long listBlobsToGetLatestIndexId() throws IOException {
        return latestGeneration(blobContainer().listBlobsByPrefix(INDEX_FILE_PREFIX).keySet());
    }

    private long latestGeneration(Collection<String> rootBlobs) {
        long latest = RepositoryData.EMPTY_REPO_GEN;
        for (String blobName : rootBlobs) {
            if (blobName.startsWith(INDEX_FILE_PREFIX) == false) {
                continue;
            }
            try {
                final long curr = Long.parseLong(blobName.substring(INDEX_FILE_PREFIX.length()));
                latest = Math.max(latest, curr);
            } catch (NumberFormatException nfe) {
                // the index- blob wasn't of the format index-N where N is a number,
                // no idea what this blob is but it doesn't belong in the repository!
                logger.warn("[{}] Unknown blob in the repository: {}", metadata.name(), blobName);
            }
        }
        return latest;
    }

    private void writeAtomic(final String blobName, final BytesReference bytesRef, boolean failIfAlreadyExists) throws IOException {
        try (InputStream stream = bytesRef.streamInput()) {
            blobContainer().writeBlobAtomic(blobName, stream, bytesRef.length(), failIfAlreadyExists);
        }
    }

    @Override
    public void snapshotShard(Store store, MapperService mapperService, SnapshotId snapshotId, IndexId indexId,
                              IndexCommit snapshotIndexCommit, IndexShardSnapshotStatus snapshotStatus) {
        final ShardId shardId = store.shardId();
        final long startTime = threadPool.absoluteTimeInMillis();
        try {
            logger.debug("[{}] [{}] snapshot to [{}] ...", shardId, snapshotId, metadata.name());

            final BlobContainer shardContainer = shardContainer(indexId, shardId);
            final Map<String, BlobMetaData> blobs;
            try {
                blobs = shardContainer.listBlobs();
            } catch (IOException e) {
                throw new IndexShardSnapshotFailedException(shardId, "failed to list blobs", e);
            }

            Tuple<BlobStoreIndexShardSnapshots, Long> tuple = buildBlobStoreIndexShardSnapshots(blobs, shardContainer);
            BlobStoreIndexShardSnapshots snapshots = tuple.v1();
            long fileListGeneration = tuple.v2();

            if (snapshots.snapshots().stream().anyMatch(sf -> sf.snapshot().equals(snapshotId.getName()))) {
                throw new IndexShardSnapshotFailedException(shardId,
                    "Duplicate snapshot name [" + snapshotId.getName() + "] detected, aborting");
            }

            final List<BlobStoreIndexShardSnapshot.FileInfo> indexCommitPointFiles = new ArrayList<>();
            store.incRef();
            try {
                ArrayList<BlobStoreIndexShardSnapshot.FileInfo> filesToSnapshot = new ArrayList<>();
                final Store.MetadataSnapshot metadata;
                // TODO apparently we don't use the MetadataSnapshot#.recoveryDiff(...) here but we should
                final Collection<String> fileNames;
                try {
                    logger.trace(
                        "[{}] [{}] Loading store metadata using index commit [{}]", shardId, snapshotId, snapshotIndexCommit);
                    metadata = store.getMetadata(snapshotIndexCommit);
                    fileNames = snapshotIndexCommit.getFileNames();
                } catch (IOException e) {
                    throw new IndexShardSnapshotFailedException(shardId, "Failed to get store file metadata", e);
                }
                int indexIncrementalFileCount = 0;
                int indexTotalNumberOfFiles = 0;
                long indexIncrementalSize = 0;
                long indexTotalFileCount = 0;
                for (String fileName : fileNames) {
                    if (snapshotStatus.isAborted()) {
                        logger.debug("[{}] [{}] Aborted on the file [{}], exiting", shardId, snapshotId, fileName);
                        throw new IndexShardSnapshotFailedException(shardId, "Aborted");
                    }

                    logger.trace("[{}] [{}] Processing [{}]", shardId, snapshotId, fileName);
                    final StoreFileMetaData md = metadata.get(fileName);
                    BlobStoreIndexShardSnapshot.FileInfo existingFileInfo = null;
                    List<BlobStoreIndexShardSnapshot.FileInfo> filesInfo = snapshots.findPhysicalIndexFiles(fileName);
                    if (filesInfo != null) {
                        for (BlobStoreIndexShardSnapshot.FileInfo fileInfo : filesInfo) {
                            if (fileInfo.isSame(md) && snapshotFileExistsInBlobs(fileInfo, blobs)) {
                                // a commit point file with the same name, size and checksum was already copied to repository
                                // we will reuse it for this snapshot
                                existingFileInfo = fileInfo;
                                break;
                            }
                        }
                    }

                    indexTotalFileCount += md.length();
                    indexTotalNumberOfFiles++;

                    if (existingFileInfo == null) {
                        indexIncrementalFileCount++;
                        indexIncrementalSize += md.length();
                        // create a new FileInfo
                        BlobStoreIndexShardSnapshot.FileInfo snapshotFileInfo =
                            new BlobStoreIndexShardSnapshot.FileInfo(DATA_BLOB_PREFIX + UUIDs.randomBase64UUID(), md, chunkSize());
                        indexCommitPointFiles.add(snapshotFileInfo);
                        filesToSnapshot.add(snapshotFileInfo);
                    } else {
                        indexCommitPointFiles.add(existingFileInfo);
                    }
                }

                snapshotStatus.moveToStarted(startTime, indexIncrementalFileCount,
                    indexTotalNumberOfFiles, indexIncrementalSize, indexTotalFileCount);

                for (BlobStoreIndexShardSnapshot.FileInfo snapshotFileInfo : filesToSnapshot) {
                    try {
                        snapshotFile(snapshotFileInfo, indexId, shardId, snapshotId, snapshotStatus, store);
                    } catch (IOException e) {
                        throw new IndexShardSnapshotFailedException(shardId, "Failed to perform snapshot (index files)", e);
                    }
                }
            } finally {
                store.decRef();
            }

            final IndexShardSnapshotStatus.Copy lastSnapshotStatus = snapshotStatus.moveToFinalize(snapshotIndexCommit.getGeneration());

            // now create and write the commit point
            final BlobStoreIndexShardSnapshot snapshot = new BlobStoreIndexShardSnapshot(snapshotId.getName(),
                lastSnapshotStatus.getIndexVersion(),
                indexCommitPointFiles,
                lastSnapshotStatus.getStartTime(),
                threadPool.absoluteTimeInMillis() - lastSnapshotStatus.getStartTime(),
                lastSnapshotStatus.getIncrementalFileCount(),
                lastSnapshotStatus.getIncrementalSize()
            );

            logger.trace("[{}] [{}] writing shard snapshot file", shardId, snapshotId);
            try {
                indexShardSnapshotFormat.write(snapshot, shardContainer, snapshotId.getUUID());
            } catch (IOException e) {
                throw new IndexShardSnapshotFailedException(shardId, "Failed to write commit point", e);
            }

            // delete all files that are not referenced by any commit point
            // build a new BlobStoreIndexShardSnapshot, that includes this one and all the saved ones
            List<SnapshotFiles> newSnapshotsList = new ArrayList<>();
            newSnapshotsList.add(new SnapshotFiles(snapshot.snapshot(), snapshot.indexFiles()));
            for (SnapshotFiles point : snapshots) {
                newSnapshotsList.add(point);
            }
            final String indexGeneration = Long.toString(fileListGeneration + 1);
            final List<String> blobsToDelete;
            try {
                final BlobStoreIndexShardSnapshots updatedSnapshots = new BlobStoreIndexShardSnapshots(newSnapshotsList);
                indexShardSnapshotsFormat.writeAtomic(updatedSnapshots, shardContainer, indexGeneration);
                // Delete all previous index-N blobs
                blobsToDelete =
                    blobs.keySet().stream().filter(blob -> blob.startsWith(SNAPSHOT_INDEX_PREFIX)).collect(Collectors.toList());
                assert blobsToDelete.stream().mapToLong(b -> Long.parseLong(b.replaceFirst(SNAPSHOT_INDEX_PREFIX, "")))
                    .max().orElse(-1L) < Long.parseLong(indexGeneration)
                    : "Tried to delete an index-N blob newer than the current generation [" + indexGeneration + "] when deleting index-N" +
                    " blobs " + blobsToDelete;
            } catch (IOException e) {
                throw new IndexShardSnapshotFailedException(shardId,
                    "Failed to finalize snapshot creation [" + snapshotId + "] with shard index ["
                        + indexShardSnapshotsFormat.blobName(indexGeneration) + "]", e);
            }
            try {
                shardContainer.deleteBlobsIgnoringIfNotExists(blobsToDelete);
            } catch (IOException e) {
                logger.warn(() -> new ParameterizedMessage("[{}][{}] failed to delete old index-N blobs during finalization",
                    snapshotId, shardId), e);
            }
            snapshotStatus.moveToDone(threadPool.absoluteTimeInMillis());
        } catch (Exception e) {
            snapshotStatus.moveToFailed(threadPool.absoluteTimeInMillis(), ExceptionsHelper.detailedMessage(e));
            if (e instanceof IndexShardSnapshotFailedException) {
                throw (IndexShardSnapshotFailedException) e;
            } else {
                throw new IndexShardSnapshotFailedException(store.shardId(), e);
            }
        }
    }

    @Override
    public void restoreShard(Store store, SnapshotId snapshotId, Version version, IndexId indexId, ShardId snapshotShardId,
                             RecoveryState recoveryState) {
        ShardId shardId = store.shardId();
        try {
            final BlobContainer container = shardContainer(indexId, snapshotShardId);
            BlobStoreIndexShardSnapshot snapshot = loadShardSnapshot(container, snapshotId);
            SnapshotFiles snapshotFiles = new SnapshotFiles(snapshot.snapshot(), snapshot.indexFiles());
            new FileRestoreContext(metadata.name(), shardId, snapshotId, recoveryState, BUFFER_SIZE) {
                @Override
                protected InputStream fileInputStream(BlobStoreIndexShardSnapshot.FileInfo fileInfo) {
                    final InputStream dataBlobCompositeStream = new SlicedInputStream(fileInfo.numberOfParts()) {
                        @Override
                        protected InputStream openSlice(long slice) throws IOException {
                            return container.readBlob(fileInfo.partName(slice));
                        }
                    };
                    return restoreRateLimiter == null ? dataBlobCompositeStream
                        : new RateLimitingInputStream(dataBlobCompositeStream, restoreRateLimiter, restoreRateLimitingTimeInNanos::inc);
                }
            }.restore(snapshotFiles, store);
        } catch (Exception e) {
            throw new IndexShardRestoreFailedException(shardId, "failed to restore snapshot [" + snapshotId + "]", e);
        }
    }

    @Override
    public IndexShardSnapshotStatus getShardSnapshotStatus(SnapshotId snapshotId, Version version, IndexId indexId, ShardId shardId) {
        BlobStoreIndexShardSnapshot snapshot = loadShardSnapshot(shardContainer(indexId, shardId), snapshotId);
        return IndexShardSnapshotStatus.newDone(snapshot.startTime(), snapshot.time(),
            snapshot.incrementalFileCount(), snapshot.totalFileCount(),
            snapshot.incrementalSize(), snapshot.totalSize());
    }

    @Override
    public void verify(String seed, DiscoveryNode localNode) {
        assertSnapshotOrGenericThread();
        if (isReadOnly()) {
            try {
                latestIndexBlobId();
            } catch (IOException e) {
                throw new RepositoryVerificationException(metadata.name(), "path " + basePath() +
                    " is not accessible on node " + localNode, e);
            }
        } else {
            BlobContainer testBlobContainer = blobStore().blobContainer(basePath().add(testBlobPrefix(seed)));
            if (testBlobContainer.blobExists("master.dat")) {
                try {
                    BytesArray bytes = new BytesArray(seed);
                    try (InputStream stream = bytes.streamInput()) {
                        testBlobContainer.writeBlob("data-" + localNode.getId() + ".dat", stream, bytes.length(), true);
                    }
                } catch (IOException exp) {
                    throw new RepositoryVerificationException(metadata.name(), "store location [" + blobStore() +
                        "] is not accessible on the node [" + localNode + "]", exp);
                }
            } else {
                throw new RepositoryVerificationException(metadata.name(), "a file written by master to the store [" + blobStore() +
                    "] cannot be accessed on the node [" + localNode + "]. " +
                    "This might indicate that the store [" + blobStore() + "] is not shared between this node and the master node or " +
                    "that permissions on the store don't allow reading files written by the master node");
            }
        }
    }

    @Override
    public String toString() {
        return "BlobStoreRepository[" +
            "[" + metadata.name() +
            "], [" + blobStore() + ']' +
            ']';
    }

    /**
     * Delete shard snapshot
     */
    private void deleteShardSnapshot(RepositoryData repositoryData, IndexId indexId, ShardId snapshotShardId, SnapshotId snapshotId) {
        final BlobContainer shardContainer = shardContainer(indexId, snapshotShardId);
        final Map<String, BlobMetaData> blobs;
        try {
            blobs = shardContainer.listBlobs();
        } catch (IOException e) {
            throw new IndexShardSnapshotException(snapshotShardId, "Failed to list content of shard directory", e);
        }

        Tuple<BlobStoreIndexShardSnapshots, Long> tuple = buildBlobStoreIndexShardSnapshots(blobs, shardContainer);
        BlobStoreIndexShardSnapshots snapshots = tuple.v1();
        long fileListGeneration = tuple.v2();

        // Build a list of snapshots that should be preserved
        List<SnapshotFiles> newSnapshotsList = new ArrayList<>();
        final Set<String> survivingSnapshotNames =
            repositoryData.getSnapshots(indexId).stream().map(SnapshotId::getName).collect(Collectors.toSet());
        for (SnapshotFiles point : snapshots) {
            if (survivingSnapshotNames.contains(point.snapshot())) {
                newSnapshotsList.add(point);
            }
        }
        final String indexGeneration = Long.toString(fileListGeneration + 1);
        try {
            final List<String> blobsToDelete;
            if (newSnapshotsList.isEmpty()) {
                // If we deleted all snapshots, we don't need to create a new index file and simply delete all the blobs we found
                blobsToDelete = new ArrayList<>(blobs.keySet());
            } else {
                final Set<String> survivingSnapshotUUIDs = repositoryData.getSnapshots(indexId).stream().map(SnapshotId::getUUID)
                    .collect(Collectors.toSet());
                final BlobStoreIndexShardSnapshots updatedSnapshots = new BlobStoreIndexShardSnapshots(newSnapshotsList);
                indexShardSnapshotsFormat.writeAtomic(updatedSnapshots, shardContainer, indexGeneration);
                // Delete all previous index-N, data- and meta-blobs and that are not referenced by the new index-N and temporary blobs
                blobsToDelete = blobs.keySet().stream().filter(blob ->
                    blob.startsWith(SNAPSHOT_INDEX_PREFIX)
                        || (blob.startsWith(SNAPSHOT_PREFIX) && blob.endsWith(".dat")
                            && survivingSnapshotUUIDs.contains(
                                blob.substring(SNAPSHOT_PREFIX.length(), blob.length() - ".dat".length())) == false)
                        || (blob.startsWith(DATA_BLOB_PREFIX) && updatedSnapshots.findNameFile(canonicalName(blob)) == null)
                        || FsBlobContainer.isTempBlobName(blob)).collect(Collectors.toList());
            }
            try {
                shardContainer.deleteBlobsIgnoringIfNotExists(blobsToDelete);
            } catch (IOException e) {
                logger.warn(() -> new ParameterizedMessage("[{}][{}] failed to delete blobs during finalization",
                    snapshotId, snapshotShardId), e);
            }
        } catch (IOException e) {
            throw new IndexShardSnapshotFailedException(snapshotShardId,
                "Failed to finalize snapshot deletion [" + snapshotId + "] with shard index ["
                    + indexShardSnapshotsFormat.blobName(indexGeneration) + "]", e);
        }
    }

    /**
     * Loads information about shard snapshot
     */
    private BlobStoreIndexShardSnapshot loadShardSnapshot(BlobContainer shardContainer, SnapshotId snapshotId) {
        try {
            return indexShardSnapshotFormat.read(shardContainer, snapshotId.getUUID());
        } catch (IOException ex) {
            throw new SnapshotException(metadata.name(), snapshotId,
                "failed to read shard snapshot file for [" + shardContainer.path() + ']', ex);
        }
    }

    /**
     * Loads all available snapshots in the repository
     *
     * @param blobs list of blobs in repository
     * @return tuple of BlobStoreIndexShardSnapshots and the last snapshot index generation
     */
    private Tuple<BlobStoreIndexShardSnapshots, Long> buildBlobStoreIndexShardSnapshots(Map<String, BlobMetaData> blobs,
                                                                                           BlobContainer shardContainer) {
        Set<String> blobKeys = blobs.keySet();
        long latest = latestGeneration(blobKeys);
        if (latest >= 0) {
            try {
                final BlobStoreIndexShardSnapshots shardSnapshots =
                    indexShardSnapshotsFormat.read(shardContainer, Long.toString(latest));
                return new Tuple<>(shardSnapshots, latest);
            } catch (IOException e) {
                final String file = SNAPSHOT_INDEX_PREFIX + latest;
                logger.warn(() -> new ParameterizedMessage("failed to read index file [{}]", file), e);
            }
        } else if (blobKeys.isEmpty() == false) {
            logger.warn("Could not find a readable index-N file in a non-empty shard snapshot directory [{}]", shardContainer.path());
        }

        // We couldn't load the index file - falling back to loading individual snapshots
        List<SnapshotFiles> snapshots = new ArrayList<>();
        for (String name : blobKeys) {
            try {
                BlobStoreIndexShardSnapshot snapshot = null;
                if (name.startsWith(SNAPSHOT_PREFIX)) {
                    snapshot = indexShardSnapshotFormat.readBlob(shardContainer, name);
                }
                if (snapshot != null) {
                    snapshots.add(new SnapshotFiles(snapshot.snapshot(), snapshot.indexFiles()));
                }
            } catch (IOException e) {
                logger.warn(() -> new ParameterizedMessage("Failed to read blob [{}]", name), e);
            }
        }
        return new Tuple<>(new BlobStoreIndexShardSnapshots(snapshots), latest);
    }

    /**
     * Snapshot individual file
     * @param fileInfo file to be snapshotted
     */
    private void snapshotFile(BlobStoreIndexShardSnapshot.FileInfo fileInfo, IndexId indexId, ShardId shardId, SnapshotId snapshotId,
                              IndexShardSnapshotStatus snapshotStatus, Store store) throws IOException {
        final BlobContainer shardContainer = shardContainer(indexId, shardId);
        final String file = fileInfo.physicalName();
        try (IndexInput indexInput = store.openVerifyingInput(file, IOContext.READONCE, fileInfo.metadata())) {
            for (int i = 0; i < fileInfo.numberOfParts(); i++) {
                final long partBytes = fileInfo.partBytes(i);

                InputStream inputStream = new InputStreamIndexInput(indexInput, partBytes);
                if (snapshotRateLimiter != null) {
                    inputStream = new RateLimitingInputStream(inputStream, snapshotRateLimiter,
                        snapshotRateLimitingTimeInNanos::inc);
                }
                // Make reads abortable by mutating the snapshotStatus object
                inputStream = new FilterInputStream(inputStream) {
                    @Override
                    public int read() throws IOException {
                        checkAborted();
                        return super.read();
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        checkAborted();
                        return super.read(b, off, len);
                    }

                    private void checkAborted() {
                        if (snapshotStatus.isAborted()) {
                            logger.debug("[{}] [{}] Aborted on the file [{}], exiting", shardId,
                                snapshotId, fileInfo.physicalName());
                            throw new IndexShardSnapshotFailedException(shardId, "Aborted");
                        }
                    }
                };
                shardContainer.writeBlob(fileInfo.partName(i), inputStream, partBytes, true);
            }
            Store.verify(indexInput);
            snapshotStatus.addProcessedFile(fileInfo.length());
        } catch (Exception t) {
            failStoreIfCorrupted(store, t);
            snapshotStatus.addProcessedFile(0);
            throw t;
        }
    }

    private static void failStoreIfCorrupted(Store store, Exception e) {
        if (Lucene.isCorruptionException(e)) {
            try {
                store.markStoreCorrupted((IOException) e);
            } catch (IOException inner) {
                inner.addSuppressed(e);
                logger.warn("store cannot be marked as corrupted", inner);
            }
        }
    }

    /**
     * Checks if snapshot file already exists in the list of blobs
     * @param fileInfo file to check
     * @param blobs list of blobs
     * @return true if file exists in the list of blobs
     */
    private static boolean snapshotFileExistsInBlobs(BlobStoreIndexShardSnapshot.FileInfo fileInfo, Map<String, BlobMetaData> blobs) {
        BlobMetaData blobMetaData = blobs.get(fileInfo.name());
        if (blobMetaData != null) {
            return blobMetaData.length() == fileInfo.length();
        } else if (blobs.containsKey(fileInfo.partName(0))) {
            // multi part file sum up the size and check
            int part = 0;
            long totalSize = 0;
            while (true) {
                blobMetaData = blobs.get(fileInfo.partName(part++));
                if (blobMetaData == null) {
                    break;
                }
                totalSize += blobMetaData.length();
            }
            return totalSize == fileInfo.length();
        }
        // no file, not exact and not multipart
        return false;
    }
}
