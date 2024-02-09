/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.application.rules;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.client.internal.OriginSettingClient;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.HeaderWarning;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.license.LicenseUtils;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.searchbusinessrules.PinnedQueryBuilder;
import org.elasticsearch.xpack.searchbusinessrules.PinnedQueryBuilder.PinnedDocument;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xpack.core.ClientHelper.ENT_SEARCH_ORIGIN;
import static org.elasticsearch.xpack.searchbusinessrules.PinnedQueryBuilder.MAX_NUM_PINNED_HITS;

/**
 * A query that will determine based on query context and configured query rules,
 * whether a query should be modified based on actions specified in matching rules.
 *
 * This iteration will determine if a query should have pinned documents and if so,
 * modify the query accordingly to pin those documents.
 */
public class RuleQueryBuilder extends AbstractQueryBuilder<RuleQueryBuilder> {

    public static final String NAME = "rule_query";

    private static final ParseField RULESET_ID_FIELD = new ParseField("ruleset_id");
    static final ParseField MATCH_CRITERIA_FIELD = new ParseField("match_criteria");
    private static final ParseField ORGANIC_QUERY_FIELD = new ParseField("organic");

    private final String rulesetId;
    private final Map<String, Object> matchCriteria;
    private final QueryBuilder organicQuery;
    private final List<PinnedDocument> pinnedDocs;
    private final Supplier<List<PinnedDocument>> pinnedDocsSupplier;

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersions.V_8_10_X;
    }

    public RuleQueryBuilder(QueryBuilder organicQuery, Map<String, Object> matchCriteria, String rulesetId) {
        this(organicQuery, matchCriteria, rulesetId, null, null);
    }

    public RuleQueryBuilder(StreamInput in) throws IOException {
        super(in);
        organicQuery = in.readNamedWriteable(QueryBuilder.class);
        matchCriteria = in.readGenericMap();
        rulesetId = in.readString();
        if (in.getTransportVersion().before(TransportVersions.SEARCH_QUERY_RULES_IDS_REMOVED)) {
            in.readOptionalStringCollectionAsList();
        }
        pinnedDocs = in.readOptionalCollectionAsList(PinnedDocument::new);
        pinnedDocsSupplier = null;
    }

    private RuleQueryBuilder(
        QueryBuilder organicQuery,
        Map<String, Object> matchCriteria,
        String rulesetId,
        List<PinnedDocument> pinnedDocs,
        Supplier<List<PinnedDocument>> pinnedDocsSupplier

    ) {
        if (organicQuery == null) {
            throw new IllegalArgumentException("organicQuery must not be null");
        }
        if (matchCriteria == null || matchCriteria.isEmpty()) {
            throw new IllegalArgumentException("matchCriteria must not be null or empty");
        }
        if (Strings.isNullOrEmpty(rulesetId)) {
            throw new IllegalArgumentException("rulesetId must not be null or empty");
        }

        // PinnedQueryBuilder will return an error if we attempt to return more than the maximum number of
        // pinned hits. Here, we truncate matching rules rather than return an error.
        if (pinnedDocs != null && pinnedDocs.size() > MAX_NUM_PINNED_HITS) {
            HeaderWarning.addWarning("Truncating query rule pinned hits to " + MAX_NUM_PINNED_HITS + " documents");
            pinnedDocs = pinnedDocs.subList(0, MAX_NUM_PINNED_HITS);
        }

        this.organicQuery = organicQuery;
        this.matchCriteria = matchCriteria;
        this.rulesetId = rulesetId;
        this.pinnedDocs = pinnedDocs;
        this.pinnedDocsSupplier = pinnedDocsSupplier;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        if (pinnedDocsSupplier != null) {
            throw new IllegalStateException("pinnedDocsSupplier must be null, can't serialize suppliers, missing a rewriteAndFetch?");
        }

        out.writeNamedWriteable(organicQuery);
        out.writeGenericMap(matchCriteria);
        out.writeString(rulesetId);
        if (out.getTransportVersion().before(TransportVersions.SEARCH_QUERY_RULES_IDS_REMOVED)) {
            out.writeOptionalStringCollection(Collections.emptyList());
        }
        out.writeOptionalCollection(pinnedDocs);
    }

    public String rulesetId() {
        return rulesetId;
    }

    public Map<String, Object> matchCriteria() {
        return matchCriteria;
    }

    public QueryBuilder organicQuery() {
        return organicQuery;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(ORGANIC_QUERY_FIELD.getPreferredName(), organicQuery);
        builder.startObject(MATCH_CRITERIA_FIELD.getPreferredName());
        builder.mapContents(matchCriteria);
        builder.endObject();
        builder.field(RULESET_ID_FIELD.getPreferredName(), rulesetId);
        boostAndQueryNameToXContent(builder);
        builder.endObject();
    }

    @Override
    protected Query doToQuery(SearchExecutionContext context) {
        throw new IllegalStateException(NAME + " should have been rewritten to another query type");
    }

    @SuppressWarnings("unchecked")
    @Override
    protected QueryBuilder doRewrite(QueryRewriteContext queryRewriteContext) throws IOException {
        if (pinnedDocs != null) {
            return new PinnedQueryBuilder(organicQuery, pinnedDocs.toArray(new PinnedDocument[0]));
        } else if (pinnedDocsSupplier != null) {
            List<PinnedDocument> identifiedPinnedDocs = pinnedDocsSupplier.get();
            return identifiedPinnedDocs != null
                ? new PinnedQueryBuilder(organicQuery, identifiedPinnedDocs.toArray(new PinnedDocument[0]))
                : this;
        }

        // Identify matching rules and apply them as applicable
        GetRequest getRequest = new GetRequest(QueryRulesIndexService.QUERY_RULES_ALIAS_NAME, rulesetId);
        SetOnce<List<PinnedDocument>> pinnedDocsSetOnce = new SetOnce<>();
        AppliedQueryRules appliedRules = new AppliedQueryRules();

        queryRewriteContext.registerAsyncAction((client, listener) -> {
            Client clientWithOrigin = new OriginSettingClient(client, ENT_SEARCH_ORIGIN);
            clientWithOrigin.get(getRequest, new ActionListener<>() {
                @Override
                public void onResponse(GetResponse getResponse) {
                    if (getResponse.isExists() == false) {
                        throw new ResourceNotFoundException("query ruleset " + rulesetId + " not found");
                    }
                    QueryRuleset queryRuleset = QueryRuleset.fromXContentBytes(
                        rulesetId,
                        getResponse.getSourceAsBytesRef(),
                        XContentType.JSON
                    );
                    for (QueryRule rule : queryRuleset.rules()) {
                        rule.applyRule(appliedRules, matchCriteria);
                    }
                    pinnedDocsSetOnce.set(appliedRules.pinnedDocs().stream().distinct().toList());
                    listener.onResponse(null);
                }

                @Override
                public void onFailure(Exception e) {
                    Throwable cause = ExceptionsHelper.unwrapCause(e);
                    if (cause instanceof IndexNotFoundException) {
                        listener.onFailure(new ResourceNotFoundException("query ruleset " + rulesetId + " not found"));
                    } else {
                        listener.onFailure(e);
                    }
                }
            });
        });

        QueryBuilder newOrganicQuery = organicQuery.rewrite(queryRewriteContext);
        List<PinnedDocument> docsToPin = pinnedDocsSetOnce.get();
        QueryBuilder rewritten;

        if (docsToPin != null) {
            rewritten = docsToPin.isEmpty()
                ? newOrganicQuery // We've identified there are no documents to pin so let's bypass returning a pinned query
                : new RuleQueryBuilder(newOrganicQuery, matchCriteria, rulesetId, docsToPin, null).rewrite(queryRewriteContext);
        } else {
            rewritten = new RuleQueryBuilder(newOrganicQuery, matchCriteria, this.rulesetId, null, pinnedDocsSetOnce::get);
        }

        rewritten.boost(this.boost);
        rewritten.queryName(this.queryName);
        return rewritten;
    }

    @Override
    protected boolean doEquals(RuleQueryBuilder other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return Objects.equals(rulesetId, other.rulesetId)
            && Objects.equals(matchCriteria, other.matchCriteria)
            && Objects.equals(organicQuery, other.organicQuery)
            && Objects.equals(pinnedDocs, other.pinnedDocs)
            && Objects.equals(pinnedDocsSupplier, other.pinnedDocsSupplier);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(rulesetId, matchCriteria, organicQuery, pinnedDocs, pinnedDocsSupplier);
    }

    private static final ConstructingObjectParser<RuleQueryBuilder, Void> PARSER = new ConstructingObjectParser<>(NAME, a -> {
        QueryBuilder organicQuery = (QueryBuilder) a[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> matchCriteria = (Map<String, Object>) a[1];
        String rulesetId = (String) a[2];
        return new RuleQueryBuilder(organicQuery, matchCriteria, rulesetId);
    });
    static {
        PARSER.declareObject(constructorArg(), (p, c) -> parseInnerQueryBuilder(p), ORGANIC_QUERY_FIELD);
        PARSER.declareObject(constructorArg(), (p, c) -> p.map(), MATCH_CRITERIA_FIELD);
        PARSER.declareString(constructorArg(), RULESET_ID_FIELD);
        declareStandardFields(PARSER);
    }

    public static RuleQueryBuilder fromXContent(XContentParser parser, XPackLicenseState licenseState) {
        if (QueryRulesConfig.QUERY_RULES_LICENSE_FEATURE.check(licenseState) == false) {
            throw LicenseUtils.newComplianceException(NAME);
        }
        try {
            return PARSER.apply(parser, null);
        } catch (IllegalArgumentException e) {
            throw new ParsingException(parser.getTokenLocation(), e.getMessage(), e);
        }
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

}
