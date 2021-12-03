/*
 *  Copyright (c) 2021 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */
package org.eclipse.dataspaceconnector.contract.negotiation;

import org.easymock.EasyMock;
import org.eclipse.dataspaceconnector.negotiation.store.memory.InMemoryContractNegotiationStore;
import org.eclipse.dataspaceconnector.policy.model.Action;
import org.eclipse.dataspaceconnector.policy.model.Duty;
import org.eclipse.dataspaceconnector.policy.model.Policy;
import org.eclipse.dataspaceconnector.policy.model.PolicyType;
import org.eclipse.dataspaceconnector.spi.contract.negotiation.response.NegotiationResponse;
import org.eclipse.dataspaceconnector.spi.contract.validation.ContractValidationService;
import org.eclipse.dataspaceconnector.spi.iam.ClaimToken;
import org.eclipse.dataspaceconnector.spi.message.MessageContext;
import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcher;
import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.agreement.ContractAgreementRequest;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation.ContractNegotiationStates;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation.ContractOfferRequest;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation.ContractRejection;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractOffer;
import org.eclipse.dataspaceconnector.spi.types.domain.message.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Setup for the contract negotiation integration test.
 */
public abstract class AbstractContractNegotiationIntegrationTest {

    protected ProviderContractNegotiationManagerImpl providerManager;
    protected ConsumerContractNegotiationManagerImpl consumerManager;

    protected InMemoryContractNegotiationStore providerStore;
    protected InMemoryContractNegotiationStore consumerStore;

    protected ContractValidationService validationService;

    protected String consumerNegotiationId;

    protected ClaimToken token;

    protected ExecutorService executorService;
    protected CountDownLatch countDownLatch;

    /**
     * Prepares the test setup
     */
    @BeforeEach
    void setUp() {
        // Create contract validation service mock, method mocking has to be done in test methods
        validationService = EasyMock.createNiceMock(ContractValidationService.class);

        // Create a monitor that logs to the console
        Monitor monitor = new FakeConsoleMonitor();

        // Create the provider contract negotiation store and manager
        providerStore = new InMemoryContractNegotiationStore();
        providerManager = ProviderContractNegotiationManagerImpl.Builder.newInstance()
                .dispatcherRegistry(new FakeProviderDispatcherRegistry())
                .monitor(monitor)
                .validationService(validationService)
                .waitStrategy(() -> 1000)
                .build();

        // Create the consumer contract negotiation store and manager
        consumerStore = new InMemoryContractNegotiationStore();
        consumerManager = ConsumerContractNegotiationManagerImpl.Builder.newInstance()
                .dispatcherRegistry(new FakeConsumerDispatcherRegistry())
                .monitor(monitor)
                .validationService(validationService)
                .waitStrategy(() -> 1000)
                .build();

        executorService = Executors.newFixedThreadPool(1);
        countDownLatch = new CountDownLatch(1);
    }

    /**
     * Implementation of the RemoteMessageDispatcherRegistry for the provider that delegates
     * the requests to the consumer negotiation manager directly.
     */
    protected class FakeProviderDispatcherRegistry implements RemoteMessageDispatcherRegistry {

        @Override
        public void register(RemoteMessageDispatcher dispatcher) {
            // Not needed for test
        }

        @Override
        public <T> CompletableFuture<T> send(Class<T> responseType, RemoteMessage message, MessageContext context) {
            return (CompletableFuture<T>) send(message);
        }

        public CompletableFuture<Object> send(RemoteMessage message) {
            NegotiationResponse result;
            if (message instanceof ContractOfferRequest) {
                var request = (ContractOfferRequest) message;
                result = consumerManager.offerReceived(token, request.getCorrelationId(), request.getContractOffer(), "hash");
            } else if (message instanceof ContractAgreementRequest) {
                var request = (ContractAgreementRequest) message;
                result = consumerManager.confirmed(token, request.getCorrelationId(), request.getContractAgreement(), "hash");
            } else if (message instanceof ContractRejection) {
                var request = (ContractRejection) message;
                result = consumerManager.declined(token, request.getCorrelationId());
            } else {
                throw new IllegalArgumentException("Unknown message type.");
            }

            CompletableFuture<Object> future = new CompletableFuture<>();
            if (NegotiationResponse.Status.OK.equals(result.getStatus())) {
                future.complete((Object) "Success!");
            } else {
                future.completeExceptionally(new Exception("Negotiation failed."));
            }

            return future;
        }
    }

    /**
     * Implementation of the RemoteMessageDispatcherRegistry for the consumer that delegates
     * the requests to the provider negotiation manager directly.
     */
    protected class FakeConsumerDispatcherRegistry implements RemoteMessageDispatcherRegistry {

        @Override
        public void register(RemoteMessageDispatcher dispatcher) {
            // Not needed for test
        }

        @Override
        public <T> CompletableFuture<T> send(Class<T> responseType, RemoteMessage message, MessageContext context) {
            return (CompletableFuture<T>) send(message);
        }

        public CompletableFuture<Object> send(RemoteMessage message) {
            NegotiationResponse result;
            if (message instanceof ContractOfferRequest) {
                var request = (ContractOfferRequest) message;
                consumerNegotiationId = request.getCorrelationId();
                result = providerManager.offerReceived(token, request.getCorrelationId(), request.getContractOffer(), "hash");
                if (NegotiationResponse.Status.FATAL_ERROR.equals(result.getStatus())) {
                    result = providerManager.requested(token, request);
                }
            } else if (message instanceof ContractAgreementRequest) {
                var request = (ContractAgreementRequest) message;
                result = providerManager.consumerApproved(token, request.getCorrelationId(), request.getContractAgreement(), "hash");
            } else if (message instanceof ContractRejection) {
                var request = (ContractRejection) message;
                result = providerManager.declined(token, request.getCorrelationId());
            } else {
                throw new IllegalArgumentException("Unknown message type.");
            }

            CompletableFuture<Object> future = new CompletableFuture<>();
            if (NegotiationResponse.Status.OK.equals(result.getStatus())) {
                future.complete((Object) "Success!");
            } else {
                future.completeExceptionally(new Exception("Negotiation failed."));
            }

            return future;
        }
    }

    /**
     * Monitor implementation that prints to the console.
     */
    protected class FakeConsoleMonitor implements Monitor {
        @Override
        public void debug(String message, Throwable... errors) {
            System.out.println("\u001B[34mDEBUG\u001B[0m - " + message);
            if (errors != null && errors.length > 0) {
                for (Throwable error : errors) {
                    error.printStackTrace();
                }
            }
        }

        @Override
        public void info(String message, Throwable... errors) {
            System.out.println("\u001B[32mINFO\u001B[0m - " + message);
            if (errors != null && errors.length > 0) {
                for (Throwable error : errors) {
                    error.printStackTrace();
                }
            }
        }

        @Override
        public void warning(String message, Throwable... errors) {
            System.out.println("\u001B[33mWARNING\u001B[0m - " + message);
            if (errors != null && errors.length > 0) {
                for (Throwable error : errors) {
                    error.printStackTrace();
                }
            }
        }

        @Override
        public void severe(String message, Throwable... errors) {
            System.out.println("\u001B[31mSEVERE\u001B[0m - " + message);
            if (errors != null && errors.length > 0) {
                for (Throwable error : errors) {
                    error.printStackTrace();
                }
            }
        }
    }

    /**
     * Returns a thread that periodically checks the current state of the negotiation on provider
     * and consumer side, until either both negotiations are in the desired end state or the
     * thread is interrupted.
     *
     * @param desiredEndState the desired end state for the negotiations.
     * @return the thread.
     */
    protected Thread getThread(ContractNegotiationStates desiredEndState) {
        return new Thread(() -> {
            var finished = false;
            while(!finished) {
                // If thread has been interrupted, stop execution
                if (Thread.interrupted()) {
                    return;
                }

                // Wait for a second to avoid constant check
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    continue;
                }

                // If null, the initial request from consumer to provider has not happened
                if (consumerNegotiationId == null) {
                    continue;
                }

                // Get negotiations from provider and consumer store
                var consumerNegotiation = consumerStore.find(consumerNegotiationId);
                var providerNegotiation = providerStore.findForCorrelationId(consumerNegotiationId);
                if (providerNegotiation == null || consumerNegotiation == null) {
                    continue;
                }

                // If both negotiations are in desired state, count down latch
                if (desiredEndState.code() == providerNegotiation.getState()
                        && desiredEndState.code() == consumerNegotiation.getState()) {
                    countDownLatch.countDown();
                    finished = true;
                }
            }
        });
    }

    /**
     * Creates the initial contract offer.
     *
     * @return the contract offer.
     */
    protected ContractOffer getContractOffer() {
        return ContractOffer.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .contractStart(ZonedDateTime.now())
                .contractEnd(ZonedDateTime.now().plusMonths(1))
                .provider(URI.create("provider"))
                .consumer(URI.create("consumer"))
                .asset(Asset.Builder.newInstance().build())
                .policy(Policy.Builder.newInstance()
                        .id(UUID.randomUUID().toString())
                        .type(PolicyType.CONTRACT)
                        .assigner("assigner")
                        .assignee("assignee")
                        .duty(Duty.Builder.newInstance()
                                .action(Action.Builder.newInstance()
                                        .type("USE")
                                        .build())
                                .build())
                        .build())
                .build();
    }

    /**
     * Creates the first counter offer.
     *
     * @return the contract offer.
     */
    protected ContractOffer getCounterOffer() {
        return ContractOffer.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .contractStart(ZonedDateTime.now())
                .contractEnd(ZonedDateTime.now().plusMonths(2))
                .provider(URI.create("provider"))
                .consumer(URI.create("consumer"))
                .policy(Policy.Builder.newInstance()
                        .id(UUID.randomUUID().toString())
                        .type(PolicyType.CONTRACT)
                        .assigner("assigner")
                        .assignee("assignee")
                        .duty(Duty.Builder.newInstance()
                                .action(Action.Builder.newInstance()
                                        .type("USE")
                                        .build())
                                .build())
                        .build())
                .build();
    }

    /**
     * Creates the second counter offer.
     *
     * @return the contract offer.
     */
    protected ContractOffer getConsumerCounterOffer() {
        return ContractOffer.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .contractStart(ZonedDateTime.now())
                .contractEnd(ZonedDateTime.now().plusMonths(3))
                .provider(URI.create("provider"))
                .consumer(URI.create("consumer"))
                .policy(Policy.Builder.newInstance()
                        .id(UUID.randomUUID().toString())
                        .type(PolicyType.CONTRACT)
                        .assigner("assigner")
                        .assignee("assignee")
                        .duty(Duty.Builder.newInstance()
                                .action(Action.Builder.newInstance()
                                        .type("USE")
                                        .build())
                                .build())
                        .build())
                .build();
    }

}
