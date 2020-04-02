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

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.Scope;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.ir.ListSubShortcutNode;
import org.elasticsearch.painless.lookup.PainlessLookupUtility;
import org.elasticsearch.painless.lookup.PainlessMethod;
import org.elasticsearch.painless.symbol.ScriptRoot;

import java.util.Objects;

/**
 * Represents a list load/store shortcut.  (Internal only.)
 */
public class PSubListShortcut extends AStoreable {

    protected final Class<?> targetClass;
    protected final AExpression index;

    PSubListShortcut(Location location, Class<?> targetClass, AExpression index) {
        super(location);

        this.targetClass = Objects.requireNonNull(targetClass);
        this.index = Objects.requireNonNull(index);
    }

    @Override
    Output analyze(ClassNode classNode, ScriptRoot scriptRoot, Scope scope, AStoreable.Input input) {
        Output output = new Output();

        String canonicalClassName = PainlessLookupUtility.typeToCanonicalTypeName(targetClass);

        PainlessMethod getter = scriptRoot.getPainlessLookup().lookupPainlessMethod(targetClass, false, "get", 1);
        PainlessMethod setter = scriptRoot.getPainlessLookup().lookupPainlessMethod(targetClass, false, "set", 2);

        if (getter != null && (getter.returnType == void.class || getter.typeParameters.size() != 1 ||
            getter.typeParameters.get(0) != int.class)) {
            throw createError(new IllegalArgumentException("Illegal list get shortcut for type [" + canonicalClassName + "]."));
        }

        if (setter != null && (setter.typeParameters.size() != 2 || setter.typeParameters.get(0) != int.class)) {
            throw createError(new IllegalArgumentException("Illegal list set shortcut for type [" + canonicalClassName + "]."));
        }

        if (getter != null && setter != null && (!getter.typeParameters.get(0).equals(setter.typeParameters.get(0))
            || !getter.returnType.equals(setter.typeParameters.get(1)))) {
            throw createError(new IllegalArgumentException("Shortcut argument types must match."));
        }

        Output indexOutput = new Output();

        if ((input.read || input.write) && (input.read == false || getter != null) && (input.write == false || setter != null)) {
            Input indexInput = new Input();
            indexInput.expected = int.class;
            indexOutput = index.analyze(classNode, scriptRoot, scope, indexInput);
            index.cast(indexInput, indexOutput);

            output.actual = setter != null ? setter.typeParameters.get(1) : getter.returnType;
        } else {
            throw createError(new IllegalArgumentException("Illegal list shortcut for type [" + canonicalClassName + "]."));
        }

        ListSubShortcutNode listSubShortcutNode = new ListSubShortcutNode();

        listSubShortcutNode.setChildNode(index.cast(indexOutput));

        listSubShortcutNode.setLocation(location);
        listSubShortcutNode.setExpressionType(output.actual);
        listSubShortcutNode.setGetter(getter);
        listSubShortcutNode.setSetter(setter);

        output.expressionNode = listSubShortcutNode;

        return output;
    }

    @Override
    boolean isDefOptimized() {
        return false;
    }
}
