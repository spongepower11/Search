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

package org.elasticsearch.client;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.repositories.delete.DeleteRepositoryRequest;
import org.elasticsearch.action.admin.cluster.repositories.delete.DeleteRepositoryResponse;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesRequest;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesResponse;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryResponse;
import org.elasticsearch.action.admin.cluster.repositories.verify.VerifyRepositoryRequest;
import org.elasticsearch.action.admin.cluster.repositories.verify.VerifyRepositoryResponse;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.status.SnapshotsStatusRequest;
import org.elasticsearch.action.admin.cluster.snapshots.status.SnapshotsStatusResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.repositories.fs.FsRepository;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.snapshots.RestoreInfo;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

public class SnapshotIT extends ESRestHighLevelClientTestCase {

    private PutRepositoryResponse createTestRepository(String repository, String type, String settings) throws IOException {
        PutRepositoryRequest request = new PutRepositoryRequest(repository);
        request.settings(settings, XContentType.JSON);
        request.type(type);
        return execute(request, highLevelClient().snapshot()::createRepository,
            highLevelClient().snapshot()::createRepositoryAsync);
    }

    private CreateSnapshotResponse createTestSnapshot(CreateSnapshotRequest createSnapshotRequest) throws IOException {
        // assumes the repository already exists

        return execute(createSnapshotRequest, highLevelClient().snapshot()::create,
            highLevelClient().snapshot()::createAsync);
    }

    public void testCreateRepository() throws IOException {
        PutRepositoryResponse response = createTestRepository("test", FsRepository.TYPE, "{\"location\": \".\"}");
        assertTrue(response.isAcknowledged());
    }

    public void testSnapshotGetRepositoriesUsingParams() throws IOException {
        String testRepository = "test";
        assertTrue(createTestRepository(testRepository, FsRepository.TYPE, "{\"location\": \".\"}").isAcknowledged());
        assertTrue(createTestRepository("other", FsRepository.TYPE, "{\"location\": \".\"}").isAcknowledged());

        GetRepositoriesRequest request = new GetRepositoriesRequest();
        request.repositories(new String[]{testRepository});
        GetRepositoriesResponse response = execute(request, highLevelClient().snapshot()::getRepository,
            highLevelClient().snapshot()::getRepositoryAsync);
        assertThat(1, equalTo(response.repositories().size()));
    }

    public void testSnapshotGetDefaultRepositories() throws IOException {
        assertTrue(createTestRepository("other", FsRepository.TYPE, "{\"location\": \".\"}").isAcknowledged());
        assertTrue(createTestRepository("test", FsRepository.TYPE, "{\"location\": \".\"}").isAcknowledged());

        GetRepositoriesResponse response = execute(new GetRepositoriesRequest(), highLevelClient().snapshot()::getRepository,
            highLevelClient().snapshot()::getRepositoryAsync);
        assertThat(2, equalTo(response.repositories().size()));
    }

    public void testSnapshotGetRepositoriesNonExistent() {
        String repository = "doesnotexist";
        GetRepositoriesRequest request = new GetRepositoriesRequest(new String[]{repository});
        ElasticsearchException exception = expectThrows(ElasticsearchException.class, () -> execute(request,
            highLevelClient().snapshot()::getRepository, highLevelClient().snapshot()::getRepositoryAsync));

        assertThat(exception.status(), equalTo(RestStatus.NOT_FOUND));
        assertThat(exception.getMessage(), equalTo(
            "Elasticsearch exception [type=repository_missing_exception, reason=[" + repository + "] missing]"));
    }

    public void testSnapshotDeleteRepository() throws IOException {
        String repository = "test";
        assertTrue(createTestRepository(repository, FsRepository.TYPE, "{\"location\": \".\"}").isAcknowledged());

        GetRepositoriesRequest request = new GetRepositoriesRequest();
        GetRepositoriesResponse response = execute(request, highLevelClient().snapshot()::getRepository,
            highLevelClient().snapshot()::getRepositoryAsync);
        assertThat(1, equalTo(response.repositories().size()));

        DeleteRepositoryRequest deleteRequest = new DeleteRepositoryRequest(repository);
        DeleteRepositoryResponse deleteResponse = execute(deleteRequest, highLevelClient().snapshot()::deleteRepository,
            highLevelClient().snapshot()::deleteRepositoryAsync);

        assertTrue(deleteResponse.isAcknowledged());
    }

    public void testVerifyRepository() throws IOException {
        PutRepositoryResponse putRepositoryResponse = createTestRepository("test", FsRepository.TYPE, "{\"location\": \".\"}");
        assertTrue(putRepositoryResponse.isAcknowledged());

        VerifyRepositoryRequest request = new VerifyRepositoryRequest("test");
        VerifyRepositoryResponse response = execute(request, highLevelClient().snapshot()::verifyRepository,
            highLevelClient().snapshot()::verifyRepositoryAsync);
        assertThat(response.getNodes().size(), equalTo(1));
    }

    public void testCreateSnapshot() throws IOException {
        String repository = "test_repository";
        assertTrue(createTestRepository(repository, FsRepository.TYPE, "{\"location\": \".\"}").isAcknowledged());

        String snapshot = "test_snapshot";
        CreateSnapshotRequest request = new CreateSnapshotRequest(repository, snapshot);
        boolean waitForCompletion = randomBoolean();
        request.waitForCompletion(waitForCompletion);
        request.partial(randomBoolean());
        request.includeGlobalState(randomBoolean());

        CreateSnapshotResponse response = createTestSnapshot(request);
        assertEquals(waitForCompletion ? RestStatus.OK : RestStatus.ACCEPTED, response.status());
    }

    public void testGetSnapshots() throws IOException {
        String repository = "test_repository";
        String snapshot1 = "test_snapshot1";
        String snapshot2 = "test_snapshot2";

        PutRepositoryResponse putRepositoryResponse = createTestRepository(repository, FsRepository.TYPE, "{\"location\": \".\"}");
        assertTrue(putRepositoryResponse.isAcknowledged());

        CreateSnapshotRequest createSnapshotRequest1 = new CreateSnapshotRequest(repository, snapshot1);
        createSnapshotRequest1.waitForCompletion(true);
        CreateSnapshotResponse putSnapshotResponse1 = createTestSnapshot(createSnapshotRequest1);
        CreateSnapshotRequest createSnapshotRequest2 = new CreateSnapshotRequest(repository, snapshot2);
        createSnapshotRequest2.waitForCompletion(true);
        CreateSnapshotResponse putSnapshotResponse2 = createTestSnapshot(createSnapshotRequest2);
        // check that the request went ok without parsing JSON here. When using the high level client, check acknowledgement instead.
        assertEquals(RestStatus.OK, putSnapshotResponse1.status());
        assertEquals(RestStatus.OK, putSnapshotResponse2.status());

        GetSnapshotsRequest request;
        if (randomBoolean()) {
            request = new GetSnapshotsRequest(repository);
        } else if (randomBoolean()) {
            request = new GetSnapshotsRequest(repository, new String[] {"_all"});

        } else {
            request = new GetSnapshotsRequest(repository, new String[] {snapshot1, snapshot2});
        }
        GetSnapshotsResponse response = execute(request, highLevelClient().snapshot()::get, highLevelClient().snapshot()::getAsync);

        assertEquals(2, response.getSnapshots().size());
        assertThat(response.getSnapshots().stream().map((s) -> s.snapshotId().getName()).collect(Collectors.toList()),
            contains("test_snapshot1", "test_snapshot2"));
    }

    public void testSnapshotsStatus() throws IOException {
        String testRepository = "test";
        String testSnapshot = "snapshot";
        String testIndex = "test_index";

        PutRepositoryResponse putRepositoryResponse = createTestRepository(testRepository, FsRepository.TYPE, "{\"location\": \".\"}");
        assertTrue(putRepositoryResponse.isAcknowledged());

        createIndex(testIndex, Settings.EMPTY);

        CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest(testRepository, testSnapshot);
        createSnapshotRequest.indices(testIndex);
        createSnapshotRequest.waitForCompletion(true);
        CreateSnapshotResponse createSnapshotResponse = createTestSnapshot(createSnapshotRequest);
        // check that the request went ok without parsing JSON here. When using the high level client, check acknowledgement instead.
        assertEquals(RestStatus.OK, createSnapshotResponse.status());

        SnapshotsStatusRequest request = new SnapshotsStatusRequest();
        request.repository(testRepository);
        request.snapshots(new String[]{testSnapshot});
        SnapshotsStatusResponse response = execute(request, highLevelClient().snapshot()::status,
            highLevelClient().snapshot()::statusAsync);
        assertThat(response.getSnapshots().size(), equalTo(1));
        assertThat(response.getSnapshots().get(0).getSnapshot().getRepository(), equalTo(testRepository));
        assertThat(response.getSnapshots().get(0).getSnapshot().getSnapshotId().getName(), equalTo(testSnapshot));
        assertThat(response.getSnapshots().get(0).getIndices().containsKey(testIndex), is(true));
    }

    public void testRestoreSnapshot() throws IOException {
        String testRepository = "test";
        String testSnapshot = "snapshot_1";
        String testIndex = "test_index";
        String restoredIndex = testIndex + "_restored";

        PutRepositoryResponse putRepositoryResponse = createTestRepository(testRepository, FsRepository.TYPE, "{\"location\": \".\"}");
        assertTrue(putRepositoryResponse.isAcknowledged());

        createIndex(testIndex, Settings.EMPTY);
        assertTrue("index [" + testIndex + "] should have been created", indexExists(testIndex));

        CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest(testRepository, testSnapshot);
        createSnapshotRequest.indices(testIndex);
        createSnapshotRequest.waitForCompletion(true);
        CreateSnapshotResponse createSnapshotResponse = createTestSnapshot(createSnapshotRequest);
        assertEquals(RestStatus.OK, createSnapshotResponse.status());

        deleteIndex(testIndex);
        assertFalse("index [" + testIndex + "] should have been deleted", indexExists(testIndex));

        RestoreSnapshotRequest request = new RestoreSnapshotRequest(testRepository, testSnapshot);
        request.waitForCompletion(true);
        request.renamePattern(testIndex);
        request.renameReplacement(restoredIndex);

        RestoreSnapshotResponse response = execute(request, highLevelClient().snapshot()::restore,
            highLevelClient().snapshot()::restoreAsync);

        RestoreInfo restoreInfo = response.getRestoreInfo();
        assertThat(restoreInfo.name(), equalTo(testSnapshot));
        assertThat(restoreInfo.indices(), equalTo(Collections.singletonList(restoredIndex)));
        assertThat(restoreInfo.successfulShards(), greaterThan(0));
        assertThat(restoreInfo.failedShards(), equalTo(0));
    }

    public void testDeleteSnapshot() throws IOException {
        String repository = "test_repository";
        String snapshot = "test_snapshot";

        PutRepositoryResponse putRepositoryResponse = createTestRepository(repository, FsRepository.TYPE, "{\"location\": \".\"}");
        assertTrue(putRepositoryResponse.isAcknowledged());

        CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest(repository, snapshot);
        createSnapshotRequest.waitForCompletion(true);
        CreateSnapshotResponse createSnapshotResponse = createTestSnapshot(createSnapshotRequest);
        // check that the request went ok without parsing JSON here. When using the high level client, check acknowledgement instead.
        assertEquals(RestStatus.OK, createSnapshotResponse.status());

        DeleteSnapshotRequest request = new DeleteSnapshotRequest(repository, snapshot);
        DeleteSnapshotResponse response = execute(request, highLevelClient().snapshot()::delete, highLevelClient().snapshot()::deleteAsync);

        assertTrue(response.isAcknowledged());
    }
}
