/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.core.ml;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.persistent.PersistentTasksCustomMetaData;
import org.elasticsearch.xpack.core.ml.datafeed.DatafeedState;
import org.elasticsearch.xpack.core.ml.job.config.JobState;
import org.elasticsearch.xpack.core.ml.job.config.JobTaskState;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public final class MlTasks {

    public static final String JOB_TASK_PREFIX = "job-";
    public static final String DATAFEED_TASK_PREFIX = "datafeed-";

    private MlTasks() {
    }

    /**
     * Namespaces the task ids for jobs.
     * A datafeed id can be used as a job id, because they are stored separately in cluster state.
     */
    public static String jobTaskId(String jobId) {
        return JOB_TASK_PREFIX + jobId;
    }

    /**
     * Namespaces the task ids for datafeeds.
     * A job id can be used as a datafeed id, because they are stored separately in cluster state.
     */
    public static String datafeedTaskId(String datafeedId) {
        return DATAFEED_TASK_PREFIX + datafeedId;
    }

    @Nullable
    public static PersistentTasksCustomMetaData.PersistentTask<?> getJobTask(String jobId, @Nullable PersistentTasksCustomMetaData tasks) {
        return tasks == null ? null : tasks.getTask(jobTaskId(jobId));
    }

    @Nullable
    public static PersistentTasksCustomMetaData.PersistentTask<?> getDatafeedTask(String datafeedId,
                                                                                  @Nullable PersistentTasksCustomMetaData tasks) {
        return tasks == null ? null : tasks.getTask(datafeedTaskId(datafeedId));
    }

    public static JobState getJobState(String jobId, @Nullable PersistentTasksCustomMetaData tasks) {
        PersistentTasksCustomMetaData.PersistentTask<?> task = getJobTask(jobId, tasks);
        if (task != null) {
            JobTaskState jobTaskState = (JobTaskState) task.getState();
            if (jobTaskState == null) {
                return JobState.OPENING;
            }
            return jobTaskState.getState();
        }
        // If we haven't opened a job than there will be no persistent task, which is the same as if the job was closed
        return JobState.CLOSED;
    }

    public static DatafeedState getDatafeedState(String datafeedId, @Nullable PersistentTasksCustomMetaData tasks) {
        PersistentTasksCustomMetaData.PersistentTask<?> task = getDatafeedTask(datafeedId, tasks);
        if (task != null && task.getState() != null) {
            return (DatafeedState) task.getState();
        } else {
            // If we haven't started a datafeed then there will be no persistent task,
            // which is the same as if the datafeed was't started
            return DatafeedState.STOPPED;
        }
    }

    /**
     * The job Ids of anomaly detector job tasks
     * @param tasks Active tasks
     * @return The job Ids of anomaly detector job tasks
     */
    public static Set<String> openJobIds(PersistentTasksCustomMetaData tasks) {
        Collection<PersistentTasksCustomMetaData.PersistentTask<?>> activeTasks = tasks.tasks();

        return activeTasks.stream().filter(t -> t.getId().startsWith(JOB_TASK_PREFIX))
                .map(t -> t.getId().substring(JOB_TASK_PREFIX.length()))
                .collect(Collectors.toSet());
    }
}
