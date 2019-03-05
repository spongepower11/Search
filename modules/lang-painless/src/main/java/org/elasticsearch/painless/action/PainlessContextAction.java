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

package org.elasticsearch.painless.action;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.painless.PainlessScriptEngine;
import org.elasticsearch.painless.lookup.PainlessClass;
import org.elasticsearch.painless.lookup.PainlessClassBinding;
import org.elasticsearch.painless.lookup.PainlessConstructor;
import org.elasticsearch.painless.lookup.PainlessField;
import org.elasticsearch.painless.lookup.PainlessInstanceBinding;
import org.elasticsearch.painless.lookup.PainlessLookup;
import org.elasticsearch.painless.lookup.PainlessLookupUtility;
import org.elasticsearch.painless.lookup.PainlessMethod;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.GET;

/**
 * Internal REST API for querying context information about Painless whitelists.
 * Commands include the following:
 * <ul>
 *     <li> GET /_scripts/painless/_context -- retrieves a list of contexts </li>
 *     <li> GET /_scripts/painless/_context?context=%name% --
 *     retrieves all available information about the API for this specific context</li>
 * </ul>
 */
public class PainlessContextAction extends Action<PainlessContextAction.Response> {

    public static final PainlessContextAction INSTANCE = new PainlessContextAction();
    private static final String NAME = "cluster:admin/scripts/painless/context";

    private static final String SCRIPT_CONTEXT_NAME_PARAM = "context";

    private PainlessContextAction() {
        super(NAME);
    }

    @Override
    public Response newResponse() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Writeable.Reader<Response> getResponseReader() {
        return Response::new;
    }

    public static class Request extends ActionRequest {

        private String scriptContextName;

        public Request() {
            scriptContextName = null;
        }

        public void setScriptContextName(String scriptContextName) {
            this.scriptContextName = scriptContextName;
        }

        public String getScriptContextName() {
            return scriptContextName;
        }

        @Override
        public ActionRequestValidationException validate() {
            return null;
        }
    }

    public static class Response extends ActionResponse implements ToXContentObject {

        private final PainlessScriptEngine painlessScriptEngine;
        private final String scriptContextName;

        public Response(PainlessScriptEngine painlessScriptEngine, String scriptContextName) {
            this.painlessScriptEngine = painlessScriptEngine;
            this.scriptContextName = scriptContextName;
        }

        public Response(StreamInput input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            if (scriptContextName == null) {
                builder.startObject();
                builder.startArray("contexts");

                for (ScriptContext<?> scriptContext : painlessScriptEngine.getContextsToLookups().keySet()) {
                    builder.value(scriptContext.name);
                }

                builder.endArray();
                builder.endObject();
            } else {
                ScriptContext<?> scriptContext = null;
                PainlessLookup painlessLookup = null;

                for (Map.Entry<ScriptContext<?>, PainlessLookup> contextLookupEntry :
                        painlessScriptEngine.getContextsToLookups().entrySet()) {
                    if (contextLookupEntry.getKey().name.equals(scriptContextName)) {
                        scriptContext = contextLookupEntry.getKey();
                        painlessLookup = contextLookupEntry.getValue();
                        break;
                    }
                }

                new PainlessContextInfo(scriptContext, painlessLookup).toXContent(builder, params);
            }

            return builder;
        }
    }

    public static class TransportAction extends HandledTransportAction<Request, Response> {

        PainlessScriptEngine painlessScriptEngine;

        @Inject
        public TransportAction(TransportService transportService, ActionFilters actionFilters, PainlessScriptEngine painlessScriptEngine) {
            super(NAME, transportService, actionFilters, Request::new);
            this.painlessScriptEngine = painlessScriptEngine;
        }

        @Override
        protected void doExecute(Task task, Request request, ActionListener<Response> listener) {
            listener.onResponse(new Response(painlessScriptEngine, request.getScriptContextName()));
        }
    }

    public static class RestAction extends BaseRestHandler {

        public RestAction(Settings settings, RestController controller) {
            super(settings);
            controller.registerHandler(GET, "/_scripts/painless/_context", this);
        }

        @Override
        public String getName() {
            return "_scripts_painless_context";
        }

        @Override
        protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) {
            Request request = new Request();
            request.setScriptContextName(restRequest.param(SCRIPT_CONTEXT_NAME_PARAM));
            return channel -> client.executeLocally(INSTANCE, request, new RestToXContentListener<>(channel));
        }
    }
}
