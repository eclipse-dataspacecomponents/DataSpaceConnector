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

package org.eclipse.edc.api.auth.spi;

import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.configuration.Config;

/**
 * Provider for {@link AuthenticationService} with a configuration
 */
@FunctionalInterface
@ExtensionPoint
public interface ApiAuthenticationProvider {

    /**
     * Returns an {@link AuthenticationService} given the input configuration
     */
    Result<AuthenticationService> provide(Config config);
}
