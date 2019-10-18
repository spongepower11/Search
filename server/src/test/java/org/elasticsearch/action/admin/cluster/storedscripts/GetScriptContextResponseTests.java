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
package org.elasticsearch.action.admin.cluster.storedscripts;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.test.AbstractSerializingTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiLettersOfLengthBetween;

public class GetScriptContextResponseTests extends AbstractSerializingTestCase<GetScriptContextResponse> {

    @Override
    protected GetScriptContextResponse createTestInstance() {
        if (randomBoolean()) {
            return new GetScriptContextResponse(Collections.emptyMap());
        }
        Map<String,Object> items = new HashMap<>();
        for (int i = randomIntBetween(1, 10); i > 0; i--) {
            items.put(randomAsciiLettersOfLengthBetween(1, 16), new Object());
        }
        return new GetScriptContextResponse(items);

    }

    @Override
    protected Writeable.Reader<GetScriptContextResponse> instanceReader() {
        return GetScriptContextResponse::new;
    }

    @Override
    protected GetScriptContextResponse doParseInstance(XContentParser parser) throws IOException {
        return GetScriptContextResponse.fromXContent(parser);
    }

    @Override
    protected GetScriptContextResponse mutateInstance(GetScriptContextResponse instance) throws IOException {
        Map<String,Object> items = new HashMap<>();
        for (int i = randomIntBetween(1, 10); i > 0; i--) {
            items.put(randomAsciiLettersOfLengthBetween(1, 16), new Object());
        }
        return new GetScriptContextResponse(items);
    }
}
