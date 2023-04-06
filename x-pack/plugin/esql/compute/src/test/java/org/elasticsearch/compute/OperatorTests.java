/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.MockBigArrays;
import org.elasticsearch.common.util.MockPageCacheRecycler;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.compute.aggregation.GroupingAggregator;
import org.elasticsearch.compute.aggregation.GroupingAggregatorFunction;
import org.elasticsearch.compute.aggregation.blockhash.BlockHash;
import org.elasticsearch.compute.ann.Experimental;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.DocBlock;
import org.elasticsearch.compute.data.DocVector;
import org.elasticsearch.compute.data.DoubleBlock;
import org.elasticsearch.compute.data.ElementType;
import org.elasticsearch.compute.data.IntArrayVector;
import org.elasticsearch.compute.data.IntBlock;
import org.elasticsearch.compute.data.IntVector;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.lucene.LuceneOperator;
import org.elasticsearch.compute.lucene.LuceneSourceOperator;
import org.elasticsearch.compute.lucene.LuceneTopNSourceOperator;
import org.elasticsearch.compute.lucene.ValueSourceInfo;
import org.elasticsearch.compute.lucene.ValuesSourceReaderOperator;
import org.elasticsearch.compute.operator.AbstractPageMappingOperator;
import org.elasticsearch.compute.operator.Driver;
import org.elasticsearch.compute.operator.EvalOperator;
import org.elasticsearch.compute.operator.FilterOperator;
import org.elasticsearch.compute.operator.HashAggregationOperator;
import org.elasticsearch.compute.operator.LimitOperator;
import org.elasticsearch.compute.operator.Operator;
import org.elasticsearch.compute.operator.OrdinalsGroupingOperator;
import org.elasticsearch.compute.operator.PageConsumerOperator;
import org.elasticsearch.compute.operator.SequenceLongBlockSourceOperator;
import org.elasticsearch.compute.operator.TopNOperator;
import org.elasticsearch.core.CheckedConsumer;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.fielddata.IndexFieldDataCache;
import org.elasticsearch.index.fielddata.IndexNumericFieldData;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;
import org.elasticsearch.index.fielddata.plain.SortedDoublesIndexFieldData;
import org.elasticsearch.index.fielddata.plain.SortedNumericIndexFieldData;
import org.elasticsearch.index.fielddata.plain.SortedSetOrdinalsIndexFieldData;
import org.elasticsearch.index.mapper.KeywordFieldMapper;
import org.elasticsearch.index.mapper.Uid;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.search.aggregations.support.CoreValuesSourceType;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.ql.util.Holder;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

import static org.elasticsearch.compute.aggregation.AggregatorMode.FINAL;
import static org.elasticsearch.compute.aggregation.AggregatorMode.INITIAL;
import static org.elasticsearch.compute.aggregation.AggregatorMode.INTERMEDIATE;
import static org.elasticsearch.compute.operator.DriverRunner.runToCompletion;
import static org.elasticsearch.core.Tuple.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

@Experimental
public class OperatorTests extends ESTestCase {

    private ThreadPool threadPool;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        threadPool = new TestThreadPool("OperatorTests");
    }

    @After
    public void tearDown() throws Exception {
        ThreadPool.terminate(threadPool, 30, TimeUnit.SECONDS);
        super.tearDown();
    }

    public void testLuceneOperatorsLimit() throws IOException {
        final int numDocs = randomIntBetween(10_000, 100_000);
        try (Directory dir = newDirectory(); RandomIndexWriter w = writeTestDocs(dir, numDocs, "value", null)) {
            try (IndexReader reader = w.getReader()) {
                AtomicInteger rowCount = new AtomicInteger();
                final int limit = randomIntBetween(1, numDocs);

                try (
                    Driver driver = new Driver(
                        new LuceneSourceOperator(reader, 0, new MatchAllDocsQuery(), randomIntBetween(1, numDocs), limit),
                        Collections.emptyList(),
                        new PageConsumerOperator(page -> rowCount.addAndGet(page.getPositionCount())),
                        () -> {}
                    )
                ) {
                    driver.run();
                }
                assertEquals(limit, rowCount.get());
            }
        }
    }

    public void testLuceneTopNSourceOperator() throws IOException {
        final int numDocs = randomIntBetween(10_000, 100_000);
        final int pageSize = randomIntBetween(1_000, 100_000);
        final int limit = randomIntBetween(1, pageSize);
        String fieldName = "value";

        try (Directory dir = newDirectory(); RandomIndexWriter w = writeTestDocs(dir, numDocs, fieldName, null)) {
            ValuesSource vs = new ValuesSource.Numeric.FieldData(
                new SortedNumericIndexFieldData(
                    fieldName,
                    IndexNumericFieldData.NumericType.LONG,
                    IndexNumericFieldData.NumericType.LONG.getValuesSourceType(),
                    null
                )
            );
            try (IndexReader reader = w.getReader()) {
                AtomicInteger rowCount = new AtomicInteger();
                Sort sort = new Sort(new SortField(fieldName, SortField.Type.LONG));
                Holder<Long> expectedValue = new Holder<>(0L);

                try (
                    Driver driver = new Driver(
                        new LuceneTopNSourceOperator(reader, 0, sort, new MatchAllDocsQuery(), pageSize, limit),
                        List.of(
                            new ValuesSourceReaderOperator(
                                List.of(new ValueSourceInfo(CoreValuesSourceType.NUMERIC, vs, ElementType.LONG, reader)),
                                0
                            ),
                            new TopNOperator(limit, List.of(new TopNOperator.SortOrder(1, true, true)))
                        ),
                        new PageConsumerOperator(page -> {
                            rowCount.addAndGet(page.getPositionCount());
                            for (int i = 0; i < page.getPositionCount(); i++) {
                                LongBlock longValuesBlock = page.getBlock(1);
                                long expected = expectedValue.get();
                                assertEquals(expected, longValuesBlock.getLong(i));
                                expectedValue.set(expected + 1);
                            }
                        }),
                        () -> {}
                    )
                ) {
                    driver.run();
                }
                assertEquals(Math.min(limit, numDocs), rowCount.get());
            }
        }
    }

    public void testOperatorsWithLuceneSlicing() throws IOException {
        final String fieldName = "value";
        final int numDocs = 100000;
        try (Directory dir = newDirectory(); RandomIndexWriter w = writeTestDocs(dir, numDocs, fieldName, randomIntBetween(1, 10))) {
            ValuesSource vs = new ValuesSource.Numeric.FieldData(
                new SortedNumericIndexFieldData(
                    fieldName,
                    IndexNumericFieldData.NumericType.LONG,
                    IndexNumericFieldData.NumericType.LONG.getValuesSourceType(),
                    null
                )
            );

            try (IndexReader reader = w.getReader()) {
                AtomicInteger rowCount = new AtomicInteger();

                List<Driver> drivers = new ArrayList<>();
                try {
                    for (LuceneOperator luceneSourceOperator : new LuceneSourceOperator(reader, 0, new MatchAllDocsQuery()).docSlice(
                        randomIntBetween(1, 10)
                    )) {
                        drivers.add(
                            new Driver(
                                luceneSourceOperator,
                                List.of(
                                    new ValuesSourceReaderOperator(
                                        List.of(new ValueSourceInfo(CoreValuesSourceType.NUMERIC, vs, ElementType.LONG, reader)),
                                        0
                                    )
                                ),
                                new PageConsumerOperator(page -> rowCount.addAndGet(page.getPositionCount())),
                                () -> {}
                            )
                        );
                    }
                    runToCompletion(threadPool.executor(ThreadPool.Names.SEARCH), drivers);
                } finally {
                    Releasables.close(drivers);
                }
                assertEquals(numDocs, rowCount.get());
            }
        }
    }

    private static RandomIndexWriter writeTestDocs(Directory dir, int numDocs, String fieldName, Integer maxSegmentCount)
        throws IOException {
        RandomIndexWriter w = new RandomIndexWriter(random(), dir);
        Document doc = new Document();
        NumericDocValuesField docValuesField = new NumericDocValuesField(fieldName, 0);
        for (int i = 0; i < numDocs; i++) {
            doc.clear();
            docValuesField.setLongValue(i);
            doc.add(docValuesField);
            w.addDocument(doc);
        }
        if (maxSegmentCount != null && randomBoolean()) {
            w.forceMerge(randomIntBetween(1, 10));
        }
        w.commit();

        return w;
    }

    public void testValuesSourceReaderOperatorWithNulls() throws IOException {  // TODO move to ValuesSourceReaderOperatorTests
        final int numDocs = 100_000;
        try (Directory dir = newDirectory(); RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
            Document doc = new Document();
            NumericDocValuesField intField = new NumericDocValuesField("i", 0);
            NumericDocValuesField longField = new NumericDocValuesField("j", 0);
            NumericDocValuesField doubleField = new DoubleDocValuesField("d", 0);
            String kwFieldName = "kw";
            for (int i = 0; i < numDocs; i++) {
                doc.clear();
                intField.setLongValue(i);
                doc.add(intField);
                if (i % 100 != 0) { // Do not set field for every 100 values
                    longField.setLongValue(i);
                    doc.add(longField);
                    doubleField.setDoubleValue(i);
                    doc.add(doubleField);
                    doc.add(new SortedDocValuesField(kwFieldName, new BytesRef("kw=" + i)));
                }
                w.addDocument(doc);
            }
            w.commit();

            ValuesSource intVs = new ValuesSource.Numeric.FieldData(
                new SortedNumericIndexFieldData(
                    intField.name(),
                    IndexNumericFieldData.NumericType.INT,
                    IndexNumericFieldData.NumericType.INT.getValuesSourceType(),
                    null
                )
            );
            ValuesSource longVs = new ValuesSource.Numeric.FieldData(
                new SortedNumericIndexFieldData(
                    longField.name(),
                    IndexNumericFieldData.NumericType.LONG,
                    IndexNumericFieldData.NumericType.LONG.getValuesSourceType(),
                    null
                )
            );
            ValuesSource doubleVs = new ValuesSource.Numeric.FieldData(
                new SortedDoublesIndexFieldData(
                    doubleField.name(),
                    IndexNumericFieldData.NumericType.DOUBLE,
                    IndexNumericFieldData.NumericType.DOUBLE.getValuesSourceType(),
                    null
                )
            );
            var breakerService = new NoneCircuitBreakerService();
            var cache = new IndexFieldDataCache.None();
            ValuesSource keywordVs = new ValuesSource.Bytes.FieldData(
                new SortedSetOrdinalsIndexFieldData(cache, kwFieldName, CoreValuesSourceType.KEYWORD, breakerService, null)
            );

            try (IndexReader reader = w.getReader()) {
                // implements cardinality on value field
                Driver driver = new Driver(
                    new LuceneSourceOperator(reader, 0, new MatchAllDocsQuery()),
                    List.of(
                        new ValuesSourceReaderOperator(
                            List.of(new ValueSourceInfo(CoreValuesSourceType.NUMERIC, intVs, ElementType.INT, reader)),
                            0
                        ),
                        new ValuesSourceReaderOperator(
                            List.of(new ValueSourceInfo(CoreValuesSourceType.NUMERIC, longVs, ElementType.LONG, reader)),
                            0
                        ),
                        new ValuesSourceReaderOperator(
                            List.of(new ValueSourceInfo(CoreValuesSourceType.NUMERIC, doubleVs, ElementType.DOUBLE, reader)),
                            0
                        ),
                        new ValuesSourceReaderOperator(
                            List.of(new ValueSourceInfo(CoreValuesSourceType.KEYWORD, keywordVs, ElementType.BYTES_REF, reader)),
                            0
                        )
                    ),
                    new PageConsumerOperator(page -> {
                        logger.debug("New page: {}", page);
                        IntBlock intValuesBlock = page.getBlock(1);
                        LongBlock longValuesBlock = page.getBlock(2);
                        DoubleBlock doubleValuesBlock = page.getBlock(3);
                        BytesRefBlock keywordValuesBlock = page.getBlock(4);

                        for (int i = 0; i < page.getPositionCount(); i++) {
                            assertFalse(intValuesBlock.isNull(i));
                            long j = intValuesBlock.getInt(i);
                            // Every 100 documents we set fields to null
                            boolean fieldIsEmpty = j % 100 == 0;
                            assertEquals(fieldIsEmpty, longValuesBlock.isNull(i));
                            assertEquals(fieldIsEmpty, doubleValuesBlock.isNull(i));
                            assertEquals(fieldIsEmpty, keywordValuesBlock.isNull(i));
                        }
                    }),
                    () -> {}
                );
                driver.run();
            }
        }
    }

    public void testQueryOperator() throws IOException {
        Map<BytesRef, Long> docs = new HashMap<>();
        CheckedConsumer<DirectoryReader, IOException> verifier = reader -> {
            final long from = randomBoolean() ? Long.MIN_VALUE : randomLongBetween(0, 10000);
            final long to = randomBoolean() ? Long.MAX_VALUE : randomLongBetween(from, from + 10000);
            final Query query = LongPoint.newRangeQuery("pt", from, to);
            final String partition = randomFrom("shard", "segment", "doc");
            final List<LuceneOperator> queryOperators = switch (partition) {
                case "shard" -> List.of(new LuceneSourceOperator(reader, 0, query));
                case "segment" -> new LuceneSourceOperator(reader, 0, query).segmentSlice();
                case "doc" -> new LuceneSourceOperator(reader, 0, query).docSlice(randomIntBetween(1, 10));
                default -> throw new AssertionError("unknown partition [" + partition + "]");
            };
            List<Driver> drivers = new ArrayList<>();
            try {
                Set<Integer> actualDocIds = Collections.newSetFromMap(ConcurrentCollections.newConcurrentMap());
                for (LuceneOperator queryOperator : queryOperators) {
                    PageConsumerOperator docCollector = new PageConsumerOperator(page -> {
                        DocVector docVector = page.<DocBlock>getBlock(0).asVector();
                        IntVector doc = docVector.docs();
                        IntVector segment = docVector.segments();
                        for (int i = 0; i < doc.getPositionCount(); i++) {
                            int docBase = reader.leaves().get(segment.getInt(i)).docBase;
                            int docId = docBase + doc.getInt(i);
                            assertTrue("duplicated docId=" + docId, actualDocIds.add(docId));
                        }
                    });
                    drivers.add(new Driver(queryOperator, List.of(), docCollector, () -> {}));
                }
                runToCompletion(threadPool.executor(ThreadPool.Names.SEARCH), drivers);
                Set<Integer> expectedDocIds = searchForDocIds(reader, query);
                assertThat("query=" + query + ", partition=" + partition, actualDocIds, equalTo(expectedDocIds));
            } finally {
                Releasables.close(drivers);
            }
        };

        try (Directory dir = newDirectory(); RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
            int numDocs = randomIntBetween(0, 10_000);
            for (int i = 0; i < numDocs; i++) {
                Document d = new Document();
                long point = randomLongBetween(0, 5000);
                d.add(new LongPoint("pt", point));
                BytesRef id = Uid.encodeId("id-" + randomIntBetween(0, 5000));
                d.add(new Field("id", id, KeywordFieldMapper.Defaults.FIELD_TYPE));
                if (docs.put(id, point) != null) {
                    w.updateDocument(new Term("id", id), d);
                } else {
                    w.addDocument(d);
                }
            }
            try (DirectoryReader reader = w.getReader()) {
                verifier.accept(reader);
            }
        }
    }

    private Operator groupByLongs(BigArrays bigArrays, int channel) {
        return new HashAggregationOperator(
            List.of(),
            () -> BlockHash.build(List.of(new HashAggregationOperator.GroupSpec(channel, ElementType.LONG)), bigArrays)
        );
    }

    private Executor randomExecutor() {
        return threadPool.executor(randomFrom(ThreadPool.Names.SAME, ThreadPool.Names.GENERIC, ThreadPool.Names.SEARCH));
    }

    public void testOperatorsWithLuceneGroupingCount() throws IOException {
        BigArrays bigArrays = bigArrays();
        final String fieldName = "value";
        final int numDocs = 100000;
        try (Directory dir = newDirectory(); RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
            Document doc = new Document();
            NumericDocValuesField docValuesField = new NumericDocValuesField(fieldName, 0);
            for (int i = 0; i < numDocs; i++) {
                doc.clear();
                docValuesField.setLongValue(i);
                doc.add(docValuesField);
                w.addDocument(doc);
            }
            w.commit();

            ValuesSource vs = new ValuesSource.Numeric.FieldData(
                new SortedNumericIndexFieldData(
                    fieldName,
                    IndexNumericFieldData.NumericType.LONG,
                    IndexNumericFieldData.NumericType.LONG.getValuesSourceType(),
                    null
                )
            );

            try (IndexReader reader = w.getReader()) {
                AtomicInteger pageCount = new AtomicInteger();
                AtomicInteger rowCount = new AtomicInteger();
                AtomicReference<Page> lastPage = new AtomicReference<>();

                // implements cardinality on value field
                try (
                    Driver driver = new Driver(
                        new LuceneSourceOperator(reader, 0, new MatchAllDocsQuery()),
                        List.of(
                            new ValuesSourceReaderOperator(
                                List.of(new ValueSourceInfo(CoreValuesSourceType.NUMERIC, vs, ElementType.LONG, reader)),
                                0
                            ),
                            new HashAggregationOperator(
                                List.of(
                                    new GroupingAggregator.GroupingAggregatorFactory(
                                        bigArrays,
                                        GroupingAggregatorFunction.COUNT,
                                        INITIAL,
                                        1
                                    )
                                ),
                                () -> BlockHash.build(List.of(new HashAggregationOperator.GroupSpec(1, ElementType.LONG)), bigArrays)
                            ),
                            new HashAggregationOperator(
                                List.of(
                                    new GroupingAggregator.GroupingAggregatorFactory(
                                        bigArrays,
                                        GroupingAggregatorFunction.COUNT,
                                        INTERMEDIATE,
                                        1
                                    )
                                ),
                                () -> BlockHash.build(List.of(new HashAggregationOperator.GroupSpec(0, ElementType.LONG)), bigArrays)
                            ),
                            new HashAggregationOperator(
                                List.of(
                                    new GroupingAggregator.GroupingAggregatorFactory(bigArrays, GroupingAggregatorFunction.COUNT, FINAL, 1)
                                ),
                                () -> BlockHash.build(List.of(new HashAggregationOperator.GroupSpec(0, ElementType.LONG)), bigArrays)
                            )
                        ),
                        new PageConsumerOperator(page -> {
                            logger.info("New page: {}", page);
                            pageCount.incrementAndGet();
                            rowCount.addAndGet(page.getPositionCount());
                            lastPage.set(page);
                        }),
                        () -> {}
                    )
                ) {
                    driver.run();
                }
                assertEquals(1, pageCount.get());
                assertEquals(2, lastPage.get().getBlockCount());
                assertEquals(numDocs, rowCount.get());
                LongBlock valuesBlock = lastPage.get().getBlock(1);
                for (int i = 0; i < numDocs; i++) {
                    assertEquals(1, valuesBlock.getLong(i));
                }
            }
        }
    }

    public void testGroupingWithOrdinals() throws IOException {
        final String gField = "g";
        final int numDocs = between(100, 10000);
        final Map<BytesRef, Long> expectedCounts = new HashMap<>();
        int keyLength = randomIntBetween(1, 10);
        try (BaseDirectoryWrapper dir = newDirectory(); RandomIndexWriter writer = new RandomIndexWriter(random(), dir)) {
            for (int i = 0; i < numDocs; i++) {
                Document doc = new Document();
                BytesRef key = new BytesRef(randomByteArrayOfLength(keyLength));
                SortedSetDocValuesField docValuesField = new SortedSetDocValuesField(gField, key);
                doc.add(docValuesField);
                writer.addDocument(doc);
                expectedCounts.compute(key, (k, v) -> v == null ? 1 : v + 1);
            }
            writer.commit();
            Map<BytesRef, Long> actualCounts = new HashMap<>();
            BigArrays bigArrays = bigArrays();
            boolean shuffleDocs = randomBoolean();
            Operator shuffleDocsOperator = new AbstractPageMappingOperator() {
                @Override
                protected Page process(Page page) {
                    if (shuffleDocs == false) {
                        return page;
                    }
                    DocVector docVector = (DocVector) page.getBlock(0).asVector();
                    int positionCount = docVector.getPositionCount();
                    IntVector shards = docVector.shards();
                    if (randomBoolean()) {
                        IntVector.Builder builder = IntVector.newVectorBuilder(positionCount);
                        for (int i = 0; i < positionCount; i++) {
                            builder.appendInt(shards.getInt(i));
                        }
                        shards = builder.build();
                    }
                    IntVector segments = docVector.segments();
                    if (randomBoolean()) {
                        IntVector.Builder builder = IntVector.newVectorBuilder(positionCount);
                        for (int i = 0; i < positionCount; i++) {
                            builder.appendInt(segments.getInt(i));
                        }
                        segments = builder.build();
                    }
                    IntVector docs = docVector.docs();
                    if (randomBoolean()) {
                        List<Integer> ids = new ArrayList<>(positionCount);
                        for (int i = 0; i < positionCount; i++) {
                            ids.add(docs.getInt(i));
                        }
                        Collections.shuffle(ids, random());
                        docs = new IntArrayVector(ids.stream().mapToInt(n -> n).toArray(), positionCount);
                    }
                    Block[] blocks = new Block[page.getBlockCount()];
                    blocks[0] = new DocVector(shards, segments, docs, false).asBlock();
                    for (int i = 1; i < blocks.length; i++) {
                        blocks[i] = page.getBlock(i);
                    }
                    return new Page(blocks);
                }

                @Override
                public String toString() {
                    return "ShuffleDocs";
                }
            };

            try (DirectoryReader reader = writer.getReader()) {
                Driver driver = new Driver(
                    new LuceneSourceOperator(reader, 0, new MatchAllDocsQuery()),
                    List.of(shuffleDocsOperator, new AbstractPageMappingOperator() {
                        @Override
                        protected Page process(Page page) {
                            return page.appendBlock(IntBlock.newConstantBlockWith(1, page.getPositionCount()));
                        }

                        @Override
                        public String toString() {
                            return "Add(1)";
                        }
                    },
                        new OrdinalsGroupingOperator(
                            List.of(
                                new ValueSourceInfo(
                                    CoreValuesSourceType.KEYWORD,
                                    randomBoolean() ? getOrdinalsValuesSource(gField) : getBytesValuesSource(gField),
                                    ElementType.BYTES_REF,
                                    reader
                                )
                            ),
                            0,
                            List.of(
                                new GroupingAggregator.GroupingAggregatorFactory(bigArrays, GroupingAggregatorFunction.COUNT, INITIAL, 1)
                            ),
                            bigArrays
                        ),
                        new HashAggregationOperator(
                            List.of(
                                new GroupingAggregator.GroupingAggregatorFactory(bigArrays, GroupingAggregatorFunction.COUNT, FINAL, 1)
                            ),
                            () -> BlockHash.build(List.of(new HashAggregationOperator.GroupSpec(0, ElementType.BYTES_REF)), bigArrays)
                        )
                    ),
                    new PageConsumerOperator(page -> {
                        BytesRefBlock keys = page.getBlock(0);
                        LongBlock counts = page.getBlock(1);
                        for (int i = 0; i < keys.getPositionCount(); i++) {
                            BytesRef spare = new BytesRef();
                            actualCounts.put(keys.getBytesRef(i, spare), counts.getLong(i));
                        }
                    }),
                    () -> {}
                );
                driver.run();
                assertThat(actualCounts, equalTo(expectedCounts));
            }
        }
    }

    private static List<Page> drainSourceToPages(Operator source) {
        List<Page> rawPages = new ArrayList<>();
        Page page;
        while ((page = source.getOutput()) != null) {
            rawPages.add(page);
        }
        assert rawPages.size() > 0;
        // shuffling provides a basic level of randomness to otherwise quite boring data
        Collections.shuffle(rawPages, random());
        return rawPages;
    }

    public void testFilterOperator() {
        var positions = 1000;
        var values = randomList(positions, positions, ESTestCase::randomLong);
        Predicate<Long> condition = l -> l % 2 == 0;

        var results = new ArrayList<Long>();

        try (
            var driver = new Driver(
                new SequenceLongBlockSourceOperator(values),
                List.of(new FilterOperator((page, position) -> condition.test(page.<LongBlock>getBlock(0).getLong(position)))),
                new PageConsumerOperator(page -> {
                    LongBlock block = page.getBlock(0);
                    for (int i = 0; i < page.getPositionCount(); i++) {
                        results.add(block.getLong(i));
                    }
                }),
                () -> {}
            )
        ) {
            driver.run();
        }

        assertThat(results, contains(values.stream().filter(condition).toArray()));
    }

    public void testFilterEvalFilter() {
        var positions = 1000;
        var values = randomList(positions, positions, ESTestCase::randomLong);
        Predicate<Long> condition1 = l -> l % 2 == 0;
        Function<Long, Long> transformation = l -> l + 1;
        Predicate<Long> condition2 = l -> l % 3 == 0;

        var results = new ArrayList<Tuple<Long, Long>>();

        try (
            var driver = new Driver(
                new SequenceLongBlockSourceOperator(values),
                List.of(
                    new FilterOperator((page, position) -> condition1.test(page.<LongBlock>getBlock(0).getLong(position))),
                    new EvalOperator(
                        (page, position) -> transformation.apply(page.<LongBlock>getBlock(0).getLong(position)),
                        ElementType.LONG
                    ),
                    new FilterOperator((page, position) -> condition2.test(page.<LongBlock>getBlock(1).getLong(position)))
                ),
                new PageConsumerOperator(page -> {
                    LongBlock block1 = page.getBlock(0);
                    LongBlock block2 = page.getBlock(1);
                    for (int i = 0; i < page.getPositionCount(); i++) {
                        results.add(tuple(block1.getLong(i), block2.getLong(i)));
                    }
                }),
                () -> {}
            )
        ) {
            driver.run();
        }

        assertThat(
            results,
            contains(
                values.stream()
                    .filter(condition1)
                    .map(l -> tuple(l, transformation.apply(l)))
                    .filter(t -> condition2.test(t.v2()))
                    .toArray()
            )
        );
    }

    public void testLimitOperator() {
        var positions = 100;
        var limit = randomIntBetween(90, 101);
        var values = randomList(positions, positions, ESTestCase::randomLong);

        var results = new ArrayList<Long>();

        try (
            var driver = new Driver(
                new SequenceLongBlockSourceOperator(values, 100),
                List.of(new LimitOperator(limit)),
                new PageConsumerOperator(page -> {
                    LongBlock block = page.getBlock(0);
                    for (int i = 0; i < page.getPositionCount(); i++) {
                        results.add(block.getLong(i));
                    }
                }),
                () -> {}
            )
        ) {
            driver.run();
        }

        assertThat(results, contains(values.stream().limit(limit).toArray()));
    }

    private static Set<Integer> searchForDocIds(IndexReader reader, Query query) throws IOException {
        IndexSearcher searcher = new IndexSearcher(reader);
        Set<Integer> docIds = new HashSet<>();
        searcher.search(query, new Collector() {
            @Override
            public LeafCollector getLeafCollector(LeafReaderContext context) {
                return new LeafCollector() {
                    @Override
                    public void setScorer(Scorable scorer) {

                    }

                    @Override
                    public void collect(int doc) {
                        int docId = context.docBase + doc;
                        assertTrue(docIds.add(docId));
                    }
                };
            }

            @Override
            public ScoreMode scoreMode() {
                return ScoreMode.COMPLETE_NO_SCORES;
            }
        });
        return docIds;
    }

    static ValuesSource.Bytes.WithOrdinals getOrdinalsValuesSource(String field) {
        return new ValuesSource.Bytes.WithOrdinals() {

            @Override
            public SortedBinaryDocValues bytesValues(LeafReaderContext context) throws IOException {
                return getBytesValuesSource(field).bytesValues(context);
            }

            @Override
            public SortedSetDocValues ordinalsValues(LeafReaderContext context) throws IOException {
                return context.reader().getSortedSetDocValues(field);
            }

            @Override
            public SortedSetDocValues globalOrdinalsValues(LeafReaderContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean supportsGlobalOrdinalsMapping() {
                return false;
            }

            @Override
            public LongUnaryOperator globalOrdinalsMapping(LeafReaderContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }

    static ValuesSource.Bytes getBytesValuesSource(String field) {
        return new ValuesSource.Bytes() {
            @Override
            public SortedBinaryDocValues bytesValues(LeafReaderContext context) throws IOException {
                final SortedSetDocValues dv = context.reader().getSortedSetDocValues(field);
                return new SortedBinaryDocValues() {
                    @Override
                    public boolean advanceExact(int doc) throws IOException {
                        return dv.advanceExact(doc);
                    }

                    @Override
                    public int docValueCount() {
                        return dv.docValueCount();
                    }

                    @Override
                    public BytesRef nextValue() throws IOException {
                        return dv.lookupOrd(dv.nextOrd());
                    }
                };
            }
        };
    }

    /**
     * Creates a {@link BigArrays} that tracks releases but doesn't throw circuit breaking exceptions.
     */
    private BigArrays bigArrays() {
        return new MockBigArrays(new MockPageCacheRecycler(Settings.EMPTY), new NoneCircuitBreakerService());
    }
}
