/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugins;

/**
 * An extension point for {@link Plugin} implementations to be themselves extensible.
 *
 * This class provides a callback for extensible plugins to be informed of other plugins
 * which extend them.
 */
public interface ExtensiblePlugin {

    /**
     * Reload any SPI implementations from the given classloader.
     */
    default void reloadSPI(ClassLoader loader) {}

    /**
     * Be notified of an extension plugin, will be called once per plugin having extendedPlugins pointing to this plugin.
     * @param plugin the extending plugin
     */
    default void extensionPlugin(Plugin plugin) {}
}
