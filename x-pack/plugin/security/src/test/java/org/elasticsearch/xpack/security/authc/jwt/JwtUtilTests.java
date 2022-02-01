/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.security.authc.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.xpack.core.security.authc.jwt.JwtRealmSettings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class JwtUtilTests extends JwtTestCase {

    private static final Logger LOGGER = LogManager.getLogger(JwtUtilTests.class);

    public void testValidateJwtIssuedAtTime() throws Exception {
        final Instant now = Instant.now();

        // auth_time optional
        JwtUtil.validateJwtAuthTime(0, Date.from(now), null);
        expectThrows(Exception.class, () -> JwtUtil.validateJwtAuthTime(0, Date.from(now.minusSeconds(1)), Date.from(now)));
        JwtUtil.validateJwtAuthTime(0, Date.from(now), Date.from(now));
        JwtUtil.validateJwtAuthTime(1, Date.from(now), Date.from(now.minusSeconds(1)));

        // iat required
        expectThrows(Exception.class, () -> JwtUtil.validateJwtIssuedAtTime(0, Date.from(now), null));
        expectThrows(Exception.class, () -> JwtUtil.validateJwtIssuedAtTime(0, Date.from(now.minusSeconds(1)), Date.from(now)));
        JwtUtil.validateJwtIssuedAtTime(0, Date.from(now), Date.from(now));
        JwtUtil.validateJwtIssuedAtTime(1, Date.from(now), Date.from(now.minusSeconds(1)));

        // nbf optional
        JwtUtil.validateJwtNotBeforeTime(0, Date.from(now), null);
        expectThrows(Exception.class, () -> JwtUtil.validateJwtNotBeforeTime(0, Date.from(now.minusSeconds(1)), Date.from(now)));
        JwtUtil.validateJwtNotBeforeTime(0, Date.from(now), Date.from(now));
        JwtUtil.validateJwtNotBeforeTime(1, Date.from(now), Date.from(now.minusSeconds(1)));

        // exp required
        expectThrows(Exception.class, () -> JwtUtil.validateJwtExpiredTime(0, Date.from(now), null));
        JwtUtil.validateJwtExpiredTime(0, Date.from(now.minusSeconds(1)), Date.from(now));
        expectThrows(Exception.class, () -> JwtUtil.validateJwtExpiredTime(0, Date.from(now), Date.from(now)));
        JwtUtil.validateJwtExpiredTime(1, Date.from(now.minusSeconds(1)), Date.from(now));
    }

    public void testCheckIfJavaDisabledES256K() throws Exception {
        final Exception exception = expectThrows(JOSEException.class, () -> this.helpTestSignatureAlgorithm(JWSAlgorithm.ES256K));
        String expected = "Unsupported signature algorithm ["
            + JWSAlgorithm.ES256K
            + "]. Supported signature algorithms are "
            + JwtRealmSettings.SUPPORTED_SIGNATURE_ALGORITHMS
            + ".";
        assertThat(exception.getMessage(), is(equalTo(expected)));
    }

    // All JWSAlgorithm values in Family.HMAC_SHA, Family.RSA, and Family.EC (except ES256K, handled separately)
    public void testSignedJwtGenerateSignVerify() throws Exception {
        for (final JWSAlgorithm signatureAlgorithm : JwtUtil.SUPPORTED_JWS_ALGORITHMS) {
            assertThat(this.helpTestSignatureAlgorithm(signatureAlgorithm), is(true));
        }
    }

    private boolean helpTestSignatureAlgorithm(final JWSAlgorithm signatureAlgorithm) throws Exception {
        LOGGER.info("Testing signature algorithm " + signatureAlgorithm);
        // randomSecretOrSecretKeyOrKeyPair() randomizes which JwtUtil methods to call, so it indirectly covers most JwtUtil code
        final JWK jwk = JwtUtilTests.randomJwk(signatureAlgorithm);
        final JWSSigner jwsSigner = JwtUtil.createJwsSigner(jwk);
        final JWSVerifier jwkVerifier = JwtUtil.createJwsVerifier(jwk);
        final String serializedJWTOriginal = JwtUtilTests.randomValidSignedJWT(jwsSigner, signatureAlgorithm.toString()).serialize();
        final SignedJWT parsedSignedJWT = SignedJWT.parse(serializedJWTOriginal);
        return JwtUtil.verifySignedJWT(jwkVerifier, parsedSignedJWT);
    }

    public void testClientAuthorizationTypeValidation() {
        final String clientAuthorizationTypeKey = JwtRealmSettings.CLIENT_AUTHORIZATION_TYPE.getKey();
        final String clientAuthorizationSharedSecretKey = JwtRealmSettings.CLIENT_AUTHORIZATION_SHARED_SECRET.getKey();
        final SecureString sharedSecretNonEmpty = new SecureString(randomAlphaOfLengthBetween(1, 32).toCharArray());
        final SecureString sharedSecretNullOrEmpty = randomBoolean() ? new SecureString("".toCharArray()) : null;

        // If type is None, verify null or empty is accepted
        JwtUtil.validateClientAuthorizationSettings(
            clientAuthorizationTypeKey,
            JwtRealmSettings.CLIENT_AUTHORIZATION_TYPE_NONE,
            clientAuthorizationSharedSecretKey,
            sharedSecretNullOrEmpty
        );
        // If type is None, verify non-empty is rejected
        final Exception exception1 = expectThrows(
            SettingsException.class,
            () -> JwtUtil.validateClientAuthorizationSettings(
                clientAuthorizationTypeKey,
                JwtRealmSettings.CLIENT_AUTHORIZATION_TYPE_NONE,
                clientAuthorizationSharedSecretKey,
                sharedSecretNonEmpty
            )
        );
        assertThat(
            exception1.getMessage(),
            is(
                equalTo(
                    "Setting ["
                        + clientAuthorizationSharedSecretKey
                        + "] is not supported, because setting ["
                        + clientAuthorizationTypeKey
                        + "] is ["
                        + JwtRealmSettings.CLIENT_AUTHORIZATION_TYPE_NONE
                        + "]"
                )
            )
        );

        // If type is SharedSecret, verify non-empty is accepted
        JwtUtil.validateClientAuthorizationSettings(
            clientAuthorizationTypeKey,
            JwtRealmSettings.CLIENT_AUTHORIZATION_TYPE_SHARED_SECRET,
            clientAuthorizationSharedSecretKey,
            sharedSecretNonEmpty
        );
        // If type is SharedSecret, verify null or empty is rejected
        final Exception exception2 = expectThrows(
            SettingsException.class,
            () -> JwtUtil.validateClientAuthorizationSettings(
                clientAuthorizationTypeKey,
                JwtRealmSettings.CLIENT_AUTHORIZATION_TYPE_SHARED_SECRET,
                clientAuthorizationSharedSecretKey,
                sharedSecretNullOrEmpty
            )
        );
        assertThat(
            exception2.getMessage(),
            is(
                equalTo(
                    "Missing setting for ["
                        + clientAuthorizationSharedSecretKey
                        + "]. It is required when setting ["
                        + clientAuthorizationTypeKey
                        + "] is ["
                        + JwtRealmSettings.CLIENT_AUTHORIZATION_TYPE_SHARED_SECRET
                        + "]"
                )
            )
        );
    }

    public void testValidateJwkSets() throws Exception {
        final List<String> algorithmsHmac = randomSubsetOf(randomIntBetween(1, 3), JwtRealmSettings.SUPPORTED_SIGNATURE_ALGORITHMS_HMAC);
        final List<JWK> jwksHmac = JwtTestCase.toRandomJwks(JwtUtil.toJwsAlgorithms(algorithmsHmac));
        final JWKSet jwkSetHmac = new JWKSet(jwksHmac);
        final String contentsHmac = JwtUtil.serializeJwkSet(jwkSetHmac, false);

        final List<String> algorithmsPkc = randomSubsetOf(randomIntBetween(1, 3), JwtRealmSettings.SUPPORTED_SIGNATURE_ALGORITHMS_PKC);
        final List<JWK> jwksPkc = JwtTestCase.toRandomJwks(JwtUtil.toJwsAlgorithms(algorithmsPkc));
        final JWKSet jwkSetPkc = new JWKSet(jwksPkc);
        final String contentsPkc = JwtUtil.serializeJwkSet(jwkSetPkc, true);

        final List<String> algorithmsBoth = new ArrayList<>(algorithmsHmac);
        algorithmsBoth.addAll(algorithmsPkc);

        // If HMAC JWKSet and algorithms are present, verify they are accepted
        JwtUtil.validateJwkSets(
            JwtRealmSettings.ALLOWED_SIGNATURE_ALGORITHMS.getKey(),
            algorithmsHmac,
            JwtRealmSettings.JWKSET_HMAC_CONTENTS.getKey(),
            contentsHmac,
            JwtRealmSettings.JWKSET_PKC_PATH.getKey(),
            null
        );
        // If RSA/EC JWKSet and algorithms are present, verify they are accepted
        JwtUtil.validateJwkSets(
            JwtRealmSettings.ALLOWED_SIGNATURE_ALGORITHMS.getKey(),
            algorithmsPkc,
            JwtRealmSettings.JWKSET_HMAC_CONTENTS.getKey(),
            null,
            JwtRealmSettings.JWKSET_PKC_PATH.getKey(),
            contentsPkc
        );
        // If both valid credentials present and both algorithms present, verify they are accepted
        JwtUtil.validateJwkSets(
            JwtRealmSettings.ALLOWED_SIGNATURE_ALGORITHMS.getKey(),
            algorithmsBoth,
            JwtRealmSettings.JWKSET_HMAC_CONTENTS.getKey(),
            contentsHmac,
            JwtRealmSettings.JWKSET_PKC_PATH.getKey(),
            contentsPkc
        );
    }

    public void testParseHttpsUri() {
        // Invalid null or empty values should be rejected
        assertThat(JwtUtil.parseHttpsUriNoException(null), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException(""), is(nullValue()));
        // Valid Windows local file paths should be rejected
        assertThat(JwtUtil.parseHttpsUriNoException("C:"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("C:/"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("C:/jwkset.json"), is(nullValue()));
        // Valid Linux local file paths should be rejected
        assertThat(JwtUtil.parseHttpsUriNoException("/"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("/tmp"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("/tmp/"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("/tmp/jwkset.json"), is(nullValue()));
        // Malformed URIs should be rejected
        assertThat(JwtUtil.parseHttpsUriNoException("http"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http:"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http://"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https:"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https://"), is(nullValue()));
        // Valid HTTP URIs should be rejected
        assertThat(JwtUtil.parseHttpsUriNoException("http:/"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http://example.com"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http://example.com:443"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http://example.com:8443"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http://example.com/"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http://example.com:443/"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http://example.com:8443/"), is(nullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("http://example.com:8443/jwkset.json"), is(nullValue()));
        // Valid HTTPS URIs should be accepted
        assertThat(JwtUtil.parseHttpsUriNoException("https:/"), is(notNullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https://example.com"), is(notNullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https://example.com:443"), is(notNullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https://example.com:8443"), is(notNullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https://example.com/"), is(notNullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https://example.com:443/"), is(notNullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https://example.com:8443/"), is(notNullValue()));
        assertThat(JwtUtil.parseHttpsUriNoException("https://example.com:8443/jwkset.json"), is(notNullValue()));
    }

    public void testComputeBitLengthRsa() throws Exception {
        for (int i = 0; i < 5; i++) {
            for (final String signatureAlgorithmRsa : JwtRealmSettings.SUPPORTED_SIGNATURE_ALGORITHMS_RSA) {
                final JWK jwk = JwtTestCase.randomJwk(JWSAlgorithm.parse(signatureAlgorithmRsa));
                final int minLength = JwtUtil.computeBitLengthRsa(jwk.toRSAKey().toPublicKey());
                assertThat(minLength, is(anyOf(equalTo(2048), equalTo(3072))));
            }
        }
    }
}
