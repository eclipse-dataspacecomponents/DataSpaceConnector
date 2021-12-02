/*
 *  Copyright (c) 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Fraunhofer Institute for Software and Systems Engineering - extended method implementation
 *
 */
package org.eclipse.dataspaceconnector.contract.negotiation;

import org.eclipse.dataspaceconnector.spi.contract.negotiation.ConsumerContractNegotiationManager;
import org.eclipse.dataspaceconnector.spi.contract.negotiation.NegotiationWaitStrategy;
import org.eclipse.dataspaceconnector.spi.contract.negotiation.response.NegotiationResponse;
import org.eclipse.dataspaceconnector.spi.contract.negotiation.store.ContractNegotiationStore;
import org.eclipse.dataspaceconnector.spi.contract.validation.ContractValidationService;
import org.eclipse.dataspaceconnector.spi.contract.validation.OfferValidationResult;
import org.eclipse.dataspaceconnector.spi.iam.ClaimToken;
import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.agreement.ContractAgreement;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.agreement.ContractAgreementRequest;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation.ContractNegotiation;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation.ContractNegotiationStates;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation.ContractOfferRequest;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation.ContractRejection;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractOffer;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.eclipse.dataspaceconnector.spi.contract.negotiation.response.NegotiationResponse.Status.FATAL_ERROR;
import static org.eclipse.dataspaceconnector.spi.contract.negotiation.response.NegotiationResponse.Status.OK;

/**
 * Implementation of the {@link ConsumerContractNegotiationManager}.
 *
 * TODO
 * - InMemoryContractNegotiationStore (see InMemoryTransferProcessStore), implement ContractNegotiationStore
 * - ContractNegotiation: Builder, transfer change methods
 * - ConsumerContractNegotiationManager & ProviderContractNegotiationManager: add start and stop methods, builder
 * - method call in CoreTransferExtension
 *
 */
public class ConsumerContractNegotiationManagerImpl implements ConsumerContractNegotiationManager {
    private final AtomicBoolean active = new AtomicBoolean();
    private ContractNegotiationStore negotiationStore;
    private ContractValidationService validationService;

    private int batchSize = 5;
    private NegotiationWaitStrategy waitStrategy = () -> 5000L;  // default wait five seconds
    private Monitor monitor;
    private ExecutorService executor;

    private RemoteMessageDispatcherRegistry dispatcherRegistry;

    public ConsumerContractNegotiationManagerImpl() {
    }

    public void start(ContractNegotiationStore store) {
        negotiationStore = store;
        active.set(true);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(this::run);
    }

    public void stop() {
        active.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * Initiates a new {@link ContractNegotiation}. The ContractNegotiation is created and
     * persisted, which moves it to state REQUESTING.
     *
     * @param contractOffer Container object containing all relevant request parameters.
     * @return a {@link NegotiationResponse}: OK
     */
    @Override
    public NegotiationResponse initiate(ContractOfferRequest contractOffer) {
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .protocol(contractOffer.getProtocol())
                .counterPartyId(contractOffer.getConnectorId())
                .counterPartyAddress(contractOffer.getConnectorAddress())
                .state(0)
                .stateCount(0)
                .stateTimestamp(Instant.now().toEpochMilli())
                .build();

        negotiation.addContractOffer(contractOffer.getContractOffer());
        negotiationStore.save(negotiation);

        monitor.debug(String.format("[Consumer] ContractNegotiation initiated. %s is now in state %s.",
                negotiation.getId(), ContractNegotiationStates.from(negotiation.getState())));
        return new NegotiationResponse(OK, negotiation);
    }

    /**
     * Tells this manager that a new contract offer has been received for a
     * {@link ContractNegotiation}. Validates the offer against the last contract offer for that
     * ContractNegotiation and transitions the ContractNegotiation to CONSUMER_APPROVING,
     * CONSUMER_OFFERING or DECLINING.
     *
     * @param token Claim token of the consumer that send the contract request.
     * @param negotiationId Id of the ContractNegotiation.
     * @param contractOffer The contract offer.
     * @param hash A hash of all previous contract offers.
     * @return a {@link NegotiationResponse}: FATAL_ERROR, if no match found for Id or no last
     *         offer found for negotiation; OK otherwise
     */
    @Override
    public NegotiationResponse offerReceived(ClaimToken token, String negotiationId, ContractOffer contractOffer, String hash) {
        var negotiation = negotiationStore.find(negotiationId);
        if (negotiation == null) {
            return new NegotiationResponse(FATAL_ERROR);
        }

        var latestOffer = negotiation.getLastContractOffer();
        try {
            Objects.requireNonNull(latestOffer, "latestOffer");
        } catch (NullPointerException e) {
            monitor.severe("[Consumer] No offer found for validation. Process id: " + negotiation.getId());
            return new NegotiationResponse(FATAL_ERROR, negotiation);
        }

        OfferValidationResult result = validationService.validate(token, contractOffer, latestOffer);
        negotiation.addContractOffer(contractOffer); // TODO persist unchecked offer of provider?
        if (result.invalid()) {
            //if (result.isCounterOfferAvailable()) {
            //    negotiation.addContractOffer(result.getCounterOffer());
            //    monitor.debug("[Consumer] Contract offer received. A counter offer is available.");
            //    negotiation.transitionOffering();
            //} else {
            // If no counter offer available + validation result invalid, decline negotiation.
            monitor.debug("[Consumer] Contract offer received. Will be rejected.");
            negotiation.setErrorDetail("Contract rejected."); //TODO set error detail
            negotiation.transitionDeclining();
            //}
        } else {
            // Offer has been approved.
            monitor.debug("[Consumer] Contract offer received. Will be approved.");
            negotiation.transitionApproving();
        }

        negotiationStore.save(negotiation);
        monitor.debug(String.format("[Consumer] ContractNegotiation %s is now in state %s.",
                negotiation.getId(), ContractNegotiationStates.from(negotiation.getState())));

        return new NegotiationResponse(OK, negotiation);
    }

    /**
     * Tells this manager that a previously sent contract offer has been confirmed by the provider.
     * Validates the contract agreement sent by the provider against the last contract offer and
     * transitions the corresponding {@link ContractNegotiation} to state CONFIRMED or DECLINING.
     *
     * @param token Claim token of the consumer that send the contract request.
     * @param negotiationId Id of the ContractNegotiation.
     * @param agreement Agreement sent by provider.
     * @param hash A hash of all previous contract offers.
     * @return a {@link NegotiationResponse}: FATAL_ERROR, if no match found for Id or no last
     *         offer found for negotiation; OK otherwise
     */
    @Override
    public NegotiationResponse confirmed(ClaimToken token, String negotiationId, ContractAgreement agreement, String hash) {
        var negotiation = negotiationStore.find(negotiationId);
        if (negotiation == null) {
            return new NegotiationResponse(FATAL_ERROR);
        }

        var latestOffer = negotiation.getLastContractOffer();
        try {
            Objects.requireNonNull(latestOffer, "latestOffer");
        } catch (NullPointerException e) {
            monitor.severe("[Consumer] No offer found for validation. Process id: " + negotiation.getId());
            return new NegotiationResponse(FATAL_ERROR, negotiation);
        }

        var result = validationService.validate(token, agreement, latestOffer);
        if (!result) {
            // TODO Add contract offer possibility.
            monitor.debug("[Consumer] Contract agreement received. Validation failed.");
            negotiation.setErrorDetail("Contract rejected."); //TODO set error detail
            negotiation.transitionDeclining();
            negotiationStore.save(negotiation);
            monitor.debug(String.format("[Consumer] ContractNegotiation %s is now in state %s.",
                    negotiation.getId(), ContractNegotiationStates.from(negotiation.getState())));
            return new NegotiationResponse(OK, negotiation);
        }

        // Agreement has been approved.
        negotiation.setContractAgreement(agreement); // TODO persist unchecked agreement of provider?
        monitor.debug("[Consumer] Contract agreement received. Validation successful.");
        negotiation.transitionConfirmed();
        negotiationStore.save(negotiation);
        monitor.debug(String.format("[Consumer] ContractNegotiation %s is now in state %s.",
                negotiation.getId(), ContractNegotiationStates.from(negotiation.getState())));

        return new NegotiationResponse(OK, negotiation);
    }

    /**
     * Tells this manager that a {@link ContractNegotiation} has been declined by the counter-party.
     * Transitions the corresponding ContractNegotiation to state DECLINED.
     *
     * @param token Claim token of the consumer that sent the rejection.
     * @param negotiationId Id of the ContractNegotiation.
     * @return a {@link NegotiationResponse}: OK, if successfully transitioned to declined;
     *         FATAL_ERROR, if no match found for Id.
     */
    @Override
    public NegotiationResponse declined(ClaimToken token, String negotiationId) {
        var negotiation = negotiationStore.find(negotiationId);
        if (negotiation == null) {
            return new NegotiationResponse(FATAL_ERROR);
        }

        monitor.debug("[Consumer] Contract rejection received. Abort negotiation process");
        negotiation.transitionDeclined();
        negotiationStore.save(negotiation);
        monitor.debug(String.format("[Consumer] ContractNegotiation %s is now in state %s.",
                negotiation.getId(), ContractNegotiationStates.from(negotiation.getState())));
        return new NegotiationResponse(OK, negotiation);
    }

    /**
     * Builds and sends a {@link ContractOfferRequest} for a given {@link ContractNegotiation} and
     * {@link ContractOffer}.
     *
     * @param offer The contract offer.
     * @param process The contract negotiation.
     * @return The response to the sent message.
     */
    private CompletableFuture<Object> sendOffer(ContractOffer offer, ContractNegotiation process, ContractOfferRequest.Type type) {
        var request = ContractOfferRequest.Builder.newInstance()
                .contractOffer(offer)
                .connectorAddress(process.getCounterPartyAddress())
                .protocol(process.getProtocol())
                .connectorId(process.getCounterPartyId())
                .correlationId(process.getId())
                .type(type)
                .build();

        // TODO protocol-independent response type?
        return dispatcherRegistry.send(Object.class, request, process::getId);
    }

    /**
     * Processes {@link ContractNegotiation}s in state REQUESTING. Tries to send the current
     * offer to the respective provider. If this succeeds, the ContractNegotiation is transitioned
     * to state REQUESTED. Else, it is transitioned to REQUESTING for a retry.
     *
     * @return the number of processed ContractNegotiations.
     */
    private int sendContractOffers() {
        var processes = negotiationStore.nextForState(ContractNegotiationStates.REQUESTING.code(), batchSize);

        for (ContractNegotiation process : processes) {
            var offer = process.getLastContractOffer();
            var response = sendOffer(offer, process, ContractOfferRequest.Type.INITIAL);
            if (response.isCompletedExceptionally()) {
                process.transitionRequesting();
                monitor.debug(format("[Consumer] Failed to send contract offer with id %s. ContractNegotiation %s stays in state %s.",
                        offer.getId(), process.getId(), ContractNegotiationStates.from(process.getState())));
                continue;
            }

            process.transitionRequested();
            monitor.debug(String.format("[Consumer] ContractNegotiation %s is now in state %s.",
                    process.getId(), ContractNegotiationStates.from(process.getState())));
            negotiationStore.save(process);
        }

        return processes.size();
    }

    /**
     * Processes {@link ContractNegotiation}s in state CONSUMER_OFFERING. Tries to send the current
     * offer to the respective provider. If this succeeds, the ContractNegotiation is transitioned
     * to state CONSUMER_OFFERED. Else, it is transitioned to CONSUMER_OFFERING for a retry.
     *
     * @return the number of processed ContractNegotiations.
     */
    private int sendCounterOffers() {
        var processes = negotiationStore.nextForState(ContractNegotiationStates.CONSUMER_OFFERING.code(), batchSize);

        for (ContractNegotiation process : processes) {
            var offer = process.getLastContractOffer();
            var response = sendOffer(offer, process, ContractOfferRequest.Type.COUNTER_OFFER);
            if (response.isCompletedExceptionally()) {
                process.transitionOffering();
                monitor.debug(format("[Consumer] Failed to send contract offer with id %s. ContractNegotiation %s stays in state %s.",
                        offer.getId(), process.getId(), ContractNegotiationStates.from(process.getState())));
                continue;
            }

            process.transitionOffered();
            monitor.debug(String.format("[Consumer] ContractNegotiation %s is now in state %s.",
                    process.getId(), ContractNegotiationStates.from(process.getState())));
            negotiationStore.save(process);
        }

        return processes.size();
    }

    /**
     * Processes {@link ContractNegotiation}s in state CONSUMER_APPROVING. Tries to send a dummy
     * contract agreement to the respective provider in order to approve the last offer sent by the
     * provider. If this succeeds, the ContractNegotiation is transitioned to state
     * CONSUMER_APPROVED. Else, it is transitioned to CONSUMER_APPROVING for a retry.
     *
     * @return the number of processed ContractNegotiations.
     */
    private int approveContractOffers() {
        var processes = negotiationStore.nextForState(ContractNegotiationStates.CONSUMER_APPROVING.code(), batchSize);

        for (ContractNegotiation process : processes) {
            //TODO this is a dummy agreement used to approve the provider's offer, real agreement will be created and sent by provider
            var lastOffer = process.getLastContractOffer();
            var agreement = ContractAgreement.Builder.newInstance()
                    .id(UUID.randomUUID().toString())
                    .contractStartDate(lastOffer.getContractStart())
                    .contractEndDate(lastOffer.getContractEnd())
                    .contractSigningDate(ZonedDateTime.now())
                    .providerAgentId(lastOffer.getProvider())
                    .consumerAgentId(lastOffer.getConsumer())
                    .policy(lastOffer.getPolicy())
                    .asset(lastOffer.getAsset())
                    .build();

            var request = ContractAgreementRequest.Builder.newInstance()
                    .protocol(process.getProtocol())
                    .connectorId(process.getCounterPartyId())
                    .connectorAddress(process.getCounterPartyAddress())
                    .contractAgreement(agreement)
                    .correlationId(process.getId())
                    .build();

            // TODO protocol-independent response type?
            var response = dispatcherRegistry.send(Object.class, request, process::getId);
            if (response.isCompletedExceptionally()) {
                process.transitionApproving();
                monitor.debug(format("[Consumer] Failed to send contract agreement with id %s. ContractNegotiation %s stays in state %s.",
                        agreement.getId(), process.getId(), ContractNegotiationStates.from(process.getState())));
                continue;
            }

            process.transitionApproved();
            monitor.debug(String.format("[Consumer] ContractNegotiation %s is now in state %s.",
                    process.getId(), ContractNegotiationStates.from(process.getState())));
            negotiationStore.save(process);
        }

        return processes.size();
    }

    /**
     * Processes {@link ContractNegotiation}s in state DECLINING. Tries to send a contract rejection
     * to the respective provider. If this succeeds, the ContractNegotiation is transitioned
     * to state DECLINED. Else, it is transitioned to DECLINING for a retry.
     *
     * @return the number of processed ContractNegotiations.
     */
    private int declineContractOffers() {
        var processes = negotiationStore.nextForState(ContractNegotiationStates.DECLINING.code(), batchSize);

        for (ContractNegotiation process : processes) {
            var offer = process.getLastContractOffer();

            var rejection = ContractRejection.Builder.newInstance()
                    .protocol(process.getProtocol())
                    .connectorId(process.getCounterPartyId())
                    .connectorAddress(process.getCounterPartyAddress())
                    .correlationId(process.getId())
                    .rejectionReason(process.getErrorDetail())
                    .build();

            // TODO protocol-independent response type?
            var response = dispatcherRegistry.send(Object.class, rejection, process::getId);
            if (response.isCompletedExceptionally()) {
                process.transitionDeclining();
                negotiationStore.save(process);
                monitor.debug(format("[Consumer] Failed to send contract rejection. ContractNegotiation %s stays in state %s.",
                        process.getId(), ContractNegotiationStates.from(process.getState())));
                continue;
            }

            process.transitionDeclined();
            monitor.debug(String.format("[Consumer] ContractNegotiation %s is now in state %s.",
                    process.getId(), ContractNegotiationStates.from(process.getState())));
            negotiationStore.save(process);
        }

        return processes.size();
    }

    /**
     * Continuously checks all unfinished {@link ContractNegotiation}s and performs actions based on
     * their states.
     */
    private void run() {
        while (active.get()) {
            try {
                int requesting = sendContractOffers();
                int offering = sendCounterOffers();
                int approving = approveContractOffers();
                int declining = declineContractOffers();

                if (requesting + offering + approving + declining == 0) {
                    Thread.sleep(waitStrategy.waitForMillis());
                }
                waitStrategy.success();
            } catch (Error e) {
                throw e; // let the thread die and don't reschedule as the error is unrecoverable
            } catch (InterruptedException e) {
                Thread.interrupted();
                active.set(false);
                break;
            } catch (Throwable e) {
                monitor.severe("Error caught in consumer contract negotiation manager", e);
                try {
                    Thread.sleep(waitStrategy.retryInMillis());
                } catch (InterruptedException e2) {
                    Thread.interrupted();
                    active.set(false);
                    break;
                }
            }
        }
    }

    /**
     * Builder for ConsumerContractNegotiationManagerImpl.
     */
    public static class Builder {
        private final ConsumerContractNegotiationManagerImpl manager;

        private Builder() {
            manager = new ConsumerContractNegotiationManagerImpl();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder validationService(ContractValidationService validationService) {
            manager.validationService = validationService;
            return this;
        }

        public Builder monitor(Monitor monitor) {
            manager.monitor = monitor;
            return this;
        }

        public Builder batchSize(int batchSize) {
            manager.batchSize = batchSize;
            return this;
        }

        public Builder waitStrategy(NegotiationWaitStrategy waitStrategy) {
            manager.waitStrategy = waitStrategy;
            return this;
        }

        public Builder dispatcherRegistry(RemoteMessageDispatcherRegistry dispatcherRegistry) {
            manager.dispatcherRegistry = dispatcherRegistry;
            return this;
        }

        public ConsumerContractNegotiationManagerImpl build() {
            Objects.requireNonNull(manager.validationService, "contractValidationService");
            Objects.requireNonNull(manager.monitor, "monitor");
            Objects.requireNonNull(manager.dispatcherRegistry, "dispatcherRegistry");
            return manager;
        }
    }
}
