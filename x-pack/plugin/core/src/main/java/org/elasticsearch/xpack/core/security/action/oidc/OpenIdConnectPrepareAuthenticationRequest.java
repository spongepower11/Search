/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.action.oidc;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;

import static org.elasticsearch.action.ValidateActions.addValidationError;

/**
 * Represents a request to prepare an OAuth 2.0 authorization request
 */
public class OpenIdConnectPrepareAuthenticationRequest extends ActionRequest {

    /**
     * The name of the OpenID Connect realm in the configuration that should be used for authentication
     */
    private String realmName;
    /**
     * In case of a
     * <a href="https://openid.net/specs/openid-connect-core-1_0.html#ThirdPartyInitiatedLogin">3rd party initiated authentication</a>, the
     * issuer to the UA needs to be redirected for authentication
     */
    private String issuer;

    public String getRealmName() {
        return realmName;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public OpenIdConnectPrepareAuthenticationRequest() {
    }

    public OpenIdConnectPrepareAuthenticationRequest(StreamInput in) throws IOException {
        super.readFrom(in);
        realmName = in.readOptionalString();
        issuer = in.readOptionalString();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (Strings.hasText(realmName) == false && Strings.hasText(issuer) == false) {
            validationException = addValidationError("one of [realm, issuer] must be provided", null);
        }
        if (Strings.hasText(realmName) && Strings.hasText(issuer)) {
            validationException = addValidationError("only one of [realm, issuer] can be provided in the same request", null);
        }
        return validationException;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(realmName);
        out.writeOptionalString(issuer);
    }

    @Override
    public void readFrom(StreamInput in) {
        throw new UnsupportedOperationException("usage of Streamable is to be replaced by Writeable");
    }

    public String toString() {
        return "{realmName=" + realmName + ", issuer=" + issuer + "}";
    }

}
