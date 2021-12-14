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

package org.eclipse.dataspaceconnector.contract.negotiation.store;

import net.jodah.failsafe.RetryPolicy;
import org.eclipse.dataspaceconnector.contract.negotiation.store.model.ContractNegotiationDocument;
import org.eclipse.dataspaceconnector.cosmos.azure.CosmosDbApi;
import org.eclipse.dataspaceconnector.cosmos.azure.CosmosDbApiImpl;
import org.eclipse.dataspaceconnector.spi.contract.negotiation.store.ContractNegotiationStore;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;

import java.util.Set;

public class CosmosContractNegotiationStoreExtension implements ServiceExtension {

    @Override
    public String name() {
        return "CosmosDB ContractNegotiation Store";
    }

    @Override
    public Set<String> provides() {
        return Set.of(ContractNegotiationStore.FEATURE);
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var configuration = new CosmosContractNegotiationStoreConfig(context);
        Vault vault = context.getService(Vault.class);

        CosmosDbApi cosmosDbApi = new CosmosDbApiImpl(vault, configuration);
        var store = new CosmosContractNegotiationStore(cosmosDbApi, context.getTypeManager(), (RetryPolicy<Object>) context.getService(RetryPolicy.class), context.getConnectorId());
        context.registerService(ContractNegotiationStore.class, store);

        context.getTypeManager().registerTypes(ContractNegotiationDocument.class);
    }

}

