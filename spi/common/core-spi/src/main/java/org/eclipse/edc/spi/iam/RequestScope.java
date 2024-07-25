/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.spi.iam;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Gets injected in the policy context on ingress/egress protocol calls to be able to append scopes needed
 * for the protocol request.
 */
public class RequestScope {

    private Set<String> scopes = new HashSet<>();

    private RequestScope() {
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public static class Builder {
        private final RequestScope requestScope;

        private Builder() {
            requestScope = new RequestScope();
        }

        public static RequestScope.Builder newInstance() {
            return new RequestScope.Builder();
        }

        public Builder scope(String scope) {
            requestScope.scopes.add(scope);
            return this;
        }

        public Builder scopes(Collection<String> scopes) {
            requestScope.scopes = new HashSet<>(scopes);
            return this;
        }

        public RequestScope build() {
            return requestScope;
        }
    }
}
