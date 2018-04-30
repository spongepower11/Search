/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.authc.ldap;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPURL;
import com.unboundid.ldap.sdk.schema.Schema;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.MockSecureSettings;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.env.TestEnvironment;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;
import org.elasticsearch.xpack.core.security.authc.AuthenticationResult;
import org.elasticsearch.xpack.core.security.authc.RealmConfig;
import org.elasticsearch.xpack.core.security.authc.ldap.ActiveDirectorySessionFactorySettings;
import org.elasticsearch.xpack.core.security.authc.ldap.LdapRealmSettings;
import org.elasticsearch.xpack.core.security.authc.ldap.PoolingSessionFactorySettings;
import org.elasticsearch.xpack.core.security.authc.ldap.support.LdapLoadBalancingSettings;
import org.elasticsearch.xpack.core.security.authc.support.CachingUsernamePasswordRealmSettings;
import org.elasticsearch.xpack.core.security.authc.support.DnRoleMapperSettings;
import org.elasticsearch.xpack.core.security.authc.support.UsernamePasswordToken;
import org.elasticsearch.xpack.core.security.user.User;
import org.elasticsearch.xpack.core.ssl.SSLConfigurationSettings;
import org.elasticsearch.xpack.core.ssl.SSLService;
import org.elasticsearch.xpack.core.ssl.VerificationMode;
import org.elasticsearch.xpack.security.authc.ldap.ActiveDirectorySessionFactory.DownLevelADAuthenticator;
import org.elasticsearch.xpack.security.authc.ldap.ActiveDirectorySessionFactory.UpnADAuthenticator;
import org.elasticsearch.xpack.security.authc.support.DnRoleMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.elasticsearch.test.SecuritySettingsSource.getSettingKey;
import static org.elasticsearch.xpack.core.security.authc.ldap.support.SessionFactorySettings.HOSTNAME_VERIFICATION_SETTING;
import static org.elasticsearch.xpack.core.security.authc.ldap.support.SessionFactorySettings.URLS_SETTING;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Active Directory Realm tests that use the UnboundID In Memory Directory Server
 * <p>
 * AD is not LDAPv3 compliant so a workaround is needed
 * AD realm binds with userPrincipalName but this is not a valid DN, so we have to add a second userPrincipalName to the
 * users in the ldif in the form of CN=user@domain.com or a set the sAMAccountName to CN=user when testing authentication
 * with the sAMAccountName field.
 * <p>
 * The username used to authenticate then has to be in the form of CN=user. Finally the username needs to be added as an
 * additional bind DN with a password in the test setup since it really is not a DN in the ldif file
 */
public class ActiveDirectoryRealmTests extends ESTestCase {

    private static final String PASSWORD = "password";

    static int numberOfLdapServers;
    InMemoryDirectoryServer[] directoryServers;

    private ResourceWatcherService resourceWatcherService;
    private ThreadPool threadPool;
    private Settings globalSettings;
    private SSLService sslService;

    @BeforeClass
    public static void setNumberOfLdapServers() {
        numberOfLdapServers = randomIntBetween(1, 4);
    }

    @Before
    public void start() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=ad,dc=test,dc=elasticsearch,dc=com");
        // Get the default schema and overlay with the AD changes
        config.setSchema(Schema.mergeSchemas(Schema.getDefaultStandardSchema(),
                Schema.getSchema(getDataPath("ad-schema.ldif").toString())));

        // Add the bind users here since AD is not LDAPv3 compliant
        config.addAdditionalBindCredentials("CN=ironman@ad.test.elasticsearch.com", PASSWORD);
        config.addAdditionalBindCredentials("CN=Thor@ad.test.elasticsearch.com", PASSWORD);

        directoryServers = new InMemoryDirectoryServer[numberOfLdapServers];
        for (int i = 0; i < numberOfLdapServers; i++) {
            InMemoryDirectoryServer directoryServer = new InMemoryDirectoryServer(config);
            directoryServer.add("dc=ad,dc=test,dc=elasticsearch,dc=com", new Attribute("dc", "UnboundID"),
                    new Attribute("objectClass", "top", "domain", "extensibleObject"));
            directoryServer.importFromLDIF(false, getDataPath("ad.ldif").toString());
            // Must have privileged access because underlying server will accept socket connections
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                directoryServer.startListening();
                return null;
            });
            directoryServers[i] = directoryServer;
        }
        threadPool = new TestThreadPool("active directory realm tests");
        resourceWatcherService = new ResourceWatcherService(Settings.EMPTY, threadPool);
        globalSettings = Settings.builder().put("path.home", createTempDir()).build();
        sslService = new SSLService(globalSettings, TestEnvironment.newEnvironment(globalSettings));
    }

    @After
    public void stop() throws InterruptedException {
        resourceWatcherService.stop();
        terminate(threadPool);
        for (int i = 0; i < numberOfLdapServers; i++) {
            directoryServers[i].shutDown(true);
        }
    }

    @Override
    public boolean enableWarningsCheck() {
        return false;
    }

    public void testAuthenticateUserPrincipleName() throws Exception {
        final RealmConfig.RealmIdentifier realmIdentifier = realmId("testAuthenticateUserPrincipleName");
        Settings settings = settings(realmIdentifier);
        RealmConfig config = getRealmConfig(realmIdentifier, settings);
        ActiveDirectorySessionFactory sessionFactory = new ActiveDirectorySessionFactory(config, sslService, threadPool);
        DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
        LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

        PlainActionFuture<AuthenticationResult> future = new PlainActionFuture<>();
        realm.authenticate(new UsernamePasswordToken("CN=ironman", new SecureString(PASSWORD)), future);
        final AuthenticationResult result = future.actionGet();
        assertThat(result.getStatus(), is(AuthenticationResult.Status.SUCCESS));
        final User user = result.getUser();
        assertThat(user, is(notNullValue()));
        assertThat(user.roles(), arrayContaining(containsString("Avengers")));
    }

    public void testAuthenticateSAMAccountName() throws Exception {
        final RealmConfig.RealmIdentifier realmIdentifier = realmId("testAuthenticateSAMAccountName");
        Settings settings = settings(realmIdentifier);
        RealmConfig config = getRealmConfig(realmIdentifier, settings);
        ActiveDirectorySessionFactory sessionFactory = new ActiveDirectorySessionFactory(config, sslService, threadPool);
        DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
        LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

        // Thor does not have a UPN of form CN=Thor@ad.test.elasticsearch.com
        PlainActionFuture<AuthenticationResult> future = new PlainActionFuture<>();
        realm.authenticate(new UsernamePasswordToken("CN=Thor", new SecureString(PASSWORD)), future);
        User user = future.actionGet().getUser();
        assertThat(user, is(notNullValue()));
        assertThat(user.roles(), arrayContaining(containsString("Avengers")));
    }

    protected String[] ldapUrls() throws LDAPException {
        List<String> urls = new ArrayList<>(numberOfLdapServers);
        for (int i = 0; i < numberOfLdapServers; i++) {
            LDAPURL url = new LDAPURL("ldap", "localhost", directoryServers[i].getListenPort(), null, null, null, null);
            urls.add(url.toString());
        }
        return urls.toArray(Strings.EMPTY_ARRAY);
    }

    public void testAuthenticateCachesSuccessfulAuthentications() throws Exception {
        final RealmConfig.RealmIdentifier realmIdentifier = realmId("testAuthenticateCachesSuccesfulAuthentications");
        Settings settings = settings(realmIdentifier);
        RealmConfig config = getRealmConfig(realmIdentifier, settings);
        ActiveDirectorySessionFactory sessionFactory = spy(new ActiveDirectorySessionFactory(config, sslService, threadPool));
        DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
        LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

        int count = randomIntBetween(2, 10);
        for (int i = 0; i < count; i++) {
            PlainActionFuture<AuthenticationResult> future = new PlainActionFuture<>();
            realm.authenticate(new UsernamePasswordToken("CN=ironman", new SecureString(PASSWORD)), future);
            future.actionGet();
        }

        // verify one and only one session as further attempts should be returned from cache
        verify(sessionFactory, times(1)).session(eq("CN=ironman"), any(SecureString.class), any(ActionListener.class));
    }

    public void testAuthenticateCachingCanBeDisabled() throws Exception {
        final String realmName = "testAuthenticateCachingCanBeDisabled";
        final RealmConfig.RealmIdentifier realmIdentifier = realmId(realmName);

        Settings settings = settings(realmIdentifier, Settings.builder()
                .put(getSettingKey(CachingUsernamePasswordRealmSettings.CACHE_TTL_SETTING, realmIdentifier), -1)
                .build());
        RealmConfig config = getRealmConfig(realmIdentifier, settings);
        ActiveDirectorySessionFactory sessionFactory = spy(new ActiveDirectorySessionFactory(config, sslService, threadPool));
        DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
        LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

        int count = randomIntBetween(2, 10);
        for (int i = 0; i < count; i++) {
            PlainActionFuture<AuthenticationResult> future = new PlainActionFuture<>();
            realm.authenticate(new UsernamePasswordToken("CN=ironman", new SecureString(PASSWORD)), future);
            future.actionGet();
        }

        // verify one and only one session as second attempt should be returned from cache
        verify(sessionFactory, times(count)).session(eq("CN=ironman"), any(SecureString.class), any(ActionListener.class));
    }

    public void testAuthenticateCachingClearsCacheOnRoleMapperRefresh() throws Exception {
        final RealmConfig.RealmIdentifier realmIdentifier = realmId("testAuthenticateCachingClearsCacheOnRoleMapperRefresh");
        Settings settings = settings(realmIdentifier);
        RealmConfig config = getRealmConfig(realmIdentifier, settings);
        ActiveDirectorySessionFactory sessionFactory = spy(new ActiveDirectorySessionFactory(config, sslService, threadPool));
        DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
        LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

        int count = randomIntBetween(2, 10);
        for (int i = 0; i < count; i++) {
            PlainActionFuture<AuthenticationResult> future = new PlainActionFuture<>();
            realm.authenticate(new UsernamePasswordToken("CN=ironman", new SecureString(PASSWORD)), future);
            future.actionGet();
        }

        // verify one and only one session as further attempts should be returned from cache
        verify(sessionFactory, times(1)).session(eq("CN=ironman"), any(SecureString.class), any(ActionListener.class));

        // Refresh the role mappings
        roleMapper.notifyRefresh();

        for (int i = 0; i < count; i++) {
            PlainActionFuture<AuthenticationResult> future = new PlainActionFuture<>();
            realm.authenticate(new UsernamePasswordToken("CN=ironman", new SecureString(PASSWORD)), future);
            future.actionGet();
        }

        verify(sessionFactory, times(2)).session(eq("CN=ironman"), any(SecureString.class), any(ActionListener.class));
    }

    public void testUnauthenticatedLookupWithConnectionPool() throws Exception {
        doUnauthenticatedLookup(true);
    }

    public void testUnauthenticatedLookupWithoutConnectionPool() throws Exception {
        doUnauthenticatedLookup(false);
    }

    private void doUnauthenticatedLookup(boolean pooled) throws Exception {
        final RealmConfig.RealmIdentifier realmIdentifier = realmId("testUnauthenticatedLookupWithConnectionPool");

        final Settings.Builder builder = Settings.builder()
                .put(getSettingKey(ActiveDirectorySessionFactorySettings.POOL_ENABLED, realmIdentifier), pooled)
                .put(getSettingKey(PoolingSessionFactorySettings.BIND_DN, realmIdentifier), "CN=ironman@ad.test.elasticsearch.com");
        final boolean useLegacyBindPassword = randomBoolean();
        if (useLegacyBindPassword) {
            builder.put(getSettingKey(PoolingSessionFactorySettings.LEGACY_BIND_PASSWORD, realmIdentifier), PASSWORD);
        } else {
            final MockSecureSettings secureSettings = new MockSecureSettings();
            secureSettings.setString(getSettingKey(PoolingSessionFactorySettings.SECURE_BIND_PASSWORD, realmIdentifier), PASSWORD);
            builder.setSecureSettings(secureSettings);
        }
        Settings settings = settings(realmIdentifier, builder.build());
        RealmConfig config = getRealmConfig(realmIdentifier, settings);
        try (ActiveDirectorySessionFactory sessionFactory = new ActiveDirectorySessionFactory(config, sslService, threadPool)) {
            DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
            LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

            PlainActionFuture<User> future = new PlainActionFuture<>();
            realm.lookupUser("CN=Thor", future);
            final User user = future.actionGet();
            assertThat(user, notNullValue());
            assertThat(user.principal(), equalTo("CN=Thor"));
        }
    }

    public void testRealmMapsGroupsToRoles() throws Exception {
        final RealmConfig.RealmIdentifier realmId = realmId("testRealmMapsGroupsToRoles");
        Settings settings = settings(realmId, Settings.builder()
                .put(getSettingKey(DnRoleMapperSettings.ROLE_MAPPING_FILE_SETTING, realmId), getDataPath("role_mapping.yml"))
                .build());
        RealmConfig config = getRealmConfig(realmId, settings);
        ActiveDirectorySessionFactory sessionFactory = new ActiveDirectorySessionFactory(config, sslService, threadPool);
        DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
        LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

        PlainActionFuture<AuthenticationResult> future = new PlainActionFuture<>();
        realm.authenticate(new UsernamePasswordToken("CN=ironman", new SecureString(PASSWORD)), future);
        User user = future.actionGet().getUser();
        assertThat(user, is(notNullValue()));
        assertThat(user.roles(), arrayContaining(equalTo("group_role")));
    }

    public void testRealmMapsUsersToRoles() throws Exception {
        final RealmConfig.RealmIdentifier realmId = realmId("testRealmMapsGroupsToRoles");
        Settings settings = settings(realmId, Settings.builder()
                .put(getSettingKey(DnRoleMapperSettings.ROLE_MAPPING_FILE_SETTING, realmId), getDataPath("role_mapping.yml"))
                .build());
        RealmConfig config = getRealmConfig(realmId, settings);
        ActiveDirectorySessionFactory sessionFactory = new ActiveDirectorySessionFactory(config, sslService, threadPool);
        DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
        LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

        PlainActionFuture<AuthenticationResult> future = new PlainActionFuture<>();
        realm.authenticate(new UsernamePasswordToken("CN=Thor", new SecureString(PASSWORD)), future);
        User user = future.actionGet().getUser();
        assertThat(user, is(notNullValue()));
        assertThat(user.roles(), arrayContainingInAnyOrder(equalTo("group_role"), equalTo("user_role")));
    }

    public void testRealmUsageStats() throws Exception {
        final RealmConfig.RealmIdentifier realmId = realmId("testRealmUsageStats");
        String loadBalanceType = randomFrom("failover", "round_robin");
        Settings settings = settings(realmId, Settings.builder()
                .put(getSettingKey(DnRoleMapperSettings.ROLE_MAPPING_FILE_SETTING, realmId), getDataPath("role_mapping.yml"))
                .put(getSettingKey(LdapLoadBalancingSettings.LOAD_BALANCE_TYPE_SETTING, realmId), loadBalanceType)
                .build());
        RealmConfig config = getRealmConfig(realmId, settings);
        ActiveDirectorySessionFactory sessionFactory = new ActiveDirectorySessionFactory(config, sslService, threadPool);
        DnRoleMapper roleMapper = new DnRoleMapper(config, resourceWatcherService);
        LdapRealm realm = new LdapRealm(config, sessionFactory, roleMapper, threadPool);

        Map<String, Object> stats = realm.usageStats();
        assertThat(stats, is(notNullValue()));
        assertThat(stats, hasEntry("name", realm.name()));
        assertThat(stats, hasEntry("order", realm.order()));
        assertThat(stats, hasEntry("size", 0));
        assertThat(stats, hasEntry("ssl", false));
        assertThat(stats, hasEntry("load_balance_type", loadBalanceType));
    }

    public void testDefaultSearchFilters() throws Exception {
        final RealmConfig.RealmIdentifier realmIdentifier = realmId("testDefaultSearchFilters");
        Settings settings = settings(realmIdentifier);
        RealmConfig config = getRealmConfig(realmIdentifier, settings);
        ActiveDirectorySessionFactory sessionFactory = new ActiveDirectorySessionFactory(config, sslService, threadPool);
        assertEquals("(&(objectClass=user)(|(sAMAccountName={0})(userPrincipalName={0}@ad.test.elasticsearch.com)))",
                sessionFactory.defaultADAuthenticator.getUserSearchFilter());
        assertEquals(UpnADAuthenticator.UPN_USER_FILTER, sessionFactory.upnADAuthenticator.getUserSearchFilter());
        assertEquals(DownLevelADAuthenticator.DOWN_LEVEL_FILTER, sessionFactory.downLevelADAuthenticator.getUserSearchFilter());
    }

    public void testCustomSearchFilters() throws Exception {
        final RealmConfig.RealmIdentifier realmId = realmId("testDefaultSearchFilters");
        Settings settings = settings(realmId, Settings.builder()
                .put(getSettingKey(ActiveDirectorySessionFactorySettings.AD_USER_SEARCH_FILTER_SETTING, realmId), "(objectClass=default)")
                .put(getSettingKey(ActiveDirectorySessionFactorySettings.AD_UPN_USER_SEARCH_FILTER_SETTING, realmId), "(objectClass=upn)")
                .put(getSettingKey(ActiveDirectorySessionFactorySettings.AD_DOWN_LEVEL_USER_SEARCH_FILTER_SETTING, realmId),
                        "(objectClass=down level)")
                .build());
        RealmConfig config = getRealmConfig(realmId, settings);
        ActiveDirectorySessionFactory sessionFactory = new ActiveDirectorySessionFactory(config, sslService, threadPool);
        assertEquals("(objectClass=default)", sessionFactory.defaultADAuthenticator.getUserSearchFilter());
        assertEquals("(objectClass=upn)", sessionFactory.upnADAuthenticator.getUserSearchFilter());
        assertEquals("(objectClass=down level)", sessionFactory.downLevelADAuthenticator.getUserSearchFilter());
    }

    private RealmConfig getRealmConfig(RealmConfig.RealmIdentifier realmIdentifier, Settings localSettings) {
        final Settings mergedSettings = Settings.builder().put(globalSettings).put(localSettings).build();
        return new RealmConfig(
                realmIdentifier,
                mergedSettings,
                TestEnvironment.newEnvironment(globalSettings), new ThreadContext(globalSettings)
        );
    }

    public RealmConfig.RealmIdentifier realmId(String realmName) {
        return new RealmConfig.RealmIdentifier(LdapRealmSettings.AD_TYPE, realmName.toLowerCase(Locale.ROOT));
    }

    private Settings settings(RealmConfig.RealmIdentifier realmIdentifier) throws Exception {
        return settings(realmIdentifier, Settings.EMPTY);
    }

    private Settings settings(RealmConfig.RealmIdentifier realmIdentifier, Settings extraSettings) throws Exception {
        Settings.Builder builder = Settings.builder()
                .putList(getSettingKey(URLS_SETTING, realmIdentifier), ldapUrls())
                .put(getSettingKey(ActiveDirectorySessionFactorySettings.AD_DOMAIN_NAME_SETTING, realmIdentifier),
                        "ad.test.elasticsearch.com")
                .put(getSettingKey(DnRoleMapperSettings.USE_UNMAPPED_GROUPS_AS_ROLES_SETTING, realmIdentifier), true);
        if (randomBoolean()) {
            builder.put(getSettingKey(SSLConfigurationSettings.VERIFICATION_MODE_SETTING_REALM, realmIdentifier),
                    VerificationMode.CERTIFICATE);
        } else {
            builder.put(getSettingKey(HOSTNAME_VERIFICATION_SETTING, realmIdentifier), false);
        }
        return builder.put(extraSettings).build();
    }
}
