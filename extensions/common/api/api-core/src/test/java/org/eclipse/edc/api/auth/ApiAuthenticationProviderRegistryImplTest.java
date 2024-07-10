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

package org.eclipse.edc.api.auth;

import org.eclipse.edc.api.auth.spi.ApiAuthenticationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiAuthenticationProviderRegistryImplTest {

    private final ApiAuthenticationProviderRegistryImpl registry = new ApiAuthenticationProviderRegistryImpl();

    @Test
    void shouldResolveRegisteredProvider() {
        ApiAuthenticationProvider provider = mock();
        registry.register("authType", provider);

        var actual = registry.resolve("authType");

        assertThat(actual).isSameAs(provider);
    }

}
