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

package org.elasticsearch.rest.action.admin.indices;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.rest.FakeRestRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.rest.BaseRestHandler.INCLUDE_TYPE_NAME_PARAMETER;
import static org.mockito.Mockito.mock;

public class RestGetIndicesActionTests extends ESTestCase {

    /**
     * Test that setting no "include_type_name" or setting it to "true" raises a warning
     */
    public void testIncludeTypeNamesWarning() throws IOException {
        Map<String, String> params = new HashMap<>();
        if (randomBoolean()) {
            params.put(INCLUDE_TYPE_NAME_PARAMETER, "true");
        }
        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.GET)
            .withPath("/some_index")
            .withParams(params)
            .build();

        RestGetIndicesAction handler = new RestGetIndicesAction(Settings.EMPTY, mock(RestController.class));
        handler.prepareRequest(request, mock(NodeClient.class));
        assertWarnings(RestGetIndicesAction.TYPES_DEPRECATION_MESSAGE);

        // the same request with the parameter set to "false" should pass without warning
        request = new FakeRestRequest.Builder(xContentRegistry())
                .withMethod(RestRequest.Method.GET)
                .withParams(Collections.singletonMap(INCLUDE_TYPE_NAME_PARAMETER, "false"))
                .withPath("/some_index")
                .build();
        handler.prepareRequest(request, mock(NodeClient.class));
    }
}
