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

package org.elasticsearch.indices;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.index.cache.filter.FilterCacheStats;
import org.elasticsearch.index.cache.id.IdCacheStats;
import org.elasticsearch.index.fielddata.FieldDataStats;
import org.elasticsearch.index.flush.FlushStats;
import org.elasticsearch.index.get.GetStats;
import org.elasticsearch.index.indexing.IndexingStats;
import org.elasticsearch.index.merge.MergeStats;
import org.elasticsearch.index.refresh.RefreshStats;
import org.elasticsearch.index.search.stats.SearchStats;
import org.elasticsearch.index.shard.DocsStats;
import org.elasticsearch.index.store.StoreStats;

import java.io.IOException;
import java.io.Serializable;

/**
 * Global information on indices stats running on a specific node.
 */
public class NodeIndicesStats implements Streamable, Serializable, ToXContent {

    private StoreStats storeStats;
    private DocsStats docsStats;
    private IndexingStats indexingStats;
    private GetStats getStats;
    private SearchStats searchStats;
    private FieldDataStats fieldDataStats;
    private MergeStats mergeStats;
    private RefreshStats refreshStats;
    private FlushStats flushStats;
    private FilterCacheStats filterCacheStats;
    private IdCacheStats idCacheStats;

    NodeIndicesStats() {
    }

    public NodeIndicesStats(StoreStats storeStats, DocsStats docsStats, IndexingStats indexingStats, GetStats getStats, SearchStats searchStats, FieldDataStats fieldDataStats, MergeStats mergeStats, RefreshStats refreshStats, FlushStats flushStats, FilterCacheStats filterCacheStats, IdCacheStats idCacheStats) {
        this.storeStats = storeStats;
        this.docsStats = docsStats;
        this.indexingStats = indexingStats;
        this.getStats = getStats;
        this.searchStats = searchStats;
        this.fieldDataStats = fieldDataStats;
        this.mergeStats = mergeStats;
        this.refreshStats = refreshStats;
        this.flushStats = flushStats;
        this.filterCacheStats = filterCacheStats;
        this.idCacheStats = idCacheStats;
    }

    /**
     * The size of the index storage taken on the node.
     */
    public StoreStats getStore() {
        return storeStats;
    }

    public DocsStats getDocs() {
        return this.docsStats;
    }

    public IndexingStats getIndexing() {
        return indexingStats;
    }

    public GetStats getGet() {
        return this.getStats;
    }

    public SearchStats getSearch() {
        return this.searchStats;
    }

    public MergeStats getMerge() {
        return this.mergeStats;
    }

    public RefreshStats getRefresh() {
        return refreshStats;
    }

    public FlushStats getFlush() {
        return this.flushStats;
    }

    public FieldDataStats getFieldData() {
        return fieldDataStats;
    }

    public FilterCacheStats getFilterCache() {
        return this.filterCacheStats;
    }

    public IdCacheStats getIdCache() {
        return this.idCacheStats;
    }

    public static NodeIndicesStats readIndicesStats(StreamInput in) throws IOException {
        NodeIndicesStats stats = new NodeIndicesStats();
        stats.readFrom(in);
        return stats;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        storeStats = StoreStats.readStoreStats(in);
        docsStats = DocsStats.readDocStats(in);
        indexingStats = IndexingStats.readIndexingStats(in);
        getStats = GetStats.readGetStats(in);
        searchStats = SearchStats.readSearchStats(in);
        fieldDataStats = FieldDataStats.readFieldDataStats(in);
        mergeStats = MergeStats.readMergeStats(in);
        refreshStats = RefreshStats.readRefreshStats(in);
        flushStats = FlushStats.readFlushStats(in);
        filterCacheStats = FilterCacheStats.readFilterCacheStats(in);
        idCacheStats = IdCacheStats.readIdCacheStats(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        storeStats.writeTo(out);
        docsStats.writeTo(out);
        indexingStats.writeTo(out);
        getStats.writeTo(out);
        searchStats.writeTo(out);
        fieldDataStats.writeTo(out);
        mergeStats.writeTo(out);
        refreshStats.writeTo(out);
        flushStats.writeTo(out);
        filterCacheStats.writeTo(out);
        idCacheStats.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(Fields.INDICES);

        storeStats.toXContent(builder, params);
        docsStats.toXContent(builder, params);
        indexingStats.toXContent(builder, params);
        getStats.toXContent(builder, params);
        searchStats.toXContent(builder, params);
        filterCacheStats.toXContent(builder, params);
        idCacheStats.toXContent(builder, params);
        fieldDataStats.toXContent(builder, params);
        mergeStats.toXContent(builder, params);
        refreshStats.toXContent(builder, params);
        flushStats.toXContent(builder, params);

        builder.endObject();
        return builder;
    }

    static final class Fields {
        static final XContentBuilderString INDICES = new XContentBuilderString("indices");
    }
}
