/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.security.authz;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.xpack.core.security.authc.Authentication;

public interface AuthorizationEngine {

    void authorizeRunAs(Authentication authentication, TransportRequest request, String action,
                        ActionListener<AuthorizationResult> listener);

    class AuthorizationResult {

        private final boolean granted;
        private final boolean auditable;

        public AuthorizationResult(boolean granted) {
            this(granted, true);
        }

        public AuthorizationResult(boolean granted, boolean auditable) {
            this.granted = granted;
            this.auditable = auditable;
        }

        public boolean isGranted() {
            return granted;
        }

        public boolean isAuditable() {
            return auditable;
        }

        public static AuthorizationResult granted() {
            return new AuthorizationResult(true);
        }

        public static AuthorizationResult deny() {
            return new AuthorizationResult(false);
        }
    }
}
