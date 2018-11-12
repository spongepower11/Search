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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksResponse;
import org.elasticsearch.action.admin.cluster.storedscripts.DeleteStoredScriptRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptResponse;
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.explain.ExplainRequest;
import org.elasticsearch.action.explain.ExplainResponse;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesRequest;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.main.MainRequest;
import org.elasticsearch.action.main.MainResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.core.TermVectorsResponse;
import org.elasticsearch.client.core.TermVectorsRequest;
import org.elasticsearch.common.CheckedConsumer;
import org.elasticsearch.common.CheckedFunction;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.rankeval.RankEvalRequest;
import org.elasticsearch.index.rankeval.RankEvalResponse;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.plugins.spi.NamedXContentProvider;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.mustache.MultiSearchTemplateRequest;
import org.elasticsearch.script.mustache.MultiSearchTemplateResponse;
import org.elasticsearch.script.mustache.SearchTemplateRequest;
import org.elasticsearch.script.mustache.SearchTemplateResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.adjacency.AdjacencyMatrixAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.adjacency.ParsedAdjacencyMatrix;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilters;
import org.elasticsearch.search.aggregations.bucket.geogrid.GeoGridAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.geogrid.ParsedGeoHashGrid;
import org.elasticsearch.search.aggregations.bucket.global.GlobalAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.global.ParsedGlobal;
import org.elasticsearch.search.aggregations.bucket.histogram.AutoDateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedAutoDateHistogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedHistogram;
import org.elasticsearch.search.aggregations.bucket.missing.MissingAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.missing.ParsedMissing;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedReverseNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.DateRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.GeoDistanceAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.IpRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.ParsedBinaryRange;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.bucket.range.ParsedGeoDistance;
import org.elasticsearch.search.aggregations.bucket.range.ParsedRange;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.sampler.InternalSampler;
import org.elasticsearch.search.aggregations.bucket.sampler.ParsedSampler;
import org.elasticsearch.search.aggregations.bucket.significant.ParsedSignificantLongTerms;
import org.elasticsearch.search.aggregations.bucket.significant.ParsedSignificantStringTerms;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantLongTerms;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.DoubleTerms;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedDoubleTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.ParsedAvg;
import org.elasticsearch.search.aggregations.metrics.cardinality.CardinalityAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.cardinality.ParsedCardinality;
import org.elasticsearch.search.aggregations.metrics.geobounds.GeoBoundsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.geobounds.ParsedGeoBounds;
import org.elasticsearch.search.aggregations.metrics.geocentroid.GeoCentroidAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.geocentroid.ParsedGeoCentroid;
import org.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.max.ParsedMax;
import org.elasticsearch.search.aggregations.metrics.min.MinAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.ParsedMin;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.InternalHDRPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.InternalHDRPercentiles;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.ParsedHDRPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.ParsedHDRPercentiles;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.scripted.ParsedScriptedMetric;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetricAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.elasticsearch.search.aggregations.metrics.stats.StatsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStatsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ParsedExtendedStats;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.tophits.ParsedTopHits;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.valuecount.ParsedValueCount;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCountAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.mad.MedianAbsoluteDeviationAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.mad.ParsedMedianAbsoluteDeviation;
import org.elasticsearch.search.aggregations.pipeline.InternalSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.ParsedSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.InternalBucketMetricValue;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.ParsedBucketMetricValue;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.percentile.ParsedPercentilesBucket;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.percentile.PercentilesBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats.ParsedStatsBucket;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats.StatsBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats.extended.ExtendedStatsBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats.extended.ParsedExtendedStatsBucket;
import org.elasticsearch.search.aggregations.pipeline.derivative.DerivativePipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.derivative.ParsedDerivative;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestion;
import org.elasticsearch.search.suggest.term.TermSuggestion;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

/**
 * High level REST client that wraps an instance of the low level {@link RestClient} and allows to build requests and read responses.
 * The {@link RestClient} instance is internally built based on the provided {@link RestClientBuilder} and it gets closed automatically
 * when closing the {@link RestHighLevelClient} instance that wraps it.
 * In case an already existing instance of a low-level REST client needs to be provided, this class can be subclassed and the
 * {@link #RestHighLevelClient(RestClient, CheckedConsumer, List)}  constructor can be used.
 * This class can also be sub-classed to expose additional client methods that make use of endpoints added to Elasticsearch through
 * plugins, or to add support for custom response sections, again added to Elasticsearch through plugins.
 */
public class RestHighLevelClient implements Closeable {

    private final RestClient client;
    private final NamedXContentRegistry registry;
    private final CheckedConsumer<RestClient, IOException> doClose;

    private final IndicesClient indicesClient = new IndicesClient(this);
    private final ClusterClient clusterClient = new ClusterClient(this);
    private final IngestClient ingestClient = new IngestClient(this);
    private final SnapshotClient snapshotClient = new SnapshotClient(this);
    private final TasksClient tasksClient = new TasksClient(this);
    private final XPackClient xPackClient = new XPackClient(this);
    private final WatcherClient watcherClient = new WatcherClient(this);
    private final GraphClient graphClient = new GraphClient(this);
    private final LicenseClient licenseClient = new LicenseClient(this);
    private final IndexLifecycleClient indexLifecycleClient = new IndexLifecycleClient(this);
    private final MigrationClient migrationClient = new MigrationClient(this);
    private final MachineLearningClient machineLearningClient = new MachineLearningClient(this);
    private final SecurityClient securityClient = new SecurityClient(this);
    private final RollupClient rollupClient = new RollupClient(this);
    private final CcrClient ccrClient = new CcrClient(this);

    /**
     * Creates a {@link RestHighLevelClient} given the low level {@link RestClientBuilder} that allows to build the
     * {@link RestClient} to be used to perform requests.
     */
    public RestHighLevelClient(RestClientBuilder restClientBuilder) {
        this(restClientBuilder, Collections.emptyList());
    }

    /**
     * Creates a {@link RestHighLevelClient} given the low level {@link RestClientBuilder} that allows to build the
     * {@link RestClient} to be used to perform requests and parsers for custom response sections added to Elasticsearch through plugins.
     */
    protected RestHighLevelClient(RestClientBuilder restClientBuilder, List<NamedXContentRegistry.Entry> namedXContentEntries) {
        this(restClientBuilder.build(), RestClient::close, namedXContentEntries);
    }

    /**
     * Creates a {@link RestHighLevelClient} given the low level {@link RestClient} that it should use to perform requests and
     * a list of entries that allow to parse custom response sections added to Elasticsearch through plugins.
     * This constructor can be called by subclasses in case an externally created low-level REST client needs to be provided.
     * The consumer argument allows to control what needs to be done when the {@link #close()} method is called.
     * Also subclasses can provide parsers for custom response sections added to Elasticsearch through plugins.
     */
    protected RestHighLevelClient(RestClient restClient, CheckedConsumer<RestClient, IOException> doClose,
                                  List<NamedXContentRegistry.Entry> namedXContentEntries) {
        this.client = Objects.requireNonNull(restClient, "restClient must not be null");
        this.doClose = Objects.requireNonNull(doClose, "doClose consumer must not be null");
        this.registry = new NamedXContentRegistry(
                Stream.of(getDefaultNamedXContents().stream(), getProvidedNamedXContents().stream(), namedXContentEntries.stream())
                    .flatMap(Function.identity()).collect(toList()));
    }

    /**
     * Returns the low-level client that the current high-level client instance is using to perform requests
     */
    public final RestClient getLowLevelClient() {
        return client;
    }

    @Override
    public final void close() throws IOException {
        doClose.accept(client);
    }

    /**
     * Provides an {@link IndicesClient} which can be used to access the Indices API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/indices.html">Indices API on elastic.co</a>
     */
    public final IndicesClient indices() {
        return indicesClient;
    }

    /**
     * Provides a {@link ClusterClient} which can be used to access the Cluster API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster.html">Cluster API on elastic.co</a>
     */
    public final ClusterClient cluster() {
        return clusterClient;
    }

    /**
     * Provides a {@link IngestClient} which can be used to access the Ingest API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/ingest.html">Ingest API on elastic.co</a>
     */
    public final IngestClient ingest() {
        return ingestClient;
    }

    /**
     * Provides a {@link SnapshotClient} which can be used to access the Snapshot API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-snapshots.html">Snapshot API on elastic.co</a>
     */
    public final SnapshotClient snapshot() {
        return snapshotClient;
    }

    /**
     * Provides methods for accessing the Elastic Licensed Rollup APIs that
     * are shipped with the default distribution of Elasticsearch. All of
     * these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/rollup-apis.html">
     * Watcher APIs on elastic.co</a> for more information.
     */
    public RollupClient rollup() {
        return rollupClient;
    }

    /**
     * Provides methods for accessing the Elastic Licensed CCR APIs that
     * are shipped with the Elastic Stack distribution of Elasticsearch. All of
     * these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/ccr-api.html">
     * CCR APIs on elastic.co</a> for more information.
     *
     * @return the client wrapper for making CCR API calls
     */
    public final CcrClient ccr() {
        return ccrClient;
    }

    /**
     * Provides a {@link TasksClient} which can be used to access the Tasks API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/tasks.html">Task Management API on elastic.co</a>
     */
    public final TasksClient tasks() {
        return tasksClient;
    }

    /**
     * Provides methods for accessing the Elastic Licensed X-Pack Info
     * and Usage APIs that are shipped with the default distribution of
     * Elasticsearch. All of these APIs will 404 if run against the OSS
     * distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/info-api.html">
     * Info APIs on elastic.co</a> for more information.
     */
    public final XPackClient xpack() {
        return xPackClient;
    }

    /**
     * Provides methods for accessing the Elastic Licensed Watcher APIs that
     * are shipped with the default distribution of Elasticsearch. All of
     * these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/watcher-api.html">
     * Watcher APIs on elastic.co</a> for more information.
     */
    public WatcherClient watcher() { return watcherClient; }

    /**
     * Provides methods for accessing the Elastic Licensed Graph explore API that
     * is shipped with the default distribution of Elasticsearch. All of
     * these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/graph-explore-api.html">
     * Graph API on elastic.co</a> for more information.
     */
    public GraphClient graph() { return graphClient; }

    /**
     * Provides methods for accessing the Elastic Licensed Licensing APIs that
     * are shipped with the default distribution of Elasticsearch. All of
     * these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/licensing-apis.html">
     * Licensing APIs on elastic.co</a> for more information.
     */
    public LicenseClient license() { return licenseClient; }

    /**
     * Provides methods for accessing the Elastic Licensed Index Lifecycle APIs that are shipped with the default distribution of
     * Elasticsearch. All of these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="http://FILL-ME-IN-WE-HAVE-NO-DOCS-YET.com"> X-Pack APIs on elastic.co</a> for more information.
     */
    public IndexLifecycleClient indexLifecycle() {
        return indexLifecycleClient;
    }

    /**
     * Provides methods for accessing the Elastic Licensed Licensing APIs that
     * are shipped with the default distribution of Elasticsearch. All of
     * these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/migration-api.html">
     * Migration APIs on elastic.co</a> for more information.
     */
    public MigrationClient migration() {
        return migrationClient;
    }

    /**
     * Provides methods for accessing the Elastic Licensed Machine Learning APIs that
     * are shipped with the Elastic Stack distribution of Elasticsearch. All of
     * these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/ml-apis.html">
     * Machine Learning APIs on elastic.co</a> for more information.
     *
     * @return the client wrapper for making Machine Learning API calls
     */
    public MachineLearningClient machineLearning() {
        return machineLearningClient;
    }

    /**
     * Provides methods for accessing the Elastic Licensed Security APIs that
     * are shipped with the Elastic Stack distribution of Elasticsearch. All of
     * these APIs will 404 if run against the OSS distribution of Elasticsearch.
     * <p>
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/security-api.html">
     * Security APIs on elastic.co</a> for more information.
     *
     * @return the client wrapper for making Security API calls
     */
    public SecurityClient security() {
        return securityClient;
    }

    /**
     * Executes a bulk request using the Bulk API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html">Bulk API on elastic.co</a>
     * @param bulkRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final BulkResponse bulk(BulkRequest bulkRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(bulkRequest, RequestConverters::bulk, options, BulkResponse::fromXContent, emptySet());
    }

    /**
     * Executes a bulk request using the Bulk API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html">Bulk API on elastic.co</a>
     * @deprecated Prefer {@link #bulk(BulkRequest, RequestOptions)}
     */
    @Deprecated
    public final BulkResponse bulk(BulkRequest bulkRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(bulkRequest, RequestConverters::bulk, BulkResponse::fromXContent, emptySet(), headers);
    }

    /**
     * Asynchronously executes a bulk request using the Bulk API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html">Bulk API on elastic.co</a>
     * @param bulkRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void bulkAsync(BulkRequest bulkRequest, RequestOptions options, ActionListener<BulkResponse> listener) {
        performRequestAsyncAndParseEntity(bulkRequest, RequestConverters::bulk, options, BulkResponse::fromXContent, listener, emptySet());
    }

    /**
     * Asynchronously executes a bulk request using the Bulk API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html">Bulk API on elastic.co</a>
     * @deprecated Prefer {@link #bulkAsync(BulkRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void bulkAsync(BulkRequest bulkRequest, ActionListener<BulkResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(bulkRequest, RequestConverters::bulk, BulkResponse::fromXContent, listener, emptySet(), headers);
    }

    /**
     * Executes a reindex request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html">Reindex API on elastic.co</a>
     * @param reindexRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final BulkByScrollResponse reindex(ReindexRequest reindexRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(
            reindexRequest, RequestConverters::reindex, options, BulkByScrollResponse::fromXContent, emptySet()
        );
    }

    /**
     * Asynchronously executes a reindex request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html">Reindex API on elastic.co</a>
     * @param reindexRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void reindexAsync(ReindexRequest reindexRequest, RequestOptions options, ActionListener<BulkByScrollResponse> listener) {
        performRequestAsyncAndParseEntity(
            reindexRequest, RequestConverters::reindex, options, BulkByScrollResponse::fromXContent, listener, emptySet()
        );
    }

    /**
     * Executes a update by query request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update-by-query.html">
     *     Update By Query API on elastic.co</a>
     * @param updateByQueryRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final BulkByScrollResponse updateByQuery(UpdateByQueryRequest updateByQueryRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(
            updateByQueryRequest, RequestConverters::updateByQuery, options, BulkByScrollResponse::fromXContent, emptySet()
        );
    }

    /**
     * Asynchronously executes an update by query request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update-by-query.html">
     *     Update By Query API on elastic.co</a>
     * @param updateByQueryRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void updateByQueryAsync(UpdateByQueryRequest updateByQueryRequest, RequestOptions options,
                                         ActionListener<BulkByScrollResponse> listener) {
        performRequestAsyncAndParseEntity(
            updateByQueryRequest, RequestConverters::updateByQuery, options, BulkByScrollResponse::fromXContent, listener, emptySet()
        );
    }

    /**
     * Executes a delete by query request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html">
     *     Delete By Query API on elastic.co</a>
     * @param deleteByQueryRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final BulkByScrollResponse deleteByQuery(DeleteByQueryRequest deleteByQueryRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(
            deleteByQueryRequest, RequestConverters::deleteByQuery, options, BulkByScrollResponse::fromXContent, emptySet()
        );
    }

    /**
     * Asynchronously executes a delete by query request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html">
     *     Delete By Query API on elastic.co</a>
     * @param deleteByQueryRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void deleteByQueryAsync(DeleteByQueryRequest deleteByQueryRequest, RequestOptions options,
                                         ActionListener<BulkByScrollResponse> listener) {
        performRequestAsyncAndParseEntity(
            deleteByQueryRequest, RequestConverters::deleteByQuery, options, BulkByScrollResponse::fromXContent, listener, emptySet()
        );
    }

    /**
     * Executes a delete by query rethrottle request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html">
     *     Delete By Query API on elastic.co</a>
     * @param rethrottleRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final ListTasksResponse deleteByQueryRethrottle(RethrottleRequest rethrottleRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(rethrottleRequest, RequestConverters::rethrottleDeleteByQuery, options,
                ListTasksResponse::fromXContent, emptySet());
    }

    /**
     * Asynchronously execute an delete by query rethrottle request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html">
     *     Delete By Query API on elastic.co</a>
     * @param rethrottleRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void deleteByQueryRethrottleAsync(RethrottleRequest rethrottleRequest, RequestOptions options,
            ActionListener<ListTasksResponse> listener) {
        performRequestAsyncAndParseEntity(rethrottleRequest, RequestConverters::rethrottleDeleteByQuery, options,
                ListTasksResponse::fromXContent, listener, emptySet());
    }

    /**
     * Executes a update by query rethrottle request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update-by-query.html">
     *     Update By Query API on elastic.co</a>
     * @param rethrottleRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final ListTasksResponse updateByQueryRethrottle(RethrottleRequest rethrottleRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(rethrottleRequest, RequestConverters::rethrottleUpdateByQuery, options,
                ListTasksResponse::fromXContent, emptySet());
    }

    /**
     * Asynchronously execute an update by query rethrottle request.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update-by-query.html">
     *     Update By Query API on elastic.co</a>
     * @param rethrottleRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void updateByQueryRethrottleAsync(RethrottleRequest rethrottleRequest, RequestOptions options,
            ActionListener<ListTasksResponse> listener) {
        performRequestAsyncAndParseEntity(rethrottleRequest, RequestConverters::rethrottleUpdateByQuery, options,
                ListTasksResponse::fromXContent, listener, emptySet());
    }

    /**
     * Executes a reindex rethrottling request.
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html#docs-reindex-rethrottle">
     * Reindex rethrottling API on elastic.co</a>
     *
     * @param rethrottleRequest the request
     * @param options           the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final ListTasksResponse reindexRethrottle(RethrottleRequest rethrottleRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(rethrottleRequest, RequestConverters::rethrottleReindex, options,
                ListTasksResponse::fromXContent, emptySet());
    }

    /**
     * Executes a reindex rethrottling request.
     * See the <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html#docs-reindex-rethrottle">
     * Reindex rethrottling API on elastic.co</a>
     *
     * @param rethrottleRequest the request
     * @param options           the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener          the listener to be notified upon request completion
     */
    public final void reindexRethrottleAsync(RethrottleRequest rethrottleRequest, RequestOptions options,
            ActionListener<ListTasksResponse> listener) {
        performRequestAsyncAndParseEntity(rethrottleRequest, RequestConverters::rethrottleReindex, options, ListTasksResponse::fromXContent,
                listener, emptySet());
    }

    /**
     * Pings the remote Elasticsearch cluster and returns true if the ping succeeded, false otherwise
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return <code>true</code> if the ping succeeded, false otherwise
     * @throws IOException in case there is a problem sending the request
     */
    public final boolean ping(RequestOptions options) throws IOException {
        return performRequest(new MainRequest(), (request) -> RequestConverters.ping(), options, RestHighLevelClient::convertExistsResponse,
                emptySet());
    }

    /**
     * Pings the remote Elasticsearch cluster and returns true if the ping succeeded, false otherwise
     * @deprecated Prefer {@link #ping(RequestOptions)}
     */
    @Deprecated
    public final boolean ping(Header... headers) throws IOException {
        return performRequest(new MainRequest(), (request) -> RequestConverters.ping(), RestHighLevelClient::convertExistsResponse,
                emptySet(), headers);
    }

    /**
     * Get the cluster info otherwise provided when sending an HTTP request to '/'
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final MainResponse info(RequestOptions options) throws IOException {
        return performRequestAndParseEntity(new MainRequest(), (request) -> RequestConverters.info(), options,
                MainResponse::fromXContent, emptySet());
    }

    /**
     * Get the cluster info otherwise provided when sending an HTTP request to port 9200
     * @deprecated Prefer {@link #info(RequestOptions)}
     */
    @Deprecated
    public final MainResponse info(Header... headers) throws IOException {
        return performRequestAndParseEntity(new MainRequest(), (request) -> RequestConverters.info(),
                MainResponse::fromXContent, emptySet(), headers);
    }

    /**
     * Retrieves a document by id using the Get API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html">Get API on elastic.co</a>
     * @param getRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final GetResponse get(GetRequest getRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(getRequest, RequestConverters::get, options, GetResponse::fromXContent, singleton(404));
    }

    /**
     * Retrieves a document by id using the Get API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html">Get API on elastic.co</a>
     * @deprecated Prefer {@link #get(GetRequest, RequestOptions)}
     */
    @Deprecated
    public final GetResponse get(GetRequest getRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(getRequest, RequestConverters::get, GetResponse::fromXContent, singleton(404), headers);
    }

    /**
     * Asynchronously retrieves a document by id using the Get API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html">Get API on elastic.co</a>
     * @param getRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void getAsync(GetRequest getRequest, RequestOptions options, ActionListener<GetResponse> listener) {
        performRequestAsyncAndParseEntity(getRequest, RequestConverters::get, options, GetResponse::fromXContent, listener,
                singleton(404));
    }

    /**
     * Asynchronously retrieves a document by id using the Get API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html">Get API on elastic.co</a>
     * @deprecated Prefer {@link #getAsync(GetRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void getAsync(GetRequest getRequest, ActionListener<GetResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(getRequest, RequestConverters::get, GetResponse::fromXContent, listener,
                singleton(404), headers);
    }

    /**
     * Retrieves multiple documents by id using the Multi Get API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html">Multi Get API on elastic.co</a>
     * @param multiGetRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     * @deprecated use {@link #mget(MultiGetRequest, RequestOptions)} instead
     */
    @Deprecated
    public final MultiGetResponse multiGet(MultiGetRequest multiGetRequest, RequestOptions options) throws IOException {
        return mget(multiGetRequest, options);
    }


    /**
     * Retrieves multiple documents by id using the Multi Get API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html">Multi Get API on elastic.co</a>
     * @param multiGetRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final MultiGetResponse mget(MultiGetRequest multiGetRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(multiGetRequest, RequestConverters::multiGet, options, MultiGetResponse::fromXContent,
                singleton(404));
    }

    /**
     * Retrieves multiple documents by id using the Multi Get API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html">Multi Get API on elastic.co</a>
     * @deprecated Prefer {@link #multiGet(MultiGetRequest, RequestOptions)}
     */
    @Deprecated
    public final MultiGetResponse multiGet(MultiGetRequest multiGetRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(multiGetRequest, RequestConverters::multiGet, MultiGetResponse::fromXContent,
                singleton(404), headers);
    }

    /**
     * Asynchronously retrieves multiple documents by id using the Multi Get API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html">Multi Get API on elastic.co</a>
     * @param multiGetRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     * @deprecated use {@link #mgetAsync(MultiGetRequest, RequestOptions, ActionListener)} instead
     */
    @Deprecated
    public final void multiGetAsync(MultiGetRequest multiGetRequest, RequestOptions options, ActionListener<MultiGetResponse> listener) {
        mgetAsync(multiGetRequest, options, listener);
    }

    /**
     * Asynchronously retrieves multiple documents by id using the Multi Get API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html">Multi Get API on elastic.co</a>
     * @param multiGetRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void mgetAsync(MultiGetRequest multiGetRequest, RequestOptions options, ActionListener<MultiGetResponse> listener) {
        performRequestAsyncAndParseEntity(multiGetRequest, RequestConverters::multiGet, options, MultiGetResponse::fromXContent, listener,
                singleton(404));
    }

    /**
     * Asynchronously retrieves multiple documents by id using the Multi Get API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-multi-get.html">Multi Get API on elastic.co</a>
     * @deprecated Prefer {@link #multiGetAsync(MultiGetRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void multiGetAsync(MultiGetRequest multiGetRequest, ActionListener<MultiGetResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(multiGetRequest, RequestConverters::multiGet, MultiGetResponse::fromXContent, listener,
                singleton(404), headers);
    }

    /**
     * Checks for the existence of a document. Returns true if it exists, false otherwise.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html">Get API on elastic.co</a>
     * @param getRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return <code>true</code> if the document exists, <code>false</code> otherwise
     * @throws IOException in case there is a problem sending the request
     */
    public final boolean exists(GetRequest getRequest, RequestOptions options) throws IOException {
        return performRequest(getRequest, RequestConverters::exists, options, RestHighLevelClient::convertExistsResponse, emptySet());
    }

    /**
     * Checks for the existence of a document. Returns true if it exists, false otherwise.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html">Get API on elastic.co</a>
     * @deprecated Prefer {@link #exists(GetRequest, RequestOptions)}
     */
    @Deprecated
    public final boolean exists(GetRequest getRequest, Header... headers) throws IOException {
        return performRequest(getRequest, RequestConverters::exists, RestHighLevelClient::convertExistsResponse, emptySet(), headers);
    }

    /**
     * Asynchronously checks for the existence of a document. Returns true if it exists, false otherwise.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html">Get API on elastic.co</a>
     * @param getRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void existsAsync(GetRequest getRequest, RequestOptions options, ActionListener<Boolean> listener) {
        performRequestAsync(getRequest, RequestConverters::exists, options, RestHighLevelClient::convertExistsResponse, listener,
                emptySet());
    }

    /**
     * Asynchronously checks for the existence of a document. Returns true if it exists, false otherwise.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html">Get API on elastic.co</a>
     * @deprecated Prefer {@link #existsAsync(GetRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void existsAsync(GetRequest getRequest, ActionListener<Boolean> listener, Header... headers) {
        performRequestAsync(getRequest, RequestConverters::exists, RestHighLevelClient::convertExistsResponse, listener,
                emptySet(), headers);
    }

    /**
     * Checks for the existence of a document with a "_source" field. Returns true if it exists, false otherwise.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html#_source">Source exists API 
     * on elastic.co</a>
     * @param getRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return <code>true</code> if the document and _source field exists, <code>false</code> otherwise
     * @throws IOException in case there is a problem sending the request
     */
    public boolean existsSource(GetRequest getRequest, RequestOptions options) throws IOException {
        return performRequest(getRequest, RequestConverters::sourceExists, options, RestHighLevelClient::convertExistsResponse, emptySet());
    }     
    
    /**
     * Asynchronously checks for the existence of a document with a "_source" field. Returns true if it exists, false otherwise.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-get.html#_source">Source exists API 
     * on elastic.co</a>
     * @param getRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void existsSourceAsync(GetRequest getRequest, RequestOptions options, ActionListener<Boolean> listener) {
        performRequestAsync(getRequest, RequestConverters::sourceExists, options, RestHighLevelClient::convertExistsResponse, listener,
                emptySet());
    }    
    
    /**
     * Index a document using the Index API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html">Index API on elastic.co</a>
     * @param indexRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final IndexResponse index(IndexRequest indexRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(indexRequest, RequestConverters::index, options, IndexResponse::fromXContent, emptySet());
    }

    /**
     * Index a document using the Index API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html">Index API on elastic.co</a>
     * @deprecated Prefer {@link #index(IndexRequest, RequestOptions)}
     */
    @Deprecated
    public final IndexResponse index(IndexRequest indexRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(indexRequest, RequestConverters::index, IndexResponse::fromXContent, emptySet(), headers);
    }

    /**
     * Asynchronously index a document using the Index API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html">Index API on elastic.co</a>
     * @param indexRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void indexAsync(IndexRequest indexRequest, RequestOptions options, ActionListener<IndexResponse> listener) {
        performRequestAsyncAndParseEntity(indexRequest, RequestConverters::index, options, IndexResponse::fromXContent, listener,
                emptySet());
    }

    /**
     * Asynchronously index a document using the Index API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html">Index API on elastic.co</a>
     * @deprecated Prefer {@link #indexAsync(IndexRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void indexAsync(IndexRequest indexRequest, ActionListener<IndexResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(indexRequest, RequestConverters::index, IndexResponse::fromXContent, listener,
                emptySet(), headers);
    }

    /**
     * Executes a count request using the Count API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-count.html">Count API on elastic.co</a>
     * @param countRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final CountResponse count(CountRequest countRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(countRequest, RequestConverters::count, options, CountResponse::fromXContent,
        emptySet());
    }

    /**
     * Asynchronously executes a count request using the Count API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-count.html">Count API on elastic.co</a>
     * @param countRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void countAsync(CountRequest countRequest, RequestOptions options, ActionListener<CountResponse> listener) {
        performRequestAsyncAndParseEntity(countRequest, RequestConverters::count,  options,CountResponse::fromXContent,
            listener, emptySet());
    }

    /**
     * Updates a document using the Update API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html">Update API on elastic.co</a>
     * @param updateRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final UpdateResponse update(UpdateRequest updateRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(updateRequest, RequestConverters::update, options, UpdateResponse::fromXContent, emptySet());
    }

    /**
     * Updates a document using the Update API.
     * <p>
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html">Update API on elastic.co</a>
     * @deprecated Prefer {@link #update(UpdateRequest, RequestOptions)}
     */
    @Deprecated
    public final UpdateResponse update(UpdateRequest updateRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(updateRequest, RequestConverters::update, UpdateResponse::fromXContent, emptySet(), headers);
    }

    /**
     * Asynchronously updates a document using the Update API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html">Update API on elastic.co</a>
     * @param updateRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void updateAsync(UpdateRequest updateRequest, RequestOptions options, ActionListener<UpdateResponse> listener) {
        performRequestAsyncAndParseEntity(updateRequest, RequestConverters::update, options, UpdateResponse::fromXContent, listener,
                emptySet());
    }

    /**
     * Asynchronously updates a document using the Update API.
     * <p>
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html">Update API on elastic.co</a>
     * @deprecated Prefer {@link #updateAsync(UpdateRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void updateAsync(UpdateRequest updateRequest, ActionListener<UpdateResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(updateRequest, RequestConverters::update, UpdateResponse::fromXContent, listener,
                emptySet(), headers);
    }

    /**
     * Deletes a document by id using the Delete API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete.html">Delete API on elastic.co</a>
     * @param deleteRequest the reuqest
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final DeleteResponse delete(DeleteRequest deleteRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(deleteRequest, RequestConverters::delete, options, DeleteResponse::fromXContent,
                singleton(404));
    }

    /**
     * Deletes a document by id using the Delete API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete.html">Delete API on elastic.co</a>
     * @deprecated Prefer {@link #delete(DeleteRequest, RequestOptions)}
     */
    @Deprecated
    public final DeleteResponse delete(DeleteRequest deleteRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(deleteRequest, RequestConverters::delete, DeleteResponse::fromXContent,
                singleton(404), headers);
    }

    /**
     * Asynchronously deletes a document by id using the Delete API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete.html">Delete API on elastic.co</a>
     * @param deleteRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void deleteAsync(DeleteRequest deleteRequest, RequestOptions options, ActionListener<DeleteResponse> listener) {
        performRequestAsyncAndParseEntity(deleteRequest, RequestConverters::delete, options, DeleteResponse::fromXContent, listener,
            Collections.singleton(404));
    }

    /**
     * Asynchronously deletes a document by id using the Delete API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete.html">Delete API on elastic.co</a>
     * @deprecated Prefer {@link #deleteAsync(DeleteRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void deleteAsync(DeleteRequest deleteRequest, ActionListener<DeleteResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(deleteRequest, RequestConverters::delete, DeleteResponse::fromXContent, listener,
                Collections.singleton(404), headers);
    }

    /**
     * Executes a search request using the Search API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-search.html">Search API on elastic.co</a>
     * @param searchRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final SearchResponse search(SearchRequest searchRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(searchRequest, RequestConverters::search, options, SearchResponse::fromXContent, emptySet());
    }

    /**
     * Executes a search using the Search API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-search.html">Search API on elastic.co</a>
     * @deprecated Prefer {@link #search(SearchRequest, RequestOptions)}
     */
    @Deprecated
    public final SearchResponse search(SearchRequest searchRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(searchRequest, RequestConverters::search, SearchResponse::fromXContent, emptySet(), headers);
    }

    /**
     * Asynchronously executes a search using the Search API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-search.html">Search API on elastic.co</a>
     * @param searchRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void searchAsync(SearchRequest searchRequest, RequestOptions options, ActionListener<SearchResponse> listener) {
        performRequestAsyncAndParseEntity(searchRequest, RequestConverters::search, options, SearchResponse::fromXContent, listener,
                emptySet());
    }

    /**
     * Asynchronously executes a search using the Search API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-search.html">Search API on elastic.co</a>
     * @deprecated Prefer {@link #searchAsync(SearchRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void searchAsync(SearchRequest searchRequest, ActionListener<SearchResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(searchRequest, RequestConverters::search, SearchResponse::fromXContent, listener,
                emptySet(), headers);
    }

    /**
     * Executes a multi search using the msearch API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-multi-search.html">Multi search API on
     * elastic.co</a>
     * @param multiSearchRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     * @deprecated use {@link #msearch(MultiSearchRequest, RequestOptions)} instead
     */
    @Deprecated
    public final MultiSearchResponse multiSearch(MultiSearchRequest multiSearchRequest, RequestOptions options) throws IOException {
        return msearch(multiSearchRequest, options);
    }

    /**
     * Executes a multi search using the msearch API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-multi-search.html">Multi search API on
     * elastic.co</a>
     * @param multiSearchRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final MultiSearchResponse msearch(MultiSearchRequest multiSearchRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(multiSearchRequest, RequestConverters::multiSearch, options, MultiSearchResponse::fromXContext,
                emptySet());
    }

    /**
     * Executes a multi search using the msearch API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-multi-search.html">Multi search API on
     * elastic.co</a>
     * @deprecated Prefer {@link #multiSearch(MultiSearchRequest, RequestOptions)}
     */
    @Deprecated
    public final MultiSearchResponse multiSearch(MultiSearchRequest multiSearchRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(multiSearchRequest, RequestConverters::multiSearch, MultiSearchResponse::fromXContext,
                emptySet(), headers);
    }

    /**
     * Asynchronously executes a multi search using the msearch API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-multi-search.html">Multi search API on
     * elastic.co</a>
     * @param searchRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     * @deprecated use {@link #msearchAsync(MultiSearchRequest, RequestOptions, ActionListener)} instead
     */
    @Deprecated
    public final void multiSearchAsync(MultiSearchRequest searchRequest, RequestOptions options,
                                   ActionListener<MultiSearchResponse> listener) {
        msearchAsync(searchRequest, options, listener);
    }

    /**
     * Asynchronously executes a multi search using the msearch API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-multi-search.html">Multi search API on
     * elastic.co</a>
     * @param searchRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void msearchAsync(MultiSearchRequest searchRequest, RequestOptions options,
                                   ActionListener<MultiSearchResponse> listener) {
        performRequestAsyncAndParseEntity(searchRequest, RequestConverters::multiSearch, options, MultiSearchResponse::fromXContext,
                listener, emptySet());
    }

    /**
     * Asynchronously executes a multi search using the msearch API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-multi-search.html">Multi search API on
     * elastic.co</a>
     * @deprecated Prefer {@link #multiSearchAsync(MultiSearchRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void multiSearchAsync(MultiSearchRequest searchRequest, ActionListener<MultiSearchResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(searchRequest, RequestConverters::multiSearch, MultiSearchResponse::fromXContext, listener,
                emptySet(), headers);
    }

    /**
     * Executes a search using the Search Scroll API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html">Search Scroll
     * API on elastic.co</a>
     * @param searchScrollRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     * @deprecated use {@link #scroll(SearchScrollRequest, RequestOptions)} instead
     */
    @Deprecated
    public final SearchResponse searchScroll(SearchScrollRequest searchScrollRequest, RequestOptions options) throws IOException {
        return scroll(searchScrollRequest, options);
    }

    /**
     * Executes a search using the Search Scroll API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html">Search Scroll
     * API on elastic.co</a>
     * @param searchScrollRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final SearchResponse scroll(SearchScrollRequest searchScrollRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(searchScrollRequest, RequestConverters::searchScroll, options, SearchResponse::fromXContent,
                emptySet());
    }

    /**
     * Executes a search using the Search Scroll API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html">Search Scroll
     * API on elastic.co</a>
     * @deprecated Prefer {@link #searchScroll(SearchScrollRequest, RequestOptions)}
     */
    @Deprecated
    public final SearchResponse searchScroll(SearchScrollRequest searchScrollRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(searchScrollRequest, RequestConverters::searchScroll, SearchResponse::fromXContent,
                emptySet(), headers);
    }

    /**
     * Asynchronously executes a search using the Search Scroll API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html">Search Scroll
     * API on elastic.co</a>
     * @param searchScrollRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     * @deprecated use {@link #scrollAsync(SearchScrollRequest, RequestOptions, ActionListener)} instead
     */
    @Deprecated
    public final void searchScrollAsync(SearchScrollRequest searchScrollRequest, RequestOptions options,
                                  ActionListener<SearchResponse> listener) {
        scrollAsync(searchScrollRequest, options, listener);
    }

    /**
     * Asynchronously executes a search using the Search Scroll API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html">Search Scroll
     * API on elastic.co</a>
     * @param searchScrollRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void scrollAsync(SearchScrollRequest searchScrollRequest, RequestOptions options,
                                  ActionListener<SearchResponse> listener) {
        performRequestAsyncAndParseEntity(searchScrollRequest, RequestConverters::searchScroll, options, SearchResponse::fromXContent,
                listener, emptySet());
    }

    /**
     * Asynchronously executes a search using the Search Scroll API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html">Search Scroll
     * API on elastic.co</a>
     * @deprecated Prefer {@link #searchScrollAsync(SearchScrollRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void searchScrollAsync(SearchScrollRequest searchScrollRequest,
                                        ActionListener<SearchResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(searchScrollRequest, RequestConverters::searchScroll, SearchResponse::fromXContent,
                listener, emptySet(), headers);
    }

    /**
     * Clears one or more scroll ids using the Clear Scroll API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html#_clear_scroll_api">
     * Clear Scroll API on elastic.co</a>
     * @param clearScrollRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final ClearScrollResponse clearScroll(ClearScrollRequest clearScrollRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(clearScrollRequest, RequestConverters::clearScroll, options, ClearScrollResponse::fromXContent,
                emptySet());
    }

    /**
     * Clears one or more scroll ids using the Clear Scroll API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html#_clear_scroll_api">
     * Clear Scroll API on elastic.co</a>
     * @deprecated Prefer {@link #clearScroll(ClearScrollRequest, RequestOptions)}
     */
    @Deprecated
    public final ClearScrollResponse clearScroll(ClearScrollRequest clearScrollRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(clearScrollRequest, RequestConverters::clearScroll, ClearScrollResponse::fromXContent,
                emptySet(), headers);
    }

    /**
     * Asynchronously clears one or more scroll ids using the Clear Scroll API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html#_clear_scroll_api">
     * Clear Scroll API on elastic.co</a>
     * @param clearScrollRequest the reuqest
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void clearScrollAsync(ClearScrollRequest clearScrollRequest, RequestOptions options,
                                       ActionListener<ClearScrollResponse> listener) {
        performRequestAsyncAndParseEntity(clearScrollRequest, RequestConverters::clearScroll, options, ClearScrollResponse::fromXContent,
                listener, emptySet());
    }

    /**
     * Asynchronously clears one or more scroll ids using the Clear Scroll API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html#_clear_scroll_api">
     * Clear Scroll API on elastic.co</a>
     * @deprecated Prefer {@link #clearScrollAsync(ClearScrollRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void clearScrollAsync(ClearScrollRequest clearScrollRequest,
                                       ActionListener<ClearScrollResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(clearScrollRequest, RequestConverters::clearScroll, ClearScrollResponse::fromXContent,
                listener, emptySet(), headers);
    }

    /**
     * Executes a request using the Search Template API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-template.html">Search Template API
     * on elastic.co</a>.
     * @param searchTemplateRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final SearchTemplateResponse searchTemplate(SearchTemplateRequest searchTemplateRequest,
                                                       RequestOptions options) throws IOException {
        return performRequestAndParseEntity(searchTemplateRequest, RequestConverters::searchTemplate, options,
            SearchTemplateResponse::fromXContent, emptySet());
    }

    /**
     * Asynchronously executes a request using the Search Template API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-template.html">Search Template API
     * on elastic.co</a>.
     */
    public final void searchTemplateAsync(SearchTemplateRequest searchTemplateRequest, RequestOptions options,
                                          ActionListener<SearchTemplateResponse> listener) {
        performRequestAsyncAndParseEntity(searchTemplateRequest, RequestConverters::searchTemplate, options,
            SearchTemplateResponse::fromXContent, listener, emptySet());
    }

    /**
     * Executes a request using the Explain API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-explain.html">Explain API on elastic.co</a>
     * @param explainRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final ExplainResponse explain(ExplainRequest explainRequest, RequestOptions options) throws IOException {
        return performRequest(explainRequest, RequestConverters::explain, options,
            response -> {
                CheckedFunction<XContentParser, ExplainResponse, IOException> entityParser =
                    parser -> ExplainResponse.fromXContent(parser, convertExistsResponse(response));
                return parseEntity(response.getEntity(), entityParser);
            },
            singleton(404));
    }

    /**
     * Asynchronously executes a request using the Explain API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-explain.html">Explain API on elastic.co</a>
     * @param explainRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void explainAsync(ExplainRequest explainRequest, RequestOptions options, ActionListener<ExplainResponse> listener) {
        performRequestAsync(explainRequest, RequestConverters::explain, options,
            response -> {
                CheckedFunction<XContentParser, ExplainResponse, IOException> entityParser =
                    parser -> ExplainResponse.fromXContent(parser, convertExistsResponse(response));
                return parseEntity(response.getEntity(), entityParser);
            },
            listener, singleton(404));
    }


    /**
     * Calls the Term Vectors API
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-termvectors.html">Term Vectors API on
     * elastic.co</a>
     *
     * @param request   the request
     * @param options   the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     */
    public final TermVectorsResponse termvectors(TermVectorsRequest request, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(request, RequestConverters::termVectors, options, TermVectorsResponse::fromXContent,
            emptySet());
    }

    /**
     * Asynchronously calls the Term Vectors API
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-termvectors.html">Term Vectors API on
     * elastic.co</a>
     * @param request the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void termvectorsAsync(TermVectorsRequest request, RequestOptions options, ActionListener<TermVectorsResponse> listener) {
        performRequestAsyncAndParseEntity(request, RequestConverters::termVectors, options, TermVectorsResponse::fromXContent, listener,
            emptySet());
    }


    /**
     * Executes a request using the Ranking Evaluation API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-rank-eval.html">Ranking Evaluation API
     * on elastic.co</a>
     * @param rankEvalRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final RankEvalResponse rankEval(RankEvalRequest rankEvalRequest, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(rankEvalRequest, RequestConverters::rankEval, options, RankEvalResponse::fromXContent,
                emptySet());
    }

    /**
     * Executes a request using the Ranking Evaluation API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-rank-eval.html">Ranking Evaluation API
     * on elastic.co</a>
     * @deprecated Prefer {@link #rankEval(RankEvalRequest, RequestOptions)}
     */
    @Deprecated
    public final RankEvalResponse rankEval(RankEvalRequest rankEvalRequest, Header... headers) throws IOException {
        return performRequestAndParseEntity(rankEvalRequest, RequestConverters::rankEval, RankEvalResponse::fromXContent,
                emptySet(), headers);
    }

    /**
     * Asynchronously executes a request using the Ranking Evaluation API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-rank-eval.html">Ranking Evaluation API
     * on elastic.co</a>
     * @param rankEvalRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void rankEvalAsync(RankEvalRequest rankEvalRequest, RequestOptions options,  ActionListener<RankEvalResponse> listener) {
        performRequestAsyncAndParseEntity(rankEvalRequest, RequestConverters::rankEval, options,  RankEvalResponse::fromXContent, listener,
                emptySet());
    }

    /**
     * Executes a request using the Multi Search Template API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/multi-search-template.html">Multi Search Template API
     * on elastic.co</a>.
     */
    public final MultiSearchTemplateResponse msearchTemplate(MultiSearchTemplateRequest multiSearchTemplateRequest,
                                                             RequestOptions options) throws IOException {
        return performRequestAndParseEntity(multiSearchTemplateRequest, RequestConverters::multiSearchTemplate,
                options, MultiSearchTemplateResponse::fromXContext, emptySet());
    }

    /**
     * Asynchronously executes a request using the Multi Search Template API
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/multi-search-template.html">Multi Search Template API
     * on elastic.co</a>.
     */
    public final void msearchTemplateAsync(MultiSearchTemplateRequest multiSearchTemplateRequest,
                                           RequestOptions options,
                                           ActionListener<MultiSearchTemplateResponse> listener) {
        performRequestAsyncAndParseEntity(multiSearchTemplateRequest, RequestConverters::multiSearchTemplate,
            options, MultiSearchTemplateResponse::fromXContext, listener, emptySet());
    }

    /**
     * Asynchronously executes a request using the Ranking Evaluation API.
     *
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-rank-eval.html">Ranking Evaluation API
     * on elastic.co</a>
     * @deprecated Prefer {@link #rankEvalAsync(RankEvalRequest, RequestOptions, ActionListener)}
     */
    @Deprecated
    public final void rankEvalAsync(RankEvalRequest rankEvalRequest, ActionListener<RankEvalResponse> listener, Header... headers) {
        performRequestAsyncAndParseEntity(rankEvalRequest, RequestConverters::rankEval, RankEvalResponse::fromXContent, listener,
                emptySet(), headers);
    }

    /**
     * Executes a request using the Field Capabilities API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-field-caps.html">Field Capabilities API
     * on elastic.co</a>.
     * @param fieldCapabilitiesRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public final FieldCapabilitiesResponse fieldCaps(FieldCapabilitiesRequest fieldCapabilitiesRequest,
                                                     RequestOptions options) throws IOException {
        return performRequestAndParseEntity(fieldCapabilitiesRequest, RequestConverters::fieldCaps, options,
            FieldCapabilitiesResponse::fromXContent, emptySet());
    }

    /**
     * Get stored script by id.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html">
     *     How to use scripts on elastic.co</a>
     * @param request the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public GetStoredScriptResponse getScript(GetStoredScriptRequest request, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(request, RequestConverters::getScript, options,
            GetStoredScriptResponse::fromXContent, emptySet());
    }

    /**
     * Asynchronously get stored script by id.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html">
     *     How to use scripts on elastic.co</a>
     * @param request the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public void getScriptAsync(GetStoredScriptRequest request, RequestOptions options,
                               ActionListener<GetStoredScriptResponse> listener) {
        performRequestAsyncAndParseEntity(request, RequestConverters::getScript, options,
            GetStoredScriptResponse::fromXContent, listener, emptySet());
    }

    /**
     * Delete stored script by id.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html">
     *     How to use scripts on elastic.co</a>
     * @param request the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public AcknowledgedResponse deleteScript(DeleteStoredScriptRequest request, RequestOptions options) throws IOException {
        return performRequestAndParseEntity(request, RequestConverters::deleteScript, options,
            AcknowledgedResponse::fromXContent, emptySet());
    }

    /**
     * Asynchronously delete stored script by id.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html">
     *     How to use scripts on elastic.co</a>
     * @param request the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public void deleteScriptAsync(DeleteStoredScriptRequest request, RequestOptions options,
                                  ActionListener<AcknowledgedResponse> listener) {
        performRequestAsyncAndParseEntity(request, RequestConverters::deleteScript, options,
            AcknowledgedResponse::fromXContent, listener, emptySet());
    }

    /**
     * Puts an stored script using the Scripting API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html"> Scripting API
     * on elastic.co</a>
     * @param putStoredScriptRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @return the response
     * @throws IOException in case there is a problem sending the request or parsing back the response
     */
    public AcknowledgedResponse putScript(PutStoredScriptRequest putStoredScriptRequest,
                                             RequestOptions options) throws IOException {
        return performRequestAndParseEntity(putStoredScriptRequest, RequestConverters::putScript, options,
            AcknowledgedResponse::fromXContent, emptySet());
    }

    /**
     * Asynchronously puts an stored script using the Scripting API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html"> Scripting API
     * on elastic.co</a>
     * @param putStoredScriptRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public void putScriptAsync(PutStoredScriptRequest putStoredScriptRequest, RequestOptions options,
                               ActionListener<AcknowledgedResponse> listener) {
        performRequestAsyncAndParseEntity(putStoredScriptRequest, RequestConverters::putScript, options,
            AcknowledgedResponse::fromXContent, listener, emptySet());
    }

    /**
     * Asynchronously executes a request using the Field Capabilities API.
     * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-field-caps.html">Field Capabilities API
     * on elastic.co</a>.
     * @param fieldCapabilitiesRequest the request
     * @param options the request options (e.g. headers), use {@link RequestOptions#DEFAULT} if nothing needs to be customized
     * @param listener the listener to be notified upon request completion
     */
    public final void fieldCapsAsync(FieldCapabilitiesRequest fieldCapabilitiesRequest, RequestOptions options,
                                     ActionListener<FieldCapabilitiesResponse> listener) {
        performRequestAsyncAndParseEntity(fieldCapabilitiesRequest, RequestConverters::fieldCaps, options,
            FieldCapabilitiesResponse::fromXContent, listener, emptySet());
    }

    @Deprecated
    protected final <Req extends ActionRequest, Resp> Resp performRequestAndParseEntity(Req request,
                                                                            CheckedFunction<Req, Request, IOException> requestConverter,
                                                                            CheckedFunction<XContentParser, Resp, IOException> entityParser,
                                                                            Set<Integer> ignores, Header... headers) throws IOException {
        return performRequest(request, requestConverter, (response) -> parseEntity(response.getEntity(), entityParser), ignores, headers);
    }

    /**
     * @deprecated If creating a new HLRC ReST API call, consider creating new actions instead of reusing server actions. The Validation
     * layer has been added to the ReST client, and requests should extend {@link Validatable} instead of {@link ActionRequest}.
     */
    @Deprecated
    protected final <Req extends ActionRequest, Resp> Resp performRequestAndParseEntity(Req request,
                                                                            CheckedFunction<Req, Request, IOException> requestConverter,
                                                                            RequestOptions options,
                                                                            CheckedFunction<XContentParser, Resp, IOException> entityParser,
                                                                            Set<Integer> ignores) throws IOException {
        return performRequest(request, requestConverter, options,
                response -> parseEntity(response.getEntity(), entityParser), ignores);
    }

    /**
     * Defines a helper method for performing a request and then parsing the returned entity using the provided entityParser.
     */
    protected final <Req extends Validatable, Resp> Resp performRequestAndParseEntity(Req request,
                                                                          CheckedFunction<Req, Request, IOException> requestConverter,
                                                                          RequestOptions options,
                                                                          CheckedFunction<XContentParser, Resp, IOException> entityParser,
                                                                          Set<Integer> ignores) throws IOException {
        return performRequest(request, requestConverter, options,
            response -> parseEntity(response.getEntity(), entityParser), ignores);
    }

    @Deprecated
    protected final <Req extends ActionRequest, Resp> Resp performRequest(Req request,
                                                          CheckedFunction<Req, Request, IOException> requestConverter,
                                                          CheckedFunction<Response, Resp, IOException> responseConverter,
                                                          Set<Integer> ignores, Header... headers) throws IOException {
        return performRequest(request, requestConverter, optionsForHeaders(headers), responseConverter, ignores);
    }

    /**
     * @deprecated If creating a new HLRC ReST API call, consider creating new actions instead of reusing server actions. The Validation
     * layer has been added to the ReST client, and requests should extend {@link Validatable} instead of {@link ActionRequest}.
     */
    @Deprecated
    protected final <Req extends ActionRequest, Resp> Resp performRequest(Req request,
                                                                           CheckedFunction<Req, Request, IOException> requestConverter,
                                                                           RequestOptions options,
                                                                           CheckedFunction<Response, Resp, IOException> responseConverter,
                                                                           Set<Integer> ignores) throws IOException {
        ActionRequestValidationException validationException = request.validate();
        if (validationException != null && validationException.validationErrors().isEmpty() == false) {
            throw validationException;
        }
        return internalPerformRequest(request, requestConverter, options, responseConverter, ignores);
    }

    /**
     * Defines a helper method for performing a request.
     */
    protected final <Req extends Validatable, Resp> Resp performRequest(Req request,
                                                             CheckedFunction<Req, Request, IOException> requestConverter,
                                                             RequestOptions options,
                                                             CheckedFunction<Response, Resp, IOException> responseConverter,
                                                             Set<Integer> ignores) throws IOException {
        Optional<ValidationException> validationException = request.validate();
        if (validationException != null && validationException.isPresent()) {
            throw validationException.get();
        }
        return internalPerformRequest(request, requestConverter, options, responseConverter, ignores);
    }

    /**
     * Provides common functionality for performing a request.
     */
    private <Req, Resp> Resp internalPerformRequest(Req request,
                                            CheckedFunction<Req, Request, IOException> requestConverter,
                                            RequestOptions options,
                                            CheckedFunction<Response, Resp, IOException> responseConverter,
                                            Set<Integer> ignores) throws IOException {
        Request req = requestConverter.apply(request);
        req.setOptions(options);
        Response response;
        try {
            response = client.performRequest(req);
        } catch (ResponseException e) {
            if (ignores.contains(e.getResponse().getStatusLine().getStatusCode())) {
                try {
                    return responseConverter.apply(e.getResponse());
                } catch (Exception innerException) {
                    // the exception is ignored as we now try to parse the response as an error.
                    // this covers cases like get where 404 can either be a valid document not found response,
                    // or an error for which parsing is completely different. We try to consider the 404 response as a valid one
                    // first. If parsing of the response breaks, we fall back to parsing it as an error.
                    throw parseResponseException(e);
                }
            }
            throw parseResponseException(e);
        }

        try {
            return responseConverter.apply(response);
        } catch(Exception e) {
            throw new IOException("Unable to parse response body for " + response, e);
        }
    }
    
    /**
     * Defines a helper method for requests that can 404 and in which case will return an empty Optional 
     * otherwise tries to parse the response body
     */
    protected final <Req extends Validatable, Resp> Optional<Resp> performRequestAndParseOptionalEntity(Req request,
                                                                  CheckedFunction<Req, Request, IOException> requestConverter,
                                                                  RequestOptions options,
                                                                  CheckedFunction<XContentParser, Resp, IOException> entityParser
                                                                  ) throws IOException {
        Optional<ValidationException> validationException = request.validate();
        if (validationException != null && validationException.isPresent()) {
            throw validationException.get();
        }
        Request req = requestConverter.apply(request);
        req.setOptions(options);
        Response response;
        try {
            response = client.performRequest(req);
        } catch (ResponseException e) {
            if (RestStatus.NOT_FOUND.getStatus() == e.getResponse().getStatusLine().getStatusCode()) {
                return Optional.empty();
            }
            throw parseResponseException(e);
        }

        try {
            return Optional.of(parseEntity(response.getEntity(), entityParser));
        } catch (Exception e) {
            throw new IOException("Unable to parse response body for " + response, e);
        }
    }      

    @Deprecated
    protected final <Req extends ActionRequest, Resp> void performRequestAsyncAndParseEntity(Req request,
                                                                 CheckedFunction<Req, Request, IOException> requestConverter,
                                                                 CheckedFunction<XContentParser, Resp, IOException> entityParser,
                                                                 ActionListener<Resp> listener, Set<Integer> ignores, Header... headers) {
        performRequestAsync(request, requestConverter, (response) -> parseEntity(response.getEntity(), entityParser),
                listener, ignores, headers);
    }

    /**
     * @deprecated If creating a new HLRC ReST API call, consider creating new actions instead of reusing server actions. The Validation
     * layer has been added to the ReST client, and requests should extend {@link Validatable} instead of {@link ActionRequest}.
     */
    @Deprecated
    protected final <Req extends ActionRequest, Resp> void performRequestAsyncAndParseEntity(Req request,
                                                                         CheckedFunction<Req, Request, IOException> requestConverter,
                                                                         RequestOptions options,
                                                                         CheckedFunction<XContentParser, Resp, IOException> entityParser,
                                                                         ActionListener<Resp> listener, Set<Integer> ignores) {
        performRequestAsync(request, requestConverter, options,
                response -> parseEntity(response.getEntity(), entityParser), listener, ignores);
    }

    /**
     * Defines a helper method for asynchronously performing a request.
     */
    protected final <Req extends Validatable, Resp> void performRequestAsyncAndParseEntity(Req request,
                                                                           CheckedFunction<Req, Request, IOException> requestConverter,
                                                                           RequestOptions options,
                                                                           CheckedFunction<XContentParser, Resp, IOException> entityParser,
                                                                           ActionListener<Resp> listener, Set<Integer> ignores) {
        performRequestAsync(request, requestConverter, options,
            response -> parseEntity(response.getEntity(), entityParser), listener, ignores);
    }

    @Deprecated
    protected final <Req extends ActionRequest, Resp> void performRequestAsync(Req request,
                                                               CheckedFunction<Req, Request, IOException> requestConverter,
                                                               CheckedFunction<Response, Resp, IOException> responseConverter,
                                                               ActionListener<Resp> listener, Set<Integer> ignores, Header... headers) {
        performRequestAsync(request, requestConverter, optionsForHeaders(headers), responseConverter, listener, ignores);
    }

    /**
     * @deprecated If creating a new HLRC ReST API call, consider creating new actions instead of reusing server actions. The Validation
     * layer has been added to the ReST client, and requests should extend {@link Validatable} instead of {@link ActionRequest}.
     */
    @Deprecated
    protected final <Req extends ActionRequest, Resp> void performRequestAsync(Req request,
                                                                            CheckedFunction<Req, Request, IOException> requestConverter,
                                                                            RequestOptions options,
                                                                            CheckedFunction<Response, Resp, IOException> responseConverter,
                                                                            ActionListener<Resp> listener, Set<Integer> ignores) {
        ActionRequestValidationException validationException = request.validate();
        if (validationException != null && validationException.validationErrors().isEmpty() == false) {
            listener.onFailure(validationException);
            return;
        }
        internalPerformRequestAsync(request, requestConverter, options, responseConverter, listener, ignores);
    }

    /**
     * Defines a helper method for asynchronously performing a request.
     */
    protected final <Req extends Validatable, Resp> void performRequestAsync(Req request,
                                                                          CheckedFunction<Req, Request, IOException> requestConverter,
                                                                          RequestOptions options,
                                                                          CheckedFunction<Response, Resp, IOException> responseConverter,
                                                                          ActionListener<Resp> listener, Set<Integer> ignores) {
        Optional<ValidationException> validationException = request.validate();
        if (validationException != null && validationException.isPresent()) {
            listener.onFailure(validationException.get());
            return;
        }
        internalPerformRequestAsync(request, requestConverter, options, responseConverter, listener, ignores);
    }

    /**
     * Provides common functionality for asynchronously performing a request.
     */
    private <Req, Resp> void internalPerformRequestAsync(Req request,
                                                 CheckedFunction<Req, Request, IOException> requestConverter,
                                                 RequestOptions options,
                                                 CheckedFunction<Response, Resp, IOException> responseConverter,
                                                 ActionListener<Resp> listener, Set<Integer> ignores) {
        Request req;
        try {
            req = requestConverter.apply(request);
        } catch (Exception e) {
            listener.onFailure(e);
            return;
        }
        req.setOptions(options);

        ResponseListener responseListener = wrapResponseListener(responseConverter, listener, ignores);
        client.performRequestAsync(req, responseListener);
    }


    final <Resp> ResponseListener wrapResponseListener(CheckedFunction<Response, Resp, IOException> responseConverter,
                                                        ActionListener<Resp> actionListener, Set<Integer> ignores) {
        return new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                try {
                    actionListener.onResponse(responseConverter.apply(response));
                } catch(Exception e) {
                    IOException ioe = new IOException("Unable to parse response body for " + response, e);
                    onFailure(ioe);
                }
            }

            @Override
            public void onFailure(Exception exception) {
                if (exception instanceof ResponseException) {
                    ResponseException responseException = (ResponseException) exception;
                    Response response = responseException.getResponse();
                    if (ignores.contains(response.getStatusLine().getStatusCode())) {
                        try {
                            actionListener.onResponse(responseConverter.apply(response));
                        } catch (Exception innerException) {
                            // the exception is ignored as we now try to parse the response as an error.
                            // this covers cases like get where 404 can either be a valid document not found response,
                            // or an error for which parsing is completely different. We try to consider the 404 response as a valid one
                            // first. If parsing of the response breaks, we fall back to parsing it as an error.
                            actionListener.onFailure(parseResponseException(responseException));
                        }
                    } else {
                        actionListener.onFailure(parseResponseException(responseException));
                    }
                } else {
                    actionListener.onFailure(exception);
                }
            }
        };
    }
    
    /**
     * Async request which returns empty Optionals in the case of 404s or parses entity into an Optional
     */
    protected final <Req extends Validatable, Resp> void performRequestAsyncAndParseOptionalEntity(Req request,
            CheckedFunction<Req, Request, IOException> requestConverter,
            RequestOptions options,
            CheckedFunction<XContentParser, Resp, IOException> entityParser,
            ActionListener<Optional<Resp>> listener) {
        Optional<ValidationException> validationException = request.validate();
        if (validationException != null && validationException.isPresent()) {
            listener.onFailure(validationException.get());
            return;
        }
        Request req;
        try {
            req = requestConverter.apply(request);
        } catch (Exception e) {
            listener.onFailure(e);
            return;
        }
        req.setOptions(options);
        ResponseListener responseListener = wrapResponseListener404sOptional(response -> parseEntity(response.getEntity(), 
                entityParser), listener);
        client.performRequestAsync(req, responseListener);        
    }    
    
    final <Resp> ResponseListener wrapResponseListener404sOptional(CheckedFunction<Response, Resp, IOException> responseConverter,
            ActionListener<Optional<Resp>> actionListener) {
        return new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                try {
                    actionListener.onResponse(Optional.of(responseConverter.apply(response)));
                } catch (Exception e) {
                    IOException ioe = new IOException("Unable to parse response body for " + response, e);
                    onFailure(ioe);
                }
            }

            @Override
            public void onFailure(Exception exception) {
                if (exception instanceof ResponseException) {
                    ResponseException responseException = (ResponseException) exception;
                    Response response = responseException.getResponse();
                    if (RestStatus.NOT_FOUND.getStatus() == response.getStatusLine().getStatusCode()) {
                            actionListener.onResponse(Optional.empty());
                    } else {
                        actionListener.onFailure(parseResponseException(responseException));
                    }
                } else {
                    actionListener.onFailure(exception);
                }
            }
        };
    }    

    /**
     * Converts a {@link ResponseException} obtained from the low level REST client into an {@link ElasticsearchException}.
     * If a response body was returned, tries to parse it as an error returned from Elasticsearch.
     * If no response body was returned or anything goes wrong while parsing the error, returns a new {@link ElasticsearchStatusException}
     * that wraps the original {@link ResponseException}. The potential exception obtained while parsing is added to the returned
     * exception as a suppressed exception. This method is guaranteed to not throw any exception eventually thrown while parsing.
     */
    protected final ElasticsearchStatusException parseResponseException(ResponseException responseException) {
        Response response = responseException.getResponse();
        HttpEntity entity = response.getEntity();
        ElasticsearchStatusException elasticsearchException;
        if (entity == null) {
            elasticsearchException = new ElasticsearchStatusException(
                    responseException.getMessage(), RestStatus.fromCode(response.getStatusLine().getStatusCode()), responseException);
        } else {
            try {
                elasticsearchException = parseEntity(entity, BytesRestResponse::errorFromXContent);
                elasticsearchException.addSuppressed(responseException);
            } catch (Exception e) {
                RestStatus restStatus = RestStatus.fromCode(response.getStatusLine().getStatusCode());
                elasticsearchException = new ElasticsearchStatusException("Unable to parse response body", restStatus, responseException);
                elasticsearchException.addSuppressed(e);
            }
        }
        return elasticsearchException;
    }

    protected final <Resp> Resp parseEntity(final HttpEntity entity,
                                      final CheckedFunction<XContentParser, Resp, IOException> entityParser) throws IOException {
        if (entity == null) {
            throw new IllegalStateException("Response body expected but not returned");
        }
        if (entity.getContentType() == null) {
            throw new IllegalStateException("Elasticsearch didn't return the [Content-Type] header, unable to parse response body");
        }
        XContentType xContentType = XContentType.fromMediaTypeOrFormat(entity.getContentType().getValue());
        if (xContentType == null) {
            throw new IllegalStateException("Unsupported Content-Type: " + entity.getContentType().getValue());
        }
        try (XContentParser parser = xContentType.xContent().createParser(registry, DEPRECATION_HANDLER, entity.getContent())) {
            return entityParser.apply(parser);
        }
    }

    private static RequestOptions optionsForHeaders(Header[] headers) {
        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        for (Header header : headers) {
            Objects.requireNonNull(header, "header cannot be null");
            options.addHeader(header.getName(), header.getValue());
        }
        return options.build();
    }

    static boolean convertExistsResponse(Response response) {
        return response.getStatusLine().getStatusCode() == 200;
    }

    /**
     * Ignores deprecation warnings. This is appropriate because it is only
     * used to parse responses from Elasticsearch. Any deprecation warnings
     * emitted there just mean that you are talking to an old version of
     * Elasticsearch. There isn't anything you can do about the deprecation.
     */
    private static final DeprecationHandler DEPRECATION_HANDLER = new DeprecationHandler() {
        @Override
        public void usedDeprecatedName(String usedName, String modernName) {}
        @Override
        public void usedDeprecatedField(String usedName, String replacedWith) {}
    };

    static List<NamedXContentRegistry.Entry> getDefaultNamedXContents() {
        Map<String, ContextParser<Object, ? extends Aggregation>> map = new HashMap<>();
        map.put(CardinalityAggregationBuilder.NAME, (p, c) -> ParsedCardinality.fromXContent(p, (String) c));
        map.put(InternalHDRPercentiles.NAME, (p, c) -> ParsedHDRPercentiles.fromXContent(p, (String) c));
        map.put(InternalHDRPercentileRanks.NAME, (p, c) -> ParsedHDRPercentileRanks.fromXContent(p, (String) c));
        map.put(InternalTDigestPercentiles.NAME, (p, c) -> ParsedTDigestPercentiles.fromXContent(p, (String) c));
        map.put(InternalTDigestPercentileRanks.NAME, (p, c) -> ParsedTDigestPercentileRanks.fromXContent(p, (String) c));
        map.put(PercentilesBucketPipelineAggregationBuilder.NAME, (p, c) -> ParsedPercentilesBucket.fromXContent(p, (String) c));
        map.put(MedianAbsoluteDeviationAggregationBuilder.NAME, (p, c) -> ParsedMedianAbsoluteDeviation.fromXContent(p, (String) c));
        map.put(MinAggregationBuilder.NAME, (p, c) -> ParsedMin.fromXContent(p, (String) c));
        map.put(MaxAggregationBuilder.NAME, (p, c) -> ParsedMax.fromXContent(p, (String) c));
        map.put(SumAggregationBuilder.NAME, (p, c) -> ParsedSum.fromXContent(p, (String) c));
        map.put(AvgAggregationBuilder.NAME, (p, c) -> ParsedAvg.fromXContent(p, (String) c));
        map.put(ValueCountAggregationBuilder.NAME, (p, c) -> ParsedValueCount.fromXContent(p, (String) c));
        map.put(InternalSimpleValue.NAME, (p, c) -> ParsedSimpleValue.fromXContent(p, (String) c));
        map.put(DerivativePipelineAggregationBuilder.NAME, (p, c) -> ParsedDerivative.fromXContent(p, (String) c));
        map.put(InternalBucketMetricValue.NAME, (p, c) -> ParsedBucketMetricValue.fromXContent(p, (String) c));
        map.put(StatsAggregationBuilder.NAME, (p, c) -> ParsedStats.fromXContent(p, (String) c));
        map.put(StatsBucketPipelineAggregationBuilder.NAME, (p, c) -> ParsedStatsBucket.fromXContent(p, (String) c));
        map.put(ExtendedStatsAggregationBuilder.NAME, (p, c) -> ParsedExtendedStats.fromXContent(p, (String) c));
        map.put(ExtendedStatsBucketPipelineAggregationBuilder.NAME,
                (p, c) -> ParsedExtendedStatsBucket.fromXContent(p, (String) c));
        map.put(GeoBoundsAggregationBuilder.NAME, (p, c) -> ParsedGeoBounds.fromXContent(p, (String) c));
        map.put(GeoCentroidAggregationBuilder.NAME, (p, c) -> ParsedGeoCentroid.fromXContent(p, (String) c));
        map.put(HistogramAggregationBuilder.NAME, (p, c) -> ParsedHistogram.fromXContent(p, (String) c));
        map.put(DateHistogramAggregationBuilder.NAME, (p, c) -> ParsedDateHistogram.fromXContent(p, (String) c));
        map.put(AutoDateHistogramAggregationBuilder.NAME, (p, c) -> ParsedAutoDateHistogram.fromXContent(p, (String) c));
        map.put(StringTerms.NAME, (p, c) -> ParsedStringTerms.fromXContent(p, (String) c));
        map.put(LongTerms.NAME, (p, c) -> ParsedLongTerms.fromXContent(p, (String) c));
        map.put(DoubleTerms.NAME, (p, c) -> ParsedDoubleTerms.fromXContent(p, (String) c));
        map.put(MissingAggregationBuilder.NAME, (p, c) -> ParsedMissing.fromXContent(p, (String) c));
        map.put(NestedAggregationBuilder.NAME, (p, c) -> ParsedNested.fromXContent(p, (String) c));
        map.put(ReverseNestedAggregationBuilder.NAME, (p, c) -> ParsedReverseNested.fromXContent(p, (String) c));
        map.put(GlobalAggregationBuilder.NAME, (p, c) -> ParsedGlobal.fromXContent(p, (String) c));
        map.put(FilterAggregationBuilder.NAME, (p, c) -> ParsedFilter.fromXContent(p, (String) c));
        map.put(InternalSampler.PARSER_NAME, (p, c) -> ParsedSampler.fromXContent(p, (String) c));
        map.put(GeoGridAggregationBuilder.NAME, (p, c) -> ParsedGeoHashGrid.fromXContent(p, (String) c));
        map.put(RangeAggregationBuilder.NAME, (p, c) -> ParsedRange.fromXContent(p, (String) c));
        map.put(DateRangeAggregationBuilder.NAME, (p, c) -> ParsedDateRange.fromXContent(p, (String) c));
        map.put(GeoDistanceAggregationBuilder.NAME, (p, c) -> ParsedGeoDistance.fromXContent(p, (String) c));
        map.put(FiltersAggregationBuilder.NAME, (p, c) -> ParsedFilters.fromXContent(p, (String) c));
        map.put(AdjacencyMatrixAggregationBuilder.NAME, (p, c) -> ParsedAdjacencyMatrix.fromXContent(p, (String) c));
        map.put(SignificantLongTerms.NAME, (p, c) -> ParsedSignificantLongTerms.fromXContent(p, (String) c));
        map.put(SignificantStringTerms.NAME, (p, c) -> ParsedSignificantStringTerms.fromXContent(p, (String) c));
        map.put(ScriptedMetricAggregationBuilder.NAME, (p, c) -> ParsedScriptedMetric.fromXContent(p, (String) c));
        map.put(IpRangeAggregationBuilder.NAME, (p, c) -> ParsedBinaryRange.fromXContent(p, (String) c));
        map.put(TopHitsAggregationBuilder.NAME, (p, c) -> ParsedTopHits.fromXContent(p, (String) c));
        map.put(CompositeAggregationBuilder.NAME, (p, c) -> ParsedComposite.fromXContent(p, (String) c));
        List<NamedXContentRegistry.Entry> entries = map.entrySet().stream()
                .map(entry -> new NamedXContentRegistry.Entry(Aggregation.class, new ParseField(entry.getKey()), entry.getValue()))
                .collect(Collectors.toList());
        entries.add(new NamedXContentRegistry.Entry(Suggest.Suggestion.class, new ParseField(TermSuggestion.NAME),
                (parser, context) -> TermSuggestion.fromXContent(parser, (String)context)));
        entries.add(new NamedXContentRegistry.Entry(Suggest.Suggestion.class, new ParseField(PhraseSuggestion.NAME),
                (parser, context) -> PhraseSuggestion.fromXContent(parser, (String)context)));
        entries.add(new NamedXContentRegistry.Entry(Suggest.Suggestion.class, new ParseField(CompletionSuggestion.NAME),
                (parser, context) -> CompletionSuggestion.fromXContent(parser, (String)context)));
        return entries;
    }

    /**
     * Loads and returns the {@link NamedXContentRegistry.Entry} parsers provided by plugins.
     */
    static List<NamedXContentRegistry.Entry> getProvidedNamedXContents() {
        List<NamedXContentRegistry.Entry> entries = new ArrayList<>();
        for (NamedXContentProvider service : ServiceLoader.load(NamedXContentProvider.class)) {
            entries.addAll(service.getNamedXContentParsers());
        }
        return entries;
    }
}
