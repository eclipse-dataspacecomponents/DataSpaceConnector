/*
 *  Copyright (c) 2021 - 2022 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.edc.connector.contract.negotiation;

import org.eclipse.edc.connector.contract.observe.ContractNegotiationObservableImpl;
import org.eclipse.edc.connector.contract.spi.negotiation.observe.ContractNegotiationListener;
import org.eclipse.edc.connector.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractOfferRequest;
import org.eclipse.edc.connector.contract.spi.types.negotiation.command.ContractNegotiationCommand;
import org.eclipse.edc.connector.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.command.CommandQueue;
import org.eclipse.edc.spi.command.CommandRunner;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.retry.ExponentialWaitStrategy;
import org.eclipse.edc.spi.types.domain.asset.Asset;
import org.eclipse.edc.statemachine.retry.EntityRetryProcessConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTING;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.AGREED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.INITIAL;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTING;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATING;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.VERIFIED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.VERIFYING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsumerContractNegotiationManagerImplTest {

    private static final int RETRY_LIMIT = 1;

    private final ContractValidationService validationService = mock(ContractValidationService.class);
    private final ContractNegotiationStore store = mock(ContractNegotiationStore.class);
    private final RemoteMessageDispatcherRegistry dispatcherRegistry = mock(RemoteMessageDispatcherRegistry.class);
    private final PolicyDefinitionStore policyStore = mock(PolicyDefinitionStore.class);
    private final ContractNegotiationListener listener = mock(ContractNegotiationListener.class);
    private ConsumerContractNegotiationManagerImpl negotiationManager;

    @BeforeEach
    void setUp() {
        CommandQueue<ContractNegotiationCommand> queue = mock(CommandQueue.class);
        when(queue.dequeue(anyInt())).thenReturn(new ArrayList<>());

        CommandRunner<ContractNegotiationCommand> commandRunner = mock(CommandRunner.class);

        var observable = new ContractNegotiationObservableImpl();
        observable.registerListener(listener);

        negotiationManager = ConsumerContractNegotiationManagerImpl.Builder.newInstance()
                .validationService(validationService)
                .dispatcherRegistry(dispatcherRegistry)
                .monitor(mock(Monitor.class))
                .commandQueue(queue)
                .commandRunner(commandRunner)
                .observable(observable)
                .store(store)
                .policyStore(policyStore)
                .entityRetryProcessConfiguration(new EntityRetryProcessConfiguration(RETRY_LIMIT, () -> new ExponentialWaitStrategy(0L)))
                .build();
    }

    @Test
    void initiate_shouldSaveNewNegotiationInInitialState() {
        var contractOffer = contractOffer();
        var request = ContractOfferRequest.Builder.newInstance()
                .connectorId("connectorId")
                .connectorAddress("connectorAddress")
                .protocol("protocol")
                .contractOffer(contractOffer)
                .build();

        var result = negotiationManager.initiate(request);

        assertThat(result.succeeded()).isTrue();
        verify(store).save(argThat(negotiation ->
                negotiation.getState() == INITIAL.code() &&
                        negotiation.getCounterPartyId().equals(request.getConnectorId()) &&
                        negotiation.getCounterPartyAddress().equals(request.getConnectorAddress()) &&
                        negotiation.getProtocol().equals(request.getProtocol()) &&
                        negotiation.getCorrelationId().equals(negotiation.getId()) &&
                        negotiation.getContractOffers().size() == 1 &&
                        negotiation.getLastContractOffer().equals(contractOffer))
        );
        verify(listener).initiated(any());
    }

    @Test
    void confirmed_invalidId() {
        var token = ClaimToken.Builder.newInstance().build();
        var contractAgreement = mock(ContractAgreement.class);
        var policy = Policy.Builder.newInstance().build();

        var result = negotiationManager.agreed(token, "not a valid id", contractAgreement, policy);

        assertThat(result.fatalError()).isTrue();
        verify(policyStore, never()).create(any());
        verify(store, never()).save(any());
        verifyNoInteractions(listener);
    }

    @Test
    void confirmed_shouldTransitionToAgreed() {
        var negotiationConsumerRequested = createContractNegotiationRequested();
        var token = ClaimToken.Builder.newInstance().build();
        var contractAgreement = mock(ContractAgreement.class);
        var def = PolicyDefinition.Builder.newInstance().policy(Policy.Builder.newInstance().build()).build();
        when(store.findById(negotiationConsumerRequested.getId())).thenReturn(negotiationConsumerRequested);
        when(validationService.validateConfirmed(eq(token), eq(contractAgreement), any(ContractOffer.class))).thenReturn(Result.success());

        var result = negotiationManager.agreed(token, negotiationConsumerRequested.getId(), contractAgreement, def.getPolicy());

        assertThat(result.succeeded()).isTrue();
        verify(store).save(argThat(negotiation ->
                negotiation.getState() == AGREED.code() &&
                        negotiation.getContractAgreement() == contractAgreement
        ));
        verify(validationService).validateConfirmed(eq(token), eq(contractAgreement), any(ContractOffer.class));
        verify(listener).agreed(any());
    }

    @Test
    void confirmed_invalidCredentials() {
        var negotiationConsumerRequested = createContractNegotiationRequested();
        var token = ClaimToken.Builder.newInstance().build();
        var contractAgreement = mock(ContractAgreement.class);
        var policy = Policy.Builder.newInstance().build();

        when(store.findById(negotiationConsumerRequested.getId())).thenReturn(negotiationConsumerRequested);
        when(validationService.validateConfirmed(eq(token), eq(contractAgreement), any(ContractOffer.class))).thenReturn(Result.failure("failure"));

        var result = negotiationManager.agreed(token, negotiationConsumerRequested.getId(), contractAgreement, policy);

        assertThat(result.succeeded()).isFalse();
        verify(validationService).validateConfirmed(eq(token), eq(contractAgreement), any(ContractOffer.class));
    }

    @Test
    void finalized_shouldTransitionToFinalizedState() {
        var negotiation = contractNegotiationBuilder().id("negotiationId").state(VERIFIED.code()).build();
        var token = ClaimToken.Builder.newInstance().build();

        when(store.findById("negotiationId")).thenReturn(negotiation);
        when(validationService.validateRequest(eq(token), eq(negotiation))).thenReturn(Result.success());

        var result = negotiationManager.finalized(token, "negotiationId");

        assertThat(result).matches(StatusResult::succeeded).extracting(StatusResult::getContent)
                .satisfies(actual -> assertThat(actual.getState()).isEqualTo(FINALIZED.code()));
        verify(store).save(argThat(n -> n.getState() == FINALIZED.code()));
        verify(listener).finalized(negotiation);
    }

    @Test
    void finalized_invalidCredentials() {
        var negotiation = contractNegotiationBuilder().id("negotiationId").state(VERIFIED.code()).build();
        var token = ClaimToken.Builder.newInstance().build();

        when(store.findById("negotiationId")).thenReturn(negotiation);
        when(validationService.validateRequest(eq(token), eq(negotiation))).thenReturn(Result.failure("failure"));

        var result = negotiationManager.finalized(token, "negotiationId");

        assertThat(result.failed()).isTrue();

        verify(validationService).validateRequest(eq(token), eq(negotiation));
    }

    @Test
    void finalized_shouldFail_whenNegotiationDoesNotExist() {
        var token = ClaimToken.Builder.newInstance().build();

        when(store.findById("negotiationId")).thenReturn(null);

        var result = negotiationManager.finalized(token, "negotiationId");

        assertThat(result).matches(StatusResult::failed);
    }

    @Test
    void declined() {
        var negotiationConsumerOffered = createContractNegotiationRequested();
        var token = ClaimToken.Builder.newInstance().build();

        when(store.findById(negotiationConsumerOffered.getId())).thenReturn(negotiationConsumerOffered);
        when(validationService.validateRequest(eq(token), eq(negotiationConsumerOffered))).thenReturn(Result.success());

        var result = negotiationManager.declined(token, negotiationConsumerOffered.getId());

        assertThat(result.succeeded()).isTrue();
        verify(store).save(argThat(negotiation -> negotiation.getState() == TERMINATED.code()));
        verify(listener).terminated(any());
    }

    @Test
    void declined_invalidCredentials() {
        var negotiationConsumerOffered = createContractNegotiationRequested();
        var token = ClaimToken.Builder.newInstance().build();

        when(store.findById(negotiationConsumerOffered.getId())).thenReturn(negotiationConsumerOffered);
        when(validationService.validateRequest(eq(token), eq(negotiationConsumerOffered))).thenReturn(Result.failure("failure"));

        var result = negotiationManager.declined(token, negotiationConsumerOffered.getId());

        assertThat(result.succeeded()).isFalse();
        verify(validationService).validateRequest(eq(token), eq(negotiationConsumerOffered));
    }

    @Test
    void initial_shouldTransitionRequesting() {
        var negotiation = contractNegotiationBuilder().state(INITIAL.code()).build();
        when(store.nextForState(eq(INITIAL.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == REQUESTING.code()));
        });
    }

    @Test
    void requesting_shouldSendOfferAndTransitionRequested() {
        var negotiation = contractNegotiationBuilder().state(REQUESTING.code()).contractOffer(contractOffer()).build();
        when(store.nextForState(eq(REQUESTING.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(dispatcherRegistry.send(any(), any())).thenReturn(completedFuture(null));
        when(store.findById(negotiation.getId())).thenReturn(negotiation);

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == REQUESTED.code()));
            verify(dispatcherRegistry, only()).send(any(), any());
            verify(listener).requested(any());
        });
    }

    @Test
    void accepting_shouldSendAgreementAndTransitionToApproved() {
        var negotiation = contractNegotiationBuilder().state(ACCEPTING.code()).contractOffer(contractOffer()).build();
        when(store.nextForState(eq(ACCEPTING.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(dispatcherRegistry.send(any(), any())).thenReturn(completedFuture(null));
        when(store.findById(negotiation.getId())).thenReturn(negotiation);

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == ACCEPTED.code()));
            verify(dispatcherRegistry, only()).send(any(), any());
            verify(listener).accepted(any());
        });
    }

    @Test
    void agreed_shouldTransitionToVerifying() {
        var negotiation = contractNegotiationBuilder().state(AGREED.code()).build();
        when(store.nextForState(eq(AGREED.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(store.findById(negotiation.getId())).thenReturn(negotiation);

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == VERIFYING.code()));
            verifyNoInteractions(dispatcherRegistry);
        });
    }

    @Deprecated(since = "milestone9")
    @Test
    void agreed_shouldTransitionToFinalized_whenProtocolIsIdsMultipart() {
        var negotiation = contractNegotiationBuilder().state(AGREED.code()).protocol("ids-multipart").build();
        when(store.nextForState(eq(AGREED.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(store.findById(negotiation.getId())).thenReturn(negotiation);

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == FINALIZED.code()));
            verifyNoInteractions(dispatcherRegistry);
        });
    }

    @Test
    void verifying_shouldSendMessageAndTransitionToVerified() {
        var negotiation = contractNegotiationBuilder().state(VERIFYING.code()).build();
        when(store.nextForState(eq(VERIFYING.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(store.findById(negotiation.getId())).thenReturn(negotiation);
        when(dispatcherRegistry.send(any(), any())).thenReturn(completedFuture("any"));

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == VERIFIED.code()));
            verify(dispatcherRegistry).send(any(), isA(ContractAgreementVerificationMessage.class));
        });
    }

    @Test
    void terminating_shouldSendRejectionAndTransitionTerminated() {
        var negotiation = contractNegotiationBuilder().state(TERMINATING.code()).contractOffer(contractOffer()).build();
        negotiation.setErrorDetail("an error");
        when(store.nextForState(eq(TERMINATING.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(dispatcherRegistry.send(any(), any())).thenReturn(completedFuture(null));
        when(store.findById(negotiation.getId())).thenReturn(negotiation);

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == TERMINATED.code()));
            verify(dispatcherRegistry, only()).send(any(), any());
            verify(listener).terminated(any());
        });
    }

    @ParameterizedTest
    @ArgumentsSource(DispatchFailureArguments.class)
    void dispatchFailure(ContractNegotiationStates starting, ContractNegotiationStates ending, UnaryOperator<ContractNegotiation.Builder> builderEnricher) {
        var negotiation = builderEnricher.apply(contractNegotiationBuilder().state(starting.code())).build();
        when(store.nextForState(eq(starting.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(dispatcherRegistry.send(any(), any())).thenReturn(failedFuture(new EdcException("error")));
        when(store.findById(negotiation.getId())).thenReturn(negotiation);


        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == ending.code()));
            verify(dispatcherRegistry, only()).send(any(), any());
        });
    }

    private static class DispatchFailureArguments implements ArgumentsProvider {

        private static final int RETRIES_NOT_EXHAUSTED = RETRY_LIMIT;
        private static final int RETRIES_EXHAUSTED = RETRIES_NOT_EXHAUSTED + 1;

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return Stream.of(
                    // retries not exhausted
                    new DispatchFailure(REQUESTING, REQUESTING, b -> b.stateCount(RETRIES_NOT_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(ACCEPTING, ACCEPTING, b -> b.stateCount(RETRIES_NOT_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(VERIFYING, VERIFYING, b -> b.stateCount(RETRIES_NOT_EXHAUSTED)),
                    new DispatchFailure(TERMINATING, TERMINATING, b -> b.stateCount(RETRIES_NOT_EXHAUSTED).errorDetail("an error")),
                    // retries exhausted
                    new DispatchFailure(REQUESTING, TERMINATING, b -> b.stateCount(RETRIES_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(ACCEPTING, TERMINATING, b -> b.stateCount(RETRIES_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(VERIFYING, TERMINATING, b -> b.stateCount(RETRIES_EXHAUSTED)),
                    new DispatchFailure(TERMINATING, TERMINATED, b -> b.stateCount(RETRIES_EXHAUSTED).errorDetail("an error"))
            );
        }

        private ContractOffer contractOffer() {
            return ContractOffer.Builder.newInstance().id("id:id")
                    .policy(Policy.Builder.newInstance().build())
                    .asset(Asset.Builder.newInstance().id("assetId").build())
                    .contractStart(ZonedDateTime.now())
                    .contractEnd(ZonedDateTime.now())
                    .build();
        }
    }

    private ContractNegotiation createContractNegotiationRequested() {
        var lastOffer = contractOffer();

        return contractNegotiationBuilder()
                .state(REQUESTED.code())
                .contractOffer(lastOffer)
                .build();
    }

    private ContractNegotiation.Builder contractNegotiationBuilder() {
        return ContractNegotiation.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .correlationId("correlationId")
                .counterPartyId("connectorId")
                .counterPartyAddress("connectorAddress")
                .protocol("protocol")
                .stateTimestamp(Instant.now().toEpochMilli());
    }

    private ContractOffer contractOffer() {
        return ContractOffer.Builder.newInstance().id("id:id")
                .policy(Policy.Builder.newInstance().build())
                .asset(Asset.Builder.newInstance().id("assetId").build())
                .contractStart(ZonedDateTime.now())
                .contractEnd(ZonedDateTime.now())
                .build();
    }

}
