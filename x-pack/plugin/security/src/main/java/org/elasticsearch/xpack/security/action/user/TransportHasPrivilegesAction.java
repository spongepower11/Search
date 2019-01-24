/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.action.user;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.security.action.user.HasPrivilegesAction;
import org.elasticsearch.xpack.core.security.action.user.HasPrivilegesRequest;
import org.elasticsearch.xpack.core.security.action.user.HasPrivilegesResponse;
import org.elasticsearch.xpack.core.security.authc.Authentication;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptor;
import org.elasticsearch.xpack.core.security.authz.permission.ResourcePrivileges;
import org.elasticsearch.xpack.core.security.authz.permission.ResourcesPrivileges;
import org.elasticsearch.xpack.core.security.authz.permission.Role;
import org.elasticsearch.xpack.core.security.authz.privilege.ApplicationPrivilegeDescriptor;
import org.elasticsearch.xpack.core.security.authz.privilege.ClusterPrivilege;
import org.elasticsearch.xpack.core.security.user.User;
import org.elasticsearch.xpack.security.authz.AuthorizationService;
import org.elasticsearch.xpack.security.authz.store.NativePrivilegeStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transport action that tests whether a user has the specified
 * {@link RoleDescriptor.IndicesPrivileges privileges}
 */
public class TransportHasPrivilegesAction extends HandledTransportAction<HasPrivilegesRequest, HasPrivilegesResponse> {

    private final ThreadPool threadPool;
    private final AuthorizationService authorizationService;
    private final NativePrivilegeStore privilegeStore;

    @Inject
    public TransportHasPrivilegesAction(ThreadPool threadPool, TransportService transportService,
                                        ActionFilters actionFilters, AuthorizationService authorizationService,
                                        NativePrivilegeStore privilegeStore) {
        super(HasPrivilegesAction.NAME, transportService, actionFilters, HasPrivilegesRequest::new);
        this.threadPool = threadPool;
        this.authorizationService = authorizationService;
        this.privilegeStore = privilegeStore;
    }

    @Override
    protected void doExecute(Task task, HasPrivilegesRequest request, ActionListener<HasPrivilegesResponse> listener) {
        final String username = request.username();

        final Authentication authentication = Authentication.getAuthentication(threadPool.getThreadContext());
        final User user = authentication.getUser();
        if (user.principal().equals(username) == false) {
            listener.onFailure(new IllegalArgumentException("users may only check the privileges of their own account"));
            return;
        }

        authorizationService.roles(user, authentication, ActionListener.wrap(
            role -> resolveApplicationPrivileges(request, ActionListener.wrap(
                applicationPrivilegeLookup -> checkPrivileges(request, role, applicationPrivilegeLookup, listener),
                listener::onFailure)),
            listener::onFailure));
    }

    private void resolveApplicationPrivileges(HasPrivilegesRequest request,
                                              ActionListener<Collection<ApplicationPrivilegeDescriptor>> listener) {
        final Set<String> applications = getApplicationNames(request);
        privilegeStore.getPrivileges(applications, null, listener);
    }

    private Set<String> getApplicationNames(HasPrivilegesRequest request) {
        return Arrays.stream(request.applicationPrivileges())
            .map(RoleDescriptor.ApplicationResourcePrivileges::getApplication)
            .collect(Collectors.toSet());
    }

    private void checkPrivileges(HasPrivilegesRequest request, Role userRole,
                                 Collection<ApplicationPrivilegeDescriptor> applicationPrivileges,
                                 ActionListener<HasPrivilegesResponse> listener) {
        logger.trace(() -> new ParameterizedMessage("Check whether role [{}] has privileges cluster=[{}] index=[{}] application=[{}]",
            Strings.arrayToCommaDelimitedString(userRole.names()),
            Strings.arrayToCommaDelimitedString(request.clusterPrivileges()),
            Strings.arrayToCommaDelimitedString(request.indexPrivileges()),
            Strings.arrayToCommaDelimitedString(request.applicationPrivileges())
        ));

        Map<String, Boolean> cluster = new HashMap<>();
        for (String checkAction : request.clusterPrivileges()) {
            final ClusterPrivilege checkPrivilege = ClusterPrivilege.get(Collections.singleton(checkAction));
            cluster.put(checkAction, userRole.checkClusterPrivilege(checkPrivilege));
        }
        boolean allMatch = cluster.values().stream().allMatch(Boolean::booleanValue);

        final List<ResourcePrivileges> indices = new ArrayList<>();
        for (RoleDescriptor.IndicesPrivileges check : request.indexPrivileges()) {
            ResourcesPrivileges resourcePrivileges = userRole.getResourcePrivileges(Arrays.asList(check.getIndices()),
                    check.allowRestrictedIndices(), Arrays.asList(check.getPrivileges()));
            allMatch = allMatch && resourcePrivileges.allAllowed();
            indices.addAll(resourcePrivileges.getResourceToResourcePrivileges().values());
        }

        final Map<String, Collection<ResourcePrivileges>> privilegesByApplication = new HashMap<>();
        for (String applicationName : getApplicationNames(request)) {
            logger.debug("Checking privileges for application {}", applicationName);
            Map<String, ResourcePrivileges> allAppPrivsByResource = new LinkedHashMap<>();
            for (RoleDescriptor.ApplicationResourcePrivileges p : request.applicationPrivileges()) {
                ResourcesPrivileges appPrivsByResource = userRole.getResourcePrivileges(applicationName, Arrays.asList(p.getResources()),
                        Arrays.asList(p.getPrivileges()), applicationPrivileges);
                allMatch = allMatch && appPrivsByResource.allAllowed();
                appPrivsByResource.getResourceToResourcePrivileges().forEach((k1, v1) -> {
                    allAppPrivsByResource.compute(k1, (k0, v0) -> {
                        if (v0 == null) {
                            return v1;
                        } else {
                            Map<String, Boolean> newPrivs = new HashMap<>(v0.getPrivileges());
                            newPrivs.putAll(v1.getPrivileges());
                            return new ResourcePrivileges(k1, newPrivs);
                        }
                    });
                });
            }
            privilegesByApplication.put(applicationName, allAppPrivsByResource.values());
        }
        listener.onResponse(new HasPrivilegesResponse(request.username(), allMatch, cluster, indices, privilegesByApplication));
    }

}
