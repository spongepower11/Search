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

package org.elasticsearch.plugin.indexbysearch;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestModule;

public class IndexBySearchPlugin extends Plugin {
    public static final String NAME = "index-by-search";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "The Index By Search plugin allows to index documents in Elasticsearch based on a search.";
    }

    public void onModule(ActionModule actionModule) {
        actionModule.registerAction(IndexBySearchAction.INSTANCE, TransportIndexBySearchAction.class);
        actionModule.registerAction(ReindexInPlaceAction.INSTANCE, TransportReindexInPlaceAction.class);
    }

    public void onModule(RestModule restModule) {
        restModule.addRestAction(RestIndexBySearchAction.class);
        restModule.addRestAction(RestReindexInPlaceAction.class);
    }
}
