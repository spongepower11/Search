/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.autoscaling.storage;

import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.json.JsonXContent;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.greaterThan;

public class ReactiveReasonTests extends ESTestCase {

    @SuppressWarnings("unchecked")
    public void testXContent() throws IOException {
        String reason = randomAlphaOfLength(10);
        long unassigned = randomNonNegativeLong();
        long assigned = randomNonNegativeLong();
        String indexUUID = UUIDs.randomBase64UUID();
        SortedSet<ShardId> unassignedShardIds = new TreeSet<>(randomUnique(() -> new ShardId("index", indexUUID, randomInt(1000)), 8));
        SortedSet<ShardId> assignedShardIds = new TreeSet<>(randomUnique(() -> new ShardId("index", indexUUID, randomInt(1000)), 600));
        var reactiveReason = new ReactiveStorageDeciderService.ReactiveReason(
            reason,
            unassigned,
            assigned,
            unassignedShardIds,
            assignedShardIds
        );

        try (
            XContentParser parser = createParser(
                JsonXContent.jsonXContent,
                BytesReference.bytes(reactiveReason.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
            )
        ) {
            Map<String, Object> map = parser.map();
            assertEquals(reason, map.get("reason"));
            assertEquals(unassigned, map.get("unassigned"));
            assertEquals(assigned, map.get("assigned"));
            assertEquals(unassignedShardIds.stream().map(ShardId::toString).collect(Collectors.toList()), map.get("unassigned_shard_ids"));
            List<String> xContentAssignedShardIds = (List<String>) map.get("assigned_shard_ids");
            assertEquals(
                assignedShardIds.stream()
                    .map(ShardId::toString)
                    .limit(ReactiveStorageDeciderService.ReactiveReason.MAX_ASSIGNED_SHARD_IDS)
                    .collect(Collectors.toList()),
                xContentAssignedShardIds
            );
            assertSorted(xContentAssignedShardIds.stream().map(ShardId::fromString).collect(Collectors.toList()));
            assertEquals(assignedShardIds.size(), map.get("amount_of_assigned_shards"));
        }
    }

    private static void assertSorted(Collection<ShardId> collection) {
        ShardId previous = null;
        for (ShardId e : collection) {
            if (previous != null) {
                assertThat(e, greaterThan(previous));
            }
            previous = e;
        }
    }
}
