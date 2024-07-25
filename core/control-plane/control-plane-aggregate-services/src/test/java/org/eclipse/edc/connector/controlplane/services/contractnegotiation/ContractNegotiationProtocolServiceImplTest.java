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

import org.eclipse.edc.connector.controlplane.contract.observe.ContractNegotiationObservableImpl;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationListener;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.ConsumerOfferResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationTerminationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractOfferMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatedConsumerOffer;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationProtocolService;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.agent.ParticipantAgent;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.message.ProcessRemoteMessage;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.CONSUMER;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.OFFERED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFIED;
import static org.eclipse.edc.connector.controlplane.services.contractnegotiation.ContractNegotiationProtocolServiceImpl.CONTRACT_NEGOTIATION_REQUEST_SCOPE;
import static org.eclipse.edc.connector.controlplane.services.contractnegotiation.ContractNegotiationProtocolServiceImplTest.TestFunctions.contractOffer;
import static org.eclipse.edc.connector.controlplane.services.contractnegotiation.ContractNegotiationProtocolServiceImplTest.TestFunctions.createPolicy;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.agent.ParticipantAgent.PARTICIPANT_IDENTITY;
import static org.eclipse.edc.spi.result.ServiceFailure.Reason.BAD_REQUEST;
import static org.eclipse.edc.spi.result.ServiceFailure.Reason.NOT_FOUND;
import static org.eclipse.edc.spi.result.ServiceFailure.Reason.UNAUTHORIZED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContractNegotiationProtocolServiceImplTest {

    private static final String CONSUMER_ID = "consumer";

    private final ContractNegotiationStore store = mock();
    private final TransactionContext transactionContext = spy(new NoopTransactionContext());
    private final ContractValidationService validationService = mock();
    private final ConsumerOfferResolver consumerOfferResolver = mock();
    private final ContractNegotiationListener listener = mock();
    private final ProtocolTokenValidator protocolTokenValidator = mock();
    private ContractNegotiationProtocolService service;

    @BeforeEach
    void setUp() {
        var observable = new ContractNegotiationObservableImpl();
        observable.registerListener(listener);
        service = new ContractNegotiationProtocolServiceImpl(store, transactionContext, validationService,
                consumerOfferResolver, protocolTokenValidator, observable, mock(), mock());
    }

    @Test
    void notifyAccepted_shouldTransitionToAccepted() {
        var contractNegotiation = createContractNegotiationOffered();
        var participantAgent = participantAgent();
        var tokenRepresentation = tokenRepresentation();
        var message = ContractNegotiationEventMessage.Builder.newInstance()
                .protocol("protocol")
                .counterPartyAddress("http://any")
                .processId("processId")
                .consumerPid("consumerPid")
                .providerPid("providerPid")
                .type(ContractNegotiationEventMessage.Type.ACCEPTED)
                .policy(Policy.Builder.newInstance().build())
                .build();
        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                .thenReturn(ServiceResult.success(participantAgent));
        when(store.findById(any())).thenReturn(contractNegotiation);
        when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(contractNegotiation));
        when(validationService.validateRequest(eq(participantAgent), any(ContractNegotiation.class))).thenReturn(Result.success());

        var result = service.notifyAccepted(message, tokenRepresentation);

        assertThat(result).isSucceeded();
        verify(store).findById("processId");
        verify(store).findByIdAndLease("processId");
        verify(store).save(argThat(negotiation -> negotiation.getState() == ACCEPTED.code()));
        verify(validationService).validateRequest(participantAgent, contractNegotiation);
        verify(listener).accepted(any());
        verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
    }

    @Test
    void notifyAgreed_shouldTransitionToAgreed() {
        var negotiationConsumerRequested = createContractNegotiationRequested();
        var participantAgent = participantAgent();
        var tokenRepresentation = tokenRepresentation();

        var contractAgreement = mock(ContractAgreement.class);
        var message = ContractAgreementMessage.Builder.newInstance()
                .protocol("protocol")
                .counterPartyAddress("http://any")
                .processId("processId")
                .consumerPid("consumerPid")
                .providerPid("providerPid")
                .contractAgreement(contractAgreement)
                .build();

        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message))).thenReturn(ServiceResult.success(participantAgent));
        when(store.findById(any())).thenReturn(negotiationConsumerRequested);
        when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiationConsumerRequested));
        when(validationService.validateConfirmed(eq(participantAgent), eq(contractAgreement), any(ContractOffer.class))).thenReturn(Result.success());

        var result = service.notifyAgreed(message, tokenRepresentation);

        assertThat(result).isSucceeded();
        verify(store).findById("processId");
        verify(store).findByIdAndLease("processId");
        verify(store).save(argThat(negotiation ->
                negotiation.getState() == AGREED.code() &&
                        negotiation.getContractAgreement() == contractAgreement
        ));
        verify(validationService).validateConfirmed(eq(participantAgent), eq(contractAgreement), any(ContractOffer.class));
        verify(listener).agreed(any());
        verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
    }

    @Test
    void notifyVerified_shouldTransitionToVerified() {
        var participantAgent = participantAgent();
        var tokenRepresentation = tokenRepresentation();
        var contractOffer = contractOffer();
        var negotiation = contractNegotiationBuilder().id("negotiationId").type(PROVIDER).state(AGREED.code()).contractOffer(contractOffer).build();
        when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
        when(validationService.validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class))).thenReturn(Result.success());
        var message = ContractAgreementVerificationMessage.Builder.newInstance()
                .protocol("protocol")
                .counterPartyAddress("http://any")
                .processId("processId")
                .consumerPid("consumerPid")
                .providerPid("providerPid")
                .build();

        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message))).thenReturn(ServiceResult.success(participantAgent));
        when(store.findById(any())).thenReturn(negotiation);
        when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
        when(validationService.validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class))).thenReturn(Result.success());

        var result = service.notifyVerified(message, tokenRepresentation);

        assertThat(result).isSucceeded();
        verify(store).findById("processId");
        verify(store).findByIdAndLease("processId");
        verify(store).save(argThat(n -> n.getState() == VERIFIED.code()));
        verify(listener).verified(negotiation);
        verify(validationService).validateRequest(same(participantAgent), any(ContractNegotiation.class));
        verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
    }

    @Test
    void notifyFinalized_shouldTransitionToFinalized() {
        var contractOffer = contractOffer();
        var negotiation = contractNegotiationBuilder().id("negotiationId").type(PROVIDER).contractOffer(contractOffer).state(VERIFIED.code()).build();

        var message = ContractNegotiationEventMessage.Builder.newInstance()
                .type(ContractNegotiationEventMessage.Type.FINALIZED)
                .protocol("protocol")
                .counterPartyAddress("http://any")
                .processId("processId")
                .consumerPid("consumerPid")
                .providerPid("providerPid")
                .build();
        var tokenRepresentation = tokenRepresentation();

        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                .thenReturn(ServiceResult.success(participantAgent()));
        when(store.findById(any())).thenReturn(negotiation);
        when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
        when(validationService.validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class))).thenReturn(Result.success());

        var result = service.notifyFinalized(message, tokenRepresentation);

        assertThat(result).isSucceeded();
        verify(store).findById("processId");
        verify(store).findByIdAndLease("processId");
        verify(store).save(argThat(n -> n.getState() == FINALIZED.code()));
        verify(listener).finalized(negotiation);
        verify(validationService).validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class));
        verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
    }

    @Test
    void notifyTerminated_shouldTransitionToTerminated() {
        var contractOffer = contractOffer();
        var negotiation = contractNegotiationBuilder().id("negotiationId").type(PROVIDER).state(VERIFIED.code()).contractOffer(contractOffer).build();
        var message = ContractNegotiationTerminationMessage.Builder.newInstance()
                .protocol("protocol")
                .processId("processId")
                .consumerPid("consumerPid")
                .providerPid("providerPid")
                .counterPartyAddress("http://any")
                .rejectionReason("any")
                .build();
        var tokenRepresentation = tokenRepresentation();

        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                .thenReturn(ServiceResult.success(participantAgent()));
        when(store.findById(any())).thenReturn(negotiation);
        when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
        when(validationService.validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class))).thenReturn(Result.success());

        var result = service.notifyTerminated(message, tokenRepresentation);

        assertThat(result).isSucceeded();
        verify(store).findById("processId");
        verify(store).findByIdAndLease("processId");
        verify(store).save(argThat(n -> n.getState() == TERMINATED.code()));
        verify(listener).terminated(negotiation);
        verify(validationService).validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class));
        verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
    }

    @Test
    void findById_shouldReturnNegotiation_whenValidCounterParty() {
        var id = "negotiationId";
        var participantAgent = participantAgent();
        var tokenRepresentation = tokenRepresentation();
        var contractOffer = contractOffer();
        var negotiation = contractNegotiationBuilder().id(id).type(PROVIDER).contractOffer(contractOffer).state(VERIFIED.code()).build();

        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), isNull()))
                .thenReturn(ServiceResult.success(participantAgent));
        when(store.findById(id)).thenReturn(negotiation);
        when(validationService.validateRequest(participantAgent, negotiation)).thenReturn(Result.success());

        var result = service.findById(id, tokenRepresentation);

        assertThat(result)
                .isSucceeded()
                .isEqualTo(negotiation);
    }

    @Test
    void findById_shouldReturnNotFound_whenNegotiationNotFound() {
        var tokenRepresentation = tokenRepresentation();
        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any()))
                .thenReturn(ServiceResult.success(participantAgent()));
        when(store.findById(any())).thenReturn(null);

        var result = service.findById("invalidId", tokenRepresentation);

        assertThat(result)
                .isFailed()
                .extracting(ServiceFailure::getReason)
                .isEqualTo(NOT_FOUND);
    }

    @Test
    void findById_shouldReturnBadRequest_whenCounterPartyUnauthorized() {
        var id = "negotiationId";
        var participantAgent = participantAgent();
        var tokenRepresentation = tokenRepresentation();
        var contractOffer = contractOffer();

        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), isNull())).thenReturn(ServiceResult.success(participantAgent));

        var negotiation = contractNegotiationBuilder().id(id).type(PROVIDER).contractOffer(contractOffer).state(VERIFIED.code()).build();

        when(store.findById(id)).thenReturn(negotiation);
        when(validationService.validateRequest(participantAgent, negotiation)).thenReturn(Result.failure("validation error"));

        var result = service.findById(id, tokenRepresentation);

        assertThat(result)
                .isFailed()
                .extracting(ServiceFailure::getReason)
                .isEqualTo(BAD_REQUEST);
    }

    @ParameterizedTest
    @ArgumentsSource(NotifyArguments.class)
    <M extends RemoteMessage> void notify_shouldReturnNotFound_whenNotFound(MethodCall<M> methodCall, M message) {
        var tokenRepresentation = tokenRepresentation();
        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                .thenReturn(ServiceResult.success(participantAgent()));
        when(store.findByIdAndLease(any())).thenReturn(StoreResult.notFound("not found"));

        // currently ContractRequestMessage cannot happen on an already existing negotiation
        if (!(message instanceof ContractRequestMessage)) {
            var result = methodCall.call(service, message, tokenRepresentation);

            assertThat(result).isFailed().extracting(ServiceFailure::getReason).isEqualTo(NOT_FOUND);
            verify(store, never()).save(any());
            verifyNoInteractions(listener);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(NotifyArguments.class)
    <M extends RemoteMessage> void notify_shouldReturnBadRequest_whenValidationFails(MethodCall<M> methodCall, M message) {
        var tokenRepresentation = tokenRepresentation();
        var validatableOffer = mock(ValidatableConsumerOffer.class);

        when(validatableOffer.getContractPolicy()).thenReturn(createPolicy());
        when(consumerOfferResolver.resolveOffer(any())).thenReturn(ServiceResult.success(validatableOffer));
        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                .thenReturn(ServiceResult.success(participantAgent()));
        when(store.findById(any())).thenReturn(createContractNegotiationOffered());
        when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(createContractNegotiationOffered()));
        when(validationService.validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class))).thenReturn(Result.failure("validation error"));
        when(validationService.validateInitialOffer(any(ParticipantAgent.class), isA(ValidatableConsumerOffer.class))).thenReturn(Result.failure("error"));
        when(validationService.validateConfirmed(any(ParticipantAgent.class), any(), any(ContractOffer.class))).thenReturn(Result.failure("failure"));

        var result = methodCall.call(service, message, tokenRepresentation);

        assertThat(result).isFailed().extracting(ServiceFailure::getReason).isEqualTo(BAD_REQUEST);
        verify(store, never()).save(any());
        verifyNoInteractions(listener);
    }

    @ParameterizedTest
    @ArgumentsSource(NotifyArguments.class)
    <M extends RemoteMessage> void notify_shouldReturnUnauthorized_whenTokenValidationFails(MethodCall<M> methodCall, M message) {
        var tokenRepresentation = tokenRepresentation();
        var validatableOffer = mock(ValidatableConsumerOffer.class);

        when(validatableOffer.getContractPolicy()).thenReturn(createPolicy());
        when(consumerOfferResolver.resolveOffer(any())).thenReturn(ServiceResult.success(validatableOffer));
        when(store.findById(any())).thenReturn(createContractNegotiationOffered());
        when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message))).thenReturn(ServiceResult.unauthorized("unauthorized"));

        var result = methodCall.call(service, message, tokenRepresentation);

        assertThat(result).isFailed().extracting(ServiceFailure::getReason).isEqualTo(UNAUTHORIZED);
        verify(store, never()).save(any());
        verifyNoInteractions(listener);
    }

    private ParticipantAgent participantAgent() {
        return new ParticipantAgent(Collections.emptyMap(), Map.of(PARTICIPANT_IDENTITY, "counterPartyId"));
    }

    private ContractNegotiation createContractNegotiationRequested() {
        var lastOffer = contractOffer();

        return contractNegotiationBuilder()
                .state(REQUESTED.code())
                .contractOffer(lastOffer)
                .build();
    }

    private ContractNegotiation createContractNegotiationOffered() {
        var lastOffer = contractOffer();

        return contractNegotiationBuilder()
                .state(OFFERED.code())
                .type(PROVIDER)
                .contractOffer(lastOffer).build();
    }

    private ContractNegotiation.Builder contractNegotiationBuilder() {
        return ContractNegotiation.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .correlationId("processId")
                .counterPartyId("connectorId")
                .counterPartyAddress("counterPartyAddress")
                .protocol("protocol")
                .stateTimestamp(Instant.now().toEpochMilli());
    }

    private TokenRepresentation tokenRepresentation() {
        return TokenRepresentation.Builder.newInstance()
                .token(UUID.randomUUID().toString())
                .build();
    }

    @FunctionalInterface
    private interface MethodCall<M extends RemoteMessage> {
        ServiceResult<?> call(ContractNegotiationProtocolService service, M message, TokenRepresentation token);
    }

    interface TestFunctions {
        static ContractOffer contractOffer() {
            return ContractOffer.Builder.newInstance()
                    .id(ContractOfferId.create("1", "test-asset-id").toString())
                    .policy(createPolicy())
                    .assetId("assetId")
                    .build();
        }

        static Policy createPolicy() {
            return Policy.Builder.newInstance().build();
        }
    }

    private static class NotifyArguments implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            MethodCall<ContractRequestMessage> requested = ContractNegotiationProtocolService::notifyRequested;
            MethodCall<ContractOfferMessage> offered = ContractNegotiationProtocolService::notifyOffered;
            MethodCall<ContractAgreementMessage> agreed = ContractNegotiationProtocolService::notifyAgreed;
            MethodCall<ContractNegotiationEventMessage> accepted = ContractNegotiationProtocolService::notifyAccepted;
            MethodCall<ContractAgreementVerificationMessage> verified = ContractNegotiationProtocolService::notifyVerified;
            MethodCall<ContractNegotiationEventMessage> finalized = ContractNegotiationProtocolService::notifyFinalized;
            MethodCall<ContractNegotiationTerminationMessage> terminated = ContractNegotiationProtocolService::notifyTerminated;
            return Stream.of(
                    Arguments.of(requested, ContractRequestMessage.Builder.newInstance()
                            .callbackAddress("callbackAddress")
                            .counterPartyAddress("counterPartyAddress")
                            .protocol("protocol")
                            .contractOffer(contractOffer())
                            .consumerPid("consumerPid")
                            .providerPid("providerPid")
                            .build(), PROVIDER, INITIAL),
                    Arguments.of(offered, ContractOfferMessage.Builder.newInstance()
                            .callbackAddress("callbackAddress")
                            .protocol("protocol")
                            .contractOffer(contractOffer())
                            .consumerPid("consumerPid")
                            .providerPid("providerPid")
                            .build(), CONSUMER, REQUESTED),
                    Arguments.of(agreed, ContractAgreementMessage.Builder.newInstance()
                            .protocol("protocol")
                            .counterPartyAddress("http://any")
                            .consumerPid("consumerPid")
                            .providerPid("providerPid")
                            .contractAgreement(mock(ContractAgreement.class))
                            .build(), CONSUMER, ACCEPTED),
                    Arguments.of(accepted, ContractNegotiationEventMessage.Builder.newInstance()
                            .type(ContractNegotiationEventMessage.Type.ACCEPTED)
                            .protocol("protocol")
                            .counterPartyAddress("http://any")
                            .consumerPid("consumerPid")
                            .providerPid("providerPid")
                            .build(), PROVIDER, OFFERED),
                    Arguments.of(verified, ContractAgreementVerificationMessage.Builder.newInstance()
                            .protocol("protocol")
                            .counterPartyAddress("http://any")
                            .consumerPid("consumerPid")
                            .providerPid("providerPid")
                            .build(), PROVIDER, AGREED),
                    Arguments.of(finalized, ContractNegotiationEventMessage.Builder.newInstance()
                            .type(ContractNegotiationEventMessage.Type.FINALIZED)
                            .protocol("protocol")
                            .counterPartyAddress("http://any")
                            .consumerPid("consumerPid")
                            .providerPid("providerPid")
                            .build(), CONSUMER, VERIFIED),
                    Arguments.of(terminated, ContractNegotiationTerminationMessage.Builder.newInstance()
                            .protocol("protocol")
                            .consumerPid("consumerPid")
                            .providerPid("providerPid")
                            .counterPartyAddress("http://any")
                            .rejectionReason("any")
                            .build(), PROVIDER, INITIAL)
            );
        }

    }

    @Nested
    class NotifyRequested {
        @Test
        void shouldInitiateNegotiation_whenNegotiationDoesNotExist() {
            var participantAgent = participantAgent();
            var tokenRepresentation = tokenRepresentation();
            var contractOffer = contractOffer();
            var validatedOffer = new ValidatedConsumerOffer(CONSUMER_ID, contractOffer);
            var message = ContractRequestMessage.Builder.newInstance()
                    .callbackAddress("callbackAddress")
                    .protocol("protocol")
                    .contractOffer(contractOffer)
                    .consumerPid("consumerPid")
                    .build();
            var validatableOffer = mock(ValidatableConsumerOffer.class);

            when(validatableOffer.getContractPolicy()).thenReturn(createPolicy());
            when(consumerOfferResolver.resolveOffer(contractOffer.getId())).thenReturn(ServiceResult.success(validatableOffer));
            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message))).thenReturn(ServiceResult.success(participantAgent));
            when(store.findByIdAndLease(any())).thenReturn(StoreResult.notFound("not found"));
            when(validationService.validateInitialOffer(participantAgent, validatableOffer)).thenReturn(Result.success(validatedOffer));

            var result = service.notifyRequested(message, tokenRepresentation);

            assertThat(result).isSucceeded();
            var calls = ArgumentCaptor.forClass(ContractNegotiation.class);
            verify(store, never()).findByIdAndLease(any());
            verify(store).save(calls.capture());
            assertThat(calls.getAllValues()).anySatisfy(n -> {
                assertThat(n.getState()).isEqualTo(REQUESTED.code());
                assertThat(n.getCounterPartyAddress()).isEqualTo(message.getCallbackAddress());
                assertThat(n.getProtocol()).isEqualTo(message.getProtocol());
                assertThat(n.getCorrelationId()).isEqualTo(message.getConsumerPid());
                assertThat(n.getContractOffers()).hasSize(1);
                assertThat(n.getLastContractOffer()).isEqualTo(contractOffer);
            });
            verify(listener).requested(any());
            verify(validationService).validateInitialOffer(participantAgent, validatableOffer);
            verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
        }

        @Test
        void shouldTransitionToRequested_whenNegotiationFound() {
            var participantAgent = participantAgent();
            var tokenRepresentation = tokenRepresentation();
            var contractOffer = contractOffer();
            var validatedOffer = new ValidatedConsumerOffer(CONSUMER_ID, contractOffer);
            var negotiation = createContractNegotiationOffered();
            var message = ContractRequestMessage.Builder.newInstance()
                    .callbackAddress("callbackAddress")
                    .protocol("protocol")
                    .processId("processId")
                    .contractOffer(contractOffer)
                    .consumerPid("consumerPid")
                    .providerPid("providerPid")
                    .build();

            var validatableOffer = mock(ValidatableConsumerOffer.class);

            when(validatableOffer.getContractPolicy()).thenReturn(createPolicy());
            when(consumerOfferResolver.resolveOffer(contractOffer.getId())).thenReturn(ServiceResult.success(validatableOffer));
            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message))).thenReturn(ServiceResult.success(participantAgent));
            when(store.findById(any())).thenReturn(negotiation);
            when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
            when(validationService.validateInitialOffer(participantAgent, validatableOffer)).thenReturn(Result.success(validatedOffer));


            var result = service.notifyRequested(message, tokenRepresentation);

            assertThat(result).isSucceeded();
            verify(store).findByIdAndLease("providerPid");
            var calls = ArgumentCaptor.forClass(ContractNegotiation.class);
            verify(store).save(calls.capture());
            assertThat(calls.getAllValues()).anySatisfy(n -> {
                assertThat(n.getState()).isEqualTo(REQUESTED.code());
                assertThat(n.getProtocol()).isEqualTo(message.getProtocol());
                assertThat(n.getContractOffers()).hasSize(2);
                assertThat(n.getLastContractOffer()).isEqualTo(contractOffer);
            });
            verify(listener).requested(any());
            verify(store).findByIdAndLease("providerPid");
            verify(validationService).validateInitialOffer(participantAgent, validatableOffer);
            verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
        }

        @Test
        void shouldReturnNotFound_whenOfferNotFound() {
            var tokenRepresentation = tokenRepresentation();
            var contractOffer = contractOffer();
            var message = ContractRequestMessage.Builder.newInstance()
                    .callbackAddress("callbackAddress")
                    .protocol("protocol")
                    .contractOffer(contractOffer)
                    .consumerPid("consumerPid")
                    .build();
            var validatableOffer = mock(ValidatableConsumerOffer.class);

            when(validatableOffer.getContractPolicy()).thenReturn(createPolicy());
            when(consumerOfferResolver.resolveOffer(contractOffer.getId())).thenReturn(ServiceResult.notFound(""));

            var result = service.notifyRequested(message, tokenRepresentation);

            assertThat(result)
                    .isFailed()
                    .extracting(ServiceFailure::getReason)
                    .isEqualTo(NOT_FOUND);
        }
    }

    @Nested
    class NotifyOffered {

        @Test
        void shouldInitiateNegotiation_whenNegotiationDoesNotExist() {
            var tokenRepresentation = tokenRepresentation();
            var contractOffer = contractOffer();
            var message = ContractOfferMessage.Builder.newInstance()
                    .callbackAddress("callbackAddress")
                    .protocol("protocol")
                    .contractOffer(contractOffer)
                    .providerPid("providerPid")
                    .build();
            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                    .thenReturn(ServiceResult.success(participantAgent()));

            var result = service.notifyOffered(message, tokenRepresentation);

            assertThat(result).isSucceeded();
            var calls = ArgumentCaptor.forClass(ContractNegotiation.class);
            verify(store, never()).findByIdAndLease(any());
            verify(store).save(calls.capture());
            assertThat(calls.getAllValues()).anySatisfy(n -> {
                assertThat(n.getState()).isEqualTo(OFFERED.code());
                assertThat(n.getType()).isEqualTo(CONSUMER);
                assertThat(n.getCounterPartyId()).isEqualTo("counterPartyId");
                assertThat(n.getCounterPartyAddress()).isEqualTo(message.getCallbackAddress());
                assertThat(n.getProtocol()).isEqualTo(message.getProtocol());
                assertThat(n.getCorrelationId()).isEqualTo(message.getConsumerPid());
                assertThat(n.getContractOffers()).hasSize(1);
                assertThat(n.getLastContractOffer()).isEqualTo(contractOffer);
            });
            verify(listener).offered(any());
            verifyNoInteractions(validationService, consumerOfferResolver);
            verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
        }

        @Test
        void shouldTransitionToOffered_whenNegotiationAlreadyExist() {
            var processId = "processId";
            var participantAgent = participantAgent();
            var tokenRepresentation = tokenRepresentation();
            var contractOffer = contractOffer();
            var message = ContractOfferMessage.Builder.newInstance()
                    .callbackAddress("callbackAddress")
                    .protocol("protocol")
                    .contractOffer(contractOffer)
                    .processId("providerPid")
                    .consumerPid("consumerPid")
                    .providerPid("providerPid")
                    .build();
            var negotiation = createContractNegotiationRequested();

            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                    .thenReturn(ServiceResult.success(participantAgent));
            when(store.findById(processId)).thenReturn(negotiation);
            when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
            when(validationService.validateRequest(participantAgent, negotiation)).thenReturn(Result.success());

            var result = service.notifyOffered(message, tokenRepresentation);

            assertThat(result).isSucceeded();
            var updatedNegotiation = result.getContent();
            assertThat(updatedNegotiation.getContractOffers()).hasSize(2);
            assertThat(updatedNegotiation.getLastContractOffer()).isEqualTo(contractOffer);
            verify(store).findByIdAndLease("providerPid");
            verify(listener).offered(any());
            verify(transactionContext, atLeastOnce()).execute(any(TransactionContext.ResultTransactionBlock.class));
        }

        @Test
        void shouldReturnNotFound_whenOfferNotFound() {
            var tokenRepresentation = tokenRepresentation();
            var contractOffer = contractOffer();
            var message = ContractOfferMessage.Builder.newInstance()
                    .callbackAddress("callbackAddress")
                    .protocol("protocol")
                    .contractOffer(contractOffer)
                    .consumerPid("consumerPid")
                    .providerPid("providerPid")
                    .build();
            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                    .thenReturn(ServiceResult.success(participantAgent()));
            when(store.findByIdAndLease(any())).thenReturn(StoreResult.notFound("not found"));

            var result = service.notifyOffered(message, tokenRepresentation);

            assertThat(result)
                    .isFailed()
                    .extracting(ServiceFailure::getReason)
                    .isEqualTo(NOT_FOUND);
        }
    }

    @Nested
    class IdempotencyProcessStateReplication {

        @ParameterizedTest
        @ArgumentsSource(NotifyArguments.class)
        <M extends ProcessRemoteMessage> void notify_shouldStoreReceivedMessageId(MethodCall<M> methodCall, M message,
                                                                                  ContractNegotiation.Type type,
                                                                                  ContractNegotiationStates currentState) {
            var offer = contractOffer();
            var negotiation = contractNegotiationBuilder().state(currentState.code()).type(type).contractOffer(offer).build();
            var validatableOffer = mock(ValidatableConsumerOffer.class);

            when(validatableOffer.getContractPolicy()).thenReturn(createPolicy());
            when(consumerOfferResolver.resolveOffer(any())).thenReturn(ServiceResult.success(validatableOffer));
            when(protocolTokenValidator.verify(any(), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                    .thenReturn(ServiceResult.success(participantAgent()));
            when(store.findById(any())).thenReturn(negotiation);
            when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
            when(validationService.validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class))).thenReturn(Result.success());
            when(validationService.validateInitialOffer(any(ParticipantAgent.class), isA(ValidatableConsumerOffer.class)))
                    .thenAnswer(i -> Result.success(new ValidatedConsumerOffer("any", offer)));
            when(validationService.validateConfirmed(any(ParticipantAgent.class), any(), any())).thenAnswer(i -> Result.success(negotiation));

            var result = methodCall.call(service, message, tokenRepresentation());

            assertThat(result).isSucceeded();
            var captor = ArgumentCaptor.forClass(ContractNegotiation.class);
            verify(store).save(captor.capture());
            var storedNegotiation = captor.getValue();
            assertThat(storedNegotiation.getProtocolMessages().isAlreadyReceived(message.getId())).isTrue();
        }

        @ParameterizedTest
        @ArgumentsSource(NotifyArguments.class)
        <M extends ProcessRemoteMessage> void notify_shouldIgnoreMessage_whenAlreadyReceived(MethodCall<M> methodCall, M message,
                                                                                             ContractNegotiation.Type type,
                                                                                             ContractNegotiationStates currentState) {
            var offer = contractOffer();
            var negotiation = contractNegotiationBuilder().state(currentState.code()).type(type).contractOffer(offer).build();
            negotiation.protocolMessageReceived(message.getId());
            var validatableOffer = mock(ValidatableConsumerOffer.class);

            when(validatableOffer.getContractPolicy()).thenReturn(createPolicy());
            when(consumerOfferResolver.resolveOffer(any())).thenReturn(ServiceResult.success(validatableOffer));
            when(protocolTokenValidator.verify(any(), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                    .thenReturn(ServiceResult.success(participantAgent()));
            when(store.findById(any())).thenReturn(negotiation);
            when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
            when(validationService.validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class))).thenReturn(Result.success());
            when(validationService.validateInitialOffer(any(ParticipantAgent.class), any(ValidatableConsumerOffer.class)))
                    .thenAnswer(i -> Result.success(new ValidatedConsumerOffer("any", offer)));
            when(validationService.validateConfirmed(any(ParticipantAgent.class), any(), any())).thenAnswer(i -> Result.success(negotiation));

            var result = methodCall.call(service, message, tokenRepresentation());

            assertThat(result).isSucceeded();
            verify(store, never()).save(any());
            verifyNoInteractions(listener);
        }

        @ParameterizedTest
        @ArgumentsSource(NotifyArguments.class)
        <M extends ProcessRemoteMessage> void notify_shouldIgnoreMessage_whenFinalState(MethodCall<M> methodCall, M message,
                                                                                        ContractNegotiation.Type type) {
            var offer = contractOffer();
            var negotiation = contractNegotiationBuilder().state(FINALIZED.code()).type(type).contractOffer(offer).build();
            var validatableOffer = mock(ValidatableConsumerOffer.class);

            when(validatableOffer.getContractPolicy()).thenReturn(createPolicy());
            when(consumerOfferResolver.resolveOffer(any())).thenReturn(ServiceResult.success(validatableOffer));
            when(protocolTokenValidator.verify(any(), eq(CONTRACT_NEGOTIATION_REQUEST_SCOPE), any(), eq(message)))
                    .thenReturn(ServiceResult.success(participantAgent()));
            when(store.findById(any())).thenReturn(negotiation);
            when(store.findByIdAndLease(any())).thenReturn(StoreResult.success(negotiation));
            when(validationService.validateRequest(any(ParticipantAgent.class), any(ContractNegotiation.class))).thenReturn(Result.success());
            when(validationService.validateInitialOffer(any(ParticipantAgent.class), any(ValidatableConsumerOffer.class)))
                    .thenAnswer(i -> Result.success(new ValidatedConsumerOffer("any", offer)));
            when(validationService.validateConfirmed(any(ParticipantAgent.class), any(), any())).thenAnswer(i -> Result.success(negotiation));

            var result = methodCall.call(service, message, tokenRepresentation());

            assertThat(result).isSucceeded();
            verify(store, never()).save(any());
            verifyNoInteractions(listener);
        }
    }
}
