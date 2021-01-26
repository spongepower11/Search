package org.elasticsearch.xpack.transform.transforms;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.xpack.core.transform.transforms.DestConfig;
import org.elasticsearch.xpack.core.transform.transforms.RetentionPolicyConfig;
import org.elasticsearch.xpack.core.transform.transforms.SettingsConfig;
import org.elasticsearch.xpack.core.transform.transforms.TimeRetentionPolicyConfig;
import org.elasticsearch.xpack.core.transform.transforms.TransformCheckpoint;

import java.time.Instant;

public final class RetentionPolicyToDeleteByQueryRequestConverter {

    public static class RetentionPolicyException extends ElasticsearchException {
        RetentionPolicyException(String msg, Object... args) {
            super(msg, args);
        }
    }

    private RetentionPolicyToDeleteByQueryRequestConverter() {}

    static DeleteByQueryRequest buildDeleteByQueryRequest(
        RetentionPolicyConfig retentionPolicyConfig,
        SettingsConfig settingsConfig,
        DestConfig destConfig,
        TransformCheckpoint checkpoint
    ) {
        DeleteByQueryRequest request = new DeleteByQueryRequest();

        if (retentionPolicyConfig instanceof TimeRetentionPolicyConfig) {
            request.setQuery(getDeleteQueryFromTimeBasedRetentionPolicy((TimeRetentionPolicyConfig) retentionPolicyConfig, checkpoint));
        } else {
            throw new RetentionPolicyException("unsupported retention policy of type [{}]", retentionPolicyConfig.getWriteableName());
        }

        /* other dbq options not set and why:
         * - timeout: we do not timeout for search, so we don't timeout for dbq
         * - batch size: don't use max page size search, dbq should be simple
         */
        request.setSlices(AbstractBulkByScrollRequest.AUTO_SLICES)
            .setBatchSize(AbstractBulkByScrollRequest.DEFAULT_SCROLL_SIZE)
            // this should not happen, but still go over version conflicts and report later
            .setAbortOnVersionConflict(false)
            // use the same throttling as for search
            .setRequestsPerSecond(settingsConfig.getDocsPerSecond())
            .indices(destConfig.getIndex());

        return request;
    }

    private static QueryBuilder getDeleteQueryFromTimeBasedRetentionPolicy(
        TimeRetentionPolicyConfig config,
        TransformCheckpoint checkpoint
    ) {
        Instant cutOffDate = Instant.ofEpochMilli(checkpoint.getTimeUpperBound()).minusMillis(config.getMaxAge().getMillis());
        return QueryBuilders.rangeQuery(config.getField()).lt(cutOffDate.toEpochMilli()).format("epoch_millis");
    }
}
