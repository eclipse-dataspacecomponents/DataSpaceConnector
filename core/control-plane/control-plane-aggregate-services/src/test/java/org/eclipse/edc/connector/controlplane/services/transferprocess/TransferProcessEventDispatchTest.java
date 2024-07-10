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
 *       Masatake Iwasaki (NTT DATA) - fixed failure due to assertion timeout
 *       SAP SE - refactoring
 *
 */

package org.eclipse.edc.connector.controlplane.services.transferprocess;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessProtocolService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessCompleted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessInitiated;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.connector.controlplane.transfer.spi.retry.TransferWaitStrategy;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.TerminateTransferCommand;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferProcessAck;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.connector.core.event.EventExecutorServiceContainer;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.agent.ParticipantAgent;
import org.eclipse.edc.spi.agent.ParticipantAgentService;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.message.RemoteMessageDispatcher;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.junit.matchers.EventEnvelopeMatcher.isEnvelopeOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class TransferProcessEventDispatchTest {

    public static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final EventSubscriber eventSubscriber = mock();

    @RegisterExtension
    static final RuntimeExtension RUNTIME = new RuntimePerClassExtension()
            .setConfiguration(Map.of(
                    "edc.transfer.send.retry.limit", "0",
                    "edc.transfer.send.retry.base-delay.ms", "0"
            ))
            .registerServiceMock(TransferWaitStrategy.class, () -> 1)
            .registerServiceMock(EventExecutorServiceContainer.class, new EventExecutorServiceContainer(newSingleThreadExecutor()))
            .registerServiceMock(IdentityService.class, mock())
            .registerServiceMock(ProtocolWebhook.class, () -> "http://dummy")
            .registerServiceMock(PolicyArchive.class, mock())
            .registerServiceMock(ContractNegotiationStore.class, mock())
            .registerServiceMock(ParticipantAgentService.class, mock())
            .registerServiceMock(DataPlaneClientFactory.class, mock());

    @Test
    void shouldDispatchEventsOnTransferProcessStateChanges(TransferProcessService service,
                                                           TransferProcessProtocolService protocolService,
                                                           EventRouter eventRouter,
                                                           RemoteMessageDispatcherRegistry dispatcherRegistry,
                                                           PolicyArchive policyArchive,
                                                           ContractNegotiationStore negotiationStore,
                                                           ParticipantAgentService agentService,
                                                           IdentityService identityService) {

        var token = ClaimToken.Builder.newInstance().build();
        var tokenRepresentation = TokenRepresentation.Builder.newInstance().token(UUID.randomUUID().toString()).build();

        when(identityService.verifyJwtToken(any(), any())).thenReturn(Result.success(token));

        var transferRequest = createTransferRequest();
        var agent = mock(ParticipantAgent.class);
        var providerId = "ProviderId";
        var agreement = ContractAgreement.Builder.newInstance()
                .assetId("assetId")
                .providerId(providerId)
                .consumerId("consumerId")
                .policy(Policy.Builder.newInstance().build())
                .build();

        when(agent.getIdentity()).thenReturn(providerId);

        dispatcherRegistry.register(getTestDispatcher());
        when(policyArchive.findPolicyForContract(matches(transferRequest.getContractId()))).thenReturn(Policy.Builder.newInstance().target("assetId").build());
        when(negotiationStore.findContractAgreement(transferRequest.getContractId())).thenReturn(agreement);
        when(agentService.createFor(token)).thenReturn(agent);
        eventRouter.register(TransferProcessEvent.class, eventSubscriber);

        var initiateResult = service.initiateTransfer(transferRequest);

        assertThat(initiateResult).isSucceeded();
        await().atMost(TIMEOUT).untilAsserted(() -> {
            verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessInitiated.class)));
            verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessProvisioned.class)));
            verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessRequested.class)));
        });

        var dataAddress = DataAddress.Builder.newInstance().type("test").build();
        var startMessage = TransferStartMessage.Builder.newInstance()
                .processId("dataRequestId")
                .protocol("any")
                .counterPartyAddress("http://any")
                .dataAddress(dataAddress)
                .build();

        var startedResult = protocolService.notifyStarted(startMessage, tokenRepresentation);

        assertThat(startedResult).isSucceeded();
        await().atMost(TIMEOUT).untilAsserted(() -> {
            var captor = ArgumentCaptor.forClass(EventEnvelope.class);
            verify(eventSubscriber, atLeast(3)).on(captor.capture());
            var payload = captor.getAllValues().stream().map(EventEnvelope::getPayload).filter(TransferProcessStarted.class::isInstance).findFirst();
            assertThat(payload).isPresent().get().isInstanceOfSatisfying(TransferProcessStarted.class, s -> {
                assertThat(s.getDataAddress()).usingRecursiveComparison().isEqualTo(dataAddress);
            });
        });

        var transferProcess = initiateResult.getContent();
        service.complete(transferProcess.getId()).orElseThrow(f -> new EdcException("Transfer cannot be completed: " + f.getFailureDetail()));

        await().atMost(TIMEOUT)
                .untilAsserted(() -> {
                    verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessCompleted.class)));
                });

        service.deprovision(transferProcess.getId());

        await().atMost(TIMEOUT).untilAsserted(() -> {
            verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessDeprovisioned.class)));
        });
    }

    @Test
    void shouldDispatchEventOnTransferProcessTerminated(TransferProcessService service,
                                                        EventRouter eventRouter,
                                                        RemoteMessageDispatcherRegistry dispatcherRegistry,
                                                        PolicyArchive policyArchive,
                                                        ContractNegotiationStore negotiationStore) {

        var transferRequest = createTransferRequest();
        when(policyArchive.findPolicyForContract(matches("contractId"))).thenReturn(Policy.Builder.newInstance().target("assetId").build());
        var agreement = ContractAgreement.Builder.newInstance()
                .assetId("assetId")
                .providerId("providerId")
                .consumerId("consumerId")
                .policy(Policy.Builder.newInstance().build())
                .build();
        when(negotiationStore.findContractAgreement(transferRequest.getContractId())).thenReturn(agreement);
        dispatcherRegistry.register(getTestDispatcher());
        eventRouter.register(TransferProcessEvent.class, eventSubscriber);

        var initiateResult = service.initiateTransfer(transferRequest);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessInitiated.class)));
            verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessProvisioned.class)));
            verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessRequested.class)));
        });

        service.terminate(new TerminateTransferCommand(initiateResult.getContent().getId(), "any reason"));

        await().atMost(TIMEOUT).untilAsserted(() -> verify(eventSubscriber, atLeastOnce()).on(argThat(isEnvelopeOf(TransferProcessTerminated.class))));
    }

    @Test
    void shouldDispatchEventOnTransferProcessFailure(TransferProcessService service, EventRouter eventRouter, RemoteMessageDispatcherRegistry dispatcherRegistry,
                                                     ContractNegotiationStore negotiationStore, PolicyArchive policyArchive) {
        dispatcherRegistry.register(getFailingDispatcher());
        eventRouter.register(TransferProcessEvent.class, eventSubscriber);
        var transferRequest = createTransferRequest();
        var agreement = ContractAgreement.Builder.newInstance()
                .assetId("assetId")
                .providerId("providerId")
                .consumerId("consumerId")
                .policy(Policy.Builder.newInstance().build())
                .build();
        when(negotiationStore.findContractAgreement(transferRequest.getContractId())).thenReturn(agreement);
        when(policyArchive.findPolicyForContract(any())).thenReturn(Policy.Builder.newInstance().build());

        service.initiateTransfer(transferRequest);

        await().atMost(TIMEOUT).untilAsserted(() -> verify(eventSubscriber).on(argThat(isEnvelopeOf(TransferProcessTerminated.class))));
    }

    @NotNull
    private RemoteMessageDispatcher getTestDispatcher() {
        var testDispatcher = mock(RemoteMessageDispatcher.class);
        when(testDispatcher.protocol()).thenReturn("test");
        var ack = TransferProcessAck.Builder.newInstance().build();
        when(testDispatcher.dispatch(any(), any())).thenReturn(completedFuture(StatusResult.success(ack)));
        return testDispatcher;
    }

    @NotNull
    private RemoteMessageDispatcher getFailingDispatcher() {
        var testDispatcher = mock(RemoteMessageDispatcher.class);
        when(testDispatcher.protocol()).thenReturn("test");
        when(testDispatcher.dispatch(any(), any())).thenReturn(failedFuture(new EdcException("cannot send message")));
        return testDispatcher;
    }

    private TransferRequest createTransferRequest() {
        return TransferRequest.Builder.newInstance()
                .id("dataRequestId")
                .dataDestination(DataAddress.Builder.newInstance().type("any").build())
                .protocol("test")
                .counterPartyAddress("http://an/address")
                .contractId("contractId")
                .transferType("DestinationType-PUSH")
                .build();
    }

}
