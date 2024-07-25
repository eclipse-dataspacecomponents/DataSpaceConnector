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
 *       Microsoft Corporation - introduced Awaitility
 *
 */

package org.eclipse.edc.connector.controlplane.contract.negotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.ConsumerOfferResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationTerminationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractOfferMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.types.protocol.ContractNegotiationAck;
import org.eclipse.edc.connector.controlplane.contract.spi.types.protocol.ContractRemoteMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatedConsumerOffer;
import org.eclipse.edc.connector.controlplane.defaults.storage.contractnegotiation.InMemoryContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.services.contractnegotiation.ContractNegotiationProtocolServiceImpl;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationProtocolService;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.agent.ParticipantAgent;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.CONSUMER;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREEING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.OFFERING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFYING;
import static org.eclipse.edc.spi.response.ResponseStatus.ERROR_RETRY;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
class ContractNegotiationIntegrationTest {
    private static final String CONSUMER_ID = "consumer";
    private static final String PROVIDER_ID = "provider";

    private static final Duration DEFAULT_TEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(100);
    private final Clock clock = Clock.systemUTC();
    private final CriterionOperatorRegistry criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();
    private final InMemoryContractNegotiationStore providerStore = new InMemoryContractNegotiationStore(clock, criterionOperatorRegistry);
    private final InMemoryContractNegotiationStore consumerStore = new InMemoryContractNegotiationStore(clock, criterionOperatorRegistry);
    private final ContractValidationService validationService = mock();
    private final ConsumerOfferResolver offerResolver = mock();
    private final RemoteMessageDispatcherRegistry providerDispatcherRegistry = mock();
    private final RemoteMessageDispatcherRegistry consumerDispatcherRegistry = mock();
    private final ProtocolTokenValidator protocolTokenValidator = mock();
    private final ProtocolWebhook protocolWebhook = () -> "http://dummy";
    protected ParticipantAgent participantAgent = new ParticipantAgent(Collections.emptyMap(), Collections.emptyMap());
    protected TokenRepresentation tokenRepresentation = TokenRepresentation.Builder.newInstance().build();
    private String consumerNegotiationId;
    private final AtomicReference<String> providerNegotiationId = new AtomicReference<>(null);
    private final NoopTransactionContext transactionContext = new NoopTransactionContext();

    private ProviderContractNegotiationManagerImpl providerManager;
    private ConsumerContractNegotiationManagerImpl consumerManager;
    private ContractNegotiationProtocolService consumerService;
    private ContractNegotiationProtocolService providerService;

    @BeforeEach
    void init() {
        var monitor = new ConsoleMonitor();

        providerManager = ProviderContractNegotiationManagerImpl.Builder.newInstance()
                .participantId(PROVIDER_ID)
                .dispatcherRegistry(providerDispatcherRegistry)
                .monitor(monitor)
                .waitStrategy(() -> 1000)
                .observable(mock())
                .store(providerStore)
                .policyStore(mock())
                .protocolWebhook(protocolWebhook)
                .build();

        consumerManager = ConsumerContractNegotiationManagerImpl.Builder.newInstance()
                .participantId(CONSUMER_ID)
                .dispatcherRegistry(consumerDispatcherRegistry)
                .monitor(monitor).waitStrategy(() -> 1000)
                .observable(mock())
                .store(consumerStore)
                .policyStore(mock())
                .protocolWebhook(protocolWebhook)
                .build();

        when(protocolTokenValidator.verify(eq(tokenRepresentation), any(), any(), any())).thenReturn(ServiceResult.success(participantAgent));
        consumerService = new ContractNegotiationProtocolServiceImpl(consumerStore, transactionContext, validationService, offerResolver, protocolTokenValidator, mock(), monitor, mock());
        providerService = new ContractNegotiationProtocolServiceImpl(providerStore, transactionContext, validationService, offerResolver, protocolTokenValidator, mock(), monitor, mock());
    }

    @AfterEach
    void tearDown() {
        providerManager.stop();
        consumerManager.stop();
    }

    @Test
    void testNegotiation_initialOfferAccepted() {
        when(consumerDispatcherRegistry.dispatch(any(), isA(ContractRequestMessage.class))).then(onConsumerSentRequest());
        when(providerDispatcherRegistry.dispatch(any(), isA(ContractAgreementMessage.class))).then(onProviderSentAgreementRequest());
        when(consumerDispatcherRegistry.dispatch(any(), isA(ContractAgreementVerificationMessage.class))).then(onConsumerSentAgreementVerification());
        when(providerDispatcherRegistry.dispatch(any(), isA(ContractNegotiationEventMessage.class))).then(onProviderSentNegotiationEventMessage());
        consumerNegotiationId = "consumerNegotiationId";
        var offer = getContractOffer();
        var validatableOffer = mock(ValidatableConsumerOffer.class);

        when(validatableOffer.getContractPolicy()).thenReturn(Policy.Builder.newInstance().build());
        when(offerResolver.resolveOffer(any())).thenReturn(ServiceResult.success(validatableOffer));
        when(validationService.validateInitialOffer(participantAgent, validatableOffer)).thenReturn(Result.success(new ValidatedConsumerOffer(CONSUMER_ID, offer)));
        when(validationService.validateConfirmed(eq(participantAgent), any(ContractAgreement.class), any(ContractOffer.class))).thenReturn(Result.success());
        when(validationService.validateRequest(eq(participantAgent), any(ContractNegotiation.class))).thenReturn(Result.success());

        // Start provider and consumer negotiation managers
        providerManager.start();
        consumerManager.start();

        // Create an initial request and trigger consumer manager
        var request = ContractRequest.Builder.newInstance()
                .counterPartyAddress("callbackAddress")
                .contractOffer(offer)
                .protocol("protocol")
                .callbackAddresses(List.of(CallbackAddress.Builder.newInstance().uri("local://test").build()))
                .build();

        consumerManager.initiate(request);

        await().atMost(DEFAULT_TEST_TIMEOUT).pollInterval(DEFAULT_POLL_INTERVAL).untilAsserted(() -> {
            assertThat(consumerNegotiationId).isNotNull();
            var consumerNegotiation = consumerStore.findById(consumerNegotiationId);
            var maybeProviderNegotiation = providerStore.findAll().filter(it -> it.getCorrelationId().equals(consumerNegotiationId)).findAny();
            assertThat(consumerNegotiation).isNotNull();
            assertThat(maybeProviderNegotiation).isPresent();
            var providerNegotiation = maybeProviderNegotiation.get();

            // Assert that the consumer has the callbacks
            assertThat(consumerNegotiation.getCallbackAddresses()).hasSize(1);
            assertThat(providerNegotiation.getCallbackAddresses()).hasSize(0);

            // Assert that provider and consumer have the same offers and agreement stored
            assertThat(consumerNegotiation.getContractOffers()).hasSize(1);
            assertThat(providerNegotiation.getContractOffers()).hasSize(1);
            assertThat(consumerNegotiation.getContractOffers().get(0)).isEqualTo(offer);
            assertThat(consumerNegotiation.getState()).isEqualTo(ContractNegotiationStates.FINALIZED.code());
            assertThat(consumerNegotiation.getLastContractOffer()).isEqualTo(providerNegotiation.getLastContractOffer());
            assertThat(consumerNegotiation.getContractAgreement()).isEqualTo(providerNegotiation.getContractAgreement());

            verify(validationService, atLeastOnce()).validateInitialOffer(participantAgent, validatableOffer);
            verify(validationService, atLeastOnce()).validateConfirmed(eq(participantAgent), any(ContractAgreement.class), any(ContractOffer.class));
        });
    }

    @Test
    void testNegotiation_initialOfferDeclined() {
        when(providerDispatcherRegistry.dispatch(any(), isA(ContractNegotiationTerminationMessage.class))).then(onProviderSentTermination());
        when(consumerDispatcherRegistry.dispatch(any(), isA(ContractRequestMessage.class))).then(onConsumerSentRequest());
        consumerNegotiationId = null;
        var offer = getContractOffer();
        var validatableOffer = mock(ValidatableConsumerOffer.class);

        when(validatableOffer.getContractPolicy()).thenReturn(Policy.Builder.newInstance().build());
        when(offerResolver.resolveOffer(any())).thenReturn(ServiceResult.success(validatableOffer));
        when(validationService.validateInitialOffer(participantAgent, validatableOffer)).thenReturn(Result.failure("must be declined"));

        // Start provider and consumer negotiation managers
        providerManager.start();
        consumerManager.start();

        // Create an initial request and trigger consumer manager
        var request = ContractRequest.Builder.newInstance()
                .counterPartyAddress("callbackAddress")
                .contractOffer(offer)
                .protocol("protocol")
                .build();

        consumerManager.initiate(request);

        await().atMost(DEFAULT_TEST_TIMEOUT)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .untilAsserted(() -> verify(validationService, atLeastOnce()).validateInitialOffer(participantAgent, validatableOffer));
    }

    @Test
    void testNegotiation_agreementDeclined() {
        when(providerDispatcherRegistry.dispatch(any(), isA(ContractAgreementMessage.class))).then(onProviderSentAgreementRequest());
        when(consumerDispatcherRegistry.dispatch(any(), isA(ContractRequestMessage.class))).then(onConsumerSentRequest());
        when(consumerDispatcherRegistry.dispatch(any(), isA(ContractNegotiationTerminationMessage.class))).then(onConsumerSentTermination());
        consumerNegotiationId = null;
        var offer = getContractOffer();
        var validatableOffer = mock(ValidatableConsumerOffer.class);

        when(validatableOffer.getContractPolicy()).thenReturn(Policy.Builder.newInstance().build());
        when(offerResolver.resolveOffer(any())).thenReturn(ServiceResult.success(validatableOffer));
        when(validationService.validateInitialOffer(participantAgent, validatableOffer)).thenReturn(Result.success(new ValidatedConsumerOffer(CONSUMER_ID, offer)));
        when(validationService.validateConfirmed(eq(participantAgent), any(ContractAgreement.class), any(ContractOffer.class))).thenReturn(Result.failure("error"));

        // Start provider and consumer negotiation managers
        providerManager.start();
        consumerManager.start();

        // Create an initial request and trigger consumer manager
        var request = ContractRequest.Builder.newInstance()
                .counterPartyAddress("callbackAddress")
                .contractOffer(offer)
                .protocol("protocol")
                .callbackAddresses(List.of(CallbackAddress.Builder.newInstance().uri("local://test").build()))
                .build();

        consumerManager.initiate(request);

        await().atMost(DEFAULT_TEST_TIMEOUT).pollInterval(DEFAULT_POLL_INTERVAL).untilAsserted(() -> {
            assertThat(consumerNegotiationId).isNotNull();
            var consumerNegotiation = consumerStore.findById(consumerNegotiationId);
            var maybeProviderNegotiation = providerStore.findAll().filter(it -> it.getCorrelationId().equals(consumerNegotiationId)).findAny();
            assertThat(consumerNegotiation).isNotNull();
            assertThat(maybeProviderNegotiation).isPresent();
            var providerNegotiation = maybeProviderNegotiation.get();

            // Assert that the consumer has the callbacks
            assertThat(consumerNegotiation.getCallbackAddresses()).hasSize(1);
            assertThat(providerNegotiation.getCallbackAddresses()).hasSize(0);

            // Assert that provider and consumer have the same offers stored
            assertThat(consumerNegotiation.getContractOffers()).hasSize(1);
            assertThat(providerNegotiation.getContractOffers()).hasSize(1);
            assertThat(consumerNegotiation.getLastContractOffer()).isEqualTo(providerNegotiation.getLastContractOffer());

            // Assert that no agreement has been stored on either side
            assertThat(consumerNegotiation.getContractAgreement()).isNull();
            assertThat(providerNegotiation.getContractAgreement()).isNull();

            verify(validationService, atLeastOnce()).validateInitialOffer(participantAgent, validatableOffer);
            verify(validationService, atLeastOnce()).validateConfirmed(eq(participantAgent), any(ContractAgreement.class), any(ContractOffer.class));
        });
    }

    private Answer<Object> onConsumerSentRequest() {
        return i -> {
            ContractRequestMessage request = i.getArgument(1);
            consumerNegotiationId = request.getProcessId();
            var result = providerService.notifyRequested(request, tokenRepresentation);
            return toFuture(result, ContractNegotiationAck.Builder.newInstance().providerPid(request.getProviderPid()).build());
        };
    }

    @NotNull
    private Answer<Object> onConsumerSentTermination() {
        return i -> {
            ContractNegotiationTerminationMessage request = i.getArgument(1);
            var result = providerService.notifyTerminated(request, tokenRepresentation);
            return toFuture(result, "Success!");
        };
    }

    @NotNull
    private Answer<Object> onProviderSentAgreementRequest() {
        return i -> {
            ContractAgreementMessage request = i.getArgument(1);
            providerNegotiationId.set(request.getProviderPid());
            var result = consumerService.notifyAgreed(request, tokenRepresentation);
            return toFuture(result, "Success!");
        };
    }

    @NotNull
    private Answer<Object> onProviderSentNegotiationEventMessage() {
        return i -> {
            ContractNegotiationEventMessage request = i.getArgument(1);
            var result = consumerService.notifyFinalized(request, tokenRepresentation);
            return toFuture(result, "Success!");
        };
    }

    @NotNull
    private Answer<Object> onConsumerSentAgreementVerification() {
        return i -> {
            ContractAgreementVerificationMessage request = i.getArgument(1);
            request.setProcessId(providerNegotiationId.get());
            var result = providerService.notifyVerified(request, tokenRepresentation);
            return toFuture(result, "Success!");
        };
    }

    @NotNull
    private Answer<Object> onProviderSentTermination() {
        return i -> {
            ContractNegotiationTerminationMessage request = i.getArgument(1);
            var result = consumerService.notifyTerminated(request, tokenRepresentation);
            return toFuture(result, "Success!");
        };
    }

    @NotNull
    private CompletableFuture<?> toFuture(ServiceResult<ContractNegotiation> result, Object content) {
        if (result.succeeded()) {
            return completedFuture(StatusResult.success(content));
        } else {
            return failedFuture(new Exception("Negotiation failed."));
        }
    }

    /**
     * Creates the initial contract offer.
     *
     * @return the contract offer.
     */
    private ContractOffer getContractOffer() {
        return ContractOffer.Builder.newInstance()
                .id(ContractOfferId.create("1", "test-asset-id").toString())
                .assetId(randomUUID().toString())
                .policy(Policy.Builder.newInstance()
                        .type(PolicyType.CONTRACT)
                        .assigner("assigner")
                        .assignee("assignee")
                        .duty(Duty.Builder.newInstance()
                                .action(Action.Builder.newInstance().type("use").build())
                                .build())
                        .build())
                .build();
    }

    @Nested
    class IdempotencyProcessStateReplication {

        @ParameterizedTest
        @ArgumentsSource(EgressMessages.class)
        void shouldSendMessageWithTheSameId_whenFirstDispatchFailed(ContractNegotiation.Type type, ContractNegotiationStates state,
                                                                    Class<? extends ContractRemoteMessage> messageType) {
            var dispatcherRegistry = type == CONSUMER ? consumerDispatcherRegistry : providerDispatcherRegistry;
            var store = type == CONSUMER ? consumerStore : providerStore;
            var manager = type == CONSUMER ? consumerManager : providerManager;

            var ack = ContractNegotiationAck.Builder.newInstance().providerPid("providerPid").consumerPid("consumerPid").build();
            when(dispatcherRegistry.dispatch(any(), isA(messageType)))
                    .thenReturn(completedFuture(StatusResult.failure(ERROR_RETRY)))
                    .thenReturn(completedFuture(StatusResult.success(ack)));

            var negotiation = contractNegotiationBuilder().type(type).state(state.code()).build();
            store.save(negotiation);

            manager.start();

            var sentMessages = ArgumentCaptor.forClass(messageType);
            await().atMost(DEFAULT_TEST_TIMEOUT).untilAsserted(() -> {
                verify(dispatcherRegistry, times(2)).dispatch(any(), sentMessages.capture());
                assertThat(sentMessages.getAllValues())
                        .map(ContractRemoteMessage::getId)
                        .matches(ids -> ids.stream().distinct().count() == 1);
            });

            await().atMost(DEFAULT_TEST_TIMEOUT).untilAsserted(() -> {
                var actual = store.findById(negotiation.getId());
                assertThat(actual).isNotNull();
                assertThat(actual.getState()).isNotEqualTo(state.code());
                assertThat(actual.lastSentProtocolMessage()).isNull();
            });
        }

        private ContractNegotiation.Builder contractNegotiationBuilder() {
            return ContractNegotiation.Builder.newInstance()
                    .correlationId("processId")
                    .counterPartyId("connectorId")
                    .counterPartyAddress("callbackAddress")
                    .protocol("protocol")
                    .stateTimestamp(Instant.now().toEpochMilli())
                    .contractOffer(contractOffer())
                    .contractAgreement(createContractAgreement());
        }

        private ContractOffer contractOffer() {
            return ContractOffer.Builder.newInstance().id("id:assetId:random")
                    .policy(Policy.Builder.newInstance().build())
                    .assetId("assetId")
                    .build();
        }

        private ContractAgreement createContractAgreement() {
            return ContractAgreement.Builder.newInstance()
                    .id("contractId")
                    .consumerId("consumerId")
                    .providerId("providerId")
                    .assetId("assetId")
                    .policy(Policy.Builder.newInstance().build())
                    .build();
        }

        private static class EgressMessages implements ArgumentsProvider {

            @Override
            public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
                return Stream.of(
                        arguments(CONSUMER, REQUESTING, ContractRequestMessage.class),
                        arguments(CONSUMER, ACCEPTING, ContractNegotiationEventMessage.class),
                        arguments(CONSUMER, VERIFYING, ContractAgreementVerificationMessage.class),
                        arguments(CONSUMER, TERMINATING, ContractNegotiationTerminationMessage.class),
                        arguments(PROVIDER, OFFERING, ContractOfferMessage.class),
                        arguments(PROVIDER, AGREEING, ContractAgreementMessage.class),
                        arguments(PROVIDER, FINALIZING, ContractNegotiationEventMessage.class),
                        arguments(PROVIDER, TERMINATING, ContractNegotiationTerminationMessage.class)
                );
            }
        }
    }

}
