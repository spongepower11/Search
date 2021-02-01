/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.gradle.test.rest.transform;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A single instruction to transforms a REST test.
 */
public interface RestTestTransform<T extends JsonNode> {

    /**
     * Transform the Json structure per the given {@link RestTestTransform}
     * Implementations are expected to mutate the node (and/or it's parent) to satisfy the transformation.
     *
     * @param node The node to transform. This may also be the logical parent of the node that should be transformed.
     */
    void transformTest(T node);

    /**
     * @return The test name to to apply this transformation to. if {@code null} will apply to any test irregardless of the name.
     */
    default String getTestName(){
        return null;
    }
}
