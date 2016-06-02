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

package org.elasticsearch.threadpool;

import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.SizeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.util.concurrent.EsThreadPoolExecutor;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.node.Node;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class FixedExecutorBuilder extends ExecutorBuilder<FixedExecutorBuilder.FixedExecutorSettings> {

    private final Setting<Integer> sizeSetting;
    private final Setting<Integer> queueSizeSetting;

    FixedExecutorBuilder(final Settings settings, final String name, final int size, final int queueSize) {
        this(settings, name, size, queueSize, "thread_pool." + name);
    }

    public FixedExecutorBuilder(final Settings settings, final String name, final int size, final int queueSize, final String prefix) {
        super(name);
        final String sizeKey = settingsKey(prefix, "size");
        this.sizeSetting =
            new Setting<>(
                sizeKey,
                s -> Integer.toString(size),
                s -> Setting.parseInt(s, 1, applyHardSizeLimit(settings, name), sizeKey),
                Setting.Property.Dynamic, Setting.Property.NodeScope);
        final String queueSizeKey = settingsKey(prefix, "queue_size");
        this.queueSizeSetting =
            Setting.intSetting(queueSizeKey, queueSize, Setting.Property.Dynamic, Setting.Property.NodeScope);
    }

    private int applyHardSizeLimit(final Settings settings, final String name) {
        if (name.equals(ThreadPool.Names.BULK) || name.equals(ThreadPool.Names.INDEX)) {
            return EsExecutors.boundedNumberOfProcessors(settings);
        } else {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public List<Setting<?>> registerSettings() {
        return Arrays.asList(sizeSetting, queueSizeSetting);
    }

    @Override
    public FixedExecutorSettings settings(Settings settings) {
        final String nodeName = Node.NODE_NAME_SETTING.get(settings);
        final int size = sizeSetting.get(settings);
        final int queueSize = queueSizeSetting.get(settings);
        return new FixedExecutorSettings(nodeName, size, queueSize);
    }

    @Override
    public ThreadPool.ExecutorHolder holder(final FixedExecutorSettings settings, final ThreadContext threadContext) {
        int size = settings.size;
        int queueSize = settings.queueSize;
        final ThreadFactory threadFactory = EsExecutors.daemonThreadFactory(EsExecutors.threadName(settings.nodeName, name()));
        Executor executor = EsExecutors.newFixed(name(), size, queueSize, threadFactory, threadContext);
        final ThreadPool.Info info =
            new ThreadPool.Info(name(), ThreadPool.ThreadPoolType.FIXED, size, size, null, queueSizeValue(queueSize));
        return new ThreadPool.ExecutorHolder(executor, info);
    }

    @Override
    public String formatInfo(ThreadPool.Info info) {
        return String.format(
            Locale.ROOT,
            "name [%s], size [%d], queue size [%s]",
            info.getName(),
            info.getMax(),
            info.getQueueSize() == null ? "unbounded" : info.getQueueSize());
    }

    private static SizeValue queueSizeValue(int queueSize) {
        return queueSize < 0 ? null : new SizeValue(queueSize);
    }

    static class FixedExecutorSettings extends ExecutorBuilder.ExecutorSettings {

        private final int size;
        private final int queueSize;

        public FixedExecutorSettings(final String nodeName, final int size, final int queueSize) {
            super(nodeName);
            this.size = size;
            this.queueSize = queueSize;
        }

    }

}
