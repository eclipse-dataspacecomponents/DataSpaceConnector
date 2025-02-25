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

package org.eclipse.edc.protocol.dsp.version.http.dispatcher;

import org.eclipse.edc.connector.controlplane.protocolversion.spi.ProtocolVersionRequestMessage;
import org.eclipse.edc.protocol.dsp.http.dispatcher.GetDspHttpRequestFactory;
import org.eclipse.edc.protocol.dsp.http.serialization.ByteArrayBodyExtractor;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspHttpRemoteMessageDispatcher;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspRequestBasePathProvider;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.eclipse.edc.protocol.dsp.version.http.dispatcher.VersionApiPaths.PATH;

/**
 * Creates and registers the HTTP dispatcher delegate for sending a version request as defined in
 * the dataspace protocol specification.
 */
@Extension(value = DspVersionHttpDispatcherExtension.NAME)
public class DspVersionHttpDispatcherExtension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol Version HTTP Dispatcher Extension";

    @Inject
    private DspHttpRemoteMessageDispatcher messageDispatcher;

    @Inject
    private DspRequestBasePathProvider dspRequestBasePathProvider;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var byteArrayBodyExtractor = new ByteArrayBodyExtractor();

        messageDispatcher.registerMessage(
                ProtocolVersionRequestMessage.class,
                new GetDspHttpRequestFactory<>(dspRequestBasePathProvider, m -> PATH),
                byteArrayBodyExtractor
        );
    }

}
