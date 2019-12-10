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
package org.elasticsearch.test.rest.yaml.section;

import org.elasticsearch.Version;

public class VersionRange {
    private final Version lower;
    private final Version upper;

    public VersionRange(Version lower, Version upper) {
        this.lower = lower;
        this.upper = upper;
    }

    public Version lower() {
        return lower;
    }

    public Version upper() {
        return upper;
    }

    public boolean contain(Version currentVersion) {
        return lower != null && upper != null && currentVersion.onOrAfter(lower)
            && currentVersion.onOrBefore(upper);
    }

    @Override
    public String toString() {
        return "[" + lower + " - " + upper + "]";
    }
}
