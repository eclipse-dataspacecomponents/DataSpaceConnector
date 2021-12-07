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

package org.eclipse.dataspaceconnector.contract.definition.store;

import net.jodah.failsafe.RetryPolicy;
import org.eclipse.dataspaceconnector.contract.definition.store.model.ContractDefinitionDocument;
import org.eclipse.dataspaceconnector.cosmos.azure.CosmosDbApi;
import org.eclipse.dataspaceconnector.cosmos.azure.CosmosDbApiImpl;
import org.eclipse.dataspaceconnector.dataloading.ContractDefinitionLoader;
import org.eclipse.dataspaceconnector.spi.contract.offer.store.ContractDefinitionStore;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;

import java.util.Set;

public class CosmosContractDefinitionStoreExtension implements ServiceExtension {

    @Override
    public String name() {
        return "CosmosDB ContractDefinition Store";
    }

    @Override
    public Set<String> provides() {
        return Set.of(ContractDefinitionStore.FEATURE, ContractDefinitionLoader.FEATURE);
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var configuration = new CosmosContractDefinitionStoreConfig(context);
        Vault vault = context.getService(Vault.class);

        CosmosDbApi cosmosDbApi = new CosmosDbApiImpl(vault, configuration);
        var store = new CosmosContractDefinitionStore(cosmosDbApi, context.getTypeManager(), (RetryPolicy<Object>) context.getService(RetryPolicy.class));
        context.registerService(ContractDefinitionStore.class, store);

        ContractDefinitionLoader loader = store::save;
        context.registerService(ContractDefinitionLoader.class, loader);

        context.getTypeManager().registerTypes(ContractDefinitionDocument.class);
    }

}

