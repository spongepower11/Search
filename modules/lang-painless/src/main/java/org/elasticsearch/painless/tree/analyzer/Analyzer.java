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

package org.elasticsearch.painless.tree.analyzer;

import org.elasticsearch.painless.compiler.CompilerSettings;
import org.elasticsearch.painless.compiler.Definition;
import org.elasticsearch.painless.tree.node.SSource;
import org.elasticsearch.painless.tree.utility.Variables;
import org.elasticsearch.painless.tree.utility.Variables.Shortcut;

public class Analyzer {
    public static Variables analyze(final CompilerSettings settings, final Definition definition,
                                    final Shortcut shortcut, final SSource root) {
        final Variables variables = new Variables(settings, definition, shortcut);
        root.analyze(settings, definition, variables);

        return variables;
    }

    private Analyzer() {}
}
