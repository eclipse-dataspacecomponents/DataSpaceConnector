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

package org.eclipse.edc.connector.controlplane.services.asset;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetCreated;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetDeleted;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetEvent;
import org.eclipse.edc.connector.controlplane.services.spi.asset.AssetService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.EdcExtension;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.junit.matchers.EventEnvelopeMatcher.isEnvelopeOf;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ComponentTest
@ExtendWith(EdcExtension.class)
public class AssetEventDispatchTest {

    private final EventSubscriber eventSubscriber = mock();

    @BeforeEach
    void setUp(EdcExtension extension) {
        extension.registerServiceMock(ProtocolWebhook.class, mock());
        extension.registerServiceMock(DataPlaneInstanceStore.class, mock());
        extension.registerServiceMock(IdentityService.class, mock());
        extension.registerServiceMock(DataPlaneClientFactory.class, mock());
        extension.setConfiguration(Map.of(
                "web.http.port", String.valueOf(getFreePort()),
                "web.http.path", "/api"
        ));
    }

    @Test
    void shouldDispatchEventsOnAssetCreationAndDeletion(AssetService service, EventRouter eventRouter, DataAddressValidatorRegistry dataAddressValidatorRegistry) {
        dataAddressValidatorRegistry.registerSourceValidator("type", a -> ValidationResult.success());
        eventRouter.register(AssetEvent.class, eventSubscriber);
        var dataAddress = DataAddress.Builder.newInstance().type("type").build();
        var asset = Asset.Builder.newInstance().id("assetId").dataAddress(dataAddress).build();

        service.create(asset);
        await().untilAsserted(() -> {
            verify(eventSubscriber).on(argThat(isEnvelopeOf(AssetCreated.class)));
        });


        service.delete(asset.getId());
        await().untilAsserted(() -> {
            verify(eventSubscriber).on(argThat(isEnvelopeOf(AssetDeleted.class)));
        });
    }
}
