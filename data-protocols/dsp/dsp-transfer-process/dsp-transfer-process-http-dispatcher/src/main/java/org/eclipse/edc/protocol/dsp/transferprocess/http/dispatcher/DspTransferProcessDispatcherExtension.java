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

package org.eclipse.edc.protocol.dsp.transferprocess.http.dispatcher;


import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferCompletionMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferProcessAck;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferSuspensionMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferTerminationMessage;
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
import static org.eclipse.edc.protocol.dsp.transferprocess.http.dispatcher.TransferProcessApiPaths.BASE_PATH;
import static org.eclipse.edc.protocol.dsp.transferprocess.http.dispatcher.TransferProcessApiPaths.TRANSFER_COMPLETION;
import static org.eclipse.edc.protocol.dsp.transferprocess.http.dispatcher.TransferProcessApiPaths.TRANSFER_INITIAL_REQUEST;
import static org.eclipse.edc.protocol.dsp.transferprocess.http.dispatcher.TransferProcessApiPaths.TRANSFER_START;
import static org.eclipse.edc.protocol.dsp.transferprocess.http.dispatcher.TransferProcessApiPaths.TRANSFER_SUSPENSION;
import static org.eclipse.edc.protocol.dsp.transferprocess.http.dispatcher.TransferProcessApiPaths.TRANSFER_TERMINATION;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;


/**
 * Provides HTTP dispatching for Dataspace Protocol transfer process messages via the {@link DspHttpRemoteMessageDispatcher}.
 */
@Extension(value = DspTransferProcessDispatcherExtension.NAME)
public class DspTransferProcessDispatcherExtension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol Transfer HTTP Dispatcher Extension";

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
        messageDispatcher.registerMessage(
                TransferRequestMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> BASE_PATH + TRANSFER_INITIAL_REQUEST),
                new JsonLdResponseBodyDeserializer<>(TransferProcessAck.class, typeManager, JSON_LD, jsonLd, dspTransformerRegistry)
        );
        messageDispatcher.registerMessage(
                TransferCompletionMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> BASE_PATH + m.getProcessId() + TRANSFER_COMPLETION),
                NOOP
        );
        messageDispatcher.registerMessage(
                TransferStartMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> BASE_PATH + m.getProcessId() + TRANSFER_START),
                NOOP
        );
        messageDispatcher.registerMessage(
                TransferSuspensionMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> BASE_PATH + m.getProcessId() + TRANSFER_SUSPENSION),
                NOOP
        );
        messageDispatcher.registerMessage(
                TransferTerminationMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> BASE_PATH + m.getProcessId() + TRANSFER_TERMINATION),
                NOOP
        );
    }
}
