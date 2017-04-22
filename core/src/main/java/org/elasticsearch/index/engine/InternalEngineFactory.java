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

package org.elasticsearch.index.engine;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.index.engine.phantom.PhantomEngineFactory;

public class InternalEngineFactory implements EngineFactory {

    private final PhantomEngineFactory factory;

    @Inject
    public InternalEngineFactory(PhantomEngineFactory factory) {
        this.factory = factory;
    }

    @Override
    public Engine newReadWriteEngine(EngineConfig config, boolean skipTranslogRecovery) {
        return new InternalEngine(config, skipTranslogRecovery);
    }

    @Override
    public Engine newReadOnlyEngine(EngineConfig config) {
        return new ShadowEngine(config);
    }

    @Override
    public Engine newPhantomEngine(EngineConfig config) {
        return factory.create(config);
    }
}
