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

package org.eclipse.edc.connector.controlplane.services.contractnegotiation;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.NegotiationWaitStrategy;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationProtocolService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.EdcExtension;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.agent.ParticipantAgentService;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.eclipse.edc.spi.message.RemoteMessageDispatcher;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.junit.matchers.EventEnvelopeMatcher.isEnvelopeOf;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
@ExtendWith(EdcExtension.class)
class ContractNegotiationEventDispatchTest {
    private static final String CONSUMER = "consumer";

    private final EventSubscriber eventSubscriber = mock();
    private final IdentityService identityService = mock();
    private final ClaimToken token = ClaimToken.Builder.newInstance().claim(ParticipantAgentService.DEFAULT_IDENTITY_CLAIM_KEY, CONSUMER).build();

    private final TokenRepresentation tokenRepresentation = TokenRepresentation.Builder.newInstance().token(UUID.randomUUID().toString()).build();


    @BeforeEach
    void setUp(EdcExtension extension) {
        extension.setConfiguration(Map.of(
                "web.http.port", String.valueOf(getFreePort()),
                "web.http.path", "/api",
                "edc.negotiation.consumer.send.retry.limit", "0",
                "edc.negotiation.provider.send.retry.limit", "0"
        ));
        extension.registerServiceMock(NegotiationWaitStrategy.class, () -> 1);
        extension.registerServiceMock(ProtocolWebhook.class, mock());
        extension.registerServiceMock(DataPlaneInstanceStore.class, mock());
        extension.registerServiceMock(IdentityService.class, identityService);
        extension.registerServiceMock(DataPlaneClientFactory.class, mock());
    }

    @Test
    void shouldDispatchEventsOnProviderContractNegotiationStateChanges(EventRouter eventRouter,
                                                                       RemoteMessageDispatcherRegistry dispatcherRegistry,
                                                                       ContractNegotiationProtocolService service,
                                                                       ContractDefinitionStore contractDefinitionStore,
                                                                       PolicyDefinitionStore policyDefinitionStore,
                                                                       AssetIndex assetIndex) {
        dispatcherRegistry.register(succeedingDispatcher());

        when(identityService.verifyJwtToken(eq(tokenRepresentation), isA(VerificationContext.class))).thenReturn(Result.success(token));
        eventRouter.register(ContractNegotiationEvent.class, eventSubscriber);
        var policy = Policy.Builder.newInstance().build();
        var contractDefinition = ContractDefinition.Builder.newInstance()
                .id("contractDefinitionId")
                .contractPolicyId("policyId")
                .accessPolicyId("policyId")
                .assetsSelector(emptyList())
                .build();
        contractDefinitionStore.save(contractDefinition);
        policyDefinitionStore.create(PolicyDefinition.Builder.newInstance().id("policyId").policy(policy).build());
        assetIndex.create(Asset.Builder.newInstance().id("assetId").dataAddress(DataAddress.Builder.newInstance().type("any").build()).build());

        service.notifyRequested(createContractOfferRequest(policy, "assetId"), tokenRepresentation);

        await().untilAsserted(() -> {
            verify(eventSubscriber).on(argThat(isEnvelopeOf(ContractNegotiationRequested.class)));
            verify(eventSubscriber).on(argThat(isEnvelopeOf(ContractNegotiationAgreed.class)));
        });
    }

    private ContractRequestMessage createContractOfferRequest(Policy policy, String assetId) {
        var contractOffer = ContractOffer.Builder.newInstance()
                .id(ContractOfferId.create("contractDefinitionId", assetId).toString())
                .assetId("assetId")
                .policy(policy)
                .build();

        return ContractRequestMessage.Builder.newInstance()
                .protocol("test")
                .counterPartyAddress("counterPartyAddress")
                .callbackAddress("callbackAddress")
                .contractOffer(contractOffer)
                .consumerPid("consumerPid")
                .build();
    }

    @NotNull
    private RemoteMessageDispatcher succeedingDispatcher() {
        var testDispatcher = mock(RemoteMessageDispatcher.class);
        when(testDispatcher.protocol()).thenReturn("test");
        when(testDispatcher.dispatch(any(), any())).thenReturn(completedFuture(StatusResult.success("any")));
        return testDispatcher;
    }

}
