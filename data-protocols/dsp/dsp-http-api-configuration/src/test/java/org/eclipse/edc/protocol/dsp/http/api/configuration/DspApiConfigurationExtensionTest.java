/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.http.api.configuration;

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.protocol.ProtocolWebhookRegistry;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.jersey.providers.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_SCHEMA;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.protocol.dsp.http.api.configuration.DspApiConfigurationExtension.DEFAULT_PROTOCOL_PATH;
import static org.eclipse.edc.protocol.dsp.http.api.configuration.DspApiConfigurationExtension.DEFAULT_PROTOCOL_PORT;
import static org.eclipse.edc.protocol.dsp.http.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP;
import static org.eclipse.edc.protocol.dsp.http.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_SCOPE_V_08;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_SCOPE_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.version.DspVersions.V_2024_1_PATH;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_PREFIX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class DspApiConfigurationExtensionTest {

    private final WebService webService = mock();
    private final TypeManager typeManager = mock();
    private final JsonLd jsonLd = mock();
    private final PortMappingRegistry portMappingRegistry = mock();
    private final ProtocolWebhookRegistry protocolWebhookRegistry = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(PortMappingRegistry.class, portMappingRegistry);
        context.registerService(WebService.class, webService);
        context.registerService(TypeManager.class, typeManager);
        context.registerService(Hostname.class, () -> "hostname");
        context.registerService(JsonLd.class, jsonLd);
        context.registerService(ProtocolWebhookRegistry.class, protocolWebhookRegistry);
        TypeTransformerRegistry typeTransformerRegistry = mock();
        when(typeTransformerRegistry.forContext(any())).thenReturn(mock());
        context.registerService(TypeTransformerRegistry.class, typeTransformerRegistry);

        when(typeManager.getMapper(any())).thenReturn(mock());
    }

    @Test
    void shouldComposeProtocolWebhook_whenNotConfigured(DspApiConfigurationExtension extension, ServiceExtensionContext context) {
        when(context.getConfig()).thenReturn(ConfigFactory.empty());

        extension.initialize(context);

        verify(portMappingRegistry).register(new PortMapping(ApiContext.PROTOCOL, DEFAULT_PROTOCOL_PORT, DEFAULT_PROTOCOL_PATH));

        var url = "http://hostname:%s%s".formatted(DEFAULT_PROTOCOL_PORT, DEFAULT_PROTOCOL_PATH);
        verify(protocolWebhookRegistry).registerWebhook(eq(DATASPACE_PROTOCOL_HTTP), argThat(webhook -> webhook.url().equals(url)));
        verify(protocolWebhookRegistry).registerWebhook(eq(DATASPACE_PROTOCOL_HTTP_V_2024_1), argThat(webhook -> webhook.url().equals(url + V_2024_1_PATH)));
    }

    @Test
    void shouldUseConfiguredProtocolWebhook(ServiceExtensionContext context, ObjectFactory factory) {
        var webhookAddress = "http://webhook";
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(
                "web.http.protocol.port", String.valueOf(1234),
                "web.http.protocol.path", "/path",
                "edc.dsp.callback.address", webhookAddress,
                "edc.dsp.wellKnownPath.enabled", "true"))
        );
        var extension = factory.constructInstance(DspApiConfigurationExtension.class);

        extension.initialize(context);

        verify(portMappingRegistry).register(new PortMapping(ApiContext.PROTOCOL, 1234, "/path"));

        verify(protocolWebhookRegistry).registerWebhook(eq(DATASPACE_PROTOCOL_HTTP), argThat(webhook -> webhook.url().equals(webhookAddress)));
        verify(protocolWebhookRegistry).registerWebhook(eq(DATASPACE_PROTOCOL_HTTP_V_2024_1), argThat(webhook -> webhook.url().equals(webhookAddress)));

    }


    @Test
    void initialize_shouldRegisterWebServiceProviders(DspApiConfigurationExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(webService).registerResource(eq(ApiContext.PROTOCOL), isA(ObjectMapperProvider.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { DSP_SCOPE_V_08, DSP_SCOPE_V_2024_1 })
    void initialize_shouldRegisterNamespaces(String scope, DspApiConfigurationExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(jsonLd).registerNamespace(DCAT_PREFIX, DCAT_SCHEMA, scope);
        verify(jsonLd).registerNamespace(DCT_PREFIX, DCT_SCHEMA, scope);
        verify(jsonLd).registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, scope);
        verify(jsonLd).registerNamespace(VOCAB, EDC_NAMESPACE, scope);
        verify(jsonLd).registerNamespace(EDC_PREFIX, EDC_NAMESPACE, scope);
        verify(jsonLd).registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, scope);
    }

}
