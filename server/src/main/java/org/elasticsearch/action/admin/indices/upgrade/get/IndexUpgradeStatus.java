/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.indices.upgrade.get;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class IndexUpgradeStatus implements Iterable<IndexShardUpgradeStatus> {

    private final String index;

    private final Map<Integer, IndexShardUpgradeStatus> indexShards;

    IndexUpgradeStatus(String index, ShardUpgradeStatus[] shards) {
        this.index = index;

        Map<Integer, List<ShardUpgradeStatus>> tmpIndexShards = new HashMap<>();
        for (ShardUpgradeStatus shard : shards) {
            List<ShardUpgradeStatus> lst = tmpIndexShards.get(shard.getShardRouting().id());
            if (lst == null) {
                lst = new ArrayList<>();
                tmpIndexShards.put(shard.getShardRouting().id(), lst);
            }
            lst.add(shard);
        }
        indexShards = new HashMap<>();
        for (Map.Entry<Integer, List<ShardUpgradeStatus>> entry : tmpIndexShards.entrySet()) {
            indexShards.put(
                entry.getKey(),
                new IndexShardUpgradeStatus(
                    entry.getValue().get(0).getShardRouting().shardId(),
                    entry.getValue().toArray(new ShardUpgradeStatus[entry.getValue().size()])
                )
            );
        }
    }

    public String getIndex() {
        return this.index;
    }

    /**
     * A shard id to index shard upgrade status map (note, index shard upgrade status is the replication shard group that maps
     * to the shard id).
     */
    public Map<Integer, IndexShardUpgradeStatus> getShards() {
        return this.indexShards;
    }

    @Override
    public Iterator<IndexShardUpgradeStatus> iterator() {
        return indexShards.values().iterator();
    }

    public long getTotalBytes() {
        long totalBytes = 0;
        for (IndexShardUpgradeStatus indexShardUpgradeStatus : indexShards.values()) {
            totalBytes += indexShardUpgradeStatus.getTotalBytes();
        }
        return totalBytes;
    }

    public long getToUpgradeBytes() {
        long upgradeBytes = 0;
        for (IndexShardUpgradeStatus indexShardUpgradeStatus : indexShards.values()) {
            upgradeBytes += indexShardUpgradeStatus.getToUpgradeBytes();
        }
        return upgradeBytes;
    }

    public long getToUpgradeBytesAncient() {
        long upgradeBytesAncient = 0;
        for (IndexShardUpgradeStatus indexShardUpgradeStatus : indexShards.values()) {
            upgradeBytesAncient += indexShardUpgradeStatus.getToUpgradeBytesAncient();
        }
        return upgradeBytesAncient;
    }

}
