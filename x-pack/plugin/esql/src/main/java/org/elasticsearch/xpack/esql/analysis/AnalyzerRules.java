/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.analysis;

import org.elasticsearch.xpack.esql.core.rule.ParameterizedRule;
import org.elasticsearch.xpack.esql.core.rule.Rule;
import org.elasticsearch.xpack.esql.plan.logical.LogicalPlan;

public final class AnalyzerRules {

    public abstract static class AnalyzerRule<SubPlan extends LogicalPlan> extends Rule<SubPlan, LogicalPlan> {

        // transformUp (post-order) - that is first children and then the node
        // but with a twist; only if the tree is not resolved or analyzed
        @Override
        public final LogicalPlan apply(LogicalPlan plan) {
            return plan.transformUp(typeToken(), t -> t.analyzed() || skipResolved() && t.resolved() ? t : rule(t));
        }

        protected abstract LogicalPlan rule(SubPlan plan);

        protected boolean skipResolved() {
            return true;
        }
    }

    public abstract static class ParameterizedAnalyzerRule<SubPlan extends LogicalPlan, P> extends ParameterizedRule<
        SubPlan,
        LogicalPlan,
        P> {

        // transformUp (post-order) - that is first children and then the node
        // but with a twist; only if the tree is not resolved or analyzed
        public final LogicalPlan apply(LogicalPlan plan, P context) {
            return plan.transformUp(typeToken(), t -> t.analyzed() || skipResolved() && t.resolved() ? t : rule(t, context));
        }

        protected abstract LogicalPlan rule(SubPlan plan, P context);

        protected boolean skipResolved() {
            return true;
        }
    }

    public abstract static class BaseAnalyzerRule extends AnalyzerRule<LogicalPlan> {

        @Override
        protected LogicalPlan rule(LogicalPlan plan) {
            if (plan.childrenResolved() == false) {
                return plan;
            }
            return doRule(plan);
        }

        protected abstract LogicalPlan doRule(LogicalPlan plan);
    }
}
