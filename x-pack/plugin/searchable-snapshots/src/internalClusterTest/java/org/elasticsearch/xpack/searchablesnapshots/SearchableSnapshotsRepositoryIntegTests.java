/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.searchablesnapshots;

import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotResponse;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.repositories.fs.FsRepository;
import org.elasticsearch.snapshots.SnapshotRestoreException;
import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.elasticsearch.cluster.metadata.IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING;
import static org.elasticsearch.index.IndexSettings.INDEX_SOFT_DELETES_SETTING;
import static org.elasticsearch.repositories.RepositoriesService.SEARCHABLE_SNAPSHOTS_DELETE_SNAPSHOT_ON_INDEX_DELETION;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.xpack.core.searchablesnapshots.MountSearchableSnapshotRequest.Storage;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class SearchableSnapshotsRepositoryIntegTests extends BaseFrozenSearchableSnapshotsIntegTestCase {

    public void testRepositoryUsedBySearchableSnapshotCanBeUpdatedButNotUnregistered() throws Exception {
        final String repositoryName = randomAlphaOfLength(10).toLowerCase(Locale.ROOT);
        final Settings.Builder repositorySettings = randomRepositorySettings();
        createRepository(repositoryName, FsRepository.TYPE, repositorySettings);

        final String indexName = randomAlphaOfLength(10).toLowerCase(Locale.ROOT);
        createAndPopulateIndex(
            indexName,
            Settings.builder().put(INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), 1).put(INDEX_SOFT_DELETES_SETTING.getKey(), true)
        );

        final TotalHits totalHits = internalCluster().client()
            .prepareSearch(indexName)
            .setTrackTotalHits(true)
            .get()
            .getHits()
            .getTotalHits();

        final String snapshotName = randomAlphaOfLength(10).toLowerCase(Locale.ROOT);
        createSnapshot(repositoryName, snapshotName, List.of(indexName));
        assertAcked(client().admin().indices().prepareDelete(indexName));

        final int nbMountedIndices = 1;
        randomIntBetween(1, 5);
        final String[] mountedIndices = new String[nbMountedIndices];

        for (int i = 0; i < nbMountedIndices; i++) {
            Storage storage = randomFrom(Storage.values());
            String restoredIndexName = (storage == Storage.FULL_COPY ? "fully-mounted-" : "partially-mounted-") + indexName + '-' + i;
            mountSnapshot(repositoryName, snapshotName, indexName, restoredIndexName, Settings.EMPTY, storage);
            assertHitCount(client().prepareSearch(restoredIndexName).setTrackTotalHits(true).get(), totalHits.value);
            mountedIndices[i] = restoredIndexName;
        }

        assertAcked(
            clusterAdmin().preparePutRepository(repositoryName)
                .setType(FsRepository.TYPE)
                .setSettings(
                    Settings.builder()
                        .put(repositorySettings.build())
                        .put(FsRepository.REPOSITORIES_CHUNK_SIZE_SETTING.getKey(), ByteSizeValue.ofMb(1L))
                        .build()
                )
        );

        final String updatedRepositoryName;
        if (randomBoolean()) {
            final String snapshotWithMountedIndices = snapshotName + "-with-mounted-indices";
            createSnapshot(repositoryName, snapshotWithMountedIndices, Arrays.asList(mountedIndices));
            assertAcked(client().admin().indices().prepareDelete(mountedIndices));
            assertAcked(clusterAdmin().prepareDeleteRepository(repositoryName));

            updatedRepositoryName = repositoryName + "-with-mounted-indices";
            createRepository(updatedRepositoryName, FsRepository.TYPE, repositorySettings, randomBoolean());

            final RestoreSnapshotResponse restoreResponse = clusterAdmin().prepareRestoreSnapshot(
                updatedRepositoryName,
                snapshotWithMountedIndices
            ).setWaitForCompletion(true).setIndices(mountedIndices).get();
            assertEquals(restoreResponse.getRestoreInfo().totalShards(), restoreResponse.getRestoreInfo().successfulShards());
        } else {
            updatedRepositoryName = repositoryName;
        }

        for (int i = 0; i < nbMountedIndices; i++) {
            IllegalStateException exception = expectThrows(
                IllegalStateException.class,
                () -> clusterAdmin().prepareDeleteRepository(updatedRepositoryName).get()
            );
            assertThat(
                exception.getMessage(),
                containsString(
                    "trying to modify or unregister repository ["
                        + updatedRepositoryName
                        + "] that is currently used (found "
                        + (nbMountedIndices - i)
                        + " searchable snapshots indices that use the repository:"
                )
            );
            assertAcked(client().admin().indices().prepareDelete(mountedIndices[i]));
        }

        assertAcked(clusterAdmin().prepareDeleteRepository(updatedRepositoryName));
    }

    public void testMountIndexWithDeletionOfSnapshotFailsIfNotSingleIndexSnapshot() throws Exception {
        final String repository = "repository-" + getTestName().toLowerCase(Locale.ROOT);
        createRepository(repository, FsRepository.TYPE, randomRepositorySettings());

        final int nbIndices = randomIntBetween(2, 5);
        for (int i = 0; i < nbIndices; i++) {
            createAndPopulateIndex(
                "index-" + i,
                Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0).put(INDEX_SOFT_DELETES_SETTING.getKey(), true)
            );
        }

        final String snapshot = "snapshot";
        createFullSnapshot(repository, snapshot);
        assertAcked(client().admin().indices().prepareDelete("index-*"));

        final String index = "index-" + randomInt(nbIndices - 1);
        final String mountedIndex = "mounted-" + index;

        final SnapshotRestoreException exception = expectThrows(
            SnapshotRestoreException.class,
            () -> mountSnapshot(repository, snapshot, index, mountedIndex, deleteSnapshotIndexSettings(true), randomFrom(Storage.values()))
        );
        assertThat(
            exception.getMessage(),
            allOf(
                containsString("cannot mount snapshot [" + repository + '/'),
                containsString(snapshot + "] as index [" + mountedIndex + "] with the deletion of snapshot on index removal enabled"),
                containsString("[index.store.snapshot.delete_searchable_snapshot: true]; "),
                containsString("snapshot contains [" + nbIndices + "] indices instead of 1.")
            )
        );
    }

    public void testMountIndexWithDeletionOfSnapshot() throws Exception {
        final String repository = "repository-" + getTestName().toLowerCase(Locale.ROOT);
        createRepository(repository, FsRepository.TYPE, randomRepositorySettings());

        final String index = "index";
        createAndPopulateIndex(index, Settings.builder().put(INDEX_SOFT_DELETES_SETTING.getKey(), true));

        final TotalHits totalHits = internalCluster().client().prepareSearch(index).setTrackTotalHits(true).get().getHits().getTotalHits();

        final String snapshot = "snapshot";
        createSnapshot(repository, snapshot, List.of(index));
        assertAcked(client().admin().indices().prepareDelete(index));

        String mounted = "mounted-with-setting-enabled";
        mountSnapshot(repository, snapshot, index, mounted, deleteSnapshotIndexSettings(true), randomFrom(Storage.values()));
        assertIndexSetting(mounted, SEARCHABLE_SNAPSHOTS_DELETE_SNAPSHOT_ON_INDEX_DELETION, equalTo("true"));
        assertHitCount(client().prepareSearch(mounted).setTrackTotalHits(true).get(), totalHits.value);

        // the snapshot is already mounted as an index with "index.store.snapshot.delete_searchable_snapshot: true",
        // any attempt to mount the snapshot again should fail
        final String mountedAgain = randomValueOtherThan(mounted, () -> randomAlphaOfLength(10).toLowerCase(Locale.ROOT));
        SnapshotRestoreException exception = expectThrows(
            SnapshotRestoreException.class,
            () -> mountSnapshot(repository, snapshot, index, mountedAgain, deleteSnapshotIndexSettings(randomBoolean()))
        );
        assertThat(
            exception.getMessage(),
            allOf(
                containsString("cannot mount snapshot [" + repository + '/'),
                containsString(':' + snapshot + "] as index [" + mountedAgain + "]; another index [" + mounted + '/'),
                containsString("] uses the snapshot with the deletion of snapshot on index removal enabled "),
                containsString("[index.store.snapshot.delete_searchable_snapshot: true].")
            )
        );

        assertAcked(client().admin().indices().prepareDelete(mounted));
        mounted = "mounted-with-setting-disabled";
        mountSnapshot(repository, snapshot, index, mounted, deleteSnapshotIndexSettings(false), randomFrom(Storage.values()));
        assertIndexSetting(mounted, SEARCHABLE_SNAPSHOTS_DELETE_SNAPSHOT_ON_INDEX_DELETION, equalTo("false"));
        assertHitCount(client().prepareSearch(mounted).setTrackTotalHits(true).get(), totalHits.value);

        // the snapshot is now mounted as an index with "index.store.snapshot.delete_searchable_snapshot: false",
        // any attempt to mount the snapshot again with "delete_searchable_snapshot: true" should fail
        exception = expectThrows(
            SnapshotRestoreException.class,
            () -> mountSnapshot(repository, snapshot, index, mountedAgain, deleteSnapshotIndexSettings(true))
        );
        assertThat(
            exception.getMessage(),
            allOf(
                containsString("cannot mount snapshot [" + repository + '/'),
                containsString(snapshot + "] as index [" + mountedAgain + "] with the deletion of snapshot on index removal enabled"),
                containsString("[index.store.snapshot.delete_searchable_snapshot: true]; another index [" + mounted + '/'),
                containsString("] uses the snapshot.")
            )
        );

        // but we can continue to mount the snapshot, as long as it does not require the cascade deletion of the snapshot
        mountSnapshot(repository, snapshot, index, mountedAgain, deleteSnapshotIndexSettings(false));
        assertIndexSetting(mountedAgain, SEARCHABLE_SNAPSHOTS_DELETE_SNAPSHOT_ON_INDEX_DELETION, equalTo("false"));
        assertHitCount(client().prepareSearch(mountedAgain).setTrackTotalHits(true).get(), totalHits.value);

        assertAcked(client().admin().indices().prepareDelete(mountedAgain));
        assertAcked(client().admin().indices().prepareDelete(mounted));
    }

    public void testDeletionOfSnapshotSettingCannotBeUpdated() throws Exception {
        final String repository = "repository-" + getTestName().toLowerCase(Locale.ROOT);
        createRepository(repository, FsRepository.TYPE, randomRepositorySettings());

        final String index = "index";
        createAndPopulateIndex(index, Settings.builder().put(INDEX_SOFT_DELETES_SETTING.getKey(), true));

        final TotalHits totalHits = internalCluster().client().prepareSearch(index).setTrackTotalHits(true).get().getHits().getTotalHits();

        final String snapshot = "snapshot";
        createSnapshot(repository, snapshot, List.of(index));
        assertAcked(client().admin().indices().prepareDelete(index));

        final String mounted = "mounted-" + index;
        final boolean deleteSnapshot = randomBoolean();

        mountSnapshot(repository, snapshot, index, mounted, deleteSnapshotIndexSettings(deleteSnapshot), randomFrom(Storage.values()));
        assertIndexSetting(mounted, SEARCHABLE_SNAPSHOTS_DELETE_SNAPSHOT_ON_INDEX_DELETION, equalTo(Boolean.toString(deleteSnapshot)));
        assertHitCount(client().prepareSearch(mounted).setTrackTotalHits(true).get(), totalHits.value);

        final IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> client().admin()
                .indices()
                .prepareUpdateSettings(mounted)
                .setSettings(deleteSnapshotIndexSettings(deleteSnapshot == false))
                .get()
        );
        assertThat(
            exception.getMessage(),
            containsString("can not update private setting [index.store.snapshot.delete_searchable_snapshot]; ")
        );

        assertAcked(client().admin().indices().prepareDelete(mounted));
    }

    private static Settings deleteSnapshotIndexSettings(boolean value) {
        return Settings.builder().put(SEARCHABLE_SNAPSHOTS_DELETE_SNAPSHOT_ON_INDEX_DELETION, value).build();
    }

    private static void assertIndexSetting(String indexName, String indexSettingName, Matcher<String> matcher) {
        final GetSettingsResponse getSettingsResponse = client().admin().indices().prepareGetSettings(indexName).get();
        assertThat(
            "Unexpected value for setting [" + indexSettingName + "] of index [" + indexName + ']',
            getSettingsResponse.getSetting(indexName, indexSettingName),
            matcher
        );
    }
}
