/*
 *  Copyright (c) 2021 Fraunhofer Institute for Software and Systems Engineering
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

package org.eclipse.dataspaceconnector.extensions.api;

import org.eclipse.dataspaceconnector.dataloading.AssetLoader;
import org.eclipse.dataspaceconnector.policy.model.Action;
import org.eclipse.dataspaceconnector.policy.model.LiteralExpression;
import org.eclipse.dataspaceconnector.policy.model.Permission;
import org.eclipse.dataspaceconnector.policy.model.Policy;
import org.eclipse.dataspaceconnector.spi.asset.AssetIndex;
import org.eclipse.dataspaceconnector.spi.asset.AssetSelectorExpression;
import org.eclipse.dataspaceconnector.spi.asset.DataAddressResolver;
import org.eclipse.dataspaceconnector.spi.contract.negotiation.ContractNegotiationManager;
import org.eclipse.dataspaceconnector.spi.contract.offer.store.ContractDefinitionStore;
import org.eclipse.dataspaceconnector.spi.policy.PolicyRegistry;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowManager;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractDefinition;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataAddress;

import java.nio.file.Path;
import java.util.Set;

public class FileTransferExtension implements ServiceExtension {

    public static final String USE_POLICY = "use-eu";
    private static final String EDC_ASSET_PATH = "edc.samples.04.asset.path";

    @Override
    public Set<String> requires() {
        return Set.of("edc:webservice", PolicyRegistry.FEATURE,
                DataAddressResolver.FEATURE, AssetIndex.FEATURE, ContractNegotiationManager.FEATURE);
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var dataFlowMgr = context.getService(DataFlowManager.class);
        var dataAddressResolver = context.getService(DataAddressResolver.class);

        var flowController = new FileTransferFlowController(context.getMonitor(), dataAddressResolver);
        dataFlowMgr.register(flowController);

        var policy = savePolicies(context);

        registerDataEntries(context);
        registerContractDefinition(context, policy);

        context.getMonitor().info("File Transfer Extension initialized!");
    }

    private Policy savePolicies(ServiceExtensionContext context) {
        PolicyRegistry policyRegistry = context.getService(PolicyRegistry.class);

        var usePermission = Permission.Builder.newInstance()
                .action(Action.Builder.newInstance().type("idsc:USE").build())
                .build();
        var usePolicy = Policy.Builder.newInstance()
                .id(USE_POLICY)
                .permission(usePermission)
                .target("test-document")
                .build();
        policyRegistry.registerPolicy(usePolicy);

        return usePolicy;
    }

    private void registerDataEntries(ServiceExtensionContext context) {
        AssetLoader loader = context.getService(AssetLoader.class);
        String assetPathSetting = context.getSetting(EDC_ASSET_PATH, "/tmp/provider/test-document.txt");
        Path assetPath = Path.of(assetPathSetting);

        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .property("type", "File")
                .property("path", assetPath.getParent().toString())
                .property("filename", assetPath.getFileName().toString())
                .build();

        String assetId = "test-document";
        Asset asset = Asset.Builder.newInstance().id(assetId).policyId(USE_POLICY).build();

        loader.accept(asset, dataAddress);
    }

    private void registerContractDefinition(ServiceExtensionContext context, Policy policy) {
        ContractDefinitionStore contractStore = context.getService(ContractDefinitionStore.class);

        ContractDefinition contractDefinition = ContractDefinition.Builder.newInstance()
                .id("1")
                .accessPolicy(policy)
                .contractPolicy(policy)
                .selectorExpression(AssetSelectorExpression.Builder.newInstance().whenEquals(Asset.PROPERTY_ID, "test-document").build())
                .build();

        contractStore.save(contractDefinition);
    }
}
