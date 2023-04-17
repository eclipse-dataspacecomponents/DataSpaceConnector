/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.catalog;

import org.eclipse.edc.catalog.spi.DataServiceRegistry;
import org.eclipse.edc.catalog.spi.DatasetResolver;
import org.eclipse.edc.catalog.spi.DistributionResolver;
import org.eclipse.edc.connector.contract.spi.offer.ContractDefinitionResolver;
import org.eclipse.edc.connector.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.asset.AssetIndex;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

@Extension(CatalogCoreExtension.NAME)
@Provides({ DataServiceRegistry.class, DistributionResolver.class, DatasetResolver.class })
public class CatalogCoreExtension implements ServiceExtension {

    public static final String NAME = "Catalog Core";

    @Inject
    private ContractDefinitionResolver contractDefinitionResolver;

    @Inject
    private AssetIndex assetIndex;

    @Inject
    private PolicyDefinitionStore policyDefinitionStore;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var registry = new DataServiceRegistryImpl();
        var datasetResolver = new DatasetResolverImpl(contractDefinitionResolver, assetIndex, policyDefinitionStore, registry);

        context.registerService(DataServiceRegistry.class, registry);
        context.registerService(DistributionResolver.class, registry);
        context.registerService(DatasetResolver.class, datasetResolver);
    }
}
