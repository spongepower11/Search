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

import java.util.function.Function;

/**
 * Simple Engine Factory
 */
public interface EngineFactory {
    /**
     * Creates a new engine with the given engine config
     */
    Engine newEngine(EngineConfig config);

    /**
     * Returns true if this engine factory creates only read-only engine (i.e, instances of {@link ReadOnlyEngine}).
     */
    boolean isReadOnlyEngineFactory();

    static EngineFactory newReadWriteEngineFactory(Function<EngineConfig, ? extends InternalEngine> factoryFn) {
        return new EngineFactory() {
            @Override
            public InternalEngine newEngine(EngineConfig config) {
                return factoryFn.apply(config);
            }
            @Override
            public boolean isReadOnlyEngineFactory() {
                return false;
            }
        };
    }

    static EngineFactory newReadOnlyEngineFactory(Function<EngineConfig, ? extends ReadOnlyEngine> factoryFn) {
        return new EngineFactory() {
            @Override
            public ReadOnlyEngine newEngine(EngineConfig config) {
                return factoryFn.apply(config);
            }
            @Override
            public boolean isReadOnlyEngineFactory() {
                return true;
            }
        };
    }
}
