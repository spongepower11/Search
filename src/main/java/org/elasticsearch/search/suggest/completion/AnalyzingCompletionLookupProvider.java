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

package org.elasticsearch.search.suggest.completion;

import gnu.trove.map.hash.TObjectLongHashMap;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.codecs.*;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.XAnalyzingSuggester;
import org.apache.lucene.search.suggest.analyzing.XFuzzySuggester;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.fst.ByteSequenceOutputs;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PairOutputs;
import org.apache.lucene.util.fst.PairOutputs.Pair;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.search.suggest.completion.Completion090PostingsFormat.CompletionLookupProvider;
import org.elasticsearch.search.suggest.completion.Completion090PostingsFormat.LookupFactory;

import java.io.IOException;
import java.util.*;

public class AnalyzingCompletionLookupProvider extends CompletionLookupProvider {

    // for serialization
    public static final int SERIALIZE_PRESERVE_SEPERATORS = 1;
    public static final int SERIALIZE_HAS_PAYLOADS = 2;
    public static final int SERIALIZE_PRESERVE_POSITION_INCREMENTS = 4;

    private static final int MAX_SURFACE_FORMS_PER_ANALYZED_FORM = 256;
    private static final int MAX_GRAPH_EXPANSIONS = -1;

    public static final String CODEC_NAME = "analyzing";
    public static final int CODEC_VERSION = 1;

    private boolean preserveSep;
    private boolean preservePositionIncrements;
    private int maxSurfaceFormsPerAnalyzedForm;
    private int maxGraphExpansions;
    private boolean hasPayloads;
    private final XAnalyzingSuggester prototype;

    public AnalyzingCompletionLookupProvider(boolean preserveSep, boolean exactFirst, boolean preservePositionIncrements, boolean hasPayloads) {
        this.preserveSep = preserveSep;
        this.preservePositionIncrements = preservePositionIncrements;
        this.hasPayloads = hasPayloads;
        this.maxSurfaceFormsPerAnalyzedForm = MAX_SURFACE_FORMS_PER_ANALYZED_FORM;
        this.maxGraphExpansions = MAX_GRAPH_EXPANSIONS;
        int options = preserveSep ? XAnalyzingSuggester.PRESERVE_SEP : 0;
        // needs to fixed in the suggester first before it can be supported
        //options |= exactFirst ? XAnalyzingSuggester.EXACT_FIRST : 0;
        prototype = new XAnalyzingSuggester(null, null, options, maxSurfaceFormsPerAnalyzedForm, maxGraphExpansions, null, false, 1);
        prototype.setPreservePositionIncrements(preservePositionIncrements);
    }

    @Override
    public String getName() {
        return "analyzing";
    }

    @Override
    public FieldsConsumer consumer(final IndexOutput output) throws IOException {
        CodecUtil.writeHeader(output, CODEC_NAME, CODEC_VERSION);
        return new FieldsConsumer() {
            private Map<FieldInfo, Long> fieldOffsets = new HashMap<FieldInfo, Long>();

            @Override
            public void close() throws IOException {
                try { /*
                       * write the offsets per field such that we know where
                       * we need to load the FSTs from
                       */
                    long pointer = output.getFilePointer();
                    output.writeVInt(fieldOffsets.size());
                    for (Map.Entry<FieldInfo, Long> entry : fieldOffsets.entrySet()) {
                        output.writeString(entry.getKey().name);
                        output.writeVLong(entry.getValue());
                    }
                    output.writeLong(pointer);
                    output.flush();
                } finally {
                    IOUtils.close(output);
                }
            }

            @Override
            public TermsConsumer addField(final FieldInfo field) throws IOException {

                return new TermsConsumer() {
                    final XAnalyzingSuggester.XBuilder builder = new XAnalyzingSuggester.XBuilder(maxSurfaceFormsPerAnalyzedForm, hasPayloads);
                    final CompletionPostingsConsumer postingsConsumer = new CompletionPostingsConsumer(AnalyzingCompletionLookupProvider.this, builder);

                    @Override
                    public PostingsConsumer startTerm(BytesRef text) throws IOException {
                        builder.startTerm(text);
                        return postingsConsumer;
                    }

                    @Override
                    public Comparator<BytesRef> getComparator() throws IOException {
                        return BytesRef.getUTF8SortedAsUnicodeComparator();
                    }

                    @Override
                    public void finishTerm(BytesRef text, TermStats stats) throws IOException {
                        builder.finishTerm(stats.docFreq); // use  doc freq as a fallback
                    }

                    @Override
                    public void finish(long sumTotalTermFreq, long sumDocFreq, int docCount) throws IOException {
                        /*
                         * Here we are done processing the field and we can
                         * buid the FST and write it to disk.
                         */
                        FST<Pair<Long, BytesRef>> build = builder.build();
                        assert build != null || docCount == 0 : "the FST is null but docCount is != 0 actual value: [" + docCount + "]";
                        /*
                         * it's possible that the FST is null if we have 2 segments that get merged
                         * and all docs that have a value in this field are deleted. This will cause
                         * a consumer to be created but it doesn't consume any values causing the FSTBuilder
                         * to return null.
                         */
                        if (build != null) {
                            fieldOffsets.put(field, output.getFilePointer());
                            build.save(output);
                            /* write some more meta-info */
                            output.writeVInt(postingsConsumer.getMaxAnalyzedPathsForOneInput());
                            output.writeVInt(maxSurfaceFormsPerAnalyzedForm);
                            output.writeInt(maxGraphExpansions); // can be negative
                            int options = 0;
                            options |= preserveSep ? SERIALIZE_PRESERVE_SEPERATORS : 0;
                            options |= hasPayloads ? SERIALIZE_HAS_PAYLOADS : 0;
                            options |= preservePositionIncrements ? SERIALIZE_PRESERVE_POSITION_INCREMENTS : 0;
                            output.writeVInt(options);
                        }
                    }
                };
            }
        };
    }

    private static final class CompletionPostingsConsumer extends PostingsConsumer {
        private final SuggestPayload spare = new SuggestPayload();
        private AnalyzingCompletionLookupProvider analyzingSuggestLookupProvider;
        private XAnalyzingSuggester.XBuilder builder;
        private int maxAnalyzedPathsForOneInput = 0;

        public CompletionPostingsConsumer(AnalyzingCompletionLookupProvider analyzingSuggestLookupProvider, XAnalyzingSuggester.XBuilder builder) {
            this.analyzingSuggestLookupProvider = analyzingSuggestLookupProvider;
            this.builder = builder;
        }

        @Override
        public void startDoc(int docID, int freq) throws IOException {
        }

        @Override
        public void addPosition(int position, BytesRef payload, int startOffset, int endOffset) throws IOException {
            analyzingSuggestLookupProvider.parsePayload(payload, spare);
            builder.addSurface(spare.surfaceForm, spare.payload, spare.weight);
            // multi fields have the same surface form so we sum up here
            maxAnalyzedPathsForOneInput = Math.max(maxAnalyzedPathsForOneInput, position + 1);
        }

        @Override
        public void finishDoc() throws IOException {
        }

        public int getMaxAnalyzedPathsForOneInput() {
            return maxAnalyzedPathsForOneInput;
        }
    }

    ;


    @Override
    public LookupFactory load(IndexInput input) throws IOException {
        CodecUtil.checkHeader(input, CODEC_NAME, CODEC_VERSION, CODEC_VERSION);
        final Map<String, AnalyzingSuggestHolder> lookupMap = new HashMap<String, AnalyzingSuggestHolder>();
        input.seek(input.length() - 8);
        long metaPointer = input.readLong();
        input.seek(metaPointer);
        int numFields = input.readVInt();

        Map<Long, String> meta = new TreeMap<Long, String>();
        for (int i = 0; i < numFields; i++) {
            String name = input.readString();
            long offset = input.readVLong();
            meta.put(offset, name);
        }

        for (Map.Entry<Long, String> entry : meta.entrySet()) {
            input.seek(entry.getKey());
            FST<Pair<Long, BytesRef>> fst = new FST<Pair<Long, BytesRef>>(input, new PairOutputs<Long, BytesRef>(
                    PositiveIntOutputs.getSingleton(), ByteSequenceOutputs.getSingleton()));
            int maxAnalyzedPathsForOneInput = input.readVInt();
            int maxSurfaceFormsPerAnalyzedForm = input.readVInt();
            int maxGraphExpansions = input.readInt();
            int options = input.readVInt();
            boolean preserveSep = (options & SERIALIZE_PRESERVE_SEPERATORS) != 0;
            boolean hasPayloads = (options & SERIALIZE_HAS_PAYLOADS) != 0;
            boolean preservePositionIncrements = (options & SERIALIZE_PRESERVE_POSITION_INCREMENTS) != 0;
            lookupMap.put(entry.getValue(), new AnalyzingSuggestHolder(preserveSep, preservePositionIncrements, maxSurfaceFormsPerAnalyzedForm, maxGraphExpansions,
                    hasPayloads, maxAnalyzedPathsForOneInput, fst));
        }
        return new LookupFactory() {
            @Override
            public Lookup getLookup(FieldMapper<?> mapper, CompletionSuggestionContext suggestionContext) {
                AnalyzingSuggestHolder analyzingSuggestHolder = lookupMap.get(mapper.names().indexName());
                if (analyzingSuggestHolder == null) {
                    return null;
                }
                int flags = analyzingSuggestHolder.preserveSep ? XAnalyzingSuggester.PRESERVE_SEP : 0;

                XAnalyzingSuggester suggester;
                if (suggestionContext.isFuzzy()) {
                    suggester = new XFuzzySuggester(mapper.indexAnalyzer(), mapper.searchAnalyzer(), flags,
                            analyzingSuggestHolder.maxSurfaceFormsPerAnalyzedForm, analyzingSuggestHolder.maxGraphExpansions,
                            suggestionContext.getFuzzyEditDistance(), suggestionContext.isFuzzyTranspositions(),
                            suggestionContext.getFuzzyPrefixLength(), suggestionContext.getFuzzyMinLength(),
                            analyzingSuggestHolder.fst, analyzingSuggestHolder.hasPayloads,
                            analyzingSuggestHolder.maxAnalyzedPathsForOneInput);

                } else {
                    suggester = new XAnalyzingSuggester(mapper.indexAnalyzer(), mapper.searchAnalyzer(), flags,
                            analyzingSuggestHolder.maxSurfaceFormsPerAnalyzedForm, analyzingSuggestHolder.maxGraphExpansions,
                            analyzingSuggestHolder.fst, analyzingSuggestHolder.hasPayloads,
                            analyzingSuggestHolder.maxAnalyzedPathsForOneInput);
                }
                suggester.setPreservePositionIncrements(analyzingSuggestHolder.preservePositionIncrements);
                return suggester;
            }

            @Override
            public CompletionStats stats(String... fields) {
                long sizeInBytes = 0;
                TObjectLongHashMap<String> completionFields = null;
                if (fields != null && fields.length > 0) {
                    completionFields = new TObjectLongHashMap<String>(fields.length);
                }

                for (Map.Entry<String, AnalyzingSuggestHolder> entry : lookupMap.entrySet()) {
                    sizeInBytes += entry.getValue().fst.sizeInBytes();
                    if (fields == null || fields.length == 0) {
                        continue;
                    }
                    for (String field : fields) {
                        // support for getting fields by regex as in fielddata
                        if (Regex.simpleMatch(field, entry.getKey())) {
                            long fstSize = entry.getValue().fst.sizeInBytes();
                            completionFields.adjustOrPutValue(field, fstSize, fstSize);
                        }
                    }
                }

                return new CompletionStats(sizeInBytes, completionFields);
            }
        };
    }

    static class AnalyzingSuggestHolder {
        final boolean preserveSep;
        final boolean preservePositionIncrements;
        final int maxSurfaceFormsPerAnalyzedForm;
        final int maxGraphExpansions;
        final boolean hasPayloads;
        final int maxAnalyzedPathsForOneInput;
        final FST<Pair<Long, BytesRef>> fst;

        public AnalyzingSuggestHolder(boolean preserveSep, boolean preservePositionIncrements, int maxSurfaceFormsPerAnalyzedForm, int maxGraphExpansions,
                                      boolean hasPayloads, int maxAnalyzedPathsForOneInput, FST<Pair<Long, BytesRef>> fst) {
            this.preserveSep = preserveSep;
            this.preservePositionIncrements = preservePositionIncrements;
            this.maxSurfaceFormsPerAnalyzedForm = maxSurfaceFormsPerAnalyzedForm;
            this.maxGraphExpansions = maxGraphExpansions;
            this.hasPayloads = hasPayloads;
            this.maxAnalyzedPathsForOneInput = maxAnalyzedPathsForOneInput;
            this.fst = fst;
        }

    }

    @Override
    public Set<IntsRef> toFiniteStrings(TokenStream stream) throws IOException {
        return prototype.toFiniteStrings(prototype.getTokenStreamToAutomaton(), stream);
    }
}