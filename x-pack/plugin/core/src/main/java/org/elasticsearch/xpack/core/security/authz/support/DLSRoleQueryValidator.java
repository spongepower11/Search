/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.authz.support;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParseException;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.BoostingQueryBuilder;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.GeoShapeQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptor;
import org.elasticsearch.xpack.core.security.user.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class helps in evaluating the query field if it is template,
 * validating the query and checking if the query type is allowed to be used in DLS role query.
 */
public final class DLSRoleQueryValidator {

    private DLSRoleQueryValidator() {
    }

    /**
     * Validates the query field in the {@link RoleDescriptor.IndicesPrivileges} only if it is not a template query.<br>
     * It parses the query and builds the {@link QueryBuilder}, also checks if the query type is supported in DLS role query.
     *
     * @param indicesPrivileges {@link RoleDescriptor.IndicesPrivileges}
     * @param xContentRegistry  {@link NamedXContentRegistry} for finding named queries
     */
    public static void validateQueryField(RoleDescriptor.IndicesPrivileges[] indicesPrivileges,
                                          NamedXContentRegistry xContentRegistry) {
        if (indicesPrivileges != null) {
            for (RoleDescriptor.IndicesPrivileges idp : indicesPrivileges) {
                BytesReference query = idp.getQuery();
                if (query != null) {
                    if (isTemplateQuery(query, xContentRegistry)) {
                        // skip template query, this requires runtime information like 'User' information.
                        continue;
                    }

                    validateAndVerifyRoleQuery(query.utf8ToString(), xContentRegistry);
                }
            }
        }
    }

    private static boolean isTemplateQuery(BytesReference query, NamedXContentRegistry xContentRegistry) {
        try {
            try (XContentParser parser = XContentType.JSON.xContent().createParser(xContentRegistry,
                LoggingDeprecationHandler.INSTANCE, query.utf8ToString())) {
                expectedToken(parser.nextToken(), parser, XContentParser.Token.START_OBJECT);
                expectedToken(parser.nextToken(), parser, XContentParser.Token.FIELD_NAME);
                String fieldName = parser.currentName();
                if ("template".equals(fieldName)) {
                    return true;
                }
            }
        } catch (XContentParseException | IOException e) {
            throw new ElasticsearchParseException("failed to determine if the query is a template query", e);
        }
        return false;
    }

    private static void expectedToken(XContentParser.Token read, XContentParser parser, XContentParser.Token expected) {
        if (read != expected) {
            throw new XContentParseException(parser.getTokenLocation(),
                "expected [" + expected + "] but found [" + read + "] instead");
        }
    }

    /**
     * Evaluates the query if it is a template and then validates the query by parsing
     * and building the {@link QueryBuilder}. It also checks if the query type is
     * supported in DLS role query.
     *
     * @param query            {@link BytesReference} query field from the role
     * @param scriptService    {@link ScriptService} used for evaluation of a template query
     * @param xContentRegistry {@link NamedXContentRegistry} for finding named queries
     * @param user             {@link User} used when evaluation a template query
     * @return {@link QueryBuilder} if the query is valid and allowed, in case {@link RoleDescriptor.IndicesPrivileges}
     * * does not have a query field then it returns {@code null}.
     */
    @Nullable
    public static QueryBuilder validateAndVerifyRoleQuery(BytesReference query, ScriptService scriptService,
                                                          NamedXContentRegistry xContentRegistry, User user) {
        QueryBuilder queryBuilder = null;
        if (query != null) {
            String templateResult = SecurityQueryTemplateEvaluator.evaluateTemplate(query.utf8ToString(), scriptService,
                user);
            queryBuilder = validateAndVerifyRoleQuery(templateResult, xContentRegistry);
        }
        return queryBuilder;
    }

    private static QueryBuilder validateAndVerifyRoleQuery(String query, NamedXContentRegistry xContentRegistry) {
        QueryBuilder queryBuilder = null;
        if (query != null) {
            try {
                try (XContentParser parser = XContentFactory.xContent(query).createParser(xContentRegistry,
                    LoggingDeprecationHandler.INSTANCE, query)) {
                    queryBuilder = AbstractQueryBuilder.parseInnerQueryBuilder(parser);
                    verifyRoleQuery(queryBuilder);
                }
            } catch (ElasticsearchParseException | ParsingException | XContentParseException | IOException e) {
                throw new ElasticsearchParseException("failed to parse field 'query' from the role descriptor", e);
            }
        }
        return queryBuilder;
    }

    /**
     * Checks whether the role query contains queries we know can't be used as DLS role query.
     *
     * @param queryBuilder {@link QueryBuilder} for given query
     */
    public static void verifyRoleQuery(QueryBuilder queryBuilder) {
        if (queryBuilder instanceof TermsQueryBuilder) {
            TermsQueryBuilder termsQueryBuilder = (TermsQueryBuilder) queryBuilder;
            if (termsQueryBuilder.termsLookup() != null) {
                throw new IllegalArgumentException("terms query with terms lookup isn't supported as part of a role query");
            }
        } else if (queryBuilder instanceof GeoShapeQueryBuilder) {
            GeoShapeQueryBuilder geoShapeQueryBuilder = (GeoShapeQueryBuilder) queryBuilder;
            if (geoShapeQueryBuilder.shape() == null) {
                throw new IllegalArgumentException("geoshape query referring to indexed shapes isn't support as part of a role query");
            }
        } else if (queryBuilder.getName().equals("percolate")) {
            // actually only if percolate query is referring to an existing document then this is problematic,
            // a normal percolate query does work. However we can't check that here as this query builder is inside
            // another module. So we don't allow the entire percolate query. I don't think users would ever use
            // a percolate query as role query, so this restriction shouldn't prohibit anyone from using dls.
            throw new IllegalArgumentException("percolate query isn't support as part of a role query");
        } else if (queryBuilder.getName().equals("has_child")) {
            throw new IllegalArgumentException("has_child query isn't support as part of a role query");
        } else if (queryBuilder.getName().equals("has_parent")) {
            throw new IllegalArgumentException("has_parent query isn't support as part of a role query");
        } else if (queryBuilder instanceof BoolQueryBuilder) {
            BoolQueryBuilder boolQueryBuilder = (BoolQueryBuilder) queryBuilder;
            List<QueryBuilder> clauses = new ArrayList<>();
            clauses.addAll(boolQueryBuilder.filter());
            clauses.addAll(boolQueryBuilder.must());
            clauses.addAll(boolQueryBuilder.mustNot());
            clauses.addAll(boolQueryBuilder.should());
            for (QueryBuilder clause : clauses) {
                verifyRoleQuery(clause);
            }
        } else if (queryBuilder instanceof ConstantScoreQueryBuilder) {
            verifyRoleQuery(((ConstantScoreQueryBuilder) queryBuilder).innerQuery());
        } else if (queryBuilder instanceof FunctionScoreQueryBuilder) {
            verifyRoleQuery(((FunctionScoreQueryBuilder) queryBuilder).query());
        } else if (queryBuilder instanceof BoostingQueryBuilder) {
            verifyRoleQuery(((BoostingQueryBuilder) queryBuilder).negativeQuery());
            verifyRoleQuery(((BoostingQueryBuilder) queryBuilder).positiveQuery());
        }
    }
}
