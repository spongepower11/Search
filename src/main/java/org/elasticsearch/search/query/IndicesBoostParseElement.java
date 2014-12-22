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

package org.elasticsearch.search.query;

import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.SearchParseElement;
import org.elasticsearch.search.internal.SearchContext;

/**
 * <pre>
 * {
 *    indicesBoost : {
 *         "index1" : 1.4,
 *         "index2" : 1.5
 *    }
 * }
 * </pre>
 *
 *
 */
public class IndicesBoostParseElement implements SearchParseElement {

    @Override
    public void parse(XContentParser parser, SearchContext context) throws Exception {
        XContentParser.Token token;
        ClusterService clusterService = context.clusterService();

        float boost = Float.MIN_VALUE;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                String indexName = parser.currentName();
                if (matchesIndex(clusterService, context.shardTarget().index(), indexName)) {
                    parser.nextToken(); // move to the value
                    // we found our query boost
                    boost = Math.max(parser.floatValue(), boost);
                }
            }
        }

        if (boost > Float.MIN_VALUE) {
            context.queryBoost(boost);
        }
    }

    protected boolean matchesIndex(ClusterService clusterService, String currentIndex, String boostTargetIndex) {
        final String[] concreteIndices = clusterService.state().metaData().concreteIndices(IndicesOptions.lenientExpandOpen(), boostTargetIndex);
        for (String index : concreteIndices) {
            if (Regex.simpleMatch(index, currentIndex)) {
                return true;
            }
        }
        return false;
    }

}
