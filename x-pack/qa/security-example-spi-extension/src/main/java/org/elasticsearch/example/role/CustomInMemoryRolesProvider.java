/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.example.role;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A custom roles provider implementation for testing that serves
 * static roles from memory.
 */
public class CustomInMemoryRolesProvider
    extends AbstractComponent
    implements BiConsumer<Set<String>, ActionListener<Set<RoleDescriptor>>> {

    public static final String INDEX = "foo";
    public static final String ROLE_A = "roleA";
    public static final String ROLE_B = "roleB";

    private final Map<String, String> rolePermissionSettings;

    public CustomInMemoryRolesProvider(Settings settings, Map<String, String> rolePermissionSettings) {
        super(settings);
        this.rolePermissionSettings = rolePermissionSettings;
    }

    @Override
    public void accept(Set<String> roles, ActionListener<Set<RoleDescriptor>> listener) {
        Set<RoleDescriptor> roleDescriptors = new HashSet<>();
        for (String role : roles) {
            if (rolePermissionSettings.containsKey(role)) {
                roleDescriptors.add(
                new RoleDescriptor(role, new String[] { "all" },
                    new RoleDescriptor.IndicesPrivileges[] {
                        RoleDescriptor.IndicesPrivileges.builder()
                            .privileges(rolePermissionSettings.get(role))
                            .indices(INDEX)
                            .grantedFields("*")
                            .build()
                    }, null)
                );
            }
        }

        listener.onResponse(roleDescriptors);
    }
}
