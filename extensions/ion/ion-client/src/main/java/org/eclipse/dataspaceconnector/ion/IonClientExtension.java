/*
 *  Copyright (c) 2021 Microsoft Corporation
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
package org.eclipse.dataspaceconnector.ion;

import org.eclipse.dataspaceconnector.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.dataspaceconnector.ion.spi.IonClient;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;

import java.util.Set;

public class IonClientExtension implements ServiceExtension {

    private static final String ION_NODE_URL_SETTING = "edc:ion:node:url";
    private static final String DEFAULT_NODE_URL = "https://beta.discover.did.microsoft.com/1.0";

    @Override
    public Set<String> requires() {
        return Set.of(DidResolverRegistry.FEATURE);
    }

    @Override
    public Set<String> provides() {
        return Set.of(IonClient.FEATURE);
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        String ionEndpoint = getIonEndpoint(context);
        context.getMonitor().info("Using ION Node for resolution " + ionEndpoint);
        var client = new DefaultIonClient(ionEndpoint, context.getTypeManager().getMapper());
        context.registerService(IonClient.class, client);

        var resolverRegistry = context.getService(DidResolverRegistry.class);
        resolverRegistry.register(client);

        context.getMonitor().info("Initialized IonClientExtension");
    }

    private String getIonEndpoint(ServiceExtensionContext context) {
        return context.getSetting(ION_NODE_URL_SETTING, DEFAULT_NODE_URL);
    }
}
