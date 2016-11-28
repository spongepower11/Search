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
package org.elasticsearch.transport;

import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;

public class ConnectionProfileTests extends ESTestCase {

    public void testBuildConnectionProfile() {
        ConnectionProfile.Builder builder = new ConnectionProfile.Builder();
        TimeValue connectTimeout = TimeValue.timeValueMillis(randomIntBetween(1, 10));
        final boolean setConnectTimeout = randomBoolean();
        if (setConnectTimeout) {
            builder.setConnectTimeout(connectTimeout);
        }
        builder.addConnections(1, TransportRequestOptions.Type.BULK);
        builder.addConnections(2, TransportRequestOptions.Type.STATE, TransportRequestOptions.Type.RECOVERY);
        builder.addConnections(3, TransportRequestOptions.Type.PING);
        IllegalStateException illegalStateException = expectThrows(IllegalStateException.class, builder::build);
        assertEquals("not all types are added for this connection profile - missing types: [REG]", illegalStateException.getMessage());

        IllegalArgumentException illegalArgumentException = expectThrows(IllegalArgumentException.class,
            () -> builder.addConnections(4, TransportRequestOptions.Type.REG, TransportRequestOptions.Type.PING));
        assertEquals("type [PING] is already registered", illegalArgumentException.getMessage());
        builder.addConnections(4, TransportRequestOptions.Type.REG);
        ConnectionProfile build = builder.build();
        assertEquals(10, build.getNumConnections());
        if (setConnectTimeout) {
            assertEquals(connectTimeout, build.getConnectTimeout());
        } else {
            assertNull(build.getConnectTimeout());
        }
        Integer[] array = new Integer[10];
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
        final int numIters = randomIntBetween(5, 10);
        assertEquals(4, build.getHandles().size());
        assertEquals(0, build.getHandles().get(0).offset);
        assertEquals(1, build.getHandles().get(0).length);
        assertArrayEquals(new TransportRequestOptions.Type[] {TransportRequestOptions.Type.BULK}, build.getHandles().get(0).getTypes());
        Integer channel = build.getHandles().get(0).getChannel(array);
        for (int i = 0; i < numIters; i++) {
            assertEquals(0, channel.intValue());
        }

        assertEquals(1, build.getHandles().get(1).offset);
        assertEquals(2, build.getHandles().get(1).length);
        assertArrayEquals(new TransportRequestOptions.Type[] {TransportRequestOptions.Type.STATE, TransportRequestOptions.Type.RECOVERY},
            build.getHandles().get(1).getTypes());
        channel = build.getHandles().get(1).getChannel(array);
        for (int i = 0; i < numIters; i++) {
            assertThat(channel, Matchers.anyOf(Matchers.is(1), Matchers.is(2)));
        }

        assertEquals(3, build.getHandles().get(2).offset);
        assertEquals(3, build.getHandles().get(2).length);
        assertArrayEquals(new TransportRequestOptions.Type[] {TransportRequestOptions.Type.PING}, build.getHandles().get(2).getTypes());
        channel = build.getHandles().get(2).getChannel(array);
        for (int i = 0; i < numIters; i++) {
            assertThat(channel, Matchers.anyOf(Matchers.is(3), Matchers.is(4), Matchers.is(5)));
        }

        assertEquals(6, build.getHandles().get(3).offset);
        assertEquals(4, build.getHandles().get(3).length);
        assertArrayEquals(new TransportRequestOptions.Type[] {TransportRequestOptions.Type.REG}, build.getHandles().get(3).getTypes());
        channel = build.getHandles().get(3).getChannel(array);
        for (int i = 0; i < numIters; i++) {
            assertThat(channel, Matchers.anyOf(Matchers.is(6), Matchers.is(7), Matchers.is(8), Matchers.is(9)));
        }
    }
}
