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

import jakarta.json.Json;
import org.eclipse.edc.connector.controlplane.transform.edc.from.JsonObjectFromAssetTransformer;
import org.eclipse.edc.connector.controlplane.transform.edc.to.JsonObjectToAssetTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.connector.controlplane.transform.odrl.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.spi.JsonLdNamespace;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.protocol.ProtocolWebhookRegistry;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.dspace.from.JsonObjectFromDataAddressDspaceTransformer;
import org.eclipse.edc.transform.transformer.dspace.to.JsonObjectToDataAddressDspaceTransformer;
import org.eclipse.edc.transform.transformer.dspace.v2024.from.JsonObjectFromDataAddressDspace2024Transformer;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.web.jersey.providers.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import java.util.Map;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.protocol.dsp.http.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP;
import static org.eclipse.edc.protocol.dsp.http.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_NAMESPACE_V_08;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_NAMESPACE_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_SCOPE_V_08;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_SCOPE_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_TRANSFORMER_CONTEXT_V_08;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_TRANSFORMER_CONTEXT_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.version.DspVersions.V_2024_1_PATH;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_PREFIX;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Configure 'protocol' api context.
 */
@Extension(value = DspApiConfigurationExtension.NAME)
public class DspApiConfigurationExtension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol API Configuration Extension";

    static final String DEFAULT_PROTOCOL_PATH = "/api/protocol";
    static final int DEFAULT_PROTOCOL_PORT = 8282;

    private static final boolean DEFAULT_WELL_KNOWN_PATH = false;

    @Setting(description = "If set enable the well known path resolution scheme will be used", key = "edc.dsp.wellKnownPath.enabled", required = false, defaultValue = DEFAULT_WELL_KNOWN_PATH + "")
    private boolean wellKnownPathEnabled;

    @Setting(description = "Configures endpoint for reaching the Protocol API in the form \"<hostname:protocol.port/protocol.path>\"", key = "edc.dsp.callback.address", required = false)
    private String callbackAddress;
    @Configuration
    private DspApiConfiguration apiConfiguration;

    @Inject
    private TypeManager typeManager;
    @Inject
    private WebService webService;
    @Inject
    private JsonLd jsonLd;
    @Inject
    private TypeTransformerRegistry transformerRegistry;
    @Inject
    private ParticipantIdMapper participantIdMapper;
    @Inject
    private Hostname hostname;
    @Inject
    private PortMappingRegistry portMappingRegistry;

    @Inject
    private ProtocolWebhookRegistry protocolWebhookRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var portMapping = new PortMapping(ApiContext.PROTOCOL, apiConfiguration.port(), apiConfiguration.path());
        portMappingRegistry.register(portMapping);

        var dspWebhookAddress = ofNullable(callbackAddress).orElseGet(() -> format("http://%s:%s%s", hostname.get(), portMapping.port(), portMapping.path()));


        var v2024Path = dspWebhookAddress + (wellKnownPathEnabled ? "" : V_2024_1_PATH);

        protocolWebhookRegistry.registerWebhook(DATASPACE_PROTOCOL_HTTP, () -> dspWebhookAddress);
        protocolWebhookRegistry.registerWebhook(DATASPACE_PROTOCOL_HTTP_V_2024_1, () -> v2024Path);

        // registers ns for DSP scope
        registerNamespaces(DSP_SCOPE_V_08, DSP_NAMESPACE_V_08);
        registerNamespaces(DSP_SCOPE_V_2024_1, DSP_NAMESPACE_V_2024_1);

        webService.registerResource(ApiContext.PROTOCOL, new ObjectMapperProvider(typeManager, JSON_LD));


        registerV08Transformers();
        registerV2024Transformers();
        registerTransformers(DSP_TRANSFORMER_CONTEXT_V_08, DSP_NAMESPACE_V_08);
        registerTransformers(DSP_TRANSFORMER_CONTEXT_V_2024_1, DSP_NAMESPACE_V_2024_1);
    }


    @Override
    public void prepare() {
        var mapper = typeManager.getMapper(JSON_LD);
        mapper.registerSubtypes(AtomicConstraint.class, LiteralExpression.class);
    }

    private void registerNamespaces(String scope, JsonLdNamespace dspNamespace) {
        jsonLd.registerNamespace(DCAT_PREFIX, DCAT_SCHEMA, scope);
        jsonLd.registerNamespace(DCT_PREFIX, DCT_SCHEMA, scope);
        jsonLd.registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, scope);
        jsonLd.registerNamespace(DSPACE_PREFIX, dspNamespace.namespace(), scope);
        jsonLd.registerNamespace(VOCAB, EDC_NAMESPACE, scope);
        jsonLd.registerNamespace(EDC_PREFIX, EDC_NAMESPACE, scope);
    }

    private void registerTransformers(String version, JsonLdNamespace dspNamespace) {
        var jsonBuilderFactory = Json.createBuilderFactory(Map.of());

        // EDC model to JSON-LD transformers
        var dspApiTransformerRegistry = transformerRegistry.forContext(version);
        dspApiTransformerRegistry.register(new JsonObjectFromAssetTransformer(jsonBuilderFactory, typeManager, JSON_LD));
        dspApiTransformerRegistry.register(new JsonObjectFromQuerySpecTransformer(jsonBuilderFactory));
        dspApiTransformerRegistry.register(new JsonObjectFromCriterionTransformer(jsonBuilderFactory, typeManager, JSON_LD));

        // JSON-LD to EDC model transformers
        // ODRL Transformers
        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper).forEach(dspApiTransformerRegistry::register);

        dspApiTransformerRegistry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));
        dspApiTransformerRegistry.register(new JsonObjectToAssetTransformer());
        dspApiTransformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        dspApiTransformerRegistry.register(new JsonObjectToCriterionTransformer());
        dspApiTransformerRegistry.register(new JsonObjectToDataAddressDspaceTransformer(dspNamespace));
    }


    private void registerV08Transformers() {
        var jsonBuilderFactory = Json.createBuilderFactory(Map.of());

        // EDC model to JSON-LD transformers
        var dspApiTransformerRegistry = transformerRegistry.forContext(DSP_TRANSFORMER_CONTEXT_V_08);

        dspApiTransformerRegistry.register(new JsonObjectFromPolicyTransformer(jsonBuilderFactory, participantIdMapper));
        dspApiTransformerRegistry.register(new JsonObjectFromDataAddressDspaceTransformer(jsonBuilderFactory, typeManager, JSON_LD));
    }

    private void registerV2024Transformers() {
        var jsonBuilderFactory = Json.createBuilderFactory(Map.of());

        // EDC model to JSON-LD transformers
        var dspApiTransformerRegistry = transformerRegistry.forContext(DSP_TRANSFORMER_CONTEXT_V_2024_1);

        dspApiTransformerRegistry.register(new JsonObjectFromPolicyTransformer(jsonBuilderFactory, participantIdMapper, true));
        dspApiTransformerRegistry.register(new JsonObjectFromDataAddressDspace2024Transformer(jsonBuilderFactory, typeManager, JSON_LD));
    }

    @Settings
    record DspApiConfiguration(
            @Setting(key = "web.http." + ApiContext.PROTOCOL + ".port", description = "Port for " + ApiContext.PROTOCOL + " api context", defaultValue = DEFAULT_PROTOCOL_PORT + "")
            int port,
            @Setting(key = "web.http." + ApiContext.PROTOCOL + ".path", description = "Path for " + ApiContext.PROTOCOL + " api context", defaultValue = DEFAULT_PROTOCOL_PATH)
            String path
    ) {

    }
}
