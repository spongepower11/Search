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

package org.elasticsearch.painless.ir;

import org.elasticsearch.painless.ClassWriter;
import org.elasticsearch.painless.Globals;
import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.MethodWriter;
import org.elasticsearch.painless.lookup.PainlessMethod;

public class DotSubShortcutNode extends ExpressionNode {

    /* begin tree structure */

    @Override
    public DotSubShortcutNode setTypeNode(TypeNode typeNode) {
        super.setTypeNode(typeNode);
        return this;
    }

    /* ---- end tree structure, begin node data ---- */

    protected PainlessMethod setter;
    protected PainlessMethod getter;

    public DotSubShortcutNode setSetter(PainlessMethod setter) {
        this.setter = setter;
        return this;
    }

    public PainlessMethod getSetter() {
        return setter;
    }

    public DotSubShortcutNode setGetter(PainlessMethod getter) {
        this.getter = getter;
        return this;
    }

    public PainlessMethod getGetter() {
        return getter;
    }


    @Override
    public DotSubShortcutNode setLocation(Location location) {
        super.setLocation(location);
        return this;
    }

    /* ---- end node data ---- */

    public DotSubShortcutNode() {
        // do nothing
    }

    @Override
    protected void write(ClassWriter classWriter, MethodWriter methodWriter, Globals globals) {
        methodWriter.writeDebugInfo(location);

        methodWriter.invokeMethodCall(getter);

        if (!getter.returnType.equals(getter.javaMethod.getReturnType())) {
            methodWriter.checkCast(MethodWriter.getType(getter.returnType));
        }
    }

    @Override
    protected int accessElementCount() {
        return 1;
    }

    @Override
    protected void setup(ClassWriter classWriter, MethodWriter methodWriter, Globals globals) {
        // do nothing
    }

    @Override
    protected void load(ClassWriter classWriter, MethodWriter methodWriter, Globals globals) {
        methodWriter.writeDebugInfo(location);

        methodWriter.invokeMethodCall(getter);

        if (getter.returnType != getter.javaMethod.getReturnType()) {
            methodWriter.checkCast(MethodWriter.getType(getter.returnType));
        }
    }

    @Override
    protected void store(ClassWriter classWriter, MethodWriter methodWriter, Globals globals) {
        methodWriter.writeDebugInfo(location);

        methodWriter.invokeMethodCall(setter);

        methodWriter.writePop(MethodWriter.getType(setter.returnType).getSize());
    }
}
