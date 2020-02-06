/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.idp.saml.authn;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.xpack.idp.authc.AuthenticationMethod;
import org.elasticsearch.xpack.idp.authc.NetworkControl;
import org.elasticsearch.xpack.idp.saml.idp.SamlIdentityProvider;
import org.elasticsearch.xpack.idp.saml.sp.SamlServiceProvider;
import org.elasticsearch.xpack.idp.saml.support.SamlUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;

import javax.xml.namespace.QName;
import java.time.Clock;
import java.util.Collection;
import java.util.Set;

/**
 * Builds SAML 2.0 {@link Response} objects for successful authentication results.
 */
public class SuccessfulAuthenticationResponseMessageBuilder {

    private final Clock clock;
    private final SamlIdentityProvider idp;
    private final XMLObjectBuilderFactory builderFactory;

    public SuccessfulAuthenticationResponseMessageBuilder(Clock clock, SamlIdentityProvider idp) {
        SamlUtils.initialize();
        this.clock = clock;
        this.idp = idp;
        this.builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
    }

    public Response build(UserServiceAuthentication user, @Nullable AuthnRequest request) {
        final DateTime now = now();
        final SamlServiceProvider serviceProvider = user.getServiceProvider();

        final Response response = object(Response.class, Response.DEFAULT_ELEMENT_NAME);
        response.setID(SamlUtils.secureIdentifier());
        if (request != null) {
            response.setInResponseTo(request.getID());
        }
        response.setIssuer(buildIssuer());
        response.setIssueInstant(now);
        response.setStatus(buildStatus());
        response.setDestination(serviceProvider.getAssertionConsumerService());

        final Assertion assertion = object(Assertion.class, Assertion.DEFAULT_ELEMENT_NAME);
        assertion.setID(SamlUtils.secureIdentifier());
        assertion.setIssuer(buildIssuer());
        assertion.setIssueInstant(now);
        assertion.setConditions(buildConditions(now, serviceProvider));
        assertion.setSubject(buildSubject(now, user, request));
        assertion.getAuthnStatements().add(buildAuthnStatement(now, user));
        final AttributeStatement attributes = buildAttributes(user);
        if (attributes != null) {
            assertion.getAttributeStatements().add(attributes);
        }
        response.getAssertions().add(assertion);

        return sign(response);
    }

    private Response sign(Response response) {
        // TODO
        return response;
    }

    private Conditions buildConditions(DateTime now, SamlServiceProvider serviceProvider) {
        final Audience spAudience = object(Audience.class, Audience.DEFAULT_ELEMENT_NAME);
        spAudience.setAudienceURI(serviceProvider.getEntityId());

        final AudienceRestriction restriction = object(AudienceRestriction.class, AudienceRestriction.DEFAULT_ELEMENT_NAME);
        restriction.getAudiences().add(spAudience);

        final Conditions conditions = object(Conditions.class, Conditions.DEFAULT_ELEMENT_NAME);
        conditions.setNotBefore(now);
        conditions.setNotOnOrAfter(now.plus(serviceProvider.getAuthnExpiry()));
        conditions.getAudienceRestrictions().add(restriction);
        return conditions;
    }

    private DateTime now() {
        return new DateTime(clock.millis(), DateTimeZone.UTC);
    }

    private Subject buildSubject(DateTime now, UserServiceAuthentication user, AuthnRequest request) {
        final SamlServiceProvider serviceProvider = user.getServiceProvider();

        final NameID nameID = object(NameID.class, NameID.DEFAULT_ELEMENT_NAME);
        nameID.setFormat(NameIDType.PERSISTENT);
        nameID.setValue(user.getPrincipal());

        final Subject subject = object(Subject.class, Subject.DEFAULT_ELEMENT_NAME);
        subject.setNameID(nameID);

        final SubjectConfirmationData data = object(SubjectConfirmationData.class,
            SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
        if (request != null) {
            data.setInResponseTo(request.getID());
        }
        data.setNotBefore(now);
        data.setNotOnOrAfter(now.plus(serviceProvider.getAuthnExpiry()));
        data.setRecipient(serviceProvider.getAssertionConsumerService());

        final SubjectConfirmation confirmation = object(SubjectConfirmation.class, SubjectConfirmation.DEFAULT_ELEMENT_NAME);
        confirmation.setMethod(SubjectConfirmation.METHOD_BEARER);
        confirmation.setSubjectConfirmationData(data);

        subject.getSubjectConfirmations().add(confirmation);
        return subject;
    }

    private AuthnStatement buildAuthnStatement(DateTime now, UserServiceAuthentication user) {
        final SamlServiceProvider serviceProvider = user.getServiceProvider();
        final AuthnStatement statement = object(AuthnStatement.class, AuthnStatement.DEFAULT_ELEMENT_NAME);
        statement.setAuthnInstant(now);
        statement.setSessionNotOnOrAfter(now.plus(serviceProvider.getAuthnExpiry()));

        final AuthnContext context = object(AuthnContext.class, AuthnContext.DEFAULT_ELEMENT_NAME);
        final AuthnContextClassRef classRef = object(AuthnContextClassRef.class, AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
        classRef.setAuthnContextClassRef(resolveAuthnClass(user.getAuthenticationMethods(), user.getNetworkControls()));
        context.setAuthnContextClassRef(classRef);
        statement.setAuthnContext(context);

        return statement;
    }

    private String resolveAuthnClass(Set<AuthenticationMethod> authenticationMethods, Set<NetworkControl> networkControls) {
        if (authenticationMethods.contains(AuthenticationMethod.PASSWORD)) {
            if (networkControls.contains(NetworkControl.IP_FILTER)) {
                return AuthnContext.IP_PASSWORD_AUTHN_CTX;
            } else if (networkControls.contains(NetworkControl.TLS)) {
                return AuthnContext.PPT_AUTHN_CTX;
            } else {
                return AuthnContext.PASSWORD_AUTHN_CTX;
            }
        } else if (authenticationMethods.contains(AuthenticationMethod.KERBEROS)) {
            return AuthnContext.KERBEROS_AUTHN_CTX;
        } else if (authenticationMethods.contains(AuthenticationMethod.TLS_CLIENT_AUTH) && networkControls.contains(NetworkControl.TLS)) {
            return AuthnContext.TLS_CLIENT_AUTHN_CTX;
        } else if (authenticationMethods.contains(AuthenticationMethod.PRIOR_SESSION)) {
            return AuthnContext.PREVIOUS_SESSION_AUTHN_CTX;
        } else if (networkControls.contains(NetworkControl.IP_FILTER)) {
            return AuthnContext.IP_AUTHN_CTX;
        } else {
            return AuthnContext.UNSPECIFIED_AUTHN_CTX;
        }
    }

    private AttributeStatement buildAttributes(UserServiceAuthentication user) {
        final SamlServiceProvider serviceProvider = user.getServiceProvider();
        final AttributeStatement statement = object(AttributeStatement.class, AttributeStatement.DEFAULT_ELEMENT_NAME);
        final Attribute groups = buildAttribute(serviceProvider.getAttributeNames().groups, "groups", user.getGroups());
        if (groups != null) {
            statement.getAttributes().add(groups);
        }
        if (statement.getAttributes().isEmpty()) {
            return null;
        }
        return statement;
    }

    private Attribute buildAttribute(String formalName, String friendlyName, Collection<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        final Attribute attribute = object(Attribute.class, Attribute.DEFAULT_ELEMENT_NAME);
        attribute.setName(formalName);
        attribute.setFriendlyName(friendlyName);
        attribute.setNameFormat(Attribute.URI_REFERENCE);
        for (String val : values) {
            final XSString string = object(XSString.class, AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
            string.setValue(val);
            attribute.getAttributeValues().add(string);
        }
        return attribute;
    }

    private Issuer buildIssuer() {
        final Issuer issuer = object(Issuer.class, Issuer.DEFAULT_ELEMENT_NAME);
        issuer.setValue(this.idp.getEntityId());
        return issuer;
    }

    private Status buildStatus() {
        final StatusCode code = object(StatusCode.class, StatusCode.DEFAULT_ELEMENT_NAME);
        code.setValue(StatusCode.SUCCESS);

        final Status status = object(Status.class, Status.DEFAULT_ELEMENT_NAME);
        status.setStatusCode(code);

        return status;
    }

    public <T extends XMLObject> T object(Class<T> type, QName elementName) {
        final XMLObject obj = builderFactory.getBuilder(elementName).buildObject(elementName);
        return cast(type, elementName, obj);
    }

    public <T extends XMLObject> T object(Class<T> type, QName elementName, QName schemaType) {
        final XMLObject obj = builderFactory.getBuilder(schemaType).buildObject(elementName, schemaType);
        return cast(type, elementName, obj);
    }

    private <T extends XMLObject> T cast(Class<T> type, QName elementName, XMLObject obj) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        } else {
            throw new IllegalArgumentException("Object for element " + elementName.getLocalPart() + " is of type " + obj.getClass()
                + " not " + type);
        }
    }
}
