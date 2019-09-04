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

package org.elasticsearch.index.reindex;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ContextPreservingActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.persistent.AllocatedPersistentTask;
import org.elasticsearch.persistent.PersistentTaskState;
import org.elasticsearch.persistent.PersistentTasksCustomMetaData;
import org.elasticsearch.persistent.PersistentTasksExecutor;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.tasks.TaskManager;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class ReindexTask extends AllocatedPersistentTask {

    private static final Logger logger = LogManager.getLogger(ReindexTask.class);

    // TODO: Name
    public static final String NAME = "reindex/job";

    private final NodeClient client;
    private final ReindexIndexClient reindexIndexClient;
    private final Reindexer reindexer;
    private final TaskId taskId;
    private final BulkByScrollTask childTask;

    public static class ReindexPersistentTasksExecutor extends PersistentTasksExecutor<ReindexJob> {

        private final ClusterService clusterService;
        private final Client client;
        private final ThreadPool threadPool;
        private final ScriptService scriptService;
        private final ReindexSslConfig reindexSslConfig;
        private final NamedXContentRegistry xContentRegistry;

        ReindexPersistentTasksExecutor(ClusterService clusterService, Client client, NamedXContentRegistry xContentRegistry,
                                       ThreadPool threadPool, ScriptService scriptService, ReindexSslConfig reindexSslConfig) {
            super(NAME, ThreadPool.Names.GENERIC);
            this.clusterService = clusterService;
            this.client = client;
            this.xContentRegistry = xContentRegistry;
            this.threadPool = threadPool;
            this.scriptService = scriptService;
            this.reindexSslConfig = reindexSslConfig;
        }

        @Override
        protected void nodeOperation(AllocatedPersistentTask task, ReindexJob reindexJob, PersistentTaskState state) {
            ReindexTask reindexTask = (ReindexTask) task;
            reindexTask.execute(reindexJob);
        }

        @Override
        protected AllocatedPersistentTask createTask(long id, String type, String action, TaskId parentTaskId,
                                                     PersistentTasksCustomMetaData.PersistentTask<ReindexJob> taskInProgress,
                                                     Map<String, String> headers) {
            headers.putAll(taskInProgress.getParams().getHeaders());
            Reindexer reindexer = new Reindexer(clusterService, client, threadPool, scriptService, reindexSslConfig);
            return new ReindexTask(id, type, action, parentTaskId, headers, clusterService, xContentRegistry, client, reindexer);
        }
    }

    private ReindexTask(long id, String type, String action, TaskId parentTask, Map<String, String> headers,
                        ClusterService clusterService, NamedXContentRegistry xContentRegistry, Client client, Reindexer reindexer) {
        // TODO: description
        super(id, type, action, "persistent reindex", parentTask, headers);
        this.client = (NodeClient) client;
        this.reindexIndexClient = new ReindexIndexClient(client, clusterService, xContentRegistry);
        this.reindexer = reindexer;
        this.taskId = new TaskId(clusterService.localNode().getId(), id);
        this.childTask = new BulkByScrollTask(id, type, action, getDescription(), parentTask, headers);
    }

    @Override
    public Status getStatus() {
        return childTask.getStatus();
    }

    BulkByScrollTask getChildTask() {
        return childTask;
    }

    private void execute(ReindexJob reindexJob) {
        Assigner assigner = new Assigner(reindexIndexClient, getPersistentTaskId(), getAllocationId());
        assigner.assign(new Assigner.Listener() {
            @Override
            public void onAssignment(ReindexTaskState taskState) {
                ReindexRequest reindexRequest = taskState.getStateDoc().getReindexRequest();
                ProgressState progressState = new ProgressState(taskState);
                Runnable performReindex = () -> performReindex(reindexJob, reindexRequest, progressState);
                reindexer.initTask(childTask, reindexRequest, new ActionListener<>() {
                    @Override
                    public void onResponse(Void aVoid) {
                        sendStartedNotification(reindexJob.shouldStoreResult(), performReindex);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleError(reindexJob.shouldStoreResult(), reindexRequest, progressState, e);
                    }
                });
            }

            @Override
            public void onFailure(ReindexJobState.Status status, Exception ex) {
                updateClusterStateToFailed(reindexJob.shouldStoreResult(), status, ex);
            }
        });
    }

    private void sendStartedNotification(boolean shouldStoreResult, Runnable listener) {
        updatePersistentTaskState(new ReindexJobState(taskId, ReindexJobState.Status.STARTED), new ActionListener<>() {
            @Override
            public void onResponse(PersistentTasksCustomMetaData.PersistentTask<?> persistentTask) {
                listener.run();
            }

            @Override
            public void onFailure(Exception e) {
                logger.info("Failed to update task in cluster state to started", e);
                markEphemeralTaskFailed(shouldStoreResult, e);
            }
        });
    }

    private void performReindex(ReindexJob reindexJob, ReindexRequest reindexRequest, ProgressState progressState) {
        ScrollableHitSource.Checkpoint checkpoint = progressState.lastState.getStateDoc().getCheckpoint();
        ThreadContext threadContext = client.threadPool().getThreadContext();

        // todo: need to store status in state so we can continue from it.
        if (childTask.isWorker()) { // only unsliced supports restarts.
            childTask.setCommittedStatus(childTask.getStatus());
        }

        boolean shouldStoreResult = reindexJob.shouldStoreResult();
        Supplier<ThreadContext.StoredContext> context = threadContext.newRestorableContext(false);
        // TODO: Eventually we only want to retain security context
        try (ThreadContext.StoredContext ignore = stashWithHeaders(threadContext, reindexJob.getHeaders())) {
            reindexer.execute(childTask, reindexRequest, new ContextPreservingActionListener<>(context, new ActionListener<>() {
                @Override
                public void onResponse(BulkByScrollResponse response) {
                    handleDone(shouldStoreResult, reindexRequest, progressState, response);
                }

                @Override
                public void onFailure(Exception ex) {
                    handleError(shouldStoreResult, reindexRequest, progressState, ex);
                }
            }), checkpoint, progressState);
        }
    }

    private void handleDone(boolean shouldStoreResult, ReindexRequest reindexRequest, ProgressState progressState,
                            BulkByScrollResponse response) {
        TaskManager taskManager = getTaskManager();
        assert taskManager != null : "TaskManager should have been set before reindex started";

        ReindexTaskStateDoc state = new ReindexTaskStateDoc(reindexRequest, getAllocationId(), response, null, (RestStatus) null, null);
        progressState.setDone(state, new ActionListener<>() {
            @Override
            public void onResponse(ReindexTaskState taskState) {
                updatePersistentTaskState(new ReindexJobState(taskId, ReindexJobState.Status.DONE), new ActionListener<>() {
                    @Override
                    public void onResponse(PersistentTasksCustomMetaData.PersistentTask<?> persistentTask) {
                        if (shouldStoreResult) {
                            taskManager.storeResult(ReindexTask.this, response, new ActionListener<>() {
                                @Override
                                public void onResponse(BulkByScrollResponse response) {
                                    markAsCompleted();
                                }

                                @Override
                                public void onFailure(Exception ex) {
                                    logger.info("Failed to store task result", ex);
                                    markAsFailed(ex);
                                }
                            });
                        } else {
                            markAsCompleted();
                        }
                    }

                    @Override
                    public void onFailure(Exception ex) {
                        logger.info("Failed to update task in cluster state to success", ex);
                        markEphemeralTaskFailed(shouldStoreResult, ex);
                    }
                });
            }

            @Override
            public void onFailure(Exception ex) {
                logger.info("Failed to write result to reindex index", ex);
                updateClusterStateToFailed(shouldStoreResult, ReindexJobState.Status.FAILED_TO_WRITE_TO_REINDEX_INDEX, ex);
            }
        });
    }

    private void handleError(boolean shouldStoreResult, ReindexRequest reindexRequest, ProgressState progressState, Exception ex) {
        TaskManager taskManager = getTaskManager();
        assert taskManager != null : "TaskManager should have been set before reindex started";

        ElasticsearchException exception = wrapException(ex);
        long allocationId = getAllocationId();
        ReindexTaskStateDoc state = new ReindexTaskStateDoc(reindexRequest, allocationId, null, exception, exception.status(), null);

        progressState.setDone(state, new ActionListener<>() {
            @Override
            public void onResponse(ReindexTaskState taskState) {
                updateClusterStateToFailed(shouldStoreResult, ReindexJobState.Status.DONE, ex);
            }

            @Override
            public void onFailure(Exception e) {
                logger.info("Failed to write exception reindex index", e);
                ex.addSuppressed(e);
                updateClusterStateToFailed(shouldStoreResult, ReindexJobState.Status.FAILED_TO_WRITE_TO_REINDEX_INDEX, ex);
            }
        });
    }

    private void updateClusterStateToFailed(boolean shouldStoreResult, ReindexJobState.Status status, Exception ex) {
        updatePersistentTaskState(new ReindexJobState(taskId, status), new ActionListener<>() {
            @Override
            public void onResponse(PersistentTasksCustomMetaData.PersistentTask<?> persistentTask) {
                markEphemeralTaskFailed(shouldStoreResult, ex);
            }

            @Override
            public void onFailure(Exception e) {
                logger.info("Failed to update task in cluster state to failed", e);
                ex.addSuppressed(e);
                markEphemeralTaskFailed(shouldStoreResult, ex);
            }
        });
    }

    private void markEphemeralTaskFailed(boolean shouldStoreResult, Exception ex) {
        TaskManager taskManager = getTaskManager();
        assert taskManager != null : "TaskManager should have been set before reindex started";
        if (shouldStoreResult) {
            taskManager.storeResult(ReindexTask.this, ex, ActionListener.wrap(() -> markAsFailed(ex)));
        } else {
            markAsFailed(ex);
        }
    }

    private static ElasticsearchException wrapException(Exception ex) {
        if (ex instanceof ElasticsearchException) {
            return (ElasticsearchException) ex;
        } else {
            return new ElasticsearchException(ex);
        }
    }

    // TODO: Copied from ClientHelper in x-pack
    private static ThreadContext.StoredContext stashWithHeaders(ThreadContext threadContext, Map<String, String> headers) {
        final ThreadContext.StoredContext storedContext = threadContext.stashContext();
        if (headers.containsKey(Task.X_OPAQUE_ID)) {
            headers = new HashMap<>(headers);
            // If the X_OPAQUE_ID is present, we should not set it again.
            headers.remove(Task.X_OPAQUE_ID);
        }
        threadContext.copyHeaders(headers.entrySet());
        return storedContext;
    }

    private static class Assigner {

        private final ReindexIndexClient reindexIndexClient;
        private final String persistentTaskId;
        private final long allocationId;
        private int assignmentAttempts = 0;

        private Assigner(ReindexIndexClient reindexIndexClient, String persistentTaskId, long allocationId) {
            this.reindexIndexClient = reindexIndexClient;
            this.persistentTaskId = persistentTaskId;
            this.allocationId = allocationId;
        }

        private void assign(Listener assignmentListener) {
            ++assignmentAttempts;
            reindexIndexClient.getReindexTaskDoc(persistentTaskId, new ActionListener<>() {
                @Override
                public void onResponse(ReindexTaskState taskState) {
                    long term = taskState.getPrimaryTerm();
                    long seqNo = taskState.getSeqNo();
                    ReindexTaskStateDoc oldDoc = taskState.getStateDoc();
                    ReindexRequest request = oldDoc.getReindexRequest();
                    BulkByScrollResponse response = oldDoc.getReindexResponse();
                    ElasticsearchException exception = oldDoc.getException();
                    RestStatus failureStatusCode = oldDoc.getFailureStatusCode();
                    ScrollableHitSource.Checkpoint checkpoint = oldDoc.getCheckpoint();

                    if (oldDoc.getAllocationId() == null || allocationId > oldDoc.getAllocationId()) {
                        ReindexTaskStateDoc newDoc = new ReindexTaskStateDoc(request, allocationId, response, exception, failureStatusCode,
                            checkpoint);
                        reindexIndexClient.updateReindexTaskDoc(persistentTaskId, newDoc, term, seqNo, new ActionListener<>() {
                            @Override
                            public void onResponse(ReindexTaskState newTaskState) {
                                assignmentListener.onAssignment(newTaskState);
                            }

                            @Override
                            public void onFailure(Exception ex) {
                                if (ex instanceof VersionConflictEngineException) {
                                    // There has been an indexing operation since the GET operation. Try
                                    // again if there are assignment attempts left.
                                    if (assignmentAttempts < 3) {
                                        assign(assignmentListener);
                                    } else {
                                        logger.info("Failed to write allocation id to reindex task doc after maximum retry attempts", ex);
                                        assignmentListener.onFailure(ReindexJobState.Status.ASSIGNMENT_FAILED, ex);
                                    }
                                } else {
                                    logger.info("Failed to write allocation id to reindex task doc", ex);
                                    assignmentListener.onFailure(ReindexJobState.Status.FAILED_TO_WRITE_TO_REINDEX_INDEX, ex);
                                }
                            }
                        });
                    } else {
                        ElasticsearchException ex = new ElasticsearchException("A newer task has already been allocated");
                        assignmentListener.onFailure(ReindexJobState.Status.ASSIGNMENT_FAILED, ex);
                    }
                }

                @Override
                public void onFailure(Exception ex) {
                    logger.info("Failed to fetch reindex task doc", ex);
                    assignmentListener.onFailure(ReindexJobState.Status.FAILED_TO_READ_FROM_REINDEX_INDEX, ex);
                }
            });
        }

        private interface Listener {

            void onAssignment(ReindexTaskState reindexTaskState);

            void onFailure(ReindexJobState.Status status, Exception exception);
        }
    }

    private class ProgressState implements Reindexer.CheckpointListener {

        private final Semaphore semaphore = new Semaphore(1);
        private ReindexTaskState lastState;
        private boolean isDone = false;

        ProgressState(ReindexTaskState initialState) {
            this.lastState = initialState;
        }

        @Override
        public void onCheckpoint(ScrollableHitSource.Checkpoint checkpoint, BulkByScrollTask.Status status) {
            // todo: need some kind of throttling here, no need to do this all the time.
            // only do one checkpoint at a time, in case checkpointing is too slow.
            if (semaphore.tryAcquire() && isDone == false) {
                ReindexTaskStateDoc nextState = lastState.getStateDoc().withCheckpoint(checkpoint, status);
                // todo: clarify whether updateReindexTaskDoc can fail with exception and use conditional update
                long term = lastState.getPrimaryTerm();
                long seqNo = lastState.getSeqNo();
                reindexIndexClient.updateReindexTaskDoc(getPersistentTaskId(), nextState, term, seqNo, new ActionListener<>() {
                        @Override
                        public void onResponse(ReindexTaskState taskState) {
                            lastState = taskState;
                            childTask.setCommittedStatus(status);
                            semaphore.release();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            semaphore.release();
                        }
                    });
            }
        }

        private void setDone(ReindexTaskStateDoc state, ActionListener<ReindexTaskState> listener) {
            // TODO: Maybe just normal acquire
            semaphore.acquireUninterruptibly();
            isDone = true;
            long term = lastState.getPrimaryTerm();
            long seqNo = lastState.getSeqNo();
            reindexIndexClient.updateReindexTaskDoc(getPersistentTaskId(), state, term, seqNo, new ActionListener<>() {
                @Override
                public void onResponse(ReindexTaskState taskState) {
                    lastState = null;
                    semaphore.release();
                    listener.onResponse(taskState);

                }

                @Override
                public void onFailure(Exception e) {
                    semaphore.release();
                    listener.onFailure(e);
                }
            });
        }
    }
}
