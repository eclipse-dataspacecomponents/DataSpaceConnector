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

package org.eclipse.edc.connector.controlplane.services.contractdefinition;

import org.eclipse.edc.connector.controlplane.contract.spi.event.contractdefinition.ContractDefinitionCreated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractdefinition.ContractDefinitionDeleted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractdefinition.ContractDefinitionEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.EdcExtension;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.junit.matchers.EventEnvelopeMatcher.isEnvelopeOf;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ComponentTest
@ExtendWith(EdcExtension.class)
public class ContractDefinitionEventDispatchTest {

    private final EventSubscriber eventSubscriber = mock(EventSubscriber.class);

    @BeforeEach
    void setUp(EdcExtension extension) {
        extension.registerServiceMock(ProtocolWebhook.class, mock(ProtocolWebhook.class));
        extension.registerServiceMock(DataPlaneInstanceStore.class, mock(DataPlaneInstanceStore.class));
        extension.registerServiceMock(IdentityService.class, mock());
        extension.registerServiceMock(DataPlaneClientFactory.class, mock());
        extension.setConfiguration(Map.of(
                "web.http.port", String.valueOf(getFreePort()),
                "web.http.path", "/api"
        ));
    }

    @Test
    void shouldDispatchEventOnContractDefinitionCreationAndDeletion(ContractDefinitionService service, EventRouter eventRouter) {
        eventRouter.register(ContractDefinitionEvent.class, eventSubscriber);
        var contractDefinition = ContractDefinition.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .contractPolicyId(UUID.randomUUID().toString())
                .accessPolicyId(UUID.randomUUID().toString())
                .build();

        service.create(contractDefinition);

        await().untilAsserted(() -> verify(eventSubscriber).on(argThat(isEnvelopeOf(ContractDefinitionCreated.class))));

        service.delete(contractDefinition.getId());

        await().untilAsserted(() -> verify(eventSubscriber).on(argThat(isEnvelopeOf(ContractDefinitionDeleted.class))));
    }

}
