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
 *       Fraunhofer Institute for Software and Systems Engineering - refactoring
 *
 */

package org.eclipse.edc.connector.controlplane.catalog;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataServiceRegistry;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.connector.controlplane.catalog.spi.DistributionResolver;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowManager;

import java.util.Base64;
import java.util.List;

public class DefaultDistributionResolver implements DistributionResolver {

    private final DataServiceRegistry dataServiceRegistry;
    private final DataFlowManager dataFlowManager;

    public DefaultDistributionResolver(DataServiceRegistry dataServiceRegistry, DataFlowManager dataFlowManager) {
        this.dataServiceRegistry = dataServiceRegistry;
        this.dataFlowManager = dataFlowManager;
    }

    @Override
    public List<Distribution> getDistributions(Asset asset) {
        if (asset.isCatalog()) {
            return List.of(Distribution.Builder.newInstance()
                    .format(asset.getDataAddress().getType())
                    .dataService(DataService.Builder.newInstance()
                            .id(Base64.getUrlEncoder().encodeToString(asset.getId().getBytes()))
                            .build())
                    .build());
        }
        return dataFlowManager.transferTypesFor(asset).stream().map(this::createDistribution).toList();
    }

    private Distribution createDistribution(String format) {
        var builder = Distribution.Builder.newInstance().format(format);
        dataServiceRegistry.getDataServices().forEach(builder::dataService);
        return builder.build();
    }
}
