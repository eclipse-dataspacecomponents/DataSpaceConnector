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
import org.eclipse.edc.connector.contract.spi.ContractId;
import org.eclipse.edc.connector.contract.spi.negotiation.observe.ContractNegotiationListener;
import org.eclipse.edc.connector.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractAgreementRequest;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractNegotiationEventMessage;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.net.URI;
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
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.AGREED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.AGREEING;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZING;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.OFFERED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.OFFERING;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATED;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATING;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.VERIFIED;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProviderContractNegotiationManagerImplTest {
    private static final String CONSUMER_ID = "consumer";
    private static final String PROVIDER_ID = "provider";

    private static final int RETRY_LIMIT = 1;
    private final ContractNegotiationStore store = mock(ContractNegotiationStore.class);
    private final ContractValidationService validationService = mock(ContractValidationService.class);
    private final RemoteMessageDispatcherRegistry dispatcherRegistry = mock(RemoteMessageDispatcherRegistry.class);
    private final PolicyDefinitionStore policyStore = mock(PolicyDefinitionStore.class);
    private final ContractNegotiationListener listener = mock(ContractNegotiationListener.class);
    private ProviderContractNegotiationManagerImpl negotiationManager;

    @BeforeEach
    void setUp() {
        CommandQueue<ContractNegotiationCommand> queue = mock(CommandQueue.class);
        when(queue.dequeue(anyInt())).thenReturn(new ArrayList<>());

        CommandRunner<ContractNegotiationCommand> commandRunner = mock(CommandRunner.class);

        var observable = new ContractNegotiationObservableImpl();
        observable.registerListener(listener);
        negotiationManager = ProviderContractNegotiationManagerImpl.Builder.newInstance()
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
    void requested_confirmOffer() {
        var token = ClaimToken.Builder.newInstance().build();
        var contractOffer = contractOffer();
        when(validationService.validateInitialOffer(token, contractOffer)).thenReturn(Result.success(contractOffer));

        ContractOfferRequest request = ContractOfferRequest.Builder.newInstance()
                .connectorId(CONSUMER_ID)
                .connectorAddress("connectorAddress")
                .protocol("protocol")
                .contractOffer(contractOffer)
                .correlationId("correlationId")
                .build();

        var result = negotiationManager.requested(token, request);

        assertThat(result.succeeded()).isTrue();
        verify(store, atLeastOnce()).save(argThat(n ->
                n.getState() == ContractNegotiationStates.AGREEING.code() &&
                        n.getCounterPartyId().equals(request.getConnectorId()) &&
                        n.getCounterPartyAddress().equals(request.getConnectorAddress()) &&
                        n.getProtocol().equals(request.getProtocol()) &&
                        n.getCorrelationId().equals(request.getCorrelationId()) &&
                        n.getContractOffers().size() == 1 &&
                        n.getLastContractOffer().equals(contractOffer)
        ));
        verify(validationService).validateInitialOffer(token, contractOffer);
    }

    @Test
    void requested_invalid() {
        var token = ClaimToken.Builder.newInstance().build();
        var contractOffer = contractOffer();
        when(validationService.validateInitialOffer(token, contractOffer)).thenReturn(Result.failure("error"));

        ContractOfferRequest request = ContractOfferRequest.Builder.newInstance()
                .connectorId(CONSUMER_ID)
                .connectorAddress("connectorAddress")
                .protocol("protocol")
                .contractOffer(contractOffer)
                .correlationId("correlationId")
                .build();

        var result = negotiationManager.requested(token, request);

        assertThat(result.succeeded()).isFalse();
        verify(validationService).validateInitialOffer(token, contractOffer);
    }

    @Test
    void verified_shouldTransitToVerifiedState() {
        var token = ClaimToken.Builder.newInstance().build();
        var negotiation = contractNegotiationBuilder().id("negotiationId").state(AGREED.code()).build();

        when(store.findById("negotiationId")).thenReturn(negotiation);
        when(validationService.validateRequest(eq(token), eq(negotiation))).thenReturn(Result.success());

        var result = negotiationManager.verified(token, "negotiationId");

        assertThat(result.succeeded()).isTrue();

        assertThat(result).matches(StatusResult::succeeded).extracting(StatusResult::getContent)
                .satisfies(actual -> assertThat(actual.getState()).isEqualTo(VERIFIED.code()));
        verify(store).save(argThat(n -> n.getState() == VERIFIED.code()));
        verify(listener).verified(negotiation);
    }

    @Test
    void verified_shouldFail_whenNegotiationDoesNotExist() {
        when(store.findById("negotiationId")).thenReturn(null);

        var result = negotiationManager.verified(null, "negotiationId");

        assertThat(result).matches(StatusResult::failed);
    }

    @Test
    void declined() {
        var negotiation = createContractNegotiation();
        when(store.findById(negotiation.getId())).thenReturn(negotiation);
        when(store.findForCorrelationId(negotiation.getCorrelationId())).thenReturn(negotiation);
        var token = ClaimToken.Builder.newInstance().build();

        when(validationService.validateRequest(eq(token), eq(negotiation))).thenReturn(Result.success());

        var result = negotiationManager.declined(token, negotiation.getCorrelationId());

        assertThat(result.succeeded()).isTrue();
        verify(store, atLeastOnce()).save(argThat(n -> n.getState() == TERMINATED.code()));
        verify(listener).terminated(any());
    }

    @Test
    void declined_invalid() {
        var negotiation = createContractNegotiation();
        when(store.findById(negotiation.getId())).thenReturn(negotiation);
        when(store.findForCorrelationId(negotiation.getCorrelationId())).thenReturn(negotiation);
        var token = ClaimToken.Builder.newInstance().build();

        when(validationService.validateRequest(eq(token), eq(negotiation))).thenReturn(Result.failure("failure"));

        var result = negotiationManager.declined(token, negotiation.getCorrelationId());

        assertThat(result.succeeded()).isFalse();

        verify(validationService).validateRequest(eq(token), eq(negotiation));
    }

    @Test
    void offering_shouldSendOfferAndTransitionToOffered() {
        var negotiation = contractNegotiationBuilder().state(OFFERING.code()).contractOffer(contractOffer()).build();
        when(store.nextForState(eq(OFFERING.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(dispatcherRegistry.send(any(), any())).thenReturn(completedFuture(null));
        when(store.findById(negotiation.getId())).thenReturn(negotiation);

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == OFFERED.code()));
            verify(dispatcherRegistry, only()).send(any(), isA(ContractOfferRequest.class));
            verify(listener).offered(any());
        });
    }

    @Test
    void verified_shouldTransitionToFinalizing() {
        var negotiation = contractNegotiationBuilder().state(VERIFIED.code()).build();
        when(store.nextForState(eq(VERIFIED.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(store.findById(negotiation.getId())).thenReturn(negotiation);

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == FINALIZING.code()));
            verifyNoInteractions(dispatcherRegistry);
        });
    }

    @Test
    void agreeing_shouldSendAgreementAndTransitionToConfirmed() {
        var negotiation = contractNegotiationBuilder()
                .state(AGREEING.code())
                .contractOffer(contractOffer())
                .contractAgreement(contractAgreementBuilder().policy(Policy.Builder.newInstance().build()).build())
                .build();
        when(store.nextForState(eq(AGREEING.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(dispatcherRegistry.send(any(), any())).thenReturn(completedFuture(null));
        when(store.findById(negotiation.getId())).thenReturn(negotiation);
        when(policyStore.findById(any())).thenReturn(PolicyDefinition.Builder.newInstance().policy(Policy.Builder.newInstance().build()).id("policyId").build());

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == AGREED.code()));
            verify(dispatcherRegistry, only()).send(any(), isA(ContractAgreementRequest.class));
            verify(listener).agreed(any());
        });
    }

    @Test
    void finalizing_shouldSendMessageAndTransitionToFinalized() {
        var negotiation = contractNegotiationBuilder().state(FINALIZING.code()).build();
        when(store.nextForState(eq(FINALIZING.code()), anyInt())).thenReturn(List.of(negotiation)).thenReturn(emptyList());
        when(store.findById(negotiation.getId())).thenReturn(negotiation);
        when(dispatcherRegistry.send(any(), any())).thenReturn(completedFuture("any"));

        negotiationManager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(p -> p.getState() == FINALIZED.code()));
            verify(dispatcherRegistry).send(any(), and(isA(ContractNegotiationEventMessage.class), argThat(it -> it.getType() == ContractNegotiationEventMessage.Type.FINALIZED)));
            verify(listener).finalized(negotiation);
        });
    }

    @Test
    void terminating_shouldSendMessageAndTransitionTerminated() {
        var negotiation = contractNegotiationBuilder().state(TERMINATING.code()).contractOffer(contractOffer()).errorDetail("an error").build();
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
                    new DispatchFailure(OFFERING, OFFERING, b -> b.stateCount(RETRIES_NOT_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(AGREEING, AGREEING, b -> b.stateCount(RETRIES_NOT_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(FINALIZING, FINALIZING, b -> b.stateCount(RETRIES_NOT_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(TERMINATING, TERMINATING, b -> b.stateCount(RETRIES_NOT_EXHAUSTED).errorDetail("an error")),
                    // retries exhausted
                    new DispatchFailure(OFFERING, TERMINATING, b -> b.stateCount(RETRIES_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(AGREEING, TERMINATING, b -> b.stateCount(RETRIES_EXHAUSTED).contractOffer(contractOffer())),
                    new DispatchFailure(FINALIZING, TERMINATING, b -> b.stateCount(RETRIES_EXHAUSTED).contractOffer(contractOffer())),
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

    @NotNull
    private ContractNegotiation createContractNegotiation() {
        return contractNegotiationBuilder()
                .contractOffer(contractOffer())
                .build();
    }

    private ContractNegotiation.Builder contractNegotiationBuilder() {
        return ContractNegotiation.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .type(ContractNegotiation.Type.PROVIDER)
                .correlationId("correlationId")
                .counterPartyId("connectorId")
                .counterPartyAddress("connectorAddress")
                .protocol("protocol")
                .state(400)
                .stateTimestamp(Instant.now().toEpochMilli());
    }

    private ContractAgreement.Builder contractAgreementBuilder() {
        return ContractAgreement.Builder.newInstance()
                .id(ContractId.createContractId(UUID.randomUUID().toString()))
                .providerAgentId("any")
                .consumerAgentId("any")
                .assetId("default")
                .policy(Policy.Builder.newInstance().build());
    }

    private ContractOffer contractOffer() {
        return ContractOffer.Builder.newInstance()
                .id(ContractId.createContractId("1"))
                .policy(Policy.Builder.newInstance().build())
                .asset(Asset.Builder.newInstance().id("assetId").build())
                .consumer(URI.create(CONSUMER_ID))
                .provider(URI.create(PROVIDER_ID))
                .contractStart(ZonedDateTime.now())
                .contractEnd(ZonedDateTime.now())
                .build();
    }

}
