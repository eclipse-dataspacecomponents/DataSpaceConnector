/*
 *  Copyright (c) 2020, 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.dataspaceconnector.controlplane.api.client;

import dev.failsafe.RetryPolicy;
import okhttp3.OkHttpClient;
import org.eclipse.dataspaceconnector.controlplane.api.client.transferprocess.TransferProcessHttpClient;
import org.eclipse.dataspaceconnector.controlplane.api.client.transferprocess.model.TransferProcessFailRequest;
import org.eclipse.dataspaceconnector.runtime.metamodel.annotation.Extension;
import org.eclipse.dataspaceconnector.runtime.metamodel.annotation.Inject;
import org.eclipse.dataspaceconnector.runtime.metamodel.annotation.Provider;
import org.eclipse.dataspaceconnector.runtime.metamodel.annotation.Provides;
import org.eclipse.dataspaceconnector.spi.controlplane.api.client.transferprocess.TransferProcessApiClient;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;


/**
 * Extensions that contains clients for Control Plane HTTP APIs
 */
@Extension(value = ControlPlaneApiClientExtension.NAME)
@Provides(TransferProcessApiClient.class)
public class ControlPlaneApiClientExtension implements ServiceExtension {


    public static final String NAME = "Control Plane HTTP API client";


    @Inject
    private Vault vault;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private RetryPolicy<Object> retryPolicy;


    @Provider
    public TransferProcessApiClient transferProcessApiClient(ServiceExtensionContext context) {

        context.getTypeManager().registerTypes(TransferProcessFailRequest.class);

        return new TransferProcessHttpClient(okHttpClient, retryPolicy, context.getTypeManager().getMapper(), context.getMonitor());
    }

}
