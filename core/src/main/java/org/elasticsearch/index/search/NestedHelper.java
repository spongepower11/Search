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

package org.elasticsearch.index.search;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.ObjectMapper;

/** Utility class to filter parent and children clauses when building nested
 * queries. */
public final class NestedHelper {

    private final MapperService mapperService;

    public NestedHelper(MapperService mapperService) {
        this.mapperService = mapperService;
    }

    /** Returns true if the given query might match nested documents. */
    public boolean mightMatchNestedDocs(Query query) {
        if (query instanceof ConstantScoreQuery) {
            return mightMatchNestedDocs(((ConstantScoreQuery) query).getQuery());
        } else if (query instanceof BoostQuery) {
            return mightMatchNestedDocs(((BoostQuery) query).getQuery());
        } else if (query instanceof MatchAllDocsQuery) {
            return true;
        } else if (query instanceof MatchNoDocsQuery) {
            return false;
        } else if (query instanceof TermQuery) {
            // We only handle term queries and range queries, which should already
            // cover a high majority of use-cases
            return mightMatchNestedDocs(((TermQuery) query).getTerm().field());
        } else if (query instanceof PointRangeQuery) {
            return mightMatchNestedDocs(((PointRangeQuery) query).getField());
        } else if (query instanceof BooleanQuery) {
            final BooleanQuery bq = (BooleanQuery) query;
            final boolean hasRequiredClauses = bq.clauses().stream().anyMatch(BooleanClause::isRequired);
            if (hasRequiredClauses) {
                return bq.clauses().stream()
                        .filter(BooleanClause::isRequired)
                        .map(BooleanClause::getQuery)
                        .allMatch(this::mightMatchNestedDocs);
            } else {
                return bq.clauses().stream()
                        .filter(c -> c.getOccur() == Occur.SHOULD)
                        .map(BooleanClause::getQuery)
                        .anyMatch(this::mightMatchNestedDocs);
            }
        } else if (query instanceof ESToParentBlockJoinQuery) {
            return ((ESToParentBlockJoinQuery) query).getPath() != null;
        } else {
            return true;
        }
    }

    /** Returns true if a query on the given field might match nested documents. */
    boolean mightMatchNestedDocs(String field) {
        if (field.startsWith("_")) {
            // meta field
            return true;
        }
        if (mapperService.fullName(field) == null) {
            return false;
        }
        for (String parent = parentObject(field); parent != null; parent = parentObject(parent)) {
            ObjectMapper mapper = mapperService.getObjectMapper(parent);
            if (mapper != null && mapper.nested().isNested()) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if the given query might match parent documents. */
    public boolean mightMatchNonNestedDocs(Query query, String nestedPath) {
        if (query instanceof ConstantScoreQuery) {
            return mightMatchNonNestedDocs(((ConstantScoreQuery) query).getQuery(), nestedPath);
        } else if (query instanceof BoostQuery) {
            return mightMatchNonNestedDocs(((BoostQuery) query).getQuery(), nestedPath);
        } else if (query instanceof MatchAllDocsQuery) {
            return true;
        } else if (query instanceof MatchNoDocsQuery) {
            return false;
        } else if (query instanceof TermQuery) {
            return mightMatchNonNestedDocs(((TermQuery) query).getTerm().field(), nestedPath);
        } else if (query instanceof PointRangeQuery) {
            return mightMatchNonNestedDocs(((PointRangeQuery) query).getField(), nestedPath);
        } else if (query instanceof BooleanQuery) {
            final BooleanQuery bq = (BooleanQuery) query;
            final boolean hasRequiredClauses = bq.clauses().stream().anyMatch(BooleanClause::isRequired);
            if (hasRequiredClauses) {
                return bq.clauses().stream()
                        .filter(BooleanClause::isRequired)
                        .map(BooleanClause::getQuery)
                        .allMatch(q -> mightMatchNonNestedDocs(q, nestedPath));
            } else {
                return bq.clauses().stream()
                        .filter(c -> c.getOccur() == Occur.SHOULD)
                        .map(BooleanClause::getQuery)
                        .anyMatch(q -> mightMatchNonNestedDocs(q, nestedPath));
            }
        } else {
            return true;
        }
    }

    /** Returns true if a query on the given field might match nested documents. */
    boolean mightMatchNonNestedDocs(String field, String nestedPath) {
        if (mapperService.fullName(field) == null) {
            return false;
        }
        for (String parent = parentObject(field); parent != null; parent = parentObject(parent)) {
            ObjectMapper mapper = mapperService.getObjectMapper(parent);
            if (mapper!= null && mapper.nested().isNested()) {
                if (mapper.fullPath().equals(nestedPath)) {
                    // If the mapper does not include in its parent or in the root object then
                    // the query might only match nested documents with the given path
                    return mapper.nested().isIncludeInParent() || mapper.nested().isIncludeInRoot();
                } else {
                    // the first parent nested mapper does not have the expected path
                    // It might be misconfiguration or a sub nested mapper
                    return true;
                }
            }
        }
        return true; // the field is not a sub field of the nested path
    }

    private static String parentObject(String field) {
        int lastDot = field.lastIndexOf('.');
        if (lastDot == -1) {
            return null;
        }
        return field.substring(0, lastDot);
    }

}
