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
 *       Fraunhofer Institute for Software and Systems Engineering - implementation for provider offer
 *
 */

package org.eclipse.edc.connector.service.contractnegotiation;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.eclipse.edc.connector.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.contract.spi.offer.ConsumerOfferResolver;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationTerminationMessage;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractOfferMessage;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.contract.spi.types.protocol.ContractRemoteMessage;
import org.eclipse.edc.connector.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.connector.contract.spi.validation.ValidatedConsumerOffer;
import org.eclipse.edc.connector.spi.contractnegotiation.ContractNegotiationProtocolService;
import org.eclipse.edc.connector.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.policy.engine.spi.PolicyScope;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.spi.types.domain.offer.ContractOffer;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiation.Type.PROVIDER;

public class ContractNegotiationProtocolServiceImpl implements ContractNegotiationProtocolService {

    @PolicyScope
    public static final String CONTRACT_NEGOTIATION_REQUEST_SCOPE = "request.contract.negotiation";
    private final ContractNegotiationStore store;
    private final TransactionContext transactionContext;
    private final ContractValidationService validationService;
    private final ConsumerOfferResolver consumerOfferResolver;
    private final ProtocolTokenValidator protocolTokenValidator;
    private final ContractNegotiationObservable observable;
    private final Monitor monitor;
    private final Telemetry telemetry;

    public ContractNegotiationProtocolServiceImpl(ContractNegotiationStore store,
                                                  TransactionContext transactionContext,
                                                  ContractValidationService validationService,
                                                  ConsumerOfferResolver consumerOfferResolver,
                                                  ProtocolTokenValidator protocolTokenValidator,
                                                  ContractNegotiationObservable observable,
                                                  Monitor monitor, Telemetry telemetry) {
        this.store = store;
        this.transactionContext = transactionContext;
        this.validationService = validationService;
        this.consumerOfferResolver = consumerOfferResolver;
        this.protocolTokenValidator = protocolTokenValidator;
        this.observable = observable;
        this.monitor = monitor;
        this.telemetry = telemetry;
    }

    @Override
    @WithSpan
    @NotNull
    public ServiceResult<ContractNegotiation> notifyRequested(ContractRequestMessage message, TokenRepresentation tokenRepresentation) {
        return transactionContext.execute(() -> fetchValidatableOffer(message)
                .compose(validatableOffer -> verifyRequest(tokenRepresentation, validatableOffer.getContractPolicy())
                        .compose(context -> validateOffer(context.claimToken(), validatableOffer))
                        .compose(validatedOffer -> {
                            var result = message.getProviderPid() == null
                                    ? createNegotiation(message, validatedOffer)
                                    : getAndLeaseNegotiation(message.getProviderPid());

                            return result.onSuccess(negotiation -> {
                                if (negotiation.shouldIgnoreIncomingMessage(message.getId())) {
                                    return;
                                }
                                negotiation.protocolMessageReceived(message.getId());
                                negotiation.addContractOffer(validatedOffer.getOffer());
                                negotiation.transitionRequested();
                                update(negotiation);
                                observable.invokeForEach(l -> l.requested(negotiation));
                            });
                        })
                ));
    }

    @Override
    @WithSpan
    @NotNull
    public ServiceResult<ContractNegotiation> notifyOffered(ContractOfferMessage message, TokenRepresentation tokenRepresentation) {
        return transactionContext.execute(() -> getNegotiation(message)
                .compose(contractNegotiation -> verifyRequest(tokenRepresentation, contractNegotiation))
                .compose(context -> validateRequest(context.claimToken(), context.negotiation()))
                .compose(cn -> onMessageDo(message, contractNegotiation -> offeredAction(message, contractNegotiation))));
    }

    @Override
    @WithSpan
    @NotNull
    public ServiceResult<ContractNegotiation> notifyAccepted(ContractNegotiationEventMessage message, TokenRepresentation tokenRepresentation) {
        return transactionContext.execute(() -> getNegotiation(message)
                .compose(contractNegotiation -> verifyRequest(tokenRepresentation, contractNegotiation))
                .compose(context -> validateRequest(context.claimToken(), context.negotiation()))
                .compose(cn -> onMessageDo(message, contractNegotiation -> acceptedAction(message, contractNegotiation))));

    }

    @Override
    @WithSpan
    @NotNull
    public ServiceResult<ContractNegotiation> notifyAgreed(ContractAgreementMessage message, TokenRepresentation tokenRepresentation) {
        return transactionContext.execute(() -> getNegotiation(message)
                .compose(contractNegotiation -> verifyRequest(tokenRepresentation, contractNegotiation))
                .compose(context -> validateAgreed(message, context.claimToken(), context.negotiation()))
                .compose(cn -> onMessageDo(message, contractNegotiation -> agreedAction(message, contractNegotiation))));
    }

    @Override
    @WithSpan
    @NotNull
    public ServiceResult<ContractNegotiation> notifyVerified(ContractAgreementVerificationMessage message, TokenRepresentation tokenRepresentation) {
        return transactionContext.execute(() -> getNegotiation(message)
                .compose(contractNegotiation -> verifyRequest(tokenRepresentation, contractNegotiation))
                .compose(context -> validateRequest(context.claimToken(), context.negotiation()))
                .compose(cn -> onMessageDo(message, contractNegotiation -> verifiedAction(message, contractNegotiation))));
    }

    @Override
    @WithSpan
    @NotNull
    public ServiceResult<ContractNegotiation> notifyFinalized(ContractNegotiationEventMessage message, TokenRepresentation tokenRepresentation) {
        return transactionContext.execute(() -> getNegotiation(message)
                .compose(contractNegotiation -> verifyRequest(tokenRepresentation, contractNegotiation))
                .compose(context -> validateRequest(context.claimToken(), context.negotiation()))
                .compose(cn -> onMessageDo(message, contractNegotiation -> finalizedAction(message, contractNegotiation))));
    }

    @Override
    @WithSpan
    @NotNull
    public ServiceResult<ContractNegotiation> notifyTerminated(ContractNegotiationTerminationMessage message, TokenRepresentation tokenRepresentation) {
        return transactionContext.execute(() -> getNegotiation(message)
                .compose(contractNegotiation -> verifyRequest(tokenRepresentation, contractNegotiation))
                .compose(context -> validateRequest(context.claimToken(), context.negotiation()))
                .compose(cn -> onMessageDo(message, contractNegotiation -> terminatedAction(message, contractNegotiation))));
    }

    @Override
    @WithSpan
    @NotNull
    public ServiceResult<ContractNegotiation> findById(String id, TokenRepresentation tokenRepresentation) {
        return transactionContext.execute(() -> getNegotiation(id)
                .compose(contractNegotiation -> verifyRequest(tokenRepresentation, contractNegotiation))
                .compose(context -> validateRequest(context.claimToken(), context.negotiation())));
    }

    @NotNull
    private ServiceResult<ContractNegotiation> onMessageDo(ContractRemoteMessage message, Function<ContractNegotiation, ServiceResult<ContractNegotiation>> action) {
        return getAndLeaseNegotiation(message.getProcessId())
                .compose(contractNegotiation -> {
                    if (contractNegotiation.shouldIgnoreIncomingMessage(message.getId())) {
                        return ServiceResult.success(contractNegotiation);
                    } else {
                        return action.apply(contractNegotiation);
                    }
                });
    }

    @NotNull
    private ServiceResult<ContractNegotiation> createNegotiation(ContractRequestMessage message, ValidatedConsumerOffer validatedOffer) {
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .correlationId(message.getConsumerPid())
                .counterPartyId(validatedOffer.getConsumerIdentity())
                .counterPartyAddress(message.getCallbackAddress())
                .protocol(message.getProtocol())
                .traceContext(telemetry.getCurrentTraceContext())
                .type(PROVIDER)
                .build();

        return ServiceResult.success(negotiation);
    }

    @NotNull
    private ServiceResult<ValidatedConsumerOffer> validateOffer(ClaimToken claimToken, ValidatableConsumerOffer consumerOffer) {
        var result = validationService.validateInitialOffer(claimToken, consumerOffer);
        if (result.failed()) {
            monitor.debug("[Provider] Contract offer rejected as invalid: " + result.getFailureDetail());
            return ServiceResult.badRequest("Contract offer is not valid: " + result.getFailureDetail());
        } else {
            return ServiceResult.success(result.getContent());
        }
    }

    @NotNull
    private ServiceResult<ValidatableConsumerOffer> fetchValidatableOffer(ContractRequestMessage message) {
        var offerId = Optional.ofNullable(message.getContractOffer())
                .map(ContractOffer::getId)
                .orElseGet(message::getContractOfferId);

        var result = consumerOfferResolver.resolveOffer(offerId);
        if (result.failed()) {
            monitor.debug(() -> "Failed to resolve offer: %s".formatted(result.getFailureDetail()));
            return ServiceResult.notFound("Not found");
        } else {
            return result;
        }
    }

    @NotNull
    private ServiceResult<ContractNegotiation> validateAgreed(ContractAgreementMessage message, ClaimToken claimToken, ContractNegotiation negotiation) {
        var agreement = message.getContractAgreement();
        var result = validationService.validateConfirmed(claimToken, agreement, negotiation.getLastContractOffer());
        if (result.failed()) {
            var msg = "Contract agreement received. Validation failed: " + result.getFailureDetail();
            monitor.debug("[Consumer] " + msg);
            return ServiceResult.badRequest(msg);
        } else {
            return ServiceResult.success(negotiation);
        }
    }

    @NotNull
    private ServiceResult<ContractNegotiation> validateRequest(ClaimToken claimToken, ContractNegotiation negotiation) {
        var result = validationService.validateRequest(claimToken, negotiation);
        if (result.failed()) {
            return ServiceResult.badRequest("Invalid client credentials: " + result.getFailureDetail());
        } else {
            return ServiceResult.success(negotiation);
        }
    }

    @NotNull
    private ServiceResult<ContractNegotiation> acceptedAction(ContractNegotiationEventMessage message, ContractNegotiation negotiation) {
        negotiation.protocolMessageReceived(message.getId());
        negotiation.transitionAccepted();
        update(negotiation);
        observable.invokeForEach(l -> l.accepted(negotiation));
        return ServiceResult.success(negotiation);
    }

    @NotNull
    private ServiceResult<ContractNegotiation> agreedAction(ContractAgreementMessage message, ContractNegotiation negotiation) {
        negotiation.protocolMessageReceived(message.getId());
        negotiation.setContractAgreement(message.getContractAgreement());
        negotiation.transitionAgreed();
        update(negotiation);
        observable.invokeForEach(l -> l.agreed(negotiation));
        return ServiceResult.success(negotiation);
    }

    @NotNull
    private ServiceResult<ContractNegotiation> verifiedAction(ContractAgreementVerificationMessage message, ContractNegotiation negotiation) {
        negotiation.protocolMessageReceived(message.getId());
        negotiation.transitionVerified();
        update(negotiation);
        observable.invokeForEach(l -> l.verified(negotiation));
        return ServiceResult.success(negotiation);
    }

    @NotNull
    private ServiceResult<ContractNegotiation> finalizedAction(ContractNegotiationEventMessage message, ContractNegotiation negotiation) {
        negotiation.protocolMessageReceived(message.getId());
        negotiation.transitionFinalized();
        update(negotiation);
        observable.invokeForEach(l -> l.finalized(negotiation));
        return ServiceResult.success(negotiation);
    }

    @NotNull
    private ServiceResult<ContractNegotiation> offeredAction(ContractOfferMessage message, ContractNegotiation negotiation) {
        negotiation.protocolMessageReceived(message.getId());
        negotiation.addContractOffer(message.getContractOffer());
        negotiation.transitionOffered();
        update(negotiation);
        observable.invokeForEach(l -> l.offered(negotiation));
        return ServiceResult.success(negotiation);
    }

    @NotNull
    private ServiceResult<ContractNegotiation> terminatedAction(ContractNegotiationTerminationMessage message, ContractNegotiation negotiation) {
        negotiation.protocolMessageReceived(message.getId());
        negotiation.transitionTerminated();
        update(negotiation);
        observable.invokeForEach(l -> l.terminated(negotiation));
        return ServiceResult.success(negotiation);
    }

    private ServiceResult<ContractNegotiation> getAndLeaseNegotiation(String negotiationId) {
        return store.findByIdAndLease(negotiationId)
                // recover needed to maintain backward compatibility when there was no distinction between providerPid and consumerPid
                .recover(it -> store.findByCorrelationIdAndLease(negotiationId))
                .flatMap(ServiceResult::from);
    }

    private ServiceResult<ClaimTokenContext> verifyRequest(TokenRepresentation tokenRepresentation, ContractNegotiation contractNegotiation) {
        return verifyRequest(tokenRepresentation, contractNegotiation.getLastContractOffer().getPolicy(), contractNegotiation);
    }

    private ServiceResult<ClaimTokenContext> verifyRequest(TokenRepresentation tokenRepresentation, Policy policy) {
        return verifyRequest(tokenRepresentation, policy, null);
    }

    private ServiceResult<ClaimTokenContext> verifyRequest(TokenRepresentation tokenRepresentation, Policy policy, ContractNegotiation contractNegotiation) {
        var result = protocolTokenValidator.verifyToken(tokenRepresentation, CONTRACT_NEGOTIATION_REQUEST_SCOPE, policy);
        if (result.failed()) {
            monitor.debug(() -> "Verification Failed: %s".formatted(result.getFailureDetail()));
            return ServiceResult.notFound("Not found");
        } else {
            return ServiceResult.success(new ClaimTokenContext(result.getContent(), contractNegotiation));
        }
    }

    private ServiceResult<ContractNegotiation> getNegotiation(ContractRemoteMessage message) {
        return getNegotiation(message.getProcessId());
    }

    private ServiceResult<ContractNegotiation> getNegotiation(String negotiationId) {
        return Optional.ofNullable(store.findById(negotiationId))
                // recover needed to maintain backward compatibility when there was no distinction between providerPid and consumerPid
                .or(() -> Optional.ofNullable(store.findForCorrelationId(negotiationId)))
                .map(ServiceResult::success)
                .orElseGet(() -> ServiceResult.notFound("No negotiation with id %s found".formatted(negotiationId)));
    }

    private void update(ContractNegotiation negotiation) {
        store.save(negotiation);
        monitor.debug(() -> "[%s] ContractNegotiation %s is now in state %s."
                .formatted(negotiation.getType(), negotiation.getId(), negotiation.stateAsString()));
    }

    private record ClaimTokenContext(ClaimToken claimToken, ContractNegotiation negotiation) {
    }
}
