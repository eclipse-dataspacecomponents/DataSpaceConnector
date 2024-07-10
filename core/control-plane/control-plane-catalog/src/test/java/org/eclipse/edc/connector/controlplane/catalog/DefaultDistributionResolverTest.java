/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.controlplane.catalog;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataServiceRegistry;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.dataaddress.httpdata.spi.HttpDataAddressSchema;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultDistributionResolverTest {

    private final DataService dataService = DataService.Builder.newInstance().build();
    private final DataServiceRegistry dataServiceRegistry = mock();
    private final DataFlowManager dataFlowManager = mock();

    private final DefaultDistributionResolver resolver = new DefaultDistributionResolver(dataServiceRegistry, dataFlowManager);

    @Test
    void shouldReturnDistributionsForEveryTransferType() {
        when(dataServiceRegistry.getDataServices()).thenReturn(List.of(dataService));
        when(dataFlowManager.transferTypesFor(any())).thenReturn(Set.of("type1", "type2"));

        var dataAddress = DataAddress.Builder.newInstance().type("any").build();
        var asset = Asset.Builder.newInstance().dataAddress(dataAddress).build();

        var distributions = resolver.getDistributions(asset);

        assertThat(distributions).hasSize(2)
                .anySatisfy(distribution -> {
                    assertThat(distribution.getFormat()).isEqualTo("type1");
                    assertThat(distribution.getDataService()).isSameAs(dataService);
                })
                .anySatisfy(distribution -> {
                    assertThat(distribution.getFormat()).isEqualTo("type2");
                    assertThat(distribution.getDataService()).isSameAs(dataService);
                });
    }

    @Test
    void shouldReturnDistribution_whenAssetIsCatalog() {
        when(dataServiceRegistry.getDataServices()).thenReturn(List.of(dataService));
        when(dataFlowManager.transferTypesFor(any())).thenReturn(Set.of("type1", "type2"));

        var dataAddress = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property(HttpDataAddressSchema.BASE_URL, "http://quizzqua.zz/buzz")
                .build();
        var asset = Asset.Builder.newInstance()
                .dataAddress(dataAddress)
                .property(Asset.PROPERTY_IS_CATALOG, true)
                .description("test description")
                .build();

        var distributions = resolver.getDistributions(asset);

        assertThat(distributions).hasSize(1)
                .anySatisfy(distribution -> {
                    assertThat(distribution.getFormat()).isEqualTo("HttpData");
                    assertThat(distribution.getDataService().getId()).isNotNull();
                });
    }
}
