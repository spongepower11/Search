/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.action.admin.indices.stats;

import com.carrotsearch.hppc.ObjectIntMap;
import com.carrotsearch.hppc.ObjectIntOpenHashMap;
import com.carrotsearch.hppc.ObjectObjectMap;
import com.carrotsearch.hppc.ObjectObjectOpenHashMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.elasticsearch.action.ShardOperationFailedException;
import org.elasticsearch.action.admin.indices.stats.CommonStats.WeightSnapshot;
import org.elasticsearch.action.support.broadcast.BroadcastOperationResponse;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.metrics.MeteredMeanMetric.TimeSnapshot;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 */
public class IndicesStatsResponse extends BroadcastOperationResponse implements ToXContent {

    private ShardStats[] shards;

    private ImmutableMap<ShardRouting, CommonStats> shardStatsMap;

    IndicesStatsResponse() {

    }

    IndicesStatsResponse(ShardStats[] shards, ClusterState clusterState, int totalShards, int successfulShards, int failedShards, List<ShardOperationFailedException> shardFailures) {
        super(totalShards, successfulShards, failedShards, shardFailures);
        this.shards = shards;
    }

    public ImmutableMap<ShardRouting, CommonStats> asMap() {
        if (shardStatsMap == null) {
            ImmutableMap.Builder<ShardRouting, CommonStats> mb = ImmutableMap.builder();
            for (ShardStats ss : shards) {
                mb.put(ss.getShardRouting(), ss.getStats());
            }

            shardStatsMap = mb.build();
        }
        return shardStatsMap;
    }

    public ShardStats[] getShards() {
        return this.shards;
    }

    public ShardStats getAt(int position) {
        return shards[position];
    }

    public IndexStats getIndex(String index) {
        return getIndices().get(index);
    }

    private Map<String, IndexStats> indicesStats;

    public Map<String, IndexStats> getIndices() {
        if (indicesStats != null) {
            return indicesStats;
        }
        Map<String, IndexStats> indicesStats = Maps.newHashMap();

        Set<String> indices = Sets.newHashSet();
        for (ShardStats shard : shards) {
            indices.add(shard.getIndex());
        }

        for (String index : indices) {
            List<ShardStats> shards = Lists.newArrayList();
            for (ShardStats shard : this.shards) {
                if (shard.getShardRouting().index().equals(index)) {
                    shards.add(shard);
                }
            }
            indicesStats.put(index, new IndexStats(index, shards.toArray(new ShardStats[shards.size()])));
        }
        this.indicesStats = indicesStats;
        return indicesStats;
    }

    private CommonStats total = null;

    public CommonStats getTotal() {
        if (total != null) {
            return total;
        }
        CommonStats stats = new CommonStats();
        for (ShardStats shard : shards) {
            stats.add(shard.getStats());
        }
        total = stats;
        return stats;
    }

    private CommonStats primary = null;

    public CommonStats getPrimaries() {
        if (primary != null) {
            return primary;
        }
        CommonStats stats = new CommonStats();
        for (ShardStats shard : shards) {
            if (shard.getShardRouting().primary()) {
                stats.add(shard.getStats());
            }
        }
        primary = stats;
        return stats;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        shards = new ShardStats[in.readVInt()];
        for (int i = 0; i < shards.length; i++) {
            shards[i] = ShardStats.readShardStats(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVInt(shards.length);
        for (ShardStats shard : shards) {
            shard.writeTo(out);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("_all");

        builder.startObject("primaries");
        getPrimaries().toXContent(builder, params);
        builder.endObject();

        builder.startObject("total");
        getTotal().toXContent(builder, params);
        builder.endObject();

        builder.endObject();

        boolean shardLevel = "shards".equalsIgnoreCase(params.param("level", null));
        if (shardLevel) {
            calculateWeights();
        }
        
        builder.startObject(Fields.INDICES);
        for (IndexStats indexStats : getIndices().values()) {
            builder.startObject(indexStats.getIndex(), XContentBuilder.FieldCaseConversion.NONE);

            builder.startObject("primaries");
            indexStats.getPrimaries().toXContent(builder, params);
            builder.endObject();

            builder.startObject("total");
            indexStats.getTotal().toXContent(builder, params);
            builder.endObject();

            if (shardLevel) {
                builder.startObject(Fields.SHARDS);
                for (IndexShardStats indexShardStats : indexStats) {
                    builder.startArray(Integer.toString(indexShardStats.getShardId().id()));
                    for (ShardStats shardStats : indexShardStats) {
                        builder.startObject();
                        shardStats.toXContent(builder, params);
                        builder.endObject();
                    }
                    builder.endArray();
                }
                builder.endObject();
            }

            builder.endObject();
        }
        builder.endObject();

        return builder;
    }
    
    private void calculateWeights() {
        ObjectObjectMap<String, TotalWeight> nodeTotalWeights = ObjectObjectOpenHashMap.newInstance();
        ObjectIntMap<String> nodeShardCounts = ObjectIntOpenHashMap.newInstance();
        TotalWeight clusterTotalWeight = new TotalWeight();
        int clusterShardCount = 0;
        for (ShardStats shard : shards) {
            String nodeId = shard.getShardRouting().currentNodeId();
            nodeShardCounts.put(nodeId, 1 + nodeShardCounts.getOrDefault(nodeId, 0));
            clusterShardCount += 1;
            
            TotalWeight shardWeight = getTotalWeightForShard(shard.stats);
            clusterTotalWeight.add(shardWeight);
            TotalWeight nodeTotalWeight = nodeTotalWeights.get(nodeId);
            if (nodeTotalWeight == null) {
                // Ok to just use the shard weight because we're not going to use it for anything else
                nodeTotalWeight = shardWeight;
            } else {
                nodeTotalWeight.add(shardWeight);
            }
            nodeTotalWeights.put(nodeId, nodeTotalWeight);
        }

        for (ShardStats shard : shards) {
            String nodeId = shard.getShardRouting().currentNodeId();
            int nodeShardCount = nodeShardCounts.get(nodeId);
            TotalWeight nodeTotalWeight = nodeTotalWeights.get(nodeId);

            TotalWeight shardTotalWeight = getTotalWeightForShard(shard.getStats());            
            shard.stats.weight = new CommonStats.Weight(
                    clusterTotalWeight.buildWeightSnapshot(shardTotalWeight, clusterShardCount),
                    nodeTotalWeight.buildWeightSnapshot(shardTotalWeight, nodeShardCount));
        }
    }
    
    private TotalWeight getTotalWeightForShard(CommonStats commonStats) {
        TotalWeight weight = new TotalWeight();
        if (commonStats.getSearch() != null) {
            weight.add(commonStats.getSearch().getTotal().getQueryTimeSnapshot()); 
            weight.add(commonStats.getSearch().getTotal().getFetchTimeSnapshot());
        }
        if (commonStats.getIndexing() != null) {
            weight.add(commonStats.getIndexing().getTotal().getIndexTimeSnapshot()); 
            weight.add(commonStats.getIndexing().getTotal().getDeleteTimeSnapshot());    
        }
        return weight;
    }

    /**
     * Holds total weight values based on moving averages and builds scaled WeightSnapshots.
     */
    private static class TotalWeight {
        private long weight1Minute, weight5Minute, weight15Minute, weight1Hour, weight1Day, weight1Week;
        
        public void add(TotalWeight other) {
            weight1Minute += other.weight1Minute;
            weight5Minute += other.weight5Minute;
            weight15Minute += other.weight15Minute;
            weight1Hour += other.weight1Hour;
            weight1Day += other.weight1Day;
            weight1Week += other.weight1Week;
        }
        
        public void add(TimeSnapshot snapshot) {
            weight1Minute += snapshot.get1MinuteRate();
            weight5Minute += snapshot.get5MinuteRate();
            weight15Minute += snapshot.get15MinuteRate();
            weight1Hour += snapshot.get1HourRate();
            weight1Day += snapshot.get1DayRate();
            weight1Week += snapshot.get1WeekRate();
        }
        
        public WeightSnapshot buildWeightSnapshot(TotalWeight shardTotalWeight, int shardCount) {
            float shardCountFloat = shardCount;
            return new WeightSnapshot(
                    shardCountFloat * shardTotalWeight.weight1Minute / weight1Minute,
                    shardCountFloat * shardTotalWeight.weight5Minute / weight15Minute,
                    shardCountFloat * shardTotalWeight.weight15Minute / weight15Minute,
                    shardCountFloat * shardTotalWeight.weight1Hour / weight1Hour,
                    shardCountFloat * shardTotalWeight.weight1Day / weight1Day,
                    shardCountFloat * shardTotalWeight.weight1Week / weight1Week);
        }
    }

    static final class Fields {
        static final XContentBuilderString INDICES = new XContentBuilderString("indices");
        static final XContentBuilderString SHARDS = new XContentBuilderString("shards");
    }

    @Override
    public String toString() {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint();
            builder.startObject();
            toXContent(builder, EMPTY_PARAMS);
            builder.endObject();
            return builder.string();
        } catch (IOException e) {
            return "{ \"error\" : \"" + e.getMessage() + "\"}";
        }
    }
}
