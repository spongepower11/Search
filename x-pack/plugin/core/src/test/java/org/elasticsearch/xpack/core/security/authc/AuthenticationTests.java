/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.security.authc;

import org.elasticsearch.Version;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.core.security.action.service.TokenInfo;
import org.elasticsearch.xpack.core.security.authc.Authentication.AuthenticationType;
import org.elasticsearch.xpack.core.security.authc.Authentication.RealmRef;
import org.elasticsearch.xpack.core.security.authc.esnative.NativeRealmSettings;
import org.elasticsearch.xpack.core.security.authc.file.FileRealmSettings;
import org.elasticsearch.xpack.core.security.authc.service.ServiceAccountSettings;
import org.elasticsearch.xpack.core.security.user.AnonymousUser;
import org.elasticsearch.xpack.core.security.user.AsyncSearchUser;
import org.elasticsearch.xpack.core.security.user.SystemUser;
import org.elasticsearch.xpack.core.security.user.User;
import org.elasticsearch.xpack.core.security.user.XPackSecurityUser;
import org.elasticsearch.xpack.core.security.user.XPackUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.elasticsearch.xpack.core.security.authc.AuthenticationField.ANONYMOUS_REALM_NAME;
import static org.elasticsearch.xpack.core.security.authc.AuthenticationField.ANONYMOUS_REALM_TYPE;
import static org.elasticsearch.xpack.core.security.authc.AuthenticationField.ATTACH_REALM_NAME;
import static org.elasticsearch.xpack.core.security.authc.AuthenticationField.ATTACH_REALM_TYPE;
import static org.elasticsearch.xpack.core.security.authc.AuthenticationField.FALLBACK_REALM_NAME;
import static org.elasticsearch.xpack.core.security.authc.AuthenticationField.FALLBACK_REALM_TYPE;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class AuthenticationTests extends ESTestCase {

    public void testWillGetLookedUpByWhenItExists() {
        final RealmRef authenticatedBy = new RealmRef("auth_by", "auth_by_type", "node");
        final RealmRef lookedUpBy = new RealmRef("lookup_by", "lookup_by_type", "node");
        final Authentication authentication = new Authentication(new User("user"), authenticatedBy, lookedUpBy);

        assertEquals(lookedUpBy, authentication.getSourceRealm());
    }

    public void testWillGetAuthenticateByWhenLookupIsNull() {
        final RealmRef authenticatedBy = new RealmRef("auth_by", "auth_by_type", "node");
        final Authentication authentication = new Authentication(new User("user"), authenticatedBy, null);

        assertEquals(authenticatedBy, authentication.getSourceRealm());
    }

    public void testCanAccessResourcesOf() {
        // Same user is the same
        final User user1 = randomUser();
        final RealmRef realm1 = randomRealmRef(false);
        assertCanAccessResources(randomAuthentication(user1, realm1), randomAuthentication(user1, realm1));

        // Different username is different no matter which realm it is from
        final User user2 = randomValueOtherThanMany(u -> u.principal().equals(user1.principal()), AuthenticationTests::randomUser);
        // user 2 can be from either the same realm or a different realm
        final RealmRef realm2 = randomFrom(realm1, randomRealm());
        assertCannotAccessResources(randomAuthentication(user1, realm2), randomAuthentication(user2, realm2));

        // Same username but different realm is different
        final RealmRef realm3;
        switch (randomIntBetween(0, 2)) {
            case 0: // change name
                realm3 = mutateRealm(realm1, randomAlphaOfLengthBetween(3, 8), null);
                if (realmIsSingleton(realm1)) {
                    assertCanAccessResources(randomAuthentication(user1, realm1), randomAuthentication(user1, realm3));
                } else {
                    assertCannotAccessResources(randomAuthentication(user1, realm1), randomAuthentication(user1, realm3));
                }
                break;
            case 1: // change type
                realm3 = mutateRealm(realm1, null, randomAlphaOfLengthBetween(3, 8));
                assertCannotAccessResources(randomAuthentication(user1, realm1), randomAuthentication(user1, realm3));
                break;
            case 2: // both
                realm3 = mutateRealm(realm1, randomAlphaOfLengthBetween(3, 8), randomAlphaOfLengthBetween(3, 8));
                assertCannotAccessResources(randomAuthentication(user1, realm1), randomAuthentication(user1, realm3));
                break;
            default:
                assert false : "Case number out of range";
        }

        // User and its API key are not the same owner
        assertCannotAccessResources(
            randomAuthentication(user1, realm1),
            randomApiKeyAuthentication(user1, randomAlphaOfLengthBetween(10, 20))
        );

        // Same API key ID are the same owner
        final String apiKeyId1 = randomAlphaOfLengthBetween(10, 20);
        assertCanAccessResources(randomApiKeyAuthentication(user1, apiKeyId1), randomApiKeyAuthentication(user1, apiKeyId1));

        // Two API keys (2 API key IDs) are not the same owner
        final String apiKeyId2 = randomValueOtherThan(apiKeyId1, () -> randomAlphaOfLengthBetween(10, 20));
        assertCannotAccessResources(
            randomApiKeyAuthentication(randomFrom(user1, user2), apiKeyId1),
            randomApiKeyAuthentication(randomFrom(user1, user2), apiKeyId2)
        );

        final User user3 = randomValueOtherThanMany(
            u -> u.principal().equals(user1.principal()) || u.principal().equals(user2.principal()),
            AuthenticationTests::randomUser
        );

        // Same API key but run-as different user are not the same owner
        assertCannotAccessResources(
            randomApiKeyAuthentication(new User(user2, user1), apiKeyId1),
            randomApiKeyAuthentication(new User(user3, user1), apiKeyId1)
        );

        // Same or different API key run-as the same user are the same owner
        assertCanAccessResources(
            randomApiKeyAuthentication(user1, apiKeyId1).runAs(user3, realm2),
            randomApiKeyAuthentication(user1, apiKeyId1).runAs(user3, realm2)
        );
        assertCanAccessResources(
            randomApiKeyAuthentication(user1, apiKeyId1).runAs(user3, realm2),
            randomApiKeyAuthentication(user2, apiKeyId2).runAs(user3, realm2)
        );
    }

    public void testTokenAccessResourceOf() {
        final User user = randomUser();
        final RealmRef realmRef = randomRealmRef(false);
        Authentication original = Authentication.newRealmAuthentication(user, realmRef);
        Authentication token = original.token();
        assertCanAccessResources(original, token);
        assertCanAccessResources(original, token.token());
        assertCanAccessResources(token, token.token());

        Authentication original2 = randomApiKeyAuthentication(user, randomAlphaOfLengthBetween(10, 20));
        Authentication token2 = original2.token();
        assertCanAccessResources(original2, token2);
        assertCanAccessResources(original2, token2.token());
        assertCanAccessResources(token2, token2.token());

        assertCannotAccessResources(original, original2);
        assertCannotAccessResources(original, token2);
        assertCannotAccessResources(token, original2);
        assertCannotAccessResources(token, token2);
        assertCannotAccessResources(token.token(), token2);
        assertCannotAccessResources(token, token2.token());

        Authentication original3 = Authentication.newAnonymousAuthentication(
            new AnonymousUser(Settings.EMPTY),
            randomAlphaOfLengthBetween(3, 8)
        );
        Authentication token3 = original3.token();
        assertCanAccessResources(original3, token3);
        assertCanAccessResources(original3, token3.token());
        assertCanAccessResources(token3, token3.token());
    }

    public void testRunAsAccessResourceOf() {
        final User user = randomUser();
        final User otherUser = randomValueOtherThan(user, () -> randomUser());
        final RealmRef realmRef = randomRealmRef(false);
        final RealmRef otherRealmRef = randomValueOtherThan(realmRef, () -> randomRealmRef(false));
        Authentication original = Authentication.newRealmAuthentication(user, realmRef);

        // can
        Authentication runAs1 = Authentication.newRealmAuthentication(otherUser, otherRealmRef).runAs(user, realmRef);
        assertCanAccessResources(original, runAs1);
        assertCanAccessResources(original.token(), runAs1);
        assertCanAccessResources(original, runAs1.token());
        assertCanAccessResources(original.token(), runAs1.token());

        Authentication runAs2 = randomApiKeyAuthentication(otherUser, randomAlphaOfLength(8)).runAs(user, realmRef);
        assertCanAccessResources(original, runAs2);
        assertCanAccessResources(original.token(), runAs2);
        assertCanAccessResources(original, runAs2.token());
        assertCanAccessResources(original.token(), runAs2.token());

        assertCanAccessResources(runAs1, runAs2);
        assertCanAccessResources(runAs1.token(), runAs2);
        assertCanAccessResources(runAs1, runAs2.token());
        assertCanAccessResources(runAs1.token(), runAs2.token());

        // cannot
        Authentication runAs3 = original.runAs(otherUser, realmRef);
        assertCannotAccessResources(original, runAs3);
        assertCannotAccessResources(original.token(), runAs3);
        assertCannotAccessResources(original, runAs3.token());
        assertCannotAccessResources(original.token(), runAs3.token());

        Authentication runAs4 = original.runAs(user, otherRealmRef);
        if (FileRealmSettings.TYPE.equals(realmRef.getType()) && FileRealmSettings.TYPE.equals(otherRealmRef.getType())
            || NativeRealmSettings.TYPE.equals(realmRef.getType()) && NativeRealmSettings.TYPE.equals(otherRealmRef.getType())) {
            assertCanAccessResources(original, runAs4);
            assertCanAccessResources(original.token(), runAs4);
            assertCanAccessResources(original, runAs4.token());
            assertCanAccessResources(original.token(), runAs4.token());
        } else {
            assertCannotAccessResources(original, runAs4);
            assertCannotAccessResources(original.token(), runAs4);
            assertCannotAccessResources(original, runAs4.token());
            assertCannotAccessResources(original.token(), runAs4.token());
        }
    }

    public void testIsServiceAccount() {
        final User user = new User(
            randomAlphaOfLengthBetween(3, 8),
            randomArray(0, 3, String[]::new, () -> randomAlphaOfLengthBetween(3, 8))
        );
        final Authentication.RealmRef authRealm;
        final boolean authRealmIsForServiceAccount = randomBoolean();
        if (authRealmIsForServiceAccount) {
            authRealm = new Authentication.RealmRef(
                ServiceAccountSettings.REALM_NAME,
                ServiceAccountSettings.REALM_TYPE,
                randomAlphaOfLengthBetween(3, 8)
            );
        } else {
            authRealm = new Authentication.RealmRef(
                randomAlphaOfLengthBetween(3, 8),
                randomAlphaOfLengthBetween(3, 8),
                randomAlphaOfLengthBetween(3, 8)
            );
        }
        final Authentication.RealmRef lookupRealm = randomFrom(
            new Authentication.RealmRef(
                randomAlphaOfLengthBetween(3, 8),
                randomAlphaOfLengthBetween(3, 8),
                randomAlphaOfLengthBetween(3, 8)
            ),
            null
        );
        final Authentication authentication = new Authentication(user, authRealm, lookupRealm);

        if (authRealmIsForServiceAccount) {
            assertThat(authentication.isAuthenticatedWithServiceAccount(), is(true));
        } else {
            assertThat(authentication.isAuthenticatedWithServiceAccount(), is(false));
        }
    }

    public void testNonRealmAuthenticationsNoDomain() {
        final String apiKeyId = randomAlphaOfLengthBetween(10, 20);
        Authentication apiAuthentication = randomApiKeyAuthentication(randomUser(), apiKeyId);
        assertThat(apiAuthentication.isAssignedToDomain(), is(false));
        assertThat(apiAuthentication.getDomain(), nullValue());
        apiAuthentication = apiAuthentication.token();
        assertThat(apiAuthentication.isAssignedToDomain(), is(false));
        assertThat(apiAuthentication.getDomain(), nullValue());
        Authentication anonAuthentication = randomAnonymousAuthentication();
        assertThat(anonAuthentication.isAssignedToDomain(), is(false));
        assertThat(anonAuthentication.getDomain(), nullValue());
        Authentication serviceAccountAuthentication = randomServiceAccountAuthentication();
        assertThat(serviceAccountAuthentication.isAssignedToDomain(), is(false));
        assertThat(serviceAccountAuthentication.getDomain(), nullValue());
        Authentication internalAuthentication = randomInternalAuthentication();
        assertThat(internalAuthentication.isAssignedToDomain(), is(false));
        assertThat(internalAuthentication.getDomain(), nullValue());
    }

    public void testRealmAuthenticationIsAssignedToDomain() {
        Authentication realmAuthn = randomRealmAuthentication(true);
        assertThat(realmAuthn.isAssignedToDomain(), is(true));
        realmAuthn = realmAuthn.token();
        assertThat(realmAuthn.isAssignedToDomain(), is(true));
        realmAuthn = randomRealmAuthentication(false);
        assertThat(realmAuthn.isAssignedToDomain(), is(false));
        realmAuthn = realmAuthn.token();
        assertThat(realmAuthn.isAssignedToDomain(), is(false));
    }

    public void testRunAsAuthenticationWithDomain() {
        RealmRef authnRealmRef = randomRealmRef(true);
        RealmRef lookupRealmRef = randomRealmRef(true);
        // realm/token run-as
        Authentication test = Authentication.newRealmAuthentication(randomUser(), authnRealmRef);
        test = test.runAs(randomUser(), lookupRealmRef);
        if (randomBoolean()) {
            test = test.token();
        }
        assertThat(test.isAssignedToDomain(), is(true));
        assertThat(test.getDomain(), is(lookupRealmRef.getDomain()));
        test = Authentication.newRealmAuthentication(randomUser(), randomRealmRef(false));
        test = test.runAs(randomUser(), lookupRealmRef);
        if (randomBoolean()) {
            test = test.token();
        }
        assertThat(test.isAssignedToDomain(), is(true));
        assertThat(test.getDomain(), is(lookupRealmRef.getDomain()));
        test = Authentication.newRealmAuthentication(randomUser(), authnRealmRef);
        test = test.runAs(randomUser(), randomRealmRef(false));
        if (randomBoolean()) {
            test = test.token();
        }
        assertThat(test.isAssignedToDomain(), is(false));
        assertThat(test.getDomain(), nullValue());
        test = Authentication.newRealmAuthentication(randomUser(), randomRealmRef(false));
        test = test.runAs(randomUser(), lookupRealmRef);
        if (randomBoolean()) {
            test = test.token();
        }
        assertThat(test.isAssignedToDomain(), is(true));
        assertThat(test.getDomain(), is(lookupRealmRef.getDomain()));
        // api key run-as
        test = randomApiKeyAuthentication(randomUser(), randomAlphaOfLengthBetween(10, 20), Version.CURRENT);
        assertThat(test.isAssignedToDomain(), is(false));
        assertThat(test.getDomain(), nullValue());
        if (randomBoolean()) {
            test = test.runAs(randomUser(), lookupRealmRef);
            if (randomBoolean()) {
                test = test.token();
            }
            assertThat(test.isAssignedToDomain(), is(true));
            assertThat(test.getDomain(), is(lookupRealmRef.getDomain()));
        } else {
            test = test.runAs(randomUser(), randomRealmRef(false));
            if (randomBoolean()) {
                test = test.token();
            }
            assertThat(test.isAssignedToDomain(), is(false));
            assertThat(test.getDomain(), nullValue());
        }
        // service account cannot run-as
        test = randomServiceAccountAuthentication();
        assertThat(test.isAssignedToDomain(), is(false));
        assertThat(test.getDomain(), nullValue());
    }

    public void testDomainSerialize() throws Exception {
        Authentication test = randomRealmAuthentication(true);
        boolean runAs = randomBoolean();
        if (runAs) {
            test = test.runAs(randomUser(), randomRealmRef(true));
        }
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            test.writeTo(out);
            StreamInput in = out.bytes().streamInput();
            Authentication testBack = new Authentication(in);
            assertThat(test.getDomain(), is(testBack.getDomain()));
        }

        try (BytesStreamOutput out = new BytesStreamOutput()) {
            out.setVersion(Version.V_8_0_0);
            test.writeTo(out);
            StreamInput in = out.bytes().streamInput();
            in.setVersion(Version.V_8_0_0);
            Authentication testBack = new Authentication(in);
            assertThat(testBack.getDomain(), nullValue());
            assertThat(testBack.isAssignedToDomain(), is(false));
        }
    }

    private void assertCanAccessResources(Authentication authentication0, Authentication authentication1) {
        assertTrue(authentication0.canAccessResourcesOf(authentication1));
        assertTrue(authentication1.canAccessResourcesOf(authentication0));
    }

    public void testToXContentWithApiKey() throws IOException {
        final String apiKeyId = randomAlphaOfLength(20);
        final Authentication authentication1 = randomApiKeyAuthentication(randomUser(), apiKeyId);
        final String apiKeyName = (String) authentication1.getMetadata().get(AuthenticationField.API_KEY_NAME_KEY);
        runWithAuthenticationToXContent(
            authentication1,
            m -> assertThat(
                m,
                hasEntry("api_key", apiKeyName != null ? Map.of("id", apiKeyId, "name", apiKeyName) : Map.of("id", apiKeyId))
            )
        );

        final Authentication authentication2 = toRunAs(authentication1, randomUser(), randomRealm());
        runWithAuthenticationToXContent(authentication2, m -> assertThat(m, not(hasKey("api_key"))));
    }

    public void testToXContentWithServiceAccount() throws IOException {
        final Authentication authentication1 = randomServiceAccountAuthentication();
        final String tokenName = (String) authentication1.getMetadata().get(ServiceAccountSettings.TOKEN_NAME_FIELD);
        final String tokenType = ServiceAccountSettings.REALM_TYPE
            + "_"
            + authentication1.getMetadata().get(ServiceAccountSettings.TOKEN_SOURCE_FIELD);
        runWithAuthenticationToXContent(
            authentication1,
            m -> assertThat(m, hasEntry("token", Map.of("name", tokenName, "type", tokenType)))
        );
    }

    private void runWithAuthenticationToXContent(Authentication authentication, Consumer<Map<String, Object>> consumer) throws IOException {
        try (XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent())) {
            authentication.toXContent(builder, ToXContent.EMPTY_PARAMS);
            consumer.accept(XContentHelper.convertToMap(BytesReference.bytes(builder), false, XContentType.JSON).v2());
        }
    }

    private void assertCannotAccessResources(Authentication authentication0, Authentication authentication1) {
        assertFalse(authentication0.canAccessResourcesOf(authentication1));
        assertFalse(authentication1.canAccessResourcesOf(authentication0));
    }

    public static User randomUser() {
        return new User(randomAlphaOfLengthBetween(3, 8), randomArray(1, 3, String[]::new, () -> randomAlphaOfLengthBetween(3, 8)));
    }

    public static RealmRef randomRealm() {
        return new RealmRef(
            randomAlphaOfLengthBetween(3, 8),
            randomFrom(FileRealmSettings.TYPE, NativeRealmSettings.TYPE, randomAlphaOfLengthBetween(3, 8)),
            randomAlphaOfLengthBetween(3, 8)
        );
    }

    public static RealmRef randomRealmRef(boolean underDomain) {
        return randomRealmRef(underDomain, true);
    }

    public static RealmRef randomRealmRef(boolean underDomain, boolean includeSingletons) {
        final Supplier<String> randomAllRealmTypeSupplier = () -> randomFrom(
            FileRealmSettings.TYPE,
            NativeRealmSettings.TYPE,
            LdapRealmSettings.AD_TYPE,
            LdapRealmSettings.LDAP_TYPE,
            JwtRealmSettings.TYPE,
            OpenIdConnectRealmSettings.TYPE,
            SamlRealmSettings.TYPE,
            KerberosRealmSettings.TYPE,
            randomAlphaOfLengthBetween(3, 8)
        );
        final Supplier<String> randomRealmTypeSupplier;
        if (includeSingletons) {
            randomRealmTypeSupplier = randomAllRealmTypeSupplier;
        } else {
            randomRealmTypeSupplier = () -> randomValueOtherThanMany(
                value -> value.equals(FileRealmSettings.TYPE) || value.equals(NativeRealmSettings.TYPE),
                randomAllRealmTypeSupplier
            );
        }
        if (underDomain) {
            final Set<RealmConfig.RealmIdentifier> domainRealms = Set.of(
                randomArray(
                    1,
                    4,
                    RealmConfig.RealmIdentifier[]::new,
                    () -> new RealmConfig.RealmIdentifier(
                        randomRealmTypeSupplier.get(),
                        randomAlphaOfLengthBetween(3, 8).toLowerCase(Locale.ROOT)
                    )
                )
            );
            RealmDomain domain = new RealmDomain("domain", domainRealms);
            RealmConfig.RealmIdentifier realmIdentifier = randomFrom(domainRealms);
            return new RealmRef(realmIdentifier.getName(), realmIdentifier.getType(), randomAlphaOfLengthBetween(3, 8), domain);
        } else {
            return new RealmRef(randomAlphaOfLengthBetween(3, 8), randomRealmTypeSupplier.get(), randomAlphaOfLengthBetween(3, 8), null);
        }
    }

    private RealmRef mutateRealm(RealmRef original, String name, String type) {
        return new RealmRef(
            name == null ? original.getName() : name,
            type == null ? original.getType() : type,
            randomBoolean() ? original.getNodeName() : randomAlphaOfLengthBetween(3, 8)
        );
    }

    public static Authentication randomAuthentication(User user, RealmRef realmRef) {
        if (user == null) {
            user = randomUser();
        }
        if (realmRef == null) {
            realmRef = randomRealm();
        }
        final Version version = VersionUtils.randomVersionBetween(random(), Version.V_7_0_0, Version.CURRENT);
        final AuthenticationType authenticationType = randomValueOtherThan(
            AuthenticationType.API_KEY,
            () -> randomFrom(AuthenticationType.values())
        );
        final Map<String, Object> metadata;
        if (randomBoolean()) {
            metadata = Map.of(randomAlphaOfLengthBetween(3, 8), randomAlphaOfLengthBetween(3, 8));
        } else {
            metadata = Arrays.stream(randomArray(1, 5, String[]::new, () -> randomAlphaOfLengthBetween(3, 8)))
                .distinct()
                .collect(Collectors.toMap(s -> s, s -> randomAlphaOfLengthBetween(3, 8)));
        }
        if (randomBoolean()) { // run-as
            return new Authentication(
                new User(user.principal(), user.roles(), randomUser()),
                randomRealm(),
                realmRef,
                version,
                authenticationType,
                metadata
            );
        } else {
            return new Authentication(user, realmRef, null, version, authenticationType, metadata);
        }
    }

    public static Authentication randomApiKeyAuthentication(User user, String apiKeyId) {
        final RealmRef apiKeyRealm = new RealmRef("_es_api_key", "_es_api_key", randomAlphaOfLengthBetween(3, 8));
        final HashMap<String, Object> metadata = new HashMap<>();
        metadata.put(AuthenticationField.API_KEY_ID_KEY, apiKeyId);
        metadata.put(AuthenticationField.API_KEY_NAME_KEY, randomBoolean() ? null : randomAlphaOfLengthBetween(1, 16));
        metadata.put(AuthenticationField.API_KEY_CREATOR_REALM_NAME, AuthenticationField.API_KEY_CREATOR_REALM_NAME);
        metadata.put(AuthenticationField.API_KEY_CREATOR_REALM_TYPE, AuthenticationField.API_KEY_CREATOR_REALM_TYPE);
        metadata.put(AuthenticationField.API_KEY_ROLE_DESCRIPTORS_KEY, new BytesArray("{}"));
        metadata.put(AuthenticationField.API_KEY_LIMITED_ROLE_DESCRIPTORS_KEY, new BytesArray("""
            {"x":{"cluster":["all"],"indices":[{"names":["index*"],"privileges":["all"]}]}}"""));
        return new Authentication(
            user,
            apiKeyRealm,
            user.isRunAs() ? new RealmRef("lookup_realm", "lookup_realm", randomAlphaOfLength(5)) : null,
            VersionUtils.randomVersionBetween(random(), Version.V_7_0_0, Version.CURRENT),
            AuthenticationType.API_KEY,
            metadata
        );
    }

    public static Authentication randomServiceAccountAuthentication() {
        final RealmRef realmRef = new RealmRef("_service_account", "_service_account", randomAlphaOfLengthBetween(3, 8));
        return new Authentication(
            new User(randomAlphaOfLengthBetween(3, 8) + "/" + randomAlphaOfLengthBetween(3, 8)),
            realmRef,
            null,
            Version.CURRENT,
            AuthenticationType.TOKEN,
            Map.of(
                "_token_name",
                randomAlphaOfLength(8),
                "_token_source",
                randomFrom(TokenInfo.TokenSource.values()).name().toLowerCase(Locale.ROOT)
            )
        );
    }

    public static Authentication randomRealmAuthentication() {
        return new Authentication(randomUser(), randomRealm(), null);
    }

    public static Authentication randomInternalAuthentication() {
        String nodeName = randomAlphaOfLengthBetween(3, 8);
        return randomFrom(
            new Authentication(
                randomFrom(SystemUser.INSTANCE, XPackUser.INSTANCE, XPackSecurityUser.INSTANCE, AsyncSearchUser.INSTANCE),
                new RealmRef(ATTACH_REALM_NAME, ATTACH_REALM_TYPE, nodeName),
                null,
                Version.CURRENT,
                Authentication.AuthenticationType.INTERNAL,
                Map.of()
            ),
            new Authentication(
                SystemUser.INSTANCE,
                new RealmRef(FALLBACK_REALM_NAME, FALLBACK_REALM_TYPE, nodeName),
                null,
                Version.CURRENT,
                Authentication.AuthenticationType.INTERNAL,
                Map.of()
            )
        );
    }

    public static Authentication randomAnonymousAuthentication() {
        Settings settings = Settings.builder().put(AnonymousUser.ROLES_SETTING.getKey(), "anon_role").build();
        String nodeName = randomAlphaOfLengthBetween(3, 8);
        return new Authentication(
            new AnonymousUser(settings),
            new RealmRef(ANONYMOUS_REALM_NAME, ANONYMOUS_REALM_TYPE, nodeName),
            null,
            Version.CURRENT,
            Authentication.AuthenticationType.ANONYMOUS,
            Map.of()
        );
    }

    public static Authentication toToken(Authentication authentication) {
        final Authentication newTokenAuthentication = new Authentication(
            authentication.getUser(),
            authentication.getAuthenticatedBy(),
            authentication.getLookedUpBy(),
            Version.CURRENT,
            AuthenticationType.TOKEN,
            authentication.getMetadata()
        );
        return newTokenAuthentication;
    }

    public static Authentication toRunAs(Authentication authentication, User runAs, @Nullable RealmRef lookupRealmRef) {
        Objects.requireNonNull(runAs);
        assert false == runAs.isRunAs();
        assert false == authentication.getUser().isRunAs();
        assert AuthenticationType.REALM == authentication.getAuthenticationType()
            || AuthenticationType.API_KEY == authentication.getAuthenticationType();
        return new Authentication(
            new User(runAs, authentication.getUser()),
            authentication.getAuthenticatedBy(),
            lookupRealmRef,
            authentication.getVersion(),
            authentication.getAuthenticationType(),
            authentication.getMetadata()
        );
    }

    private boolean realmIsSingleton(RealmRef realmRef) {
        return Set.of(FileRealmSettings.TYPE, NativeRealmSettings.TYPE).contains(realmRef.getType());
    }
}
