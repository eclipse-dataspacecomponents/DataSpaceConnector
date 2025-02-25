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

package org.eclipse.edc.protocol.dsp.catalog.http.dispatcher;

import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequestMessage;
import org.eclipse.edc.protocol.dsp.http.dispatcher.GetDspHttpRequestFactory;
import org.eclipse.edc.protocol.dsp.http.dispatcher.PostDspHttpRequestFactory;
import org.eclipse.edc.protocol.dsp.http.serialization.ByteArrayBodyExtractor;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspHttpRemoteMessageDispatcher;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspRequestBasePathProvider;
import org.eclipse.edc.protocol.dsp.http.spi.serialization.JsonLdRemoteMessageSerializer;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.eclipse.edc.protocol.dsp.catalog.http.dispatcher.CatalogApiPaths.BASE_PATH;
import static org.eclipse.edc.protocol.dsp.catalog.http.dispatcher.CatalogApiPaths.CATALOG_REQUEST;
import static org.eclipse.edc.protocol.dsp.catalog.http.dispatcher.CatalogApiPaths.DATASET_REQUEST;

/**
 * Creates and registers the HTTP dispatcher delegate for sending a catalog request as defined in
 * the dataspace protocol specification.
 */
@Extension(value = DspCatalogHttpDispatcherExtension.NAME)
public class DspCatalogHttpDispatcherExtension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol Catalog HTTP Dispatcher Extension";

    @Inject
    private DspHttpRemoteMessageDispatcher messageDispatcher;
    @Inject
    private JsonLdRemoteMessageSerializer remoteMessageSerializer;
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
                CatalogRequestMessage.class,
                new PostDspHttpRequestFactory<>(remoteMessageSerializer, dspRequestBasePathProvider, m -> BASE_PATH + CATALOG_REQUEST),
                byteArrayBodyExtractor
        );
        messageDispatcher.registerMessage(
                DatasetRequestMessage.class,
                new GetDspHttpRequestFactory<>(dspRequestBasePathProvider, m -> BASE_PATH + DATASET_REQUEST + "/" + m.getDatasetId()),
                byteArrayBodyExtractor
        );
    }

}
