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

package org.elasticsearch.painless;

import org.antlr.v4.runtime.ParserRuleContext;
import org.elasticsearch.painless.Definition.Cast;
import org.elasticsearch.painless.Definition.Type;

import java.util.HashMap;
import java.util.Map;

/**
 * Metadata is a wrapper for all the data that is collected by the {@link Analyzer}.  Each node in the ANTLR parse tree
 * will have one of the types of metadata to store information used either in a different node by the analyzer
 * or by the {@link Writer} during byte code generation.  Metadata also contains several objects passed into the
 * {@link Analyzer} and {@link Writer} used during compilation including the {@link Definition}, the source code,
 * the root of the ANTLR parse tree, and the {@link CompilerSettings}.
 */
class Metadata {
    /**
     * StatementMetadata is used to store metadata mostly about
     * control flow for ANTLR nodes related to if/else, do, while, for, etc.
     */
    static class StatementMetadata {
        /**
         * The source variable is the ANTLR node used to generate this metadata.
         */
        final ParserRuleContext source;

        /**
         * The lastSource variable will be set to true when the final statement from the root ANTLR node is about
         * to be visited.  This is used to determine whether or not the auto-return feature is allowed to be used,
         * and if a null return value needs to be generated automatically since a return value is always required.
         */
        boolean lastSource = false;

        /**
         * The beginLoop variable will be set to true whenever a loop node is initially visited including inner
         * loops.  This will not be propagated down the parse tree afterwards, though.  This is used to determine
         * whether or not inLoop should be set further down the tree.  Note that inLoop alone is not enough
         * information to determine whether we are in the last statement of a loop because we may inside of
         * multiple loops, so this variable is necessary.
         */
        boolean beginLoop = false;

        /**
         * The inLoop variable is set to true when inside a loop.  This will be propagated down the parse tree.  This
         * is used to determine whether or not continue and break statements are legal.
         */
        boolean inLoop = false;

        /**
         * The lastLoop variable is set to true when the final statement of a loop is reached.  This will be
         * propagated down the parse tree until another loop is reached and then will not be propagated further for
         * the current loop.  This is used to determine whether or not a continue statement is superfluous.
         */
        boolean lastLoop = false;

        /**
         * The methodEscape variable is set to true when a statement would cause the method to potentially exit.  This
         * includes return, throw, and continuous loop statements.  Note that a catch statement may possibly
         * reset this to false after a throw statement.  This will be propagated up the tree as far as necessary.
         * This is used by the {@link Writer} to ensure that superfluous statements aren't unnecessarily written
         * into generated bytecode.
         */
        boolean methodEscape = false;

        /**
         * The loopEscape variable is set to true when a loop is going to be exited.  This may be caused by a number of
         * different statements including continue, break, return, etc.  This will only be propagated as far as the
         * loop node.  This is used to ensure that in certain case an infinite loop will be caught at
         * compile-time rather than run-time.
         */
        boolean loopEscape = false;

        /**
         * The allLast variable is set whenever a final statement in a block is reached. This includes the end of loop,
         * if, else, etc.  This will be only propagated to the top of the block statement ANTLR node.
         * This is used to ensure that there are no unreachable statements within the script.
         */
        boolean allLast = false;

        /**
         * The anyContinue will be set to true when a continue statement is visited.  This will be propagated to the
         * loop node it's within.  This is used to ensure that in certain case an infinite loop will be caught at
         * compile-time rather than run-time.
         */
        boolean anyContinue = false;

        /**
         * The anyBreak will be set to true when a break statement is visited.  This will be propagated to the
         * loop node it's within.  This is used to in conjunction with methodEscape to ensure there are no unreachable
         * statements within the script.
         */
        boolean anyBreak = false;

        /**
         * The count variable is used as a rudimentary count of statements within a loop.  This will be used in
         * the {@link Writer} to keep a count of statements that have been executed at run-time to ensure that a loop
         * will exit if it runs too long.
         */
        int count = 0;

        /**
         * The exception variable is used to store the exception type when a throw node is visited.  This is used by
         * the {@link Writer} to write the correct type of exception in the generated byte code.
         */
        Type exception = null;

        /**
         * The slot variable is used to store the place on the stack of where a thrown exception will be stored to.
         * This is used by the {@link Writer}.
         */
        int slot = -1;

        /**
         * Constructor.
         * @param source The associated ANTLR node.
         */
        private StatementMetadata(final ParserRuleContext source) {
            this.source = source;
        }
    }

    /**
     * ExpressionMetadata is used to store metadata mostly about constants and casting
     * for ANTLR nodes related to mathematical operations.
     */
    static class ExpressionMetadata {
        /**
         * The source variable is the ANTLR node used to generate this metadata.
         */
        final ParserRuleContext source;

        /**
         * The read variable is used to determine whether or not the value of an expression will be read from.
         * This is set to false when the expression is the left-hand side of an assignment that is not chained or
         * when a method call is made alone.  This will propagate down the tree as far as necessary.
         * The {@link Writer} uses this to determine when a value may need to be popped from the stack
         * such as when a method call returns a value that is never read.
         */
        boolean read = true;

        /**
         * The statement variable is set true when an expression is a complete meaning that there is some sort
         * of effect on a variable or a method call is made.  This will propagate up the tree as far as necessary.
         * This prevents statements that have no effect on the output of a script from being executed.
         */
        boolean statement = false;

        /**
         * The preConst variable is set to a non-null value when a constant statement is made in a script.  This is
         * used to track the constant value prior to any casts being made on an ANTLR node.
         */
        Object preConst = null;

        /**
         * The postConst variable is set to a non-null value when a cast is made on a node where a preConst variable
         * has already been set when the cast would leave the constant as a non-object value except in the case of a
         * String.  This will be propagated up the tree and used to simplify constants when possible such as making
         * the value of 2*2 be 4 in the * node, so that the {@link Writer} only has to push a 4 onto the stack.
         */
        Object postConst = null;

        /**
         * The isNull variable is set to true when a null constant statement is made in the script.  This allows the
         * {@link Writer} to potentially shortcut certain comparison operations.
         */
        boolean isNull = false;

        /**
         * The to variable is used to track what an ANTLR node's value should be cast to.  This is set on every ANTLR
         * node in the tree, and used by the {@link Writer} to make a run-time cast if necessary in the byte code.
         * This is also used by the {@link Analyzer} to determine if a cast is legal.
         */
        Type to = null;

        /**
         * The from variable is used to track what an ANTLR node's value should be cast from.  This is set on every
         * ANTLR node in the tree independent of other nodes.  This is used by the {@link Analyzer} to determine if a
         * cast is legal.
         */
        Type from = null;

        /**
         * The explicit variable is set to true when a cast is explicitly made in the script.  This tracks whether
         * or not a cast is a legal up cast.
         */
        boolean explicit = false;

        /**
         * The typesafe variable is set to true when a dynamic type is used as part of an expression.  This propagates
         * up the tree to the top of the expression.  This allows for implicit up casts throughout the expression and
         * is used by the {@link Analyzer}.
         */
        boolean typesafe = true;

        /**
         * This is set to the combination of the to and from variables at the end of each node visit in the
         * {@link Analyzer}.  This is set on every ANTLR node in the tree independent of other nodes, and is
         * used by {@link Writer} to make a run-time cast if necessary in the byte code.
         */
        Cast cast = null;

        /**
         * Constructor.
         * @param source The associated ANTLR node.
         */
        private ExpressionMetadata(final ParserRuleContext source) {
            this.source = source;
        }
    }

    /**
     * ExternalMetadata is used to store metadata about the overall state of a variable/method chain such as
     * '(int)x.get(3)' where each piece of that chain is broken into it's indiviual pieces and stored in
     * {@link ExtNodeMetadata}.
     */
    static class ExternalMetadata {
        /**
         * The source variable is the ANTLR node used to generate this metadata.
         */
        final ParserRuleContext source;

        /**
         * The read variable is set to true when the value of a variable/method chain is going to be read from.
         * This is used by the {@link Analyzer} to determine if this variable/method chain will be in a standalone
         * statement.
         */
        boolean read = false;

        /**
         * The storeExpr variable is set to the right-hand side of an assignment in the variable/method chain if
         * necessary.  This is used by the {@link Analyzer} to set the proper metadata for a read versus a write,
         * and is used by the {@link Writer} to determine if a bytecode operation should be a load or a store.
         */
        ParserRuleContext storeExpr = null;

        /**
         * The token variable is set to a constant value of the operator type (+, -, etc.) when a compound assignment
         * is being visited.  This is also used by the increment and decrement operators.  This is used by both the
         * {@link Analyzer} and {@link Writer} to correctly handle the compound assignment.
         */
        int token = 0;

        /**
         * The pre variable is set to true when pre-increment or pre-decrement is visited.  This is used by both the
         * {@link Analyzer} and {@link Writer} to correctly handle any reads of the variable/method chain that are
         * necessary.
         */
        boolean pre = false;

        /**
         * The post variable is set to true when post-increment or post-decrement is visited. This is used by both the
         * {@link Analyzer} and {@link Writer} to correctly handle any reads of the variable/method chain that are
         * necessary.
         */
        boolean post = false;

        /**
         * The scope variable is incremented and decremented when a precedence node is visited as part of a
         * variable/method chain.  This is used by the {@link Analyzer} to determine when the final piece of the
         * variable/method chain has been reached.
         */
        int scope = 0;

        /**
         * The current variable is set to whatever the current type is within the visited node of the variable/method
         * chain.  This changes as the nodes for the variable/method are walked through.  This is used by the
         * {@link Analyzer} to make decisions about whether or not a cast is legal, and what methods are available
         * for that specific type.
         */
        Type current = null;

        /**
         * The statik variable is set to true when a variable/method chain begins with static type.  This is used by
         * the {@link Analyzer} to determine what methods/members are available for that specific type.
         */
        boolean statik = false;

        /**
         * The statement variable is set to true when a variable/method chain can be standalone statement.  This is
         * used by the {@link Analyzer} to error out if there a variable/method chain that is not a statement.
         */
        boolean statement = false;

        /**
         * The constant variable is set when a String constant is part of the variable/method chain.  String is a
         * special case because methods/members need to be able to be called on a String constant, so this can't be
         * only as part of {@link ExpressionMetadata}.  This is used by the {@link Writer} to write out the String
         * constant in the byte code.
         */
        Object constant = null;

        /**
         * Constructor.
         * @param source The associated ANTLR node.
         */
        private ExternalMetadata(final ParserRuleContext source) {
            this.source = source;
        }
    }

    static class ExtNodeMetadata {
        /**
         * The parent variable is top-level ANTLR node of the variable/method chain.  This is used to retrieve the
         * ExternalMetadata for the variable/method chain this ExtNodeMetadata is a piece of.
         */
        final ParserRuleContext parent;

        /**
         * The source variable is the ANTLR node used to generate this metadata.
         */
        final ParserRuleContext source;

        /**
         * The target variable is set to a value based on the type of ANTLR node that is visited.  This is used by
         * {@link Writer} to determine whether a cast, store, load, or method call should be written in byte code
         * depending on what the target variable is.
         */
        Object target = null;

        /**
         * The last variable is set to true when the last ANTLR node of the variable/method chain is visted.  This is
         * used by the {@link Writer} in conjuction with the storeExpr variable to determine whether or not a store
         * needs to be written as opposed to a load.
         */
        boolean last = false;

        /**
         * The type variable is set to the type that a visited node ends with.  This is used by both the
         * {@link Analyzer} and {@link Writer} to make decisions about compound assignments, String constants, and
         * shortcuts.
         */
        Type type = null;

        /**
         * The promote variable is set to the type of a promotion within a compound assignment.  Compound assignments
         * may require promotion between the left-hand side variable and right-hand side value.  This is used by the
         * {@link Writer} to make the correct decision about the byte code operation.
         */
        Type promote = null;

        /**
         * The castFrom variable is set during a compound assignment.  This is used by the {@link Writer} to
         * cast the values to the promoted type during a compound assignment.
         */
        Cast castFrom = null;

        /**
         * The castTo variable is set during an explicit cast in a variable/method chain or during a compound
         * assignment.  This is used by the {@link Writer} to either do an explicit cast, or cast the values
         * from the promoted type back to the original type during a compound assignment.
         */
        Cast castTo = null;

        /**
         * Constructor.
         * @param parent The top-level ANTLR node for the variable/method chain.
         * @param source The associated ANTLR node.
         */
        private ExtNodeMetadata(final ParserRuleContext parent, final ParserRuleContext source) {
            this.parent = parent;
            this.source = source;
        }
    }

    /**
     * Acts as both the Painless API and white-list for what types and methods are allowed.
     */
    final Definition definition;

    /**
     * The original text of the input script.  This is used to write out the source code into
     * the byte code file for debugging purposes.
     */
    final String source;

    /**
     * Toot node of the ANTLR tree for the Painless script.
     */
    final ParserRuleContext root;

    /**
     * Used to determine certain compile-time constraints such as whether or not numeric overflow is allowed
     * and how many statements are allowed before a loop will throw an exception.
     */
    final CompilerSettings settings;

    /**
     * Used to determine what slot the input variable is stored in.  This is used in the {@link Writer} whenever
     * the input variable is accessed.
     */
    int inputValueSlot = -1;

    /**
     * Used to determine what slot the loopCounter variable is stored in.  This is used n the {@link Writer} whenever
     * the loop variable is accessed.
     */
    int loopCounterSlot = -1;

    /**
     * Used to determine what slot the _score variable is stored in.  This is used in the {@link Writer} whenever
     * the score variable is accessed.
     */
    int scoreValueSlot = -1;

    /**
     * Used to determine if the _score variable is actually used.  This is used in the {@link Analyzer} to update
     * variable slots at the completion of analysis if _score is not used.
     */
    boolean scoreValueUsed = false;

    /**
     * Maps the relevant ANTLR node to its metadata.
     */
    private final Map<ParserRuleContext, StatementMetadata> statementMetadata = new HashMap<>();

    /**
     * Maps the relevant ANTLR node to its metadata.
     */
    private final Map<ParserRuleContext, ExpressionMetadata> expressionMetadata = new HashMap<>();

    /**
     * Maps the relevant ANTLR node to its metadata.
     */
    private final Map<ParserRuleContext, ExternalMetadata> externalMetadata = new HashMap<>();

    /**
     * Maps the relevant ANTLR node to its metadata.
     */
    private final Map<ParserRuleContext, ExtNodeMetadata> extNodeMetadata = new HashMap<>();

    /**
     * Constructor.
     * @param definition The Painless definition.
     * @param source The source text for the script.
     * @param root The root ANTLR node.
     * @param settings The compile-time settings.
     */
    Metadata(final Definition definition, final String source, final ParserRuleContext root, final CompilerSettings settings) {
        this.definition = definition;
        this.source = source;
        this.root = root;
        this.settings = settings;
    }

    /**
     * Creates a new StatementMetadata and stores it in the statementMetadata map.
     * @param source The ANTLR node for this metadata.
     * @return The new StatementMetadata.
     */
    StatementMetadata createStatementMetadata(final ParserRuleContext source) {
        final StatementMetadata sourcesmd = new StatementMetadata(source);
        statementMetadata.put(source, sourcesmd);

        return sourcesmd;
    }

    /**
     * Retrieves StatementMetadata from the statementMetadata map.
     * @param source The ANTLR node for this metadata.
     * @return The retrieved StatementMetadata.
     */
    StatementMetadata getStatementMetadata(final ParserRuleContext source) {
        final StatementMetadata sourcesmd = statementMetadata.get(source);

        if (sourcesmd == null) {
            throw new IllegalStateException("Statement metadata does not exist at" +
                " the parse node with text [" + source.getText() + "].");
        }

        return sourcesmd;
    }

    /**
     * Creates a new ExpressionMetadata and stores it in the expressionMetadata map.
     * @param source The ANTLR node for this metadata.
     * @return The new ExpressionMetadata.
     */
    ExpressionMetadata createExpressionMetadata(ParserRuleContext source) {
        final ExpressionMetadata sourceemd = new ExpressionMetadata(source);
        expressionMetadata.put(source, sourceemd);

        return sourceemd;
    }

    /**
     * Retrieves ExpressionMetadata from the expressionMetadata map.
     * @param source The ANTLR node for this metadata.
     * @return The retrieved ExpressionMetadata.
     */
    ExpressionMetadata getExpressionMetadata(final ParserRuleContext source) {
        final ExpressionMetadata sourceemd = expressionMetadata.get(source);

        if (sourceemd == null) {
            throw new IllegalStateException("Expression metadata does not exist at" +
                " the parse node with text [" + source.getText() + "].");
        }

        return sourceemd;
    }

    /**
     * Creates a new ExternalMetadata and stores it in the externalMetadata map.
     * @param source The ANTLR node for this metadata.
     * @return The new ExternalMetadata.
     */
    ExternalMetadata createExternalMetadata(final ParserRuleContext source) {
        final ExternalMetadata sourceemd = new ExternalMetadata(source);
        externalMetadata.put(source, sourceemd);

        return sourceemd;
    }

    /**
     * Retrieves ExternalMetadata from the externalMetadata map.
     * @param source The ANTLR node for this metadata.
     * @return The retrieved ExternalMetadata.
     */
    ExternalMetadata getExternalMetadata(final ParserRuleContext source) {
        final ExternalMetadata sourceemd = externalMetadata.get(source);

        if (sourceemd == null) {
            throw new IllegalStateException("Chain metadata does not exist at" +
                " the parse node with text [" + source.getText() + "].");
        }

        return sourceemd;
    }

    /**
     * Creates a new ExtNodeMetadata and stores it in the extNodeMetadata map.
     * @param source The ANTLR node for this metadata.
     * @return The new ExtNodeMetadata.
     */
    ExtNodeMetadata createExtNodeMetadata(final ParserRuleContext parent, final ParserRuleContext source) {
        final ExtNodeMetadata sourceemd = new ExtNodeMetadata(parent, source);
        extNodeMetadata.put(source, sourceemd);

        return sourceemd;
    }

    /**
     * Retrieves ExtNodeMetadata from the extNodeMetadata map.
     * @param source The ANTLR node for this metadata.
     * @return The retrieved ExtNodeMetadata.
     */
    ExtNodeMetadata getExtNodeMetadata(final ParserRuleContext source) {
        final ExtNodeMetadata sourceemd = extNodeMetadata.get(source);

        if (sourceemd == null) {
            throw new IllegalStateException("Chain metadata does not exist at" +
                " the parse node with text [" + source.getText() + "].");
        }

        return sourceemd;
    }
}
