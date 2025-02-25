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

package org.eclipse.edc.protocol.dsp.negotiation.http.dispatcher;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationTerminationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractOfferMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.protocol.ContractNegotiationAck;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.protocol.dsp.http.dispatcher.PostDspHttpRequestFactory;
import org.eclipse.edc.protocol.dsp.http.serialization.JsonLdResponseBodyDeserializer;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspHttpRemoteMessageDispatcher;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspRequestBasePathProvider;
import org.eclipse.edc.protocol.dsp.http.spi.serialization.JsonLdRemoteMessageSerializer;
import org.eclipse.edc.protocol.dsp.spi.transform.DspProtocolTypeTransformerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import static org.eclipse.edc.protocol.dsp.http.spi.dispatcher.response.DspHttpResponseBodyExtractor.NOOP;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

@Extension(value = DspNegotiationHttpDispatcherExtension.NAME)
public class DspNegotiationHttpDispatcherExtension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol Negotiation HTTP Dispatcher Extension";

    @Inject
    private DspHttpRemoteMessageDispatcher messageDispatcher;

    @Inject
    private JsonLdRemoteMessageSerializer remoteMessageSerializer;

    @Inject
    private TypeManager typeManager;

    @Inject
    private DspProtocolTypeTransformerRegistry dspTransformerRegistry;

    @Inject
    private JsonLd jsonLd;

    @Inject
    private DspRequestBasePathProvider dspRequestBasePathProvider;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var contractNegotiationAckDeserializer = new JsonLdResponseBodyDeserializer<>(
                ContractNegotiationAck.class, typeManager, JSON_LD, jsonLd, dspTransformerRegistry);

        messageDispatcher.registerMessage(
                ContractAgreementMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> NegotiationApiPaths.BASE_PATH + m.getProcessId() + NegotiationApiPaths.AGREEMENT),
                NOOP
        );
        messageDispatcher.registerMessage(
                ContractAgreementVerificationMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> NegotiationApiPaths.BASE_PATH + m.getProcessId() + NegotiationApiPaths.AGREEMENT + NegotiationApiPaths.VERIFICATION),
                NOOP
        );
        messageDispatcher.registerMessage(
                ContractNegotiationEventMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> NegotiationApiPaths.BASE_PATH + m.getProcessId() + NegotiationApiPaths.EVENT),
                NOOP
        );
        messageDispatcher.registerMessage(
                ContractNegotiationTerminationMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> NegotiationApiPaths.BASE_PATH + m.getProcessId() + NegotiationApiPaths.TERMINATION),
                NOOP
        );
        messageDispatcher.registerMessage(
                ContractRequestMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> {
                    if (m.getType() == ContractRequestMessage.Type.INITIAL) {
                        return NegotiationApiPaths.BASE_PATH + NegotiationApiPaths.INITIAL_CONTRACT_REQUEST;
                    } else {
                        return NegotiationApiPaths.BASE_PATH + m.getProcessId() + NegotiationApiPaths.CONTRACT_REQUEST;
                    }
                }),
                contractNegotiationAckDeserializer
        );
        messageDispatcher.registerMessage(
                ContractOfferMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> NegotiationApiPaths.BASE_PATH + m.getProcessId() + NegotiationApiPaths.CONTRACT_OFFER),
                contractNegotiationAckDeserializer
        );
    }
}
