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

package org.elasticsearch.index.store;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.store.distributor.Distributor;
import org.elasticsearch.index.store.distributor.LeastUsedDistributor;
import org.elasticsearch.index.store.distributor.RandomWeightedDistributor;
import org.elasticsearch.jmx.JmxService;

/**
 *
 */
public class StoreModule extends AbstractModule {

    private final Settings settings;

    private final IndexStore indexStore;

    private Class<? extends Distributor> distributor;

    public StoreModule(Settings settings, IndexStore indexStore) {
        this.indexStore = indexStore;
        this.settings = settings;
    }

    public void setDistributor(Class<? extends Distributor> distributor) {
        this.distributor = distributor;
    }

    @Override
    protected void configure() {
        bind(DirectoryService.class).to(indexStore.shardDirectory()).asEagerSingleton();
        bind(Store.class).asEagerSingleton();
        if (JmxService.shouldExport(settings)) {
            bind(StoreManagement.class).asEagerSingleton();
        }
        if (distributor == null) {
            distributor = loadDistributor(settings);
        }
        bind(Distributor.class).to(distributor).asEagerSingleton();
    }

    private Class<? extends Distributor> loadDistributor(Settings settings) {
        final Class<? extends Distributor> distributor;
        final String type = settings.get("index.store.distributor");
        if ("least_used".equals(type)) {
            distributor = LeastUsedDistributor.class;
        } else if ("random".equals(type)) {
            distributor = RandomWeightedDistributor.class;
        } else {
            distributor = settings.getAsClass("index.store.distributor", LeastUsedDistributor.class,
                    "org.elasticsearch.index.store.distributor.", "Distributor");
        }
        return distributor;
    }

}
