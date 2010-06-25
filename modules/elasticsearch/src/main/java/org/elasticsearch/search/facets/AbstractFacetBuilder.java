/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
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

package org.elasticsearch.search.facets;

import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.query.xcontent.XContentFilterBuilder;

/**
 * @author kimchy (shay.banon)
 */
public abstract class AbstractFacetBuilder implements ToXContent {

    protected final String name;

    protected Boolean global;

    protected XContentFilterBuilder filter;

    protected AbstractFacetBuilder(String name) {
        this.name = name;
    }

    public AbstractFacetBuilder filter(XContentFilterBuilder filter) {
        this.filter = filter;
        return this;
    }

    public AbstractFacetBuilder global(boolean global) {
        this.global = global;
        return this;
    }
}
