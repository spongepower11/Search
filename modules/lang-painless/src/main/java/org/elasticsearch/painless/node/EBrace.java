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
import org.elasticsearch.painless.lookup.PainlessLookupUtility;
import org.elasticsearch.painless.lookup.PainlessMethod;
import org.elasticsearch.painless.lookup.def;
import org.elasticsearch.painless.phase.DefaultSemanticAnalysisPhase;
import org.elasticsearch.painless.phase.UserTreeVisitor;
import org.elasticsearch.painless.symbol.Decorations.DefOptimized;
import org.elasticsearch.painless.symbol.Decorations.Explicit;
import org.elasticsearch.painless.symbol.Decorations.GetterPainlessMethod;
import org.elasticsearch.painless.symbol.Decorations.ListShortcut;
import org.elasticsearch.painless.symbol.Decorations.MapShortcut;
import org.elasticsearch.painless.symbol.Decorations.Read;
import org.elasticsearch.painless.symbol.Decorations.SetterPainlessMethod;
import org.elasticsearch.painless.symbol.Decorations.TargetType;
import org.elasticsearch.painless.symbol.Decorations.ValueType;
import org.elasticsearch.painless.symbol.Decorations.Write;
import org.elasticsearch.painless.symbol.SemanticScope;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an array load/store and defers to a child subnode.
 */
public class EBrace extends AExpression {

    private final AExpression prefixNode;
    private final AExpression indexNode;

    public EBrace(int identifier, Location location, AExpression prefixNode, AExpression indexNode) {
        super(identifier, location);

        this.prefixNode = Objects.requireNonNull(prefixNode);
        this.indexNode = Objects.requireNonNull(indexNode);
    }

    public AExpression getPrefixNode() {
        return prefixNode;
    }

    public AExpression getIndexNode() {
        return indexNode;
    }

    @Override
    public <Scope> void visit(UserTreeVisitor<Scope> userTreeVisitor, Scope scope) {
        userTreeVisitor.visitBrace(this, scope);
    }

    @Override
    public <Scope> void visitChildren(UserTreeVisitor<Scope> userTreeVisitor, Scope scope) {
        prefixNode.visit(userTreeVisitor, scope);
        indexNode.visit(userTreeVisitor, scope);
    }

    public static void visitDefaultSemanticAnalysis(
            DefaultSemanticAnalysisPhase visitor, EBrace userBraceNode, SemanticScope semanticScope) {

        boolean read = semanticScope.getCondition(userBraceNode, Read.class);
        boolean write = semanticScope.getCondition(userBraceNode, Write.class);

        if (read == false && write == false) {
            throw userBraceNode.createError(new IllegalArgumentException("not a statement: result of brace operator not used"));
        }

        AExpression userPrefixNode = userBraceNode.getPrefixNode();
        semanticScope.setCondition(userPrefixNode, Read.class);
        visitor.checkedVisit(userPrefixNode, semanticScope);
        Class<?> prefixValueType = semanticScope.getDecoration(userPrefixNode, ValueType.class).getValueType();

        AExpression userIndexNode = userBraceNode.getIndexNode();
        Class<?> valueType;

        if (prefixValueType.isArray()) {
            semanticScope.setCondition(userIndexNode, Read.class);
            semanticScope.putDecoration(userIndexNode, new TargetType(int.class));
            visitor.checkedVisit(userIndexNode, semanticScope);
            visitor.decorateWithCast(userIndexNode, semanticScope);
            valueType = prefixValueType.getComponentType();
        } else if (prefixValueType == def.class) {
            semanticScope.setCondition(userIndexNode, Read.class);
            visitor.checkedVisit(userIndexNode, semanticScope);
            TargetType targetType = semanticScope.getDecoration(userBraceNode, TargetType.class);
            // TODO: remove ZonedDateTime exception when JodaCompatibleDateTime is removed
            valueType = targetType == null || targetType.getTargetType() == ZonedDateTime.class ||
                    semanticScope.getCondition(userBraceNode, Explicit.class) ? def.class : targetType.getTargetType();
            semanticScope.setCondition(userBraceNode, DefOptimized.class);
        } else if (Map.class.isAssignableFrom(prefixValueType)) {
            String canonicalClassName = PainlessLookupUtility.typeToCanonicalTypeName(prefixValueType);

            PainlessMethod getter =
                    semanticScope.getScriptScope().getPainlessLookup().lookupPainlessMethod(prefixValueType, false, "get", 1);
            PainlessMethod setter =
                    semanticScope.getScriptScope().getPainlessLookup().lookupPainlessMethod(prefixValueType, false, "put", 2);

            if (getter != null && (getter.returnType == void.class || getter.typeParameters.size() != 1)) {
                throw userBraceNode.createError(new IllegalArgumentException(
                        "Illegal map get shortcut for type [" + canonicalClassName + "]."));
            }

            if (setter != null && setter.typeParameters.size() != 2) {
                throw userBraceNode.createError(new IllegalArgumentException(
                        "Illegal map set shortcut for type [" + canonicalClassName + "]."));
            }

            if (getter != null && setter != null && (!getter.typeParameters.get(0).equals(setter.typeParameters.get(0)) ||
                    getter.returnType.equals(setter.typeParameters.get(1)) == false)) {
                throw userBraceNode.createError(new IllegalArgumentException("Shortcut argument types must match."));
            }

            if ((read == false || getter != null) && (write == false || setter != null)) {
                semanticScope.setCondition(userIndexNode, Read.class);
                semanticScope.putDecoration(userIndexNode,
                        new TargetType(setter != null ? setter.typeParameters.get(0) : getter.typeParameters.get(0)));
                visitor.checkedVisit(userIndexNode, semanticScope);
                visitor.decorateWithCast(userIndexNode, semanticScope);
                valueType = setter != null ? setter.typeParameters.get(1) : getter.returnType;

                if (getter != null) {
                    semanticScope.putDecoration(userBraceNode, new GetterPainlessMethod(getter));
                }

                if (setter != null) {
                    semanticScope.putDecoration(userBraceNode, new SetterPainlessMethod(setter));
                }
            } else {
                throw userBraceNode.createError(new IllegalArgumentException(
                        "Illegal map shortcut for type [" + canonicalClassName + "]."));
            }

            semanticScope.setCondition(userBraceNode, MapShortcut.class);
        } else if (List.class.isAssignableFrom(prefixValueType)) {
            String canonicalClassName = PainlessLookupUtility.typeToCanonicalTypeName(prefixValueType);

            PainlessMethod getter =
                    semanticScope.getScriptScope().getPainlessLookup().lookupPainlessMethod(prefixValueType, false, "get", 1);
            PainlessMethod setter =
                    semanticScope.getScriptScope().getPainlessLookup().lookupPainlessMethod(prefixValueType, false, "set", 2);

            if (getter != null && (getter.returnType == void.class || getter.typeParameters.size() != 1 ||
                    getter.typeParameters.get(0) != int.class)) {
                throw userBraceNode.createError(new IllegalArgumentException(
                        "Illegal list get shortcut for type [" + canonicalClassName + "]."));
            }

            if (setter != null && (setter.typeParameters.size() != 2 || setter.typeParameters.get(0) != int.class)) {
                throw userBraceNode.createError(new IllegalArgumentException(
                        "Illegal list set shortcut for type [" + canonicalClassName + "]."));
            }

            if (getter != null && setter != null && (!getter.typeParameters.get(0).equals(setter.typeParameters.get(0))
                    || !getter.returnType.equals(setter.typeParameters.get(1)))) {
                throw userBraceNode.createError(new IllegalArgumentException("Shortcut argument types must match."));
            }

            if ((read == false || getter != null) && (write == false || setter != null)) {
                semanticScope.setCondition(userIndexNode, Read.class);
                semanticScope.putDecoration(userIndexNode, new TargetType(int.class));
                visitor.checkedVisit(userIndexNode, semanticScope);
                visitor.decorateWithCast(userIndexNode, semanticScope);
                valueType = setter != null ? setter.typeParameters.get(1) : getter.returnType;

                if (getter != null) {
                    semanticScope.putDecoration(userBraceNode, new GetterPainlessMethod(getter));
                }

                if (setter != null) {
                    semanticScope.putDecoration(userBraceNode, new SetterPainlessMethod(setter));
                }
            } else {
                throw userBraceNode.createError(new IllegalArgumentException(
                        "Illegal list shortcut for type [" + canonicalClassName + "]."));
            }

            semanticScope.setCondition(userBraceNode, ListShortcut.class);
        } else {
            throw userBraceNode.createError(new IllegalArgumentException("Illegal array access on type " +
                    "[" + PainlessLookupUtility.typeToCanonicalTypeName(prefixValueType) + "]."));
        }

        semanticScope.putDecoration(userBraceNode, new ValueType(valueType));
    }
}
