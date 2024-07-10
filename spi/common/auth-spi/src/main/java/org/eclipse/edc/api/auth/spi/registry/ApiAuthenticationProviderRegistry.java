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

package org.eclipse.edc.api.auth.spi.registry;

import org.eclipse.edc.api.auth.spi.ApiAuthenticationProvider;
import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;

/**
 * Registry for {@link ApiAuthenticationProvider}
 */
@ExtensionPoint
public interface ApiAuthenticationProviderRegistry {
    /**
     * Register an {@link ApiAuthenticationProvider} for the specified type
     *
     * @param type     the type
     * @param provider the auth provider
     */
    void register(String type, ApiAuthenticationProvider provider);

    /**
     * Resolve an {@link ApiAuthenticationProvider} for the specified type
     *
     * @param type the type
     * @return The {@link ApiAuthenticationProvider} if found, otherwise null
     */
    ApiAuthenticationProvider resolve(String type);
}
