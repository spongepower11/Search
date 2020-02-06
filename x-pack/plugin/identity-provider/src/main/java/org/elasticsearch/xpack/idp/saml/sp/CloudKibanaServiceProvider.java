/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.idp.saml.sp;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.joda.time.Duration;
import org.joda.time.ReadableDuration;
import org.opensaml.security.x509.X509Credential;


public class CloudKibanaServiceProvider implements SamlServiceProvider {

    private final String entityid;
    private final String assertionConsumerService;
    private final ReadableDuration authnExpiry;
    private final String nameIdPolicyFormat;
    private final X509Credential signingCredential;

    public CloudKibanaServiceProvider(String entityId, String assertionConsumerService, String nameIdPolicyFormat,
                                      @Nullable X509Credential signingCredential) {
        if (Strings.isNullOrEmpty(entityId)) {
            throw new IllegalArgumentException("Service Provider Entity ID cannot be null or empty");
        }
        this.entityid = entityId;
        this.assertionConsumerService = assertionConsumerService;
        this.nameIdPolicyFormat = nameIdPolicyFormat;
        this.authnExpiry = Duration.standardMinutes(5);
        this.signingCredential = signingCredential;

    }

    @Override
    public String getEntityId() {
        return entityid;
    }

    @Override
    public String getNameIDPolicyFormat() {
        return nameIdPolicyFormat;
    }

    @Override
    public String getAssertionConsumerService() {
        return assertionConsumerService;
    }

    @Override
    public ReadableDuration getAuthnExpiry() {
        return authnExpiry;
    }

    @Override
    public AttributeNames getAttributeNames() {
        return new SamlServiceProvider.AttributeNames();
    }

    @Override
    public X509Credential getSigningCredential() {
        return signingCredential;
    }
}
