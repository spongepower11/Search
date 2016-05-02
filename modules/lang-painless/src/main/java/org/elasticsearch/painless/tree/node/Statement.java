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

package org.elasticsearch.painless.tree.node;

import org.objectweb.asm.Label;

public abstract class Statement extends Node {
    protected boolean lastSource = false;

    protected boolean beginLoop = false;
    protected boolean inLoop = false;
    protected boolean lastLoop = false;

    protected boolean methodEscape = false;
    protected boolean loopEscape = false;
    protected boolean allEscape = false;

    protected boolean anyContinue = false;
    protected boolean anyBreak = false;

    protected int statementCount = 0;

    protected Label continu = null;
    protected Label brake = null;

    public Statement(final String location) {
        super(location);
    }
}
