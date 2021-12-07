/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
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

package org.eclipse.dataspaceconnector.ids.api.transfer;

import org.eclipse.dataspaceconnector.ids.spi.daps.DapsService;
import org.eclipse.dataspaceconnector.ids.spi.policy.IdsPolicyService;
import org.eclipse.dataspaceconnector.spi.asset.AssetIndex;
import org.eclipse.dataspaceconnector.spi.policy.PolicyRegistry;
import org.eclipse.dataspaceconnector.spi.protocol.web.WebService;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.transfer.TransferProcessManager;

import java.util.Set;

/**
 * Implements the IDS Controller REST API for data transfer services.
 */
public class IdsTransferApiServiceExtension implements ServiceExtension {

    @Override
    public String name() {
        return "IDS Transfer API";
    }

    @Override
    public Set<String> requires() {
        return Set.of("edc:ids:core", PolicyRegistry.FEATURE);
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        registerControllers(context);
    }

    private void registerControllers(ServiceExtensionContext context) {

        var webService = context.getService(WebService.class);

        var dapService = context.getService(DapsService.class);

        var transferManager = context.getService(TransferProcessManager.class);

        var assetIndex = context.getService(AssetIndex.class);

        var policyService = context.getService(IdsPolicyService.class);

        var monitor = context.getMonitor();

        var vault = context.getService(Vault.class);

        var policyRegistry = context.getService(PolicyRegistry.class);

        webService.registerController(new ArtifactRequestController(dapService, assetIndex, transferManager, policyService, policyRegistry, vault, monitor));
    }


}
