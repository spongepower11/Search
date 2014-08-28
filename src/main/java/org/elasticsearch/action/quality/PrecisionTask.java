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

package org.elasticsearch.action.quality;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import org.elasticsearch.action.quality.PrecisionAtN.Rating;

import java.util.Collection;

/**
 * This class defines a qa task including query intent and query spec.
 *
 * Each QA run is based on a set of queries to send to the index and multiple QA specifications that define how to translate the query
 * intents into elastic search queries. In addition it contains the quality metrics to compute.
 * */
public class PrecisionTask {

    /** Collection of query intents to check against including expected document ids.*/
    private Collection<Intent<Rating>> intents;
    /** Collection of query specifications, that is e.g. search request templates to use for query translation. */
    private Collection<Specification> specifications;
    
    /** Returns a list of search intents to evaluate. */
    public Collection<Intent<Rating>> getIntents() {
        return intents;
    }

    /** Set a list of search intents to evaluate. */
    public void setIntents(Collection<Intent<Rating>> intents) {
        this.intents = intents;
    }

    /** Returns a list of intent to query translation specifications to evaluate. */
    public Collection<Specification> getSpecifications() {
        return specifications;
    }

    /** Set the list of intent to query translation specifications to evaluate. */
    public void setSpecifications(Collection<Specification> specifications) {
        this.specifications = specifications;
    }

    @Override
    public String toString() {
        ToStringHelper help = Objects.toStringHelper(this).add("Intent", intents);
        help.add("Specifications", specifications);
        return help.toString();
    }
}
