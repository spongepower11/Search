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

package org.elasticsearch.gradle.util;

import org.elasticsearch.gradle.info.GlobalBuildInfoPlugin;
import org.gradle.api.GradleException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class Util {

    public static boolean getBooleanProperty(String property, boolean defaultValue) {
        String propertyValue = System.getProperty(property);
        if (propertyValue == null) {
            return defaultValue;
        }
        if ("true".equals(propertyValue)) {
            return true;
        } else if ("false".equals(propertyValue)) {
            return false;
        } else {
            throw new GradleException("Sysprop [" + property + "] must be [true] or [false] but was [" + propertyValue + "]");
        }
    }

    public static String getResourceContents(String resourcePath) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(GlobalBuildInfoPlugin.class.getResourceAsStream(resourcePath)))
        ) {
            StringBuilder b = new StringBuilder();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (b.length() != 0) {
                    b.append('\n');
                }
                b.append(line);
            }

            return b.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Error trying to read classpath resource: " + resourcePath, e);
        }
    }

    public static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    public static URI getBuildSrcCodeSource() {
        try {
            return Util.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new GradleException("Error determining build tools JAR location", e);
        }
    }
}
