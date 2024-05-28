/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.api.control.configuration;

import org.eclipse.edc.api.auth.spi.AuthenticationRequestFilter;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.WebServiceConfiguration;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.context.ControlApiUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.connector.api.control.configuration.ControlApiConfigurationExtension.CONTROL_API_ENDPOINT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
public class ControlApiConfigurationExtensionTest {

    private final WebServiceConfigurer configurator = mock();
    private final WebService webService = mock();

    private final WebServiceConfiguration webServiceConfiguration = WebServiceConfiguration.Builder.newInstance()
            .path("/path")
            .port(1234)
            .build();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(WebServiceConfigurer.class, configurator);
        context.registerService(Hostname.class, () -> "hostname");
        context.registerService(WebService.class, webService);
        context.registerService(TypeManager.class, new JacksonTypeManager());

        when(configurator.configure(any(), any(), any())).thenReturn(webServiceConfiguration);
    }

    @Test
    void shouldComposeControlApiUrl(ControlApiConfigurationExtension extension, ServiceExtensionContext context) {
        when(context.getConfig()).thenReturn(ConfigFactory.empty());

        extension.initialize(context);

        var url = context.getService(ControlApiUrl.class);
        assertThat(url.get().toString()).isEqualTo("http://hostname:1234/path");
    }

    @Test
    void shouldUseConfiguredControlApiUrl(ControlApiConfigurationExtension extension, ServiceExtensionContext context) {
        var configuredEndpoint = "http://localhost:8080/test";
        when(context.getConfig()).thenReturn(ConfigFactory.empty());
        when(context.getSetting(eq(CONTROL_API_ENDPOINT), any())).thenReturn(configuredEndpoint);

        extension.initialize(context);

        var url = context.getService(ControlApiUrl.class);
        assertThat(url.get().toString()).isEqualTo(configuredEndpoint);
    }

    @Test
    void shouldThrowError_whenUrlIsNotValid(ControlApiConfigurationExtension extension, ServiceExtensionContext context) {
        var endpoint = "http:// invalid";
        when(context.getConfig()).thenReturn(ConfigFactory.empty());
        when(context.getSetting(eq(CONTROL_API_ENDPOINT), any())).thenReturn(endpoint);

        assertThatThrownBy(() -> extension.initialize(context)).isInstanceOf(EdcException.class);
    }

    @Test
    void shouldRegisterAuthenticationFilter(ControlApiConfigurationExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(webService).registerResource(any(), isA(AuthenticationRequestFilter.class));
    }
}
