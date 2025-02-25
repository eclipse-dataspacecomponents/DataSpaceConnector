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

package org.eclipse.edc.protocol.dsp.http;

import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationTerminationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.protocol.ContractRemoteMessage;
import org.eclipse.edc.connector.controlplane.protocolversion.spi.ProtocolVersionRequestMessage;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolVersionRegistry;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferCompletionMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRemoteMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferSuspensionMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferTerminationMessage;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestVersionPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.protocol.dsp.http.dispatcher.DspHttpRemoteMessageDispatcherImpl;
import org.eclipse.edc.protocol.dsp.http.dispatcher.DspRequestBasePathProviderImpl;
import org.eclipse.edc.protocol.dsp.http.message.DspRequestHandlerImpl;
import org.eclipse.edc.protocol.dsp.http.protocol.DspProtocolParserImpl;
import org.eclipse.edc.protocol.dsp.http.serialization.JsonLdRemoteMessageSerializerImpl;
import org.eclipse.edc.protocol.dsp.http.spi.DspProtocolParser;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspHttpRemoteMessageDispatcher;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspRequestBasePathProvider;
import org.eclipse.edc.protocol.dsp.http.spi.message.DspRequestHandler;
import org.eclipse.edc.protocol.dsp.http.spi.serialization.JsonLdRemoteMessageSerializer;
import org.eclipse.edc.protocol.dsp.http.transform.DspProtocolTypeTransformerRegistryImpl;
import org.eclipse.edc.protocol.dsp.spi.transform.DspProtocolTypeTransformerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.spi.TokenDecorator;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import static org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext.CATALOGING_REQUEST_SCOPE;
import static org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext.CONTRACT_NEGOTIATION_REQUEST_SCOPE;
import static org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext.TRANSFER_PROCESS_REQUEST_SCOPE;
import static org.eclipse.edc.policy.context.request.spi.RequestVersionPolicyContext.VERSION_REQUEST_SCOPE;
import static org.eclipse.edc.protocol.dsp.http.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP;
import static org.eclipse.edc.protocol.dsp.http.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP_V_2024_1;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_SCOPE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_TRANSFORMER_CONTEXT;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Provides an implementation of {@link DspHttpRemoteMessageDispatcher} to support sending dataspace
 * protocol messages. The dispatcher can then be used by other extensions to add support for
 * specific message types.
 */
@Extension(value = DspHttpCoreExtension.NAME)
public class DspHttpCoreExtension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol Core Extension";

    private static final boolean DEFAULT_WELL_KNOWN_PATH = false;

    @Setting(description = "If set enable the well known path resolution scheme will be used", key = "edc.dsp.wellKnownPath.enabled", required = false, defaultValue = DEFAULT_WELL_KNOWN_PATH + "")
    private boolean wellKnownPathEnabled;

    @Inject
    private RemoteMessageDispatcherRegistry dispatcherRegistry;
    @Inject
    private EdcHttpClient httpClient;
    @Inject
    private IdentityService identityService;
    @Inject
    private TypeTransformerRegistry transformerRegistry;
    @Inject
    private TypeManager typeManager;
    @Inject
    private JsonLd jsonLdService;
    @Inject(required = false)
    private TokenDecorator decorator;
    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private AudienceResolver audienceResolver;
    @Inject
    private Monitor monitor;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private ProtocolVersionRegistry versionRegistry;

    private DspProtocolTypeTransformerRegistry dspTransformerRegistry;
    private DspProtocolParser dspProtocolParser;


    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public DspHttpRemoteMessageDispatcher dspHttpRemoteMessageDispatcher(ServiceExtensionContext context) {
        policyEngine.registerScope(TRANSFER_PROCESS_REQUEST_SCOPE, RequestTransferProcessPolicyContext.class);
        policyEngine.registerScope(CONTRACT_NEGOTIATION_REQUEST_SCOPE, RequestContractNegotiationPolicyContext.class);
        policyEngine.registerScope(CATALOGING_REQUEST_SCOPE, RequestCatalogPolicyContext.class);
        policyEngine.registerScope(VERSION_REQUEST_SCOPE, RequestVersionPolicyContext.class);

        TokenDecorator td; // either a decorator, or noop
        if (decorator != null) {
            td = decorator;
        } else {
            context.getMonitor().warning("No TokenDecorator was registered. The 'scope' field of outgoing protocol messages will be empty");
            td = bldr -> bldr;
        }

        var dispatcher = new DspHttpRemoteMessageDispatcherImpl(httpClient, identityService, td, policyEngine, audienceResolver);
        registerNegotiationPolicyScopes(dispatcher);
        registerTransferProcessPolicyScopes(dispatcher);
        registerCatalogPolicyScopes(dispatcher);
        registerVersionPolicyScopes(dispatcher);
        dispatcherRegistry.register(DATASPACE_PROTOCOL_HTTP, dispatcher);
        dispatcherRegistry.register(DATASPACE_PROTOCOL_HTTP_V_2024_1, dispatcher);
        return dispatcher;
    }

    @Provider
    public DspRequestHandler dspRequestHandler() {
        return new DspRequestHandlerImpl(monitor, validatorRegistry, dspTransformerRegistry());
    }

    @Provider
    public JsonLdRemoteMessageSerializer jsonLdRemoteMessageSerializer() {
        return new JsonLdRemoteMessageSerializerImpl(dspTransformerRegistry(), typeManager, JSON_LD, jsonLdService, dspProtocolParser(), DSP_SCOPE);
    }

    @Provider
    public DspProtocolTypeTransformerRegistry dspTransformerRegistry() {
        if (dspTransformerRegistry == null) {
            dspTransformerRegistry = new DspProtocolTypeTransformerRegistryImpl(transformerRegistry, DSP_TRANSFORMER_CONTEXT, dspProtocolParser());
        }
        return dspTransformerRegistry;
    }

    @Provider
    public DspProtocolParser dspProtocolParser() {
        if (dspProtocolParser == null) {
            dspProtocolParser = new DspProtocolParserImpl(versionRegistry);
        }
        return dspProtocolParser;
    }

    @Provider
    public DspRequestBasePathProvider dspRequestBasePathProvider() {
        return new DspRequestBasePathProviderImpl(dspProtocolParser(), wellKnownPathEnabled);
    }

    private void registerNegotiationPolicyScopes(DspHttpRemoteMessageDispatcher dispatcher) {
        dispatcher.registerPolicyScope(ContractAgreementMessage.class, ContractRemoteMessage::getPolicy, RequestContractNegotiationPolicyContext::new);
        dispatcher.registerPolicyScope(ContractNegotiationEventMessage.class, ContractRemoteMessage::getPolicy, RequestContractNegotiationPolicyContext::new);
        dispatcher.registerPolicyScope(ContractRequestMessage.class, ContractRemoteMessage::getPolicy, RequestContractNegotiationPolicyContext::new);
        dispatcher.registerPolicyScope(ContractNegotiationTerminationMessage.class, ContractRemoteMessage::getPolicy, RequestContractNegotiationPolicyContext::new);
        dispatcher.registerPolicyScope(ContractAgreementVerificationMessage.class, ContractRemoteMessage::getPolicy, RequestContractNegotiationPolicyContext::new);
    }

    private void registerTransferProcessPolicyScopes(DspHttpRemoteMessageDispatcher dispatcher) {
        dispatcher.registerPolicyScope(TransferCompletionMessage.class, TransferRemoteMessage::getPolicy, RequestTransferProcessPolicyContext::new);
        dispatcher.registerPolicyScope(TransferSuspensionMessage.class, TransferRemoteMessage::getPolicy, RequestTransferProcessPolicyContext::new);
        dispatcher.registerPolicyScope(TransferTerminationMessage.class, TransferRemoteMessage::getPolicy, RequestTransferProcessPolicyContext::new);
        dispatcher.registerPolicyScope(TransferStartMessage.class, TransferRemoteMessage::getPolicy, RequestTransferProcessPolicyContext::new);
        dispatcher.registerPolicyScope(TransferRequestMessage.class, TransferRemoteMessage::getPolicy, RequestTransferProcessPolicyContext::new);
    }

    private void registerCatalogPolicyScopes(DspHttpRemoteMessageDispatcher dispatcher) {
        dispatcher.registerPolicyScope(CatalogRequestMessage.class, CatalogRequestMessage::getPolicy, RequestCatalogPolicyContext::new);
        dispatcher.registerPolicyScope(DatasetRequestMessage.class, DatasetRequestMessage::getPolicy, RequestCatalogPolicyContext::new);
    }

    private void registerVersionPolicyScopes(DspHttpRemoteMessageDispatcher dispatcher) {
        dispatcher.registerPolicyScope(ProtocolVersionRequestMessage.class, ProtocolVersionRequestMessage::getPolicy, RequestVersionPolicyContext::new);
    }
}
