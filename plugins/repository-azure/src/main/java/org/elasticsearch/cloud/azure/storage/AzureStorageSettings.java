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

package org.elasticsearch.cloud.azure.storage;

import com.microsoft.azure.storage.RetryPolicy;
import org.elasticsearch.common.settings.SecureSetting;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.AffixSetting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AzureStorageSettings {
    // prefix for azure client settings
    private static final String PREFIX = "azure.client.";

    /**
     * Azure account name
     */
    public static final AffixSetting<SecureString> ACCOUNT_SETTING = Setting.affixKeySetting(PREFIX, "account",
        key -> SecureSetting.secureString(key, null));

    /**
     * max_retries: Number of retries in case of Azure errors. Defaults to 3 (RetryPolicy.DEFAULT_CLIENT_RETRY_COUNT).
     */
    private static final Setting<Integer> MAX_RETRIES_SETTING =
        Setting.affixKeySetting(PREFIX, "max_retries",
            (key) -> Setting.intSetting(key, RetryPolicy.DEFAULT_CLIENT_RETRY_COUNT, Setting.Property.NodeScope));

    /**
     * Azure key
     */
    public static final AffixSetting<SecureString> KEY_SETTING = Setting.affixKeySetting(PREFIX, "key",
        key -> SecureSetting.secureString(key, null));

    public static final AffixSetting<TimeValue> TIMEOUT_SETTING = Setting.affixKeySetting(PREFIX, "timeout",
        (key) -> Setting.timeSetting(key, TimeValue.timeValueMinutes(-1), Property.NodeScope));

    private final String account;
    private final String key;
    private final TimeValue timeout;
    private final int maxRetries;

    public AzureStorageSettings(String account, String key, TimeValue timeout, int maxRetries) {
        this.account = account;
        this.key = key;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
    }

    public String getKey() {
        return key;
    }

    public String getAccount() {
        return account;
    }

    public TimeValue getTimeout() {
        return timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AzureStorageSettings{");
        sb.append(", account='").append(account).append('\'');
        sb.append(", key='").append(key).append('\'');
        sb.append(", timeout=").append(timeout);
        sb.append(", maxRetries=").append(maxRetries);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parses settings and read all settings available under azure.client.*
     * @param settings settings to parse
     * @return All the named configurations
     */
    public static Map<String, AzureStorageSettings> load(Settings settings) {
        // Get the list of existing named configurations
        Set<String> clientNames = settings.getGroups(PREFIX).keySet();
        Map<String, AzureStorageSettings> storageSettings = new HashMap<>();
        for (String clientName : clientNames) {
            storageSettings.put(clientName, getClientSettings(settings, clientName));
        }

        if (storageSettings.containsKey("default") == false && storageSettings.isEmpty() == false) {
            // in case no setting named "default" has been set, let's define our "default"
            // as the first named config we get
            AzureStorageSettings defaultSettings = storageSettings.values().iterator().next();
            storageSettings.put("default", defaultSettings);
        }
        return Collections.unmodifiableMap(storageSettings);
    }

    // pkg private for tests
    /** Parse settings for a single client. */
    static AzureStorageSettings getClientSettings(Settings settings, String clientName) {
        try (SecureString account = getConfigValue(settings, clientName, ACCOUNT_SETTING);
             SecureString key = getConfigValue(settings, clientName, KEY_SETTING)) {
            return new AzureStorageSettings(account.toString(), key.toString(),
                getValue(settings, clientName, TIMEOUT_SETTING),
                getValue(settings, clientName, MAX_RETRIES_SETTING));
        }
    }

    private static <T> T getConfigValue(Settings settings, String clientName,
                                        Setting.AffixSetting<T> clientSetting) {
        Setting<T> concreteSetting = clientSetting.getConcreteSettingForNamespace(clientName);
        return concreteSetting.get(settings);
    }

    public static <T> T getValue(Settings settings, String groupName, Setting<T> setting) {
        Setting.AffixKey k = (Setting.AffixKey) setting.getRawKey();
        String fullKey = k.toConcreteKey(groupName).toString();
        return setting.getConcreteSetting(fullKey).get(settings);
    }
}
