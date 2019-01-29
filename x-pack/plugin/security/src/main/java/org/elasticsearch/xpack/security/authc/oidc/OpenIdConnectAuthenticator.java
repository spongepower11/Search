/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.authc.oidc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.IOUtils;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerTokenError;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.AccessTokenValidator;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import net.minidev.json.JSONObject;
import org.apache.commons.codec.Charsets;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.CheckedRunnable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.watcher.FileChangesListener;
import org.elasticsearch.watcher.FileWatcher;
import org.elasticsearch.watcher.ResourceWatcherService;
import org.elasticsearch.xpack.core.security.authc.RealmConfig;
import org.elasticsearch.xpack.core.security.authc.RealmSettings;
import org.elasticsearch.xpack.core.ssl.SSLConfiguration;
import org.elasticsearch.xpack.core.ssl.SSLService;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xpack.core.security.authc.oidc.OpenIdConnectRealmSettings.ALLOWED_CLOCK_SKEW;
import static org.elasticsearch.xpack.core.security.authc.oidc.OpenIdConnectRealmSettings.HTTP_CONNECT_TIMEOUT;
import static org.elasticsearch.xpack.core.security.authc.oidc.OpenIdConnectRealmSettings.HTTP_CONNECTION_READ_TIMEOUT;
import static org.elasticsearch.xpack.core.security.authc.oidc.OpenIdConnectRealmSettings.HTTP_MAX_CONNECTIONS;
import static org.elasticsearch.xpack.core.security.authc.oidc.OpenIdConnectRealmSettings.HTTP_MAX_ENDPOINT_CONNECTIONS;
import static org.elasticsearch.xpack.core.security.authc.oidc.OpenIdConnectRealmSettings.HTTP_SOCKET_TIMEOUT;

/**
 * Handles an OpenID Connect Authentication response as received by the facilitator. In the case of an implicit flow, validates
 * the ID Token and extracts the elasticsearch user properties from it. In the case of an authorization code flow, it first
 * exchanges the code in the authentication response for an ID Token at the token endpoint of the OpenID Connect Provider.
 */
public class OpenIdConnectAuthenticator {

    private final RealmConfig realmConfig;
    private final OpenIdConnectProviderConfiguration opConfig;
    private final RelyingPartyConfiguration rpConfig;
    private final SSLService sslService;
    private IDTokenValidator idTokenValidator;
    private final CloseableHttpAsyncClient httpClient;
    private final ResourceWatcherService watcherService;

    protected final Logger logger = LogManager.getLogger(getClass());

    public OpenIdConnectAuthenticator(RealmConfig realmConfig, OpenIdConnectProviderConfiguration opConfig,
                                      RelyingPartyConfiguration rpConfig, SSLService sslService, ResourceWatcherService watcherService) {
        this.realmConfig = realmConfig;
        this.opConfig = opConfig;
        this.rpConfig = rpConfig;
        this.sslService = sslService;
        this.httpClient = createHttpClient();
        this.idTokenValidator = createIdTokenValidator(getPrivilegedResourceRetriever());
        this.watcherService = watcherService;
    }

    // For testing
    OpenIdConnectAuthenticator(RealmConfig realmConfig, OpenIdConnectProviderConfiguration opConfig, RelyingPartyConfiguration rpConfig,
                               SSLService sslService, IDTokenValidator idTokenValidator, ResourceWatcherService watcherService) {
        this.realmConfig = realmConfig;
        this.opConfig = opConfig;
        this.rpConfig = rpConfig;
        this.sslService = sslService;
        this.httpClient = createHttpClient();
        this.idTokenValidator = idTokenValidator;
        this.watcherService = watcherService;
    }

    /**
     * Processes an OpenID Connect Response to an Authentication Request that comes in the form of a URL with the necessary parameters,
     * that is contained in the provided Token. If the response is valid, it calls the provided listener with a set of OpenID Connect
     * claims that identify the authenticated user. If the UserInfo endpoint is specified in the configuration, we attempt to make a
     * UserInfo request and add the returned claims to the Id Token claims.
     *
     * @param token    The OpenIdConnectToken to consume
     * @param listener The listener to notify with the resolved {@link JWTClaimsSet}
     */
    public void authenticate(OpenIdConnectToken token, final ActionListener<JWTClaimsSet> listener) {
        try {
            AuthenticationResponse authenticationResponse = AuthenticationResponseParser.parse(new URI(token.getRedirectUrl()));
            final Nonce expectedNonce = token.getNonce();
            State expectedState = token.getState();
            if (logger.isTraceEnabled()) {
                logger.trace("OpenID Connect Provider redirected user to [{}]. Expected Nonce is [{}] and expected State is [{}]",
                    token.getRedirectUrl(), expectedNonce, expectedState);
            }
            if (authenticationResponse instanceof AuthenticationErrorResponse) {
                ErrorObject error = ((AuthenticationErrorResponse) authenticationResponse).getErrorObject();
                listener.onFailure(new ElasticsearchSecurityException("OpenID Connect Provider response indicates authentication failure" +
                    "Code=[{}], Description=[{}]", error.getCode(), error.getDescription()));
            }
            final AuthenticationSuccessResponse response = authenticationResponse.toSuccessResponse();
            validateState(expectedState, response.getState());
            validateResponseType(response);
            if (rpConfig.getResponseType().impliesCodeFlow()) {
                final AuthorizationCode code = response.getAuthorizationCode();
                exchangeCodeForToken(code, ActionListener.wrap(tokens -> {
                    final AccessToken accessToken = tokens.v1();
                    final JWT idToken = tokens.v2();
                    validateAccessToken(accessToken, idToken, true);
                    getUserClaims(accessToken, idToken, expectedNonce, listener);
                }, listener::onFailure));
            } else {
                final JWT idToken = response.getIDToken();
                final AccessToken accessToken = response.getAccessToken();
                validateAccessToken(accessToken, idToken, false);
                getUserClaims(accessToken, idToken, expectedNonce, listener);
            }
        } catch (ElasticsearchSecurityException e) {
            // Don't wrap in a new ElasticsearchSecurityException
            listener.onFailure(e);
        } catch (Exception e) {
            listener.onFailure(new ElasticsearchSecurityException("Failed to consume the OpenID connect response. ", e));
        }
    }

    /**
     * Collects all the user claims we can get for the authenticated user. This happens in two steps:
     * <ul>
     * <li>First we attempt to validate the Id Token we have received and get any claims it contains</li>
     * <li>If the UserInfo endpoint is configured, we also attempt to get the user info response from there and parse the returned
     * claims</li>
     * </ul>
     *
     * @param accessToken    The {@link AccessToken} that the OP has issued for this user
     * @param idToken        The {@link JWT} Id Token that the OP has issued for this user
     * @param expectedNonce  The nonce value we sent in the authentication request and should be contained in the Id Token
     * @param claimsListener The listener to notify with the resolved {@link JWTClaimsSet}
     */
    private void getUserClaims(AccessToken accessToken, JWT idToken, Nonce expectedNonce, ActionListener<JWTClaimsSet> claimsListener) {
        try {
            JWTClaimsSet verifiedIdTokenClaims = idTokenValidator.validate(idToken, expectedNonce).toJWTClaimsSet();
            if (logger.isTraceEnabled()) {
                logger.trace("Received and validated the Id Token for the user: [{}]", verifiedIdTokenClaims);
            }
            if (opConfig.getUserinfoEndpoint() != null) {
                getAndCombineUserInfoClaims(accessToken, verifiedIdTokenClaims, claimsListener);
            } else {
                claimsListener.onResponse(verifiedIdTokenClaims);
            }
        } catch (com.nimbusds.oauth2.sdk.ParseException | JOSEException | BadJOSEException e) {
            claimsListener.onFailure(new ElasticsearchSecurityException("Failed to parse or validate the ID Token", e));
        }
    }


    /**
     * Validates an access token according to the
     * <a href="https://openid.net/specs/openid-connect-core-1_0.html#ImplicitTokenValidation">specification</a>
     *
     * @param accessToken The Access Token to validate
     * @param idToken     The Id Token that was received in the same response
     * @param optional    When using the authorization code flow the OP might not provide the at_hash parameter in the
     *                    Id Token as allowed in the specification. In such a case we can't validate the access token
     *                    but this is considered safe as it was received in a back channel communication that was protected
     *                    by TLS.
     */
    private void validateAccessToken(AccessToken accessToken, JWT idToken, boolean optional) {
        try {
            // only "Bearer" is defined in the specification but check just in case
            if (accessToken.getType().toString().equals("Bearer") == false) {
                throw new ElasticsearchSecurityException("Invalid access token type [{}], while [Bearer] was expected",
                    accessToken.getType());
            }
            String atHashValue = idToken.getJWTClaimsSet().getStringClaim("at_hash");
            if (null == atHashValue && optional == false) {
                throw new ElasticsearchSecurityException("Failed to verify access token. at_hash claim is missing from the ID Token");
            }
            AccessTokenHash atHash = new AccessTokenHash(atHashValue);
            JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(idToken.getHeader().getAlgorithm().getName());
            AccessTokenValidator.validate(accessToken, jwsAlgorithm, atHash);
        } catch (Exception e) {
            throw new ElasticsearchSecurityException("Failed to verify access token.", e);
        }
    }

    /**
     * Reads and parses a JWKSet from a file
     *
     * @param jwkSetPath The path to the file that contains the JWKs as a string.
     * @return the parsed {@link JWKSet}
     * @throws ParseException if the file cannot be parsed
     * @throws IOException    if the file cannot be read
     */
    @SuppressForbidden(reason = "uses toFile")
    private JWKSet readJwkSetFromFile(String jwkSetPath) throws IOException, ParseException {
        final Path path = realmConfig.env().configFile().resolve(jwkSetPath);
        return JWKSet.load(path.toFile());
    }

    /**
     * Validate that the response we received corresponds to the response type we requested
     *
     * @param response The {@link AuthenticationSuccessResponse} we received
     * @throws ElasticsearchSecurityException if the response is not the expected one for the configured response type
     */
    private void validateResponseType(AuthenticationSuccessResponse response) {
        if (rpConfig.getResponseType().equals(response.impliedResponseType()) == false) {
            throw new ElasticsearchSecurityException("Unexpected response type [{}], while [{}] is configured",
                response.impliedResponseType(), rpConfig.getResponseType());

        }
    }

    /**
     * Validate that the state parameter the response contained corresponds to the one that we generated in the
     * beginning of this authentication attempt and was stored with the user's session at the facilitator
     *
     * @param expectedState The state that was originally generated
     * @param state         The state that was contained in the response
     */
    private void validateState(State expectedState, State state) {
        if (null == state) {
            throw new ElasticsearchSecurityException("Failed to validate the response, the response did not contain a state parameter");
        } else if (null == expectedState) {
            throw new ElasticsearchSecurityException("Failed to validate the response, the user's session did not contain a state " +
                "parameter");
        } else if (state.equals(expectedState) == false) {
            throw new ElasticsearchSecurityException("Invalid state parameter [{}], while [{}] was expected", state, expectedState);
        }
    }

    /**
     * Attempts to make a request to the UserInfo Endpoint of the OpenID Connect provider
     */
    private void getAndCombineUserInfoClaims(AccessToken accessToken, JWTClaimsSet verifiedIdTokenClaims,
                                             ActionListener<JWTClaimsSet> claimsListener) {
        try {
            final HttpGet httpGet = new HttpGet(opConfig.getUserinfoEndpoint());
            httpGet.setHeader("Authorization", "Bearer " + accessToken.getValue());
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                httpClient.execute(httpGet, new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(HttpResponse result) {
                        handleUserinfoResponse(result, verifiedIdTokenClaims, claimsListener);
                    }

                    @Override
                    public void failed(Exception ex) {
                        claimsListener.onFailure(new ElasticsearchSecurityException("Failed to get claims from the Userinfo Endpoint.",
                            ex));
                    }

                    @Override
                    public void cancelled() {
                        claimsListener.onFailure(
                            new ElasticsearchSecurityException("Failed to get claims from the Userinfo Endpoint. Request was cancelled"));
                    }
                });
                return null;
            });
        } catch (Exception e) {
            claimsListener.onFailure(new ElasticsearchSecurityException("Failed to get user information from the UserInfo endpoint.", e));
        }
    }

    /**
     * Handle the UserInfo Response from the OpenID Connect Provider. If successful, merge the returned claims with the claims
     * of the Id Token and call the provided listener.
     */
    private void handleUserinfoResponse(HttpResponse httpResponse, JWTClaimsSet verifiedIdTokenClaims,
                                        ActionListener<JWTClaimsSet> claimsListener) {
        try {
            final HttpEntity entity = httpResponse.getEntity();
            final Header encodingHeader = entity.getContentEncoding();
            final Charset encoding = encodingHeader == null ? StandardCharsets.UTF_8 : Charsets.toCharset(encodingHeader.getValue());
            final Header contentHeader = entity.getContentType();
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                final String contentAsString = EntityUtils.toString(entity, encoding);
                if (ContentType.parse(contentHeader.getValue()).getMimeType().equals("application/json")) {
                    final JWTClaimsSet userInfoClaims = JWTClaimsSet.parse(contentAsString);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Successfully retrieved user information: [{}]", userInfoClaims.toJSONObject().toJSONString());
                    }
                    final JSONObject combinedClaims = verifiedIdTokenClaims.toJSONObject();
                    combinedClaims.merge(userInfoClaims.toJSONObject());
                    claimsListener.onResponse(JWTClaimsSet.parse(combinedClaims));
                } else if (ContentType.parse(contentHeader.getValue()).getMimeType().equals("application/jwt")) {
                    //TODO Handle validating possibly signed responses
                    claimsListener.onFailure(new IllegalStateException("Unable to parse Userinfo Response. Signed/encryopted JWTs are" +
                        "not currently supported"));
                } else {
                    claimsListener.onFailure(new IllegalStateException("Unable to parse Userinfo Response. Content type was expected to " +
                        "be [application/json] or [appliation/jwt] but was [" + contentHeader.getValue() + "]"));
                }
            } else {
                final Header wwwAuthenticateHeader = httpResponse.getFirstHeader("WWW-Authenticate");
                if (Strings.hasText(wwwAuthenticateHeader.getValue())) {
                    BearerTokenError error = BearerTokenError.parse(wwwAuthenticateHeader.getValue());
                    claimsListener.onFailure(
                        new ElasticsearchSecurityException("Failed to get user information from the UserInfo endpoint. Code=[{}], " +
                            "Description=[{}]", error.getCode(), error.getDescription()));
                } else {
                    claimsListener.onFailure(
                        new ElasticsearchSecurityException("Failed to get user information from the UserInfo endpoint. Code=[{}], " +
                            "Description=[{}]", httpResponse.getStatusLine().getStatusCode(),
                            httpResponse.getStatusLine().getReasonPhrase()));
                }
            }
        } catch (IOException | com.nimbusds.oauth2.sdk.ParseException | ParseException e) {
            claimsListener.onFailure(new ElasticsearchSecurityException("Failed to get user information from the UserInfo endpoint.",
                e));
        }
    }

    /**
     * Attempts to make a request to the Token Endpoint of the OpenID Connect provider in order to exchange an
     * authorization code for an Id Token (and potentially an Access Token)
     */
    private void exchangeCodeForToken(AuthorizationCode code, ActionListener<Tuple<AccessToken, JWT>> tokensListener) {
        try {
            final AuthorizationCodeGrant codeGrant = new AuthorizationCodeGrant(code, rpConfig.getRedirectUri());
            final HttpPost httpPost = new HttpPost(opConfig.getTokenEndpoint());
            final List<NameValuePair> params = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : codeGrant.toParameters().entrySet()) {
                // All parameters of AuthorizationCodeGrant are singleton lists
                params.add(new BasicNameValuePair(entry.getKey(), entry.getValue().get(0)));
            }
            httpPost.setEntity(new UrlEncodedFormEntity(params));
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(rpConfig.getClientId().getValue(),
                rpConfig.getClientSecret().toString());
            httpPost.addHeader(new BasicScheme().authenticate(creds, httpPost, null));
            SpecialPermission.check();
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                httpClient.execute(httpPost, new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(HttpResponse result) {
                        handleTokenResponse(result, tokensListener);
                    }

                    @Override
                    public void failed(Exception ex) {
                        tokensListener.onFailure(
                            new ElasticsearchSecurityException("Failed to exchange code for Id Token using the Token Endpoint.", ex));
                    }

                    @Override
                    public void cancelled() {
                        final String message = "Failed to exchange code for Id Token using the Token Endpoint. Request was cancelled";
                        tokensListener.onFailure(new ElasticsearchSecurityException(message));
                    }
                });
                return null;
            });
        } catch (AuthenticationException | UnsupportedEncodingException e) {
            tokensListener.onFailure(
                new ElasticsearchSecurityException("Failed to exchange code for Id Token using the Token Endpoint.", e));
        }
    }

    /**
     * Handle the Token Response from the OpenID Connect Provider. If successful, extract the (yet not validated) Id Token
     * and access token and call the provided listener.
     */
    private void handleTokenResponse(HttpResponse httpResponse, ActionListener<Tuple<AccessToken, JWT>> tokensListener) {
        try {
            final HttpEntity entity = httpResponse.getEntity();
            final Header encodingHeader = entity.getContentEncoding();
            final Header contentHeader = entity.getContentType();
            if (ContentType.parse(contentHeader.getValue()).getMimeType().equals("application/json") == false) {
                tokensListener.onFailure(new IllegalStateException("Unable to parse Token Response. Content type was expected to be " +
                    "[application/json] but was [" + contentHeader.getValue() + "]"));
                return;
            }
            final Charset encoding = encodingHeader == null ? StandardCharsets.UTF_8 : Charsets.toCharset(encodingHeader.getValue());
            final String json = EntityUtils.toString(entity, encoding);
            final OIDCTokenResponse oidcTokenResponse = OIDCTokenResponse.parse(JSONObjectUtils.parse(json));
            if (oidcTokenResponse.indicatesSuccess() == false) {
                TokenErrorResponse errorResponse = oidcTokenResponse.toErrorResponse();
                tokensListener.onFailure(
                    new ElasticsearchSecurityException("Failed to exchange code for Id Token. Code=[{}], Description=[{}]",
                        errorResponse.getErrorObject().getCode(), errorResponse.getErrorObject().getDescription()));
            } else {
                OIDCTokenResponse successResponse = oidcTokenResponse.toSuccessResponse();
                if (logger.isTraceEnabled()) {
                    logger.trace("Successfully exchanged code for ID Token: [{}]", successResponse.toJSONObject().toJSONString());
                }
                final OIDCTokens oidcTokens = successResponse.getOIDCTokens();
                final AccessToken accessToken = oidcTokens.getAccessToken();
                final JWT idToken = oidcTokens.getIDToken();
                if (idToken == null) {
                    tokensListener.onFailure(new ElasticsearchSecurityException("Token Response did not contain an ID Token or parsing of" +
                        " the JWT failed."));
                    return;
                }
                tokensListener.onResponse(new Tuple<>(accessToken, idToken));
            }
        } catch (IOException | com.nimbusds.oauth2.sdk.ParseException e) {
            tokensListener.onFailure(
                new ElasticsearchSecurityException("Failed to exchange code for Id Token using the Token Endpoint. " +
                    "Unable to parse Token Response", e));
        }
    }

    /**
     * Creates a {@link CloseableHttpAsyncClient} that uses a {@link PoolingNHttpClientConnectionManager}
     */
    private CloseableHttpAsyncClient createHttpClient() {
        try {
            SpecialPermission.check();
            return AccessController.doPrivileged(
                (PrivilegedExceptionAction<CloseableHttpAsyncClient>) () -> {
                    ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
                    PoolingNHttpClientConnectionManager connectionManager = new PoolingNHttpClientConnectionManager(ioReactor);
                    connectionManager.setDefaultMaxPerRoute(realmConfig.getSetting(HTTP_MAX_ENDPOINT_CONNECTIONS));
                    connectionManager.setMaxTotal(realmConfig.getSetting(HTTP_MAX_CONNECTIONS));
                    final String sslKey = RealmSettings.realmSslPrefix(realmConfig.identifier());
                    final SSLConfiguration sslConfiguration = sslService.getSSLConfiguration(sslKey);
                    final SSLContext clientContext = sslService.sslContext(sslConfiguration);
                    boolean isHostnameVerificationEnabled = sslConfiguration.verificationMode().isHostnameVerificationEnabled();
                    final HostnameVerifier verifier = isHostnameVerificationEnabled ?
                        new DefaultHostnameVerifier() : NoopHostnameVerifier.INSTANCE;
                    final RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(Math.toIntExact(realmConfig.getSetting(HTTP_CONNECT_TIMEOUT).getMillis()))
                        .setConnectionRequestTimeout(Math.toIntExact(realmConfig.getSetting(HTTP_CONNECTION_READ_TIMEOUT).getSeconds()))
                        .setSocketTimeout(Math.toIntExact(realmConfig.getSetting(HTTP_SOCKET_TIMEOUT).getMillis())).build();
                    CloseableHttpAsyncClient httpAsyncClient = HttpAsyncClients.custom()
                        .setConnectionManager(connectionManager)
                        .setSSLContext(clientContext)
                        .setSSLHostnameVerifier(verifier)
                        .setDefaultRequestConfig(requestConfig)
                        .build();
                    httpAsyncClient.start();
                    return httpAsyncClient;
                });
        } catch (PrivilegedActionException e) {
            throw new IllegalStateException("Unable to create a HttpAsyncClient instance", e);
        }
    }

    /*
     * Creates an {@link IDTokenValidator} based on the current Relying Party configuration
     */
    IDTokenValidator createIdTokenValidator(final PrivilegedResourceRetriever retriever) {
        try {
            final JWSAlgorithm requestedAlgorithm = rpConfig.getSignatureAlgorithm();
            final int allowedClockSkew = Math.toIntExact(realmConfig.getSetting(ALLOWED_CLOCK_SKEW).getMillis());
            final IDTokenValidator idTokenValidator;
            if (JWSAlgorithm.Family.HMAC_SHA.contains(requestedAlgorithm)) {
                final Secret clientSecret = new Secret(rpConfig.getClientSecret().toString());
                idTokenValidator =
                    new IDTokenValidator(opConfig.getIssuer(), rpConfig.getClientId(), requestedAlgorithm, clientSecret);
            } else {
                String jwkSetPath = opConfig.getJwkSetPath();
                if (jwkSetPath.startsWith("https://")) {
                    idTokenValidator = new IDTokenValidator(opConfig.getIssuer(), rpConfig.getClientId(),
                        requestedAlgorithm, new URL(jwkSetPath), retriever);

                } else {
                    setMetadataFileWatcher(jwkSetPath, retriever);
                    final JWKSet jwkSet = readJwkSetFromFile(jwkSetPath);
                    idTokenValidator = new IDTokenValidator(opConfig.getIssuer(), rpConfig.getClientId(), requestedAlgorithm, jwkSet);
                }
            }
            idTokenValidator.setMaxClockSkew(allowedClockSkew);
            return idTokenValidator;
        } catch (IOException | ParseException e) {
            throw new IllegalStateException("Unable to create a IDTokenValidator instance", e);
        }
    }

    private void setMetadataFileWatcher(String jwkSetPath, PrivilegedResourceRetriever resourceRetriever) throws IOException {
        final Path path = realmConfig.env().configFile().resolve(jwkSetPath);
        FileWatcher watcher = new FileWatcher(path);
        watcher.addListener(new FileListener(logger, () -> this.idTokenValidator = createIdTokenValidator(resourceRetriever)));
        watcherService.add(watcher, ResourceWatcherService.Frequency.MEDIUM);
    }

    protected void close() {
        try {
            this.httpClient.close();
        } catch (IOException e) {
            logger.debug("Unable to close the HttpAsyncClient", e);
        }
    }

    private static class FileListener implements FileChangesListener {

        private final Logger logger;
        private final CheckedRunnable<Exception> onChange;

        private FileListener(Logger logger, CheckedRunnable<Exception> onChange) {
            this.logger = logger;
            this.onChange = onChange;
        }

        @Override
        public void onFileCreated(Path file) {
            onFileChanged(file);
        }

        @Override
        public void onFileDeleted(Path file) {
            onFileChanged(file);
        }

        @Override
        public void onFileChanged(Path file) {
            try {
                onChange.run();
            } catch (Exception e) {
                logger.warn(new ParameterizedMessage("An error occurred while reloading file {}", file), e);
            }
        }
    }

    /**
     * Creates a new {@link PrivilegedResourceRetriever} to be used with the {@link IDTokenValidator} by passing the
     * necessary client SSLContext and hostname verification configuration
     */
    private PrivilegedResourceRetriever getPrivilegedResourceRetriever() {
        final String sslKey = RealmSettings.realmSslPrefix(realmConfig.identifier());
        final SSLConfiguration sslConfiguration = sslService.getSSLConfiguration(sslKey);
        boolean isHostnameVerificationEnabled = sslConfiguration.verificationMode().isHostnameVerificationEnabled();
        final HostnameVerifier verifier = isHostnameVerificationEnabled ? new DefaultHostnameVerifier() : NoopHostnameVerifier.INSTANCE;
        return new PrivilegedResourceRetriever(sslService.sslContext(sslConfiguration), verifier, realmConfig);
    }

    static class PrivilegedResourceRetriever extends DefaultResourceRetriever {
        private SSLContext clientContext;
        private HostnameVerifier verifier;
        private RealmConfig config;

        PrivilegedResourceRetriever(final SSLContext clientContext, final HostnameVerifier verifier, final RealmConfig config) {
            super();
            this.clientContext = clientContext;
            this.verifier = verifier;
            this.config = config;
        }

        @Override
        public Resource retrieveResource(final URL url) throws IOException {
            SpecialPermission.check();
            try {
                return AccessController.doPrivileged(
                    (PrivilegedExceptionAction<Resource>) () -> {
                        final BasicHttpContext context = new BasicHttpContext();
                        final RequestConfig requestConfig = RequestConfig.custom()
                            .setConnectTimeout(Math.toIntExact(config.getSetting(HTTP_CONNECT_TIMEOUT).getMillis()))
                            .setConnectionRequestTimeout(Math.toIntExact(config.getSetting(HTTP_CONNECTION_READ_TIMEOUT).getSeconds()))
                            .setSocketTimeout(Math.toIntExact(config.getSetting(HTTP_SOCKET_TIMEOUT).getMillis())).build();
                        try (CloseableHttpClient client = HttpClients.custom()
                            .setSSLContext(clientContext)
                            .setSSLHostnameVerifier(verifier)
                            .setDefaultRequestConfig(requestConfig)
                            .build()) {
                            HttpGet get = new HttpGet(url.toURI());
                            HttpResponse response = client.execute(get, context);
                            return new Resource(IOUtils.readInputStreamToString(response.getEntity().getContent(),
                                StandardCharsets.UTF_8), response.getEntity().getContentType().getValue());
                        }
                    });
            } catch (final PrivilegedActionException e) {
                throw (IOException) e.getCause();
            }
        }
    }
}
