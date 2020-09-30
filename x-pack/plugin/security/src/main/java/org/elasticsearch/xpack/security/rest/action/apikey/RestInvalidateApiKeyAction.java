/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.security.rest.action.apikey;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestBuilderListener;
import org.elasticsearch.xpack.core.security.action.InvalidateApiKeyAction;
import org.elasticsearch.xpack.core.security.action.InvalidateApiKeyRequest;
import org.elasticsearch.xpack.core.security.action.InvalidateApiKeyResponse;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.DELETE;

/**
 * Rest action to invalidate one or more API keys
 */
public final class RestInvalidateApiKeyAction extends ApiKeyBaseRestHandler {
    private static final DeprecationLogger deprecationLogger = DeprecationLogger.getLogger(RestInvalidateApiKeyAction.class);

    static final ConstructingObjectParser<InvalidateApiKeyRequest, Void> PARSER = new ConstructingObjectParser<>("invalidate_api_key",
            a -> {
                return new InvalidateApiKeyRequest((String) a[0], (String) a[1], (String) a[2], (String) a[3],
                    (a[4] == null) ? false : (Boolean) a[4],
                    (a[5] == null) ? null : ((Collection<String>) a[5]).toArray(String[]::new));
            });

    static {
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), new ParseField("realm_name"));
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), new ParseField("username"));
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), new ParseField("id"));
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), new ParseField("name"));
        PARSER.declareBoolean(ConstructingObjectParser.optionalConstructorArg(), new ParseField("owner"));
        PARSER.declareStringArray(ConstructingObjectParser.optionalConstructorArg(), new ParseField("ids"));
    }

    public RestInvalidateApiKeyAction(Settings settings, XPackLicenseState licenseState) {
        super(settings, licenseState);
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(DELETE, "/_security/api_key"));
    }

    @Override
    protected RestChannelConsumer innerPrepareRequest(RestRequest request, NodeClient client) throws IOException {
        try (XContentParser parser = request.contentParser()) {
            final InvalidateApiKeyRequest invalidateApiKeyRequest = PARSER.parse(parser, null);
            if (invalidateApiKeyRequest.getId() != null) {
                deprecationLogger.deprecate("invalidate_api_key.id",
                    "The id parameter is deprecated, use ids instead");
            }

            return channel -> client.execute(InvalidateApiKeyAction.INSTANCE, invalidateApiKeyRequest,
                new RestBuilderListener<InvalidateApiKeyResponse>(channel) {
                    @Override
                    public RestResponse buildResponse(InvalidateApiKeyResponse invalidateResp,
                                                      XContentBuilder builder) throws Exception {
                        invalidateResp.toXContent(builder, channel.request());
                        return new BytesRestResponse(RestStatus.OK, builder);
                    }
                });
        }
    }

    @Override
    public String getName() {
        return "xpack_security_invalidate_api_key";
    }

}
