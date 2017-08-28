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
package org.elasticsearch.search.suggest.completion;

import org.apache.lucene.search.suggest.document.CompletionQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.mapper.CompletionFieldMapper;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.suggest.SuggestionSearchContext;
import org.elasticsearch.search.suggest.completion.context.ContextMapping;
import org.elasticsearch.search.suggest.completion.context.ContextMapping.InternalQueryContext.Operation;
import org.elasticsearch.search.suggest.completion.context.ContextMappings;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class CompletionSuggestionContext extends SuggestionSearchContext.SuggestionContext {
    private CompletionFieldMapper.CompletionFieldType fieldType;
    private FuzzyOptions fuzzyOptions;
    private RegexOptions regexOptions;
    private Map<String, List<ContextMapping.InternalQueryContext>> queryContexts = Collections.emptyMap();

    CompletionSuggestionContext(QueryShardContext shardContext) {
        super(CompletionSuggester.INSTANCE, shardContext);
    }

    void setFieldType(CompletionFieldMapper.CompletionFieldType fieldType) {
        this.fieldType = fieldType;
    }

    void setRegexOptions(RegexOptions regexOptions) {
        this.regexOptions = regexOptions;
    }

    void setFuzzyOptions(FuzzyOptions fuzzyOptions) {
        this.fuzzyOptions = fuzzyOptions;
    }

    void setQueryContexts(Map<String, List<ContextMapping.InternalQueryContext>> queryContexts) {
        this.queryContexts = queryContexts;
    }

    CompletionFieldMapper.CompletionFieldType getFieldType() {
        return this.fieldType;
    }

    /**
     * When context is enabled for a completion field,
     * we factor in the number of indexed context mappings (when no query context is provided)
     * or the number of query context values into the maximum number of suggestion to traverse
     * for a lucene segment, to ensure search admissibility.
     *
     * NOTE: a suggestion with N context values are represented as N suggestions, at the lucene level.
     * Completion suggester will early-terminate when {@link #getSize()} acceptable results are collected
     * for each lucene segment.
     */
    int getMaxSize() {
        if (fieldType.hasContextMappings()) {
            if (queryContexts.isEmpty() == false) {
                // multiply the size with # of query context values
                int nQueryContexts = queryContexts.values().stream().mapToInt(List::size).sum();
                return nQueryContexts * getSize();
            } else {
                // multiply the size with # of indexed context types
                int nIndexedContextTypes = fieldType.getContextMappings().size();
                return nIndexedContextTypes * getSize();
            }
        } else {
            return getSize();
        }
    }

    /**
     * Predicate to test if a suggestion is acceptable given it's collected contexts,
     * to ensure suggestions that doesn't have all the required (AND'ed) contexts
     * are filtered out at query time.
     */
    Predicate<List<CharSequence>> suggestContextsPredicate() {
        return suggestDocContexts -> {
            if (fieldType.hasContextMappings() == false) {
                return true;
            }
            final Set<String> requiredContextTypes = getRequiredContextTypes();
            if (requiredContextTypes.isEmpty()) {
                return true;
            }
            final Map<String, Set<CharSequence>> documentContexts = fieldType.getContextMappings()
                    .getNamedContexts(suggestDocContexts);
            final Set<String> documentContextTypes = documentContexts.keySet();
            boolean hasAllRequiredContextTypes = requiredContextTypes.stream().allMatch(documentContextTypes::contains);
            if (hasAllRequiredContextTypes) {
                for (String requiredContextType : requiredContextTypes) {
                    final List<String> requiredContextValues = getContexts(requiredContextType, Operation.AND);
                    final Set<CharSequence> documentContextValues = documentContexts.get(requiredContextType);
                    boolean hasAllRequiredContextValues = requiredContextValues.stream().allMatch(documentContextValues::contains);
                    if (hasAllRequiredContextValues == false) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        };
    }

    CompletionQuery toQuery() {
        CompletionFieldMapper.CompletionFieldType fieldType = getFieldType();
        final CompletionQuery query;
        if (getPrefix() != null) {
            query = createCompletionQuery(getPrefix(), fieldType);
        } else if (getRegex() != null) {
            if (fuzzyOptions != null) {
                throw new IllegalArgumentException("can not use 'fuzzy' options with 'regex");
            }
            if (regexOptions == null) {
                regexOptions = RegexOptions.builder().build();
            }
            query = fieldType.regexpQuery(getRegex(), regexOptions.getFlagsValue(),
                    regexOptions.getMaxDeterminizedStates());
        } else if (getText() != null) {
            query = createCompletionQuery(getText(), fieldType);
        } else {
            throw new IllegalArgumentException("'prefix/text' or 'regex' must be defined");
        }
        if (fieldType.hasContextMappings()) {
            ContextMappings contextMappings = fieldType.getContextMappings();
            return contextMappings.toContextQuery(query, queryContexts);
        }
        return query;
    }

    private CompletionQuery createCompletionQuery(BytesRef prefix, CompletionFieldMapper.CompletionFieldType fieldType) {
        final CompletionQuery query;
        if (fuzzyOptions != null) {
            query = fieldType.fuzzyQuery(prefix.utf8ToString(),
                    Fuzziness.fromEdits(fuzzyOptions.getEditDistance()),
                    fuzzyOptions.getFuzzyPrefixLength(), fuzzyOptions.getFuzzyMinLength(),
                    fuzzyOptions.getMaxDeterminizedStates(), fuzzyOptions.isTranspositions(),
                    fuzzyOptions.isUnicodeAware());
        } else {
            query = fieldType.prefixQuery(prefix);
        }
        return query;
    }

    private List<String> getContexts(final String type, final Operation operation) {
        return queryContexts.get(type).stream()
                .filter(context -> context.operation == operation)
                .map(context -> context.context)
                .collect(Collectors.toList());
    }

    private Set<String> getRequiredContextTypes() {
        return queryContexts.keySet().stream()
                .filter(context -> getContexts(context, Operation.AND).size() > 0)
                .collect(Collectors.toSet());
    }

}
