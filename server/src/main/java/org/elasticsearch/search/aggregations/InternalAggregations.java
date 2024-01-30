/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.search.aggregations;

import org.apache.lucene.util.SetOnce;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.util.Maps;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.pipeline.SiblingPipelineAggregator;
import org.elasticsearch.search.aggregations.support.AggregationPath;
import org.elasticsearch.search.aggregations.support.SamplingContext;
import org.elasticsearch.search.sort.SortValue;
import org.elasticsearch.xcontent.ToXContentFragment;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.elasticsearch.common.xcontent.XContentParserUtils.parseTypedKeysObject;

/**
 * Represents a set of {@link InternalAggregation}s
 */
public final class InternalAggregations implements Iterable<InternalAggregation>, ToXContentFragment, Writeable {

    public static final String AGGREGATIONS_FIELD = "aggregations";

    public static final InternalAggregations EMPTY = new InternalAggregations(Collections.emptyList());

    private static final Comparator<InternalAggregation> INTERNAL_AGG_COMPARATOR = (agg1, agg2) -> {
        if (agg1.canLeadReduction() == agg2.canLeadReduction()) {
            return 0;
        } else if (agg1.canLeadReduction() && agg2.canLeadReduction() == false) {
            return -1;
        } else {
            return 1;
        }
    };

    protected final List<InternalAggregation> aggregations;
    private Map<String, InternalAggregation> aggregationsAsMap;

    /**
     * Constructs a new aggregation.
     */
    private InternalAggregations(List<InternalAggregation> aggregations) {
        this.aggregations = aggregations;
        if (aggregations.isEmpty()) {
            aggregationsAsMap = emptyMap();
        }
    }

    /**
     * Iterates over the {@link Aggregation}s.
     */
    @Override
    public Iterator<InternalAggregation> iterator() {
        return aggregations.iterator();
    }

    /**
     * The list of {@link Aggregation}s.
     */
    public List<InternalAggregation> asList() {
        return Collections.unmodifiableList(aggregations);
    }

    /**
     * Returns the {@link Aggregation}s keyed by aggregation name.
     */
    public Map<String, InternalAggregation> asMap() {
        return getAsMap();
    }

    /**
     * Returns the {@link Aggregation}s keyed by aggregation name.
     */
    public Map<String, InternalAggregation> getAsMap() {
        if (aggregationsAsMap == null) {
            Map<String, InternalAggregation> newAggregationsAsMap = Maps.newMapWithExpectedSize(aggregations.size());
            for (InternalAggregation aggregation : aggregations) {
                newAggregationsAsMap.put(aggregation.getName(), aggregation);
            }
            this.aggregationsAsMap = unmodifiableMap(newAggregationsAsMap);
        }
        return aggregationsAsMap;
    }

    /**
     * Returns the aggregation that is associated with the specified name.
     */
    @SuppressWarnings("unchecked")
    public <A extends InternalAggregation> A get(String name) {
        return (A) asMap().get(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        return aggregations.equals(((InternalAggregations) obj).aggregations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), aggregations);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (aggregations.isEmpty()) {
            return builder;
        }
        builder.startObject(AGGREGATIONS_FIELD);
        toXContentInternal(builder, params);
        return builder.endObject();
    }

    /**
     * Directly write all the aggregations without their bounding object. Used by sub-aggregations (non top level aggs)
     */
    public XContentBuilder toXContentInternal(XContentBuilder builder, Params params) throws IOException {
        for (InternalAggregation aggregation : aggregations) {
            aggregation.toXContent(builder, params);
        }
        return builder;
    }

    public static InternalAggregations fromXContent(XContentParser parser) throws IOException {
        final List<InternalAggregation> aggregations = new ArrayList<>();
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.START_OBJECT) {
                SetOnce<InternalAggregation> typedAgg = new SetOnce<>();
                String currentField = parser.currentName();
                parseTypedKeysObject(parser, Aggregation.TYPED_KEYS_DELIMITER, InternalAggregation.class, typedAgg::set);
                if (typedAgg.get() != null) {
                    aggregations.add(typedAgg.get());
                } else {
                    throw new ParsingException(
                        parser.getTokenLocation(),
                        String.format(Locale.ROOT, "Could not parse aggregation keyed as [%s]", currentField)
                    );
                }
            }
        }
        return new InternalAggregations(aggregations);
    }

    public static InternalAggregations from(List<InternalAggregation> aggregations) {
        if (aggregations.isEmpty()) {
            return EMPTY;
        }
        return new InternalAggregations(aggregations);
    }

    public static InternalAggregations readFrom(StreamInput in) throws IOException {
        return from(in.readCollectionAsList(stream -> stream.readNamedWriteable(InternalAggregation.class)));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeNamedWriteableCollection(getInternalAggregations());
    }

    /**
     * Make a mutable copy of the aggregation results.
     */
    public List<InternalAggregation> copyResults() {
        return new ArrayList<>(getInternalAggregations());
    }

    @SuppressWarnings("unchecked")
    private List<InternalAggregation> getInternalAggregations() {
        return aggregations;
    }

    /**
     * Get value to use when sorting by a descendant of the aggregation containing this.
     */
    public SortValue sortValue(AggregationPath.PathElement head, Iterator<AggregationPath.PathElement> tail) {
        InternalAggregation aggregation = get(head.name());
        if (aggregation == null) {
            throw new IllegalArgumentException("Cannot find aggregation named [" + head.name() + "]");
        }
        if (tail.hasNext()) {
            return aggregation.sortValue(tail.next(), tail);
        }
        // We can sort by either the `[value]` or `.value`
        return aggregation.sortValue(Optional.ofNullable(head.key()).orElse(head.metric()));
    }

    /**
     * Begin the reduction process.  This should be the entry point for the "first" reduction, e.g. called by
     * SearchPhaseController or anywhere else that wants to initiate a reduction.  It _should not_ be called
     * as an intermediate reduction step (e.g. in the middle of an aggregation tree).
     *
     * This method first reduces the aggregations, and if it is the final reduce, then reduce the pipeline
     * aggregations (both embedded parent/sibling as well as top-level sibling pipelines)
     */
    public static InternalAggregations topLevelReduce(List<InternalAggregations> aggregationsList, AggregationReduceContext context) {
        InternalAggregations reduced = reduce(aggregationsList, context);
        if (reduced == null) {
            return null;
        }

        if (context.isFinalReduce()) {
            List<InternalAggregation> reducedInternalAggs = reduced.getInternalAggregations();
            reducedInternalAggs = reducedInternalAggs.stream()
                .map(agg -> agg.reducePipelines(agg, context, context.pipelineTreeRoot().subTree(agg.getName())))
                .collect(Collectors.toCollection(ArrayList::new));

            for (PipelineAggregator pipelineAggregator : context.pipelineTreeRoot().aggregators()) {
                SiblingPipelineAggregator sib = (SiblingPipelineAggregator) pipelineAggregator;
                InternalAggregation newAgg = sib.doReduce(from(reducedInternalAggs), context);
                reducedInternalAggs.add(newAgg);
            }
            return from(reducedInternalAggs);
        }
        return reduced;
    }

    /**
     * Reduces the given list of aggregations as well as the top-level pipeline aggregators extracted from the first
     * {@link InternalAggregations} object found in the list.
     * Note that pipeline aggregations _are not_ reduced by this method.  Pipelines are handled
     * separately by {@link InternalAggregations#topLevelReduce(List, AggregationReduceContext)}
     */
    public static InternalAggregations reduce(List<InternalAggregations> aggregationsList, AggregationReduceContext context) {
        if (aggregationsList.isEmpty()) {
            return null;
        }

        // first we collect all aggregations of the same type and list them together
        Map<String, List<InternalAggregation>> aggByName = new HashMap<>();
        for (InternalAggregations aggregations : aggregationsList) {
            for (Aggregation aggregation : aggregations.aggregations) {
                List<InternalAggregation> aggs = aggByName.computeIfAbsent(
                    aggregation.getName(),
                    k -> new ArrayList<>(aggregationsList.size())
                );
                aggs.add((InternalAggregation) aggregation);
            }
        }

        // now we can use the first aggregation of each list to handle the reduce of its list
        List<InternalAggregation> reducedAggregations = new ArrayList<>();
        for (Map.Entry<String, List<InternalAggregation>> entry : aggByName.entrySet()) {
            List<InternalAggregation> aggregations = entry.getValue();
            // Sort aggregations so that unmapped aggs come last in the list
            // If all aggs are unmapped, the agg that leads the reduction will just return itself
            aggregations.sort(INTERNAL_AGG_COMPARATOR);
            InternalAggregation first = aggregations.get(0); // the list can't be empty as it's created on demand
            if (first.mustReduceOnSingleInternalAgg() || aggregations.size() > 1) {
                reducedAggregations.add(first.reduce(aggregations, context.forAgg(entry.getKey())));
            } else {
                // no need for reduce phase
                reducedAggregations.add(first);
            }
        }

        return from(reducedAggregations);
    }

    /**
     * Finalizes the sampling for all the internal aggregations
     * @param samplingContext the sampling context
     * @return the finalized aggregations
     */
    public static InternalAggregations finalizeSampling(InternalAggregations internalAggregations, SamplingContext samplingContext) {
        return from(
            internalAggregations.aggregations.stream().map(agg -> agg.finalizeSampling(samplingContext)).collect(Collectors.toList())
        );
    }
}
