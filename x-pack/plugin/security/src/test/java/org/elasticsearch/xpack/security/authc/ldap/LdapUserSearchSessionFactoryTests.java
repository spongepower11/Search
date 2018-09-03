/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.authc.ldap;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.sdk.GetEntryLDAPConnectionPoolHealthCheck;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPConnectionPoolHealthCheck;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPURL;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.ldap.sdk.SingleServerSet;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.MockSecureSettings;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.TestEnvironment;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.core.security.authc.RealmConfig;
import org.elasticsearch.xpack.core.security.authc.RealmSettings;
import org.elasticsearch.xpack.core.security.authc.ldap.LdapUserSearchSessionFactorySettings;
import org.elasticsearch.xpack.core.security.authc.ldap.PoolingSessionFactorySettings;
import org.elasticsearch.xpack.core.security.authc.ldap.support.LdapSearchScope;
import org.elasticsearch.xpack.core.security.authc.ldap.support.SessionFactorySettings;
import org.elasticsearch.xpack.core.security.support.NoOpLogger;
import org.elasticsearch.xpack.core.ssl.SSLService;
import org.elasticsearch.xpack.security.authc.ldap.support.LdapSession;
import org.elasticsearch.xpack.security.authc.ldap.support.LdapTestCase;
import org.junit.After;
import org.junit.Before;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.notNullValue;

public class LdapUserSearchSessionFactoryTests extends LdapTestCase {

    private SSLService sslService;
    private Settings globalSettings;
    private ThreadPool threadPool;

    @Before
    public void init() throws Exception {
        Path certPath = getDataPath("support/smb_ca.crt");
        Environment env = TestEnvironment.newEnvironment(Settings.builder().put("path.home", createTempDir()).build());
        /*
         * Prior to each test we reinitialize the socket factory with a new SSLService so that we get a new SSLContext.
         * If we re-use a SSLContext, previously connected sessions can get re-established which breaks hostname
         * verification tests since a re-established connection does not perform hostname verification.
         */

        globalSettings = Settings.builder()
            .put("path.home", createTempDir())
            .put("xpack.ssl.certificate_authorities", certPath)
            .build();
        sslService = new SSLService(globalSettings, env);
        threadPool = new TestThreadPool("LdapUserSearchSessionFactoryTests");
    }

    @After
    public void shutdown() throws InterruptedException {
        terminate(threadPool);
    }

    private MockSecureSettings newSecureSettings(String key, String value) {
        MockSecureSettings secureSettings = new MockSecureSettings();
        secureSettings.setString(key, value);
        return secureSettings;
    }

    public void testSupportsUnauthenticatedSessions() throws Exception {
        final boolean useAttribute = randomBoolean();
        Settings.Builder builder = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, "", LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), "")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());
        final boolean useLegacyBindPassword = configureBindPassword(builder);
        if (useAttribute) {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE), "cn");
        } else {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_FILTER), "(cn={0})");
        }

        RealmConfig config = getRealmConfig(builder);

        LdapUserSearchSessionFactory sessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);
        try {
            assertThat(sessionFactory.supportsUnauthenticatedSession(), is(true));
        } finally {
            sessionFactory.close();
        }

        assertDeprecationWarnings(config.identifier(), useAttribute, useLegacyBindPassword);
    }

    private RealmConfig getRealmConfig(Settings.Builder builder) {
        return new RealmConfig(REALM_IDENTIFIER,
                mergeSettings(builder.build(), globalSettings),
                TestEnvironment.newEnvironment(globalSettings), new ThreadContext(globalSettings));
    }

    public void testUserSearchSubTree() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";

        final boolean useAttribute = randomBoolean();
        Settings.Builder builder = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());
        final boolean useLegacyBindPassword = configureBindPassword(builder);
        if (useAttribute) {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE), "cn");
        } else {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_FILTER), "(cn={0})");
        }
        RealmConfig config = getRealmConfig(builder);

        LdapUserSearchSessionFactory sessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);

        String user = "William Bush";
        SecureString userPass = new SecureString("pass");

        try {
            // auth
            try (LdapSession ldap = session(sessionFactory, user, userPass)) {
                assertConnectionValid(ldap.getConnection(), sessionFactory.bindCredentials);
                String dn = ldap.userDn();
                assertThat(dn, containsString(user));
            }

            //lookup
            try (LdapSession ldap = unauthenticatedSession(sessionFactory, user)) {
                assertConnectionValid(ldap.getConnection(), sessionFactory.bindCredentials);
                String dn = ldap.userDn();
                assertThat(dn, containsString(user));
            }
        } finally {
            sessionFactory.close();
        }

        assertDeprecationWarnings(config.identifier(), useAttribute, useLegacyBindPassword);
    }

    public void testUserSearchBaseScopeFailsWithWrongBaseDN() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";

        final boolean useAttribute = randomBoolean();
        Settings.Builder builder = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_SCOPE), LdapSearchScope.BASE)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());
        final boolean useLegacyBindPassword = configureBindPassword(builder);
        if (useAttribute) {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE), "cn");
        } else {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_FILTER), "(cn={0})");
        }
        RealmConfig config = getRealmConfig(builder);

        LdapUserSearchSessionFactory sessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);

        String user = "William Bush";
        SecureString userPass = new SecureString("pass");

        try {
            assertNull(session(sessionFactory, user, userPass));
            assertNull(unauthenticatedSession(sessionFactory, user));
        } finally {
            sessionFactory.close();
        }

        assertDeprecationWarnings(config.identifier(), useAttribute, useLegacyBindPassword);
    }

    public void testUserSearchBaseScopePassesWithCorrectBaseDN() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "cn=William Bush,ou=people,o=sevenSeas";

        Settings.Builder builder = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_SCOPE), LdapSearchScope.BASE)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());
        final boolean useLegacyBindPassword = configureBindPassword(builder);
        final boolean useAttribute = randomBoolean();
        if (useAttribute) {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE), "cn");
        } else {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_FILTER), "(cn={0})");
        }
        RealmConfig config = getRealmConfig(builder);

        LdapUserSearchSessionFactory sessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);

        String user = "William Bush";
        SecureString userPass = new SecureString("pass");

        try {
            // auth
            try (LdapSession ldap = session(sessionFactory, user, userPass)) {
                assertConnectionValid(ldap.getConnection(), sessionFactory.bindCredentials);
                String dn = ldap.userDn();
                assertThat(dn, containsString(user));
            }

            //lookup
            try (LdapSession ldap = unauthenticatedSession(sessionFactory, user)) {
                assertConnectionValid(ldap.getConnection(), sessionFactory.bindCredentials);
                String dn = ldap.userDn();
                assertThat(dn, containsString(user));
            }
        } finally {
            sessionFactory.close();
        }

        assertDeprecationWarnings(config.identifier(), useAttribute, useLegacyBindPassword);
    }

    public void testUserSearchOneLevelScopeFailsWithWrongBaseDN() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";

        Settings.Builder builder = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_SCOPE), LdapSearchScope.ONE_LEVEL)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());
        final boolean useLegacyBindPassword = configureBindPassword(builder);
        final boolean useAttribute = randomBoolean();
        if (useAttribute) {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE), "cn");
        } else {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_FILTER), "(cn={0})");
        }
        RealmConfig config = getRealmConfig(builder);

        LdapUserSearchSessionFactory sessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);

        String user = "William Bush";
        SecureString userPass = new SecureString("pass");

        try {
            assertNull(session(sessionFactory, user, userPass));
            assertNull(unauthenticatedSession(sessionFactory, user));
        } finally {
            sessionFactory.close();
        }

        assertDeprecationWarnings(config.identifier(), useAttribute, useLegacyBindPassword);
    }

    public void testUserSearchOneLevelScopePassesWithCorrectBaseDN() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "ou=people,o=sevenSeas";

        Settings.Builder builder = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_SCOPE), LdapSearchScope.ONE_LEVEL)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());
        final boolean useLegacyBindPassword = configureBindPassword(builder);
        final boolean useAttribute = randomBoolean();
        if (useAttribute) {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE), "cn");
        } else {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_FILTER), "(cn={0})");
        }
        RealmConfig config = getRealmConfig(builder);

        LdapUserSearchSessionFactory sessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);

        String user = "William Bush";
        SecureString userPass = new SecureString("pass");

        try {
            //auth
            try (LdapSession ldap = session(sessionFactory, user, userPass)) {
                assertConnectionValid(ldap.getConnection(), sessionFactory.bindCredentials);
                String dn = ldap.userDn();
                assertThat(dn, containsString(user));
            }

            //lookup
            try (LdapSession ldap = unauthenticatedSession(sessionFactory, user)) {
                assertConnectionValid(ldap.getConnection(), sessionFactory.bindCredentials);
                String dn = ldap.userDn();
                assertThat(dn, containsString(user));
            }
        } finally {
            sessionFactory.close();
        }

        assertDeprecationWarnings(config.identifier(), useAttribute, useLegacyBindPassword);
    }

    public void testUserSearchWithBadAttributeFails() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";

        Settings.Builder builder = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());
        final boolean useLegacyBindPassword = configureBindPassword(builder);
        final boolean useAttribute = randomBoolean();
        if (useAttribute) {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE), "uid1");
        } else {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_FILTER), "(uid1={0})");
        }
        RealmConfig config = getRealmConfig(builder);

        LdapUserSearchSessionFactory sessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);

        String user = "William Bush";
        SecureString userPass = new SecureString("pass");

        try {
            assertNull(session(sessionFactory, user, userPass));
            assertNull(unauthenticatedSession(sessionFactory, user));
        } finally {
            sessionFactory.close();
        }

        assertDeprecationWarnings(config.identifier(), useAttribute, useLegacyBindPassword);
    }

    public void testUserSearchWithoutAttributePasses() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";

        final Settings.Builder realmSettings = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());
        final boolean useLegacyBindPassword = configureBindPassword(realmSettings);
        RealmConfig config = getRealmConfig(realmSettings);

        LdapUserSearchSessionFactory sessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);

        String user = "wbush";
        SecureString userPass = new SecureString("pass");

        try {
            //auth
            try (LdapSession ldap = session(sessionFactory, user, userPass)) {
                assertConnectionValid(ldap.getConnection(), sessionFactory.bindCredentials);
                String dn = ldap.userDn();
                assertThat(dn, containsString("William Bush"));
            }

            //lookup
            try (LdapSession ldap = unauthenticatedSession(sessionFactory, user)) {
                assertConnectionValid(ldap.getConnection(), sessionFactory.bindCredentials);
                String dn = ldap.userDn();
                assertThat(dn, containsString("William Bush"));
            }
        } finally {
            sessionFactory.close();
        }

        assertDeprecationWarnings(config.identifier(), false, useLegacyBindPassword);
    }

    public void testConnectionPoolDefaultSettings() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";
        final Settings.Builder realmSettings = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas");
        configureBindPassword(realmSettings);
        RealmConfig config = getRealmConfig(realmSettings);

        LDAPConnectionPool connectionPool = LdapUserSearchSessionFactory.createConnectionPool(config, new SingleServerSet("localhost",
                        randomFrom(ldapServers).getListenPort()), TimeValue.timeValueSeconds(5), NoOpLogger.INSTANCE,
                new SimpleBindRequest("cn=Horatio Hornblower,ou=people,o=sevenSeas", "pass"),
                () -> "cn=Horatio Hornblower,ou=people,o=sevenSeas");
        try {
            assertThat(connectionPool.getCurrentAvailableConnections(),
                    is(PoolingSessionFactorySettings.DEFAULT_CONNECTION_POOL_INITIAL_SIZE));
            assertThat(connectionPool.getMaximumAvailableConnections(),
                    is(PoolingSessionFactorySettings.DEFAULT_CONNECTION_POOL_SIZE));
            assertEquals(connectionPool.getHealthCheck().getClass(), GetEntryLDAPConnectionPoolHealthCheck.class);
            GetEntryLDAPConnectionPoolHealthCheck healthCheck = (GetEntryLDAPConnectionPoolHealthCheck) connectionPool.getHealthCheck();
            assertThat(healthCheck.getEntryDN(), is("cn=Horatio Hornblower,ou=people,o=sevenSeas"));
            assertThat(healthCheck.getMaxResponseTimeMillis(), is(SessionFactorySettings.TIMEOUT_DEFAULT.millis()));
        } finally {
            connectionPool.close();
        }
    }

    public void testConnectionPoolSettings() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";
        final Settings.Builder realmSettings = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=Horatio Hornblower,ou=people,o=sevenSeas")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.POOL_INITIAL_SIZE), 10)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.POOL_SIZE), 12)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.HEALTH_CHECK_ENABLED), false);
        configureBindPassword(realmSettings);
        RealmConfig config = getRealmConfig(realmSettings);

        LDAPConnectionPool connectionPool = LdapUserSearchSessionFactory.createConnectionPool(config, new SingleServerSet("localhost",
                        randomFrom(ldapServers).getListenPort()), TimeValue.timeValueSeconds(5), NoOpLogger.INSTANCE,
                new SimpleBindRequest("cn=Horatio Hornblower,ou=people,o=sevenSeas", "pass"),
                () -> "cn=Horatio Hornblower,ou=people,o=sevenSeas");
        try {
            assertThat(connectionPool.getCurrentAvailableConnections(), is(10));
            assertThat(connectionPool.getMaximumAvailableConnections(), is(12));
            assertThat(connectionPool.retryFailedOperationsDueToInvalidConnections(), is(true));
            assertEquals(connectionPool.getHealthCheck().getClass(), LDAPConnectionPoolHealthCheck.class);
        } finally {
            connectionPool.close();
        }
    }

    public void testThatEmptyBindDNWithHealthCheckEnabledDoesNotThrow() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";
        RealmConfig config = getRealmConfig(Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.LEGACY_BIND_PASSWORD), "pass"));

        LdapUserSearchSessionFactory searchSessionFactory = null;
        try {
            searchSessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);
        } finally {
            if (searchSessionFactory != null) {
                searchSessionFactory.close();
            }
        }

        assertDeprecationWarnings(config.identifier(), false, true);
    }

    public void testThatEmptyBindDNAndDisabledPoolingDoesNotThrow() throws Exception {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";
        RealmConfig config = getRealmConfig(Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), false)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.LEGACY_BIND_PASSWORD), "pass"));

        LdapUserSearchSessionFactory searchSessionFactory = null;
        try {
            searchSessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);
            final PlainActionFuture<LdapSession> future = new PlainActionFuture<>();
            searchSessionFactory.session("cn=ironman", new SecureString("password".toCharArray()), future);
            future.get();
        } finally {
            if (searchSessionFactory != null) {
                searchSessionFactory.close();
            }
        }

        assertDeprecationWarnings(config.identifier(), false, true);
    }

    public void testEmptyBindDNReturnsAnonymousBindRequest() throws LDAPException {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";
        final Settings.Builder realmSettings = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase);
        final boolean useLegacyBindPassword = configureBindPassword(realmSettings);
        RealmConfig config = new RealmConfig(REALM_IDENTIFIER,
                mergeSettings(realmSettings.build(), globalSettings),
                TestEnvironment.newEnvironment(globalSettings), new ThreadContext(globalSettings));
        try (LdapUserSearchSessionFactory searchSessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool)) {
            assertThat(searchSessionFactory.bindCredentials, notNullValue());
            assertThat(searchSessionFactory.bindCredentials.getBindDN(), isEmptyString());
        }
        assertDeprecationWarnings(config.identifier(), false, useLegacyBindPassword);
    }

    public void testThatBindRequestReturnsSimpleBindRequest() throws LDAPException {
        String groupSearchBase = "o=sevenSeas";
        String userSearchBase = "o=sevenSeas";
        final Settings.Builder realmSettings = Settings.builder()
                .put(buildLdapSettings(ldapUrls(), Strings.EMPTY_ARRAY, groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "cn=ironman")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase);
        final boolean useLegacyBindPassword = configureBindPassword(realmSettings);
        RealmConfig config = new RealmConfig(REALM_IDENTIFIER,
                mergeSettings(realmSettings.build(), globalSettings),
                TestEnvironment.newEnvironment(globalSettings), new ThreadContext(globalSettings));
        try (LdapUserSearchSessionFactory searchSessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool)) {
            assertThat(searchSessionFactory.bindCredentials, notNullValue());
            assertThat(searchSessionFactory.bindCredentials.getBindDN(), is("cn=ironman"));
        }
        assertDeprecationWarnings(config.identifier(), false, useLegacyBindPassword);
    }

    public void testThatConnectErrorIsNotThrownOnConstruction() throws Exception {
        String groupSearchBase = "DC=ad,DC=test,DC=elasticsearch,DC=com";
        String userSearchBase = "CN=Users,DC=ad,DC=test,DC=elasticsearch,DC=com";

        // pick a random ldap server and stop it
        InMemoryDirectoryServer inMemoryDirectoryServer = randomFrom(ldapServers);
        String ldapUrl = new LDAPURL("ldap", "localhost", inMemoryDirectoryServer.getListenPort(), null, null, null, null).toString();
        inMemoryDirectoryServer.shutDown(true);

        final Settings.Builder ldapSettingsBuilder = Settings.builder()
                .put(LdapTestCase.buildLdapSettings(new String[]{ldapUrl}, Strings.EMPTY_ARRAY,
                        groupSearchBase, LdapSearchScope.SUB_TREE))
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_BASE_DN), userSearchBase)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.BIND_DN), "ironman@ad.test.elasticsearch.com")
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE), "cn")
                .put("timeout.tcp_connect", "500ms")
                .put("type", "ldap")
                .put("user_search.pool.health_check.enabled", false)
                .put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER.getName(), LdapUserSearchSessionFactorySettings.POOL_ENABLED), randomBoolean());

        final boolean useLegacyBindPassword = configureBindPassword(ldapSettingsBuilder);
        RealmConfig config = getRealmConfig(ldapSettingsBuilder);
        LdapUserSearchSessionFactory searchSessionFactory = null;
        try {
            searchSessionFactory = getLdapUserSearchSessionFactory(config, sslService, threadPool);
        } finally {
            if (searchSessionFactory != null) {
                searchSessionFactory.close();
            }
        }

        assertDeprecationWarnings(config.identifier(), true, useLegacyBindPassword);
    }

    private void assertDeprecationWarnings(RealmConfig.RealmIdentifier realmIdentifier, boolean useAttribute, boolean legacyBindPassword) {
        List<Setting<?>> deprecatedSettings = new ArrayList<>();
        if (useAttribute) {
            deprecatedSettings.add(LdapUserSearchSessionFactorySettings.SEARCH_ATTRIBUTE
                    .getConcreteSettingForNamespace(realmIdentifier.getName())
            );
        }
        if (legacyBindPassword) {
            deprecatedSettings.add(PoolingSessionFactorySettings.LEGACY_BIND_PASSWORD
                    .apply(realmIdentifier.getType())
                    .getConcreteSettingForNamespace(realmIdentifier.getName()));
        }
        if (deprecatedSettings.size() > 0) {
            assertSettingDeprecationsAndWarnings(deprecatedSettings.toArray(new Setting<?>[deprecatedSettings.size()]));
        }
    }

    private boolean configureBindPassword(Settings.Builder builder) {
        final boolean useLegacyBindPassword = randomBoolean();
        if (useLegacyBindPassword) {
            builder.put(RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.LEGACY_BIND_PASSWORD), "pass");
        } else {
            final String secureKey = RealmSettings.getFullSettingKey(REALM_IDENTIFIER, PoolingSessionFactorySettings.SECURE_BIND_PASSWORD);
            builder.setSecureSettings(newSecureSettings(secureKey, "pass"));
        }
        return useLegacyBindPassword;
    }

    static LdapUserSearchSessionFactory getLdapUserSearchSessionFactory(RealmConfig config, SSLService sslService, ThreadPool threadPool)
            throws LDAPException {
        LdapUserSearchSessionFactory sessionFactory = new LdapUserSearchSessionFactory(config, sslService, threadPool);
        if (sessionFactory.getConnectionPool() != null) {
            // don't use this in production
            // used here to catch bugs that might get masked by an automatic retry
            sessionFactory.getConnectionPool().setRetryFailedOperationsDueToInvalidConnections(false);
        }
        return sessionFactory;
    }
}
