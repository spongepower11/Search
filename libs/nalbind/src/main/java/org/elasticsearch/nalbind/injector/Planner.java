/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nalbind.injector;

import org.elasticsearch.logging.LogManager;
import org.elasticsearch.logging.Logger;
import org.elasticsearch.nalbind.exceptions.InjectionConfigurationException;
import org.elasticsearch.nalbind.injector.spec.AliasSpec;
import org.elasticsearch.nalbind.injector.spec.AmbiguousSpec;
import org.elasticsearch.nalbind.injector.spec.ExistingInstanceSpec;
import org.elasticsearch.nalbind.injector.spec.InjectionSpec;
import org.elasticsearch.nalbind.injector.spec.MethodHandleSpec;
import org.elasticsearch.nalbind.injector.step.InjectionStep;
import org.elasticsearch.nalbind.injector.step.InstantiateStep;
import org.elasticsearch.nalbind.injector.step.ListProxyCreateStep;
import org.elasticsearch.nalbind.injector.step.ListProxyResolveStep;
import org.elasticsearch.nalbind.injector.step.RollUpStep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static org.elasticsearch.nalbind.injector.spec.InjectionModifiers.LIST;

/**
 * <em>Evolution note</em>: the intent is to plan one domain/subsystem at a time.
 */
final class Planner {
    final List<InjectionStep> plan;
    final Queue<Class<?>> queue;
    final Map<Class<?>, InjectionSpec> specsByClass;
    final Set<Class<?>> requiredTypes; // The injector's job is to ensure there is an instance of these; this is like the "root set"
    final Set<Class<?>> allParameterTypes; // All the injectable types in all dependencies (recursively) of all required types
    final Set<InjectionSpec> startedPlanning;
    final Set<InjectionSpec> finishedPlanning;
    final Set<Class<?>> alreadyProxied;

    /**
     * @param specsByClass an {@link InjectionSpec} indicating how each class should be injected
     * @param requiredTypes the classes of which we need instances
     * @param allParameterTypes the classes that appear as the type of any parameter of any constructor we might call
     */
    Planner(Map<Class<?>, InjectionSpec> specsByClass, Set<Class<?>> requiredTypes, Set<Class<?>> allParameterTypes) {
        this.requiredTypes = requiredTypes;
        this.plan = new ArrayList<>();
        this.specsByClass = unmodifiableMap(specsByClass);
        this.allParameterTypes = unmodifiableSet(allParameterTypes);
        this.startedPlanning = new HashSet<>();
        this.finishedPlanning = new HashSet<>();
        this.alreadyProxied = new HashSet<>();

        // Evolution note: this was a queue because we anticipated cases where the planner needs
        // to put items at the end rather than recursing. If that never turns out to be necessary,
        // this could be simplified away.
        this.queue = new ArrayDeque<>(requiredTypes);
    }

    /**
     * Intended to be called once.
     * <p>
     * Note that not all proxies are resolved once this plan has been executed.
     * <p>
     *
     * <em>Evolution note</em>: in a world with multiple domains/subsystems,
     * it will become necessary to defer proxy resolution until after other plans
     * have been executed, because they could create additional objects that ought
     * to be included in the proxies created by this plan.
     *
     * @return the {@link InjectionStep} objects listed in execution order.
     */
    List<InjectionStep> injectionPlan() {
        Class<?> c;
        while ((c = queue.poll()) != null) {
            planForClass(c, 0);
        }
        planProxyResolution(unresolvedProxies());
        return plan;
    }

    /**
     * An analysis that scans the current plan and finds proxies that are created but never resolved.
     *
     * @return the element types for list proxies that aren't resolved in the current plan
     */
    private Collection<Class<?>> unresolvedProxies() {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (InjectionStep step : plan) {
            if (step instanceof ListProxyCreateStep s) {
                result.add(s.elementType());
            } else if (step instanceof ListProxyResolveStep s) {
                result.remove(s.elementType());
            }
        }
        return result;
    }

    private void planProxyResolution(Collection<Class<?>> proxiesToResolve) {
        LOGGER.trace("Resolving {} remaining proxies", proxiesToResolve.size());
        proxiesToResolve.forEach(elementType -> rollUpAndResolveListProxy(elementType, 0, ""));
    }

    /**
     * Recursive procedure that determines what effect <code>requestedClass</code>
     * should have on the plan under construction.
     *
     * @param depth is used just for indenting the logs
     */
    private void planForClass(Class<?> requestedClass, int depth) {
        InjectionSpec spec = specsByClass.get(requestedClass);
        if (spec == null) {
            throw new InjectionConfigurationException("Cannot instantiate " + requestedClass);
        }
        planForSpec(spec, depth);
    }

    private void planForSpec(InjectionSpec spec, int depth) {
        String indent;
        if (LOGGER.isTraceEnabled()) {
            indent = "\t".repeat(depth);
        } else {
            indent = null;
        }

        if (finishedPlanning.contains(spec)) {
            LOGGER.trace("{}Already planned {}", indent, spec);
            return;
        }

        LOGGER.trace("{}Planning for {}", indent, spec);
        if (startedPlanning.add(spec) == false) {
            throw new InjectionConfigurationException("Cyclic dependency involving " + spec);
        }

        if (spec instanceof MethodHandleSpec m) {
            for (var p : m.parameters()) {
                if (p.canBeProxied()) {
                    if (alreadyProxied.add(p.injectableType())) {
                        addStep(new ListProxyCreateStep(p.injectableType()), indent);
                    } else {
                        LOGGER.trace("{}- Use existing proxy for {}", indent, p);
                    }
                } else {
                    LOGGER.trace("{}- Recursing into {} for actual parameter {}", indent, p.injectableType(), p);
                    planForClass(p.injectableType(), depth + 1);
                    if (p.modifiers().contains(LIST)) {
                        rollUpAndResolveListProxy(p.injectableType(), depth, indent);
                    }
                }
            }
            addStep(new InstantiateStep(m), indent);
        } else if (spec instanceof AliasSpec a) {
            LOGGER.trace("{}- Recursing into subtype for {}", indent, a);
            planForClass(a.subtype(), depth + 1);
            if (allParameterTypes.contains(a.requestedType()) == false) {
                // Could be an opportunity for optimization here.
                // The _only_ reason we need these unused aliases is in case
                // somebody asks for one directly from the injector; they are
                // not needed otherwise.
                // If we change the injector setup so the user must specify
                // which types they'll pull directly, we could skip these.
                LOGGER.trace("{}- Planning unused {}", indent, a);
            }
            addStep(new RollUpStep(a.requestedType(), a.subtype()), indent);
        } else if (spec instanceof ExistingInstanceSpec e) {
            LOGGER.trace("{}- Plan {}", indent, e);
            // Nothing to do. The injector will already have the required object.
        } else if (spec instanceof AmbiguousSpec a) {
            if (requiredTypes.contains(a.requestedType())) {
                throw new InjectionConfigurationException("Ambiguous injection spec for required type: " + a);
            } else {
                // Nobody could validly ask for an instance of an ambiguous class, so
                // this must be a class we encountered as a List.
                // Ensure we generate the necessary rollups to ensure the list has all the right objects.
                LOGGER.trace("{}- Processing candidates for {}", indent, a.requestedType());
                a.candidates().forEach(candidate -> planForSpec(candidate, depth + 1));
            }
        }

        finishedPlanning.add(spec);
    }

    private void addStep(InjectionStep newStep, String indent) {
        LOGGER.trace("{}- Add step {}", indent, newStep);
        plan.add(newStep);
    }

    private void rollUpAndResolveListProxy(Class<?> elementType, int depth, String indent) {
        LOGGER.trace("{}- Roll up and resolve {}", indent, elementType);
        planForClass(elementType, depth + 1);
        addStep(new ListProxyResolveStep(elementType), indent);
    }

    private static final Logger LOGGER = LogManager.getLogger(Planner.class);
}
