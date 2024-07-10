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

package org.eclipse.edc.api.auth.delegated;

import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationRegistry;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.nimbusds.jose.jwk.source.JWKSourceBuilder.DEFAULT_CACHE_TIME_TO_LIVE;
import static org.eclipse.edc.api.auth.delegated.DelegatedAuthenticationExtension.AUTH_CACHE_VALIDITY_MS;
import static org.eclipse.edc.api.auth.delegated.DelegatedAuthenticationExtension.AUTH_KEY_URL;
import static org.eclipse.edc.api.auth.delegated.DelegatedAuthenticationExtension.AUTH_SETTING_KEY_URL;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class DelegatedAuthenticationExtensionTest {

    private final Monitor monitor = mock(Monitor.class);
    private final ApiAuthenticationRegistry registry = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
        when(context.getMonitor()).thenReturn(monitor);
        context.registerService(ApiAuthenticationRegistry.class, registry);
    }

    @Test
    void initialize(DelegatedAuthenticationExtension extension, ServiceExtensionContext context) {

        var configMock = mock(Config.class);
        when(configMock.getString(eq(AUTH_SETTING_KEY_URL), eq(null))).thenReturn("http://foo.bar/.well-known/jwks.json");
        when(context.getConfig()).thenReturn(configMock);

        extension.initialize(context);

        verify(registry).register(eq("management-api"), isA(DelegatedAuthenticationService.class));
    }

    @Test
    void initialize_noUrlGiven_shouldNotRegister(DelegatedAuthenticationExtension extension, ServiceExtensionContext context) {

        extension.initialize(context);

        verify(monitor).warning("The '%s' setting was not provided, so the DelegatedAuthenticationService will NOT be registered. In this case, the TokenBasedAuthenticationService usually acts as fallback.".formatted(AUTH_SETTING_KEY_URL));
        verify(registry, never()).register(eq("management-api"), isA(DelegatedAuthenticationService.class));
    }

    @Test
    public void delegatedProvider(DelegatedAuthenticationExtension extension) {

        var config = mock(Config.class);

        when(config.getString(AUTH_KEY_URL)).thenReturn("http://url");

        assertThat(extension.delegatedProvider(monitor, config))
                .isSucceeded().isInstanceOf(DelegatedAuthenticationService.class);


        verify(config).getString(AUTH_KEY_URL);
        verify(config).getLong(AUTH_CACHE_VALIDITY_MS, DEFAULT_CACHE_TIME_TO_LIVE);

    }
}