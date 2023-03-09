/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.security.transport.netty4;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.transport.RemoteClusterPortSettings;
import org.elasticsearch.transport.TransportSettings;
import org.elasticsearch.xpack.core.security.transport.netty4.SecurityNetty4Transport.RemoteClusterClientBootStrapOptions;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class SecurityNetty4TransportTests extends ESTestCase {

    public void testRemoteClusterClientBootStrapOptions() {

        // 1. The default
        final Settings settings1 = Settings.builder().build();
        final var options1 = RemoteClusterClientBootStrapOptions.fromSettings(settings1);
        assertThat(options1.isEmpty(), is(true));

        // 2. Configuration for default profile only, _remote_cluster profile defaults to settings of the default profile
        final Settings settings2 = Settings.builder()
            .put(TransportSettings.TCP_NO_DELAY.getKey(), randomBoolean())
            .put(TransportSettings.TCP_KEEP_ALIVE.getKey(), randomBoolean())
            .put(TransportSettings.TCP_KEEP_IDLE.getKey(), randomIntBetween(-1, 300))
            .put(TransportSettings.TCP_KEEP_INTERVAL.getKey(), randomIntBetween(-1, 300))
            .put(TransportSettings.TCP_KEEP_COUNT.getKey(), randomIntBetween(-1, 300))
            .put(TransportSettings.TCP_SEND_BUFFER_SIZE.getKey(), ByteSizeValue.ofBytes(randomIntBetween(-1, 1000)))
            .put(TransportSettings.TCP_RECEIVE_BUFFER_SIZE.getKey(), ByteSizeValue.ofBytes(randomIntBetween(-1, 1000)))
            .put(TransportSettings.TCP_REUSE_ADDRESS.getKey(), randomBoolean())
            .build();
        final var options2 = RemoteClusterClientBootStrapOptions.fromSettings(settings2);
        assertThat(options2.isEmpty(), is(true));

        // 3. Configure different settings for _remote_cluster profile
        final Settings.Builder builder3 = Settings.builder();
        if (randomBoolean()) {
            builder3.put(TransportSettings.TCP_NO_DELAY.getKey(), true)
                .put(TransportSettings.TCP_KEEP_ALIVE.getKey(), true)
                .put(TransportSettings.TCP_KEEP_IDLE.getKey(), randomIntBetween(-1, 300))
                .put(TransportSettings.TCP_KEEP_INTERVAL.getKey(), randomIntBetween(-1, 300))
                .put(TransportSettings.TCP_KEEP_COUNT.getKey(), randomIntBetween(-1, 300))
                .put(TransportSettings.TCP_SEND_BUFFER_SIZE.getKey(), ByteSizeValue.ofBytes(-1))
                .put(TransportSettings.TCP_RECEIVE_BUFFER_SIZE.getKey(), ByteSizeValue.ofBytes(-1));
        }
        final Settings settings3 = builder3.put(RemoteClusterPortSettings.TCP_NO_DELAY.getKey(), false)
            .put(RemoteClusterPortSettings.TCP_KEEP_ALIVE.getKey(), false)
            .put(RemoteClusterPortSettings.TCP_SEND_BUFFER_SIZE.getKey(), ByteSizeValue.ofBytes(42))
            .put(RemoteClusterPortSettings.TCP_RECEIVE_BUFFER_SIZE.getKey(), ByteSizeValue.ofBytes(99))
            .put(RemoteClusterPortSettings.TCP_REUSE_ADDRESS.getKey(), false == TransportSettings.TCP_REUSE_ADDRESS.get(Settings.EMPTY))
            .build();
        final var options3 = RemoteClusterClientBootStrapOptions.fromSettings(settings3);
        assertThat(options3.isEmpty(), is(false));
        assertThat(options3.tcpNoDelay(), is(false));
        assertThat(options3.tcpKeepAlive(), is(false));
        assertThat(options3.tcpKeepIdle(), nullValue());
        assertThat(options3.tcpKeepInterval(), nullValue());
        assertThat(options3.tcpKeepCount(), nullValue());
        assertThat(options3.tcpSendBufferSize(), equalTo(ByteSizeValue.ofBytes(42)));
        assertThat(options3.tcpReceiveBufferSize(), equalTo(ByteSizeValue.ofBytes(99)));
        assertThat(options3.tcpReuseAddress(), notNullValue());

        // 4. Configure different keepIdle, keepInterval or keepCount
        final Settings.Builder builder4 = Settings.builder();
        if (randomBoolean()) {
            builder4.put(TransportSettings.TCP_NO_DELAY.getKey(), true)
                .put(TransportSettings.TCP_KEEP_ALIVE.getKey(), true)
                .put(TransportSettings.TCP_KEEP_IDLE.getKey(), 299)
                .put(TransportSettings.TCP_KEEP_INTERVAL.getKey(), 299)
                .put(TransportSettings.TCP_KEEP_COUNT.getKey(), 299)
                .put(TransportSettings.TCP_SEND_BUFFER_SIZE.getKey(), ByteSizeValue.ofBytes(-1))
                .put(TransportSettings.TCP_RECEIVE_BUFFER_SIZE.getKey(), ByteSizeValue.ofBytes(-1));
        }
        if (randomBoolean()) {
            builder4.put(RemoteClusterPortSettings.TCP_KEEP_ALIVE.getKey(), true);
        }
        final boolean differentKeepIdle = randomBoolean();
        if (differentKeepIdle) {
            builder4.put(RemoteClusterPortSettings.TCP_KEEP_IDLE.getKey(), 42);
        }
        final boolean differentKeepInterval = randomBoolean();
        final boolean differentKeepCount = false == differentKeepInterval;
        if (differentKeepInterval) {
            builder4.put(RemoteClusterPortSettings.TCP_KEEP_INTERVAL.getKey(), 42);
        }
        if (differentKeepCount) {
            builder4.put(RemoteClusterPortSettings.TCP_KEEP_COUNT.getKey(), 42);
        }

        final Settings settings4 = builder4.build();
        final var options4 = RemoteClusterClientBootStrapOptions.fromSettings(settings4);
        assertThat(options4.isEmpty(), is(false));
        assertThat(options4.tcpKeepAlive(), is(true));
        assertThat(options4.tcpKeepIdle(), differentKeepIdle ? equalTo(42) : nullValue());
        assertThat(options4.tcpKeepInterval(), differentKeepInterval ? equalTo(42) : nullValue());
        assertThat(options4.tcpKeepCount(), differentKeepCount ? equalTo(42) : nullValue());
    }

}
