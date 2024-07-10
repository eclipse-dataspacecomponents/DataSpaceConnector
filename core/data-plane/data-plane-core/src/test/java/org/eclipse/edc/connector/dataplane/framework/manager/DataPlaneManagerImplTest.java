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
 *
 */

package org.eclipse.edc.connector.dataplane.framework.manager;

import org.eclipse.edc.connector.controlplane.api.client.spi.transferprocess.TransferProcessApiClient;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.connector.dataplane.spi.pipeline.TransferService;
import org.eclipse.edc.connector.dataplane.spi.registry.TransferServiceRegistry;
import org.eclipse.edc.connector.dataplane.spi.store.DataPlaneStore;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.response.ResponseFailure;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowResponseMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.dataplane.spi.DataFlow.TERMINATION_REASON;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.COMPLETED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.FAILED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.NOTIFIED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.RECEIVED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.STARTED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.SUSPENDED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.TERMINATED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.persistence.StateEntityStore.hasState;
import static org.eclipse.edc.spi.response.ResponseStatus.ERROR_RETRY;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.GENERAL_ERROR;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


class DataPlaneManagerImplTest {

    private final TransferService transferService = mock();
    private final TransferProcessApiClient transferProcessApiClient = mock();
    private final DataPlaneStore store = mock();
    private final DataFlowStartMessage request = createRequest();
    private final TransferServiceRegistry registry = mock();
    private final DataPlaneAuthorizationService authorizationService = mock();
    private DataPlaneManagerImpl manager;

    @BeforeEach
    public void setUp() {
        when(registry.resolveTransferService(request)).thenReturn(transferService);
        manager = DataPlaneManagerImpl.Builder.newInstance()
                .executorInstrumentation(ExecutorInstrumentation.noop())
                .transferServiceRegistry(registry)
                .store(store)
                .transferProcessClient(transferProcessApiClient)
                .authorizationService(authorizationService)
                .monitor(mock())
                .build();
    }

    @Test
    void initiateDataFlow() {
        var request = DataFlowStartMessage.Builder.newInstance()
                .id("1")
                .processId("1")
                .sourceDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .callbackAddress(URI.create("http://any"))
                .properties(Map.of("key", "value"))
                .flowType(FlowType.PUSH)
                .build();

        manager.start(request);

        var captor = ArgumentCaptor.forClass(DataFlow.class);
        verify(store).save(captor.capture());
        var dataFlow = captor.getValue();
        assertThat(dataFlow.getId()).isEqualTo(request.getProcessId());
        assertThat(dataFlow.getSource()).isSameAs(request.getSourceDataAddress());
        assertThat(dataFlow.getDestination()).isSameAs(request.getDestinationDataAddress());
        assertThat(dataFlow.getCallbackAddress()).isEqualTo(URI.create("http://any"));
        assertThat(dataFlow.getProperties()).isEqualTo(request.getProperties());
        assertThat(dataFlow.getState()).isEqualTo(RECEIVED.code());

        verifyNoInteractions(authorizationService);
    }

    @Test
    void initiatePullDataFlow() {

        var dataAddress = DataAddress.Builder.newInstance().type("type").build();
        var request = DataFlowStartMessage.Builder.newInstance()
                .id("1")
                .processId("1")
                .sourceDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .callbackAddress(URI.create("http://any"))
                .properties(Map.of("key", "value"))
                .flowType(FlowType.PULL)
                .build();

        when(authorizationService.createEndpointDataReference(request)).thenReturn(Result.success(dataAddress));

        var result = manager.start(request);

        assertThat(result).isSucceeded().extracting(DataFlowResponseMessage::getDataAddress).isEqualTo(dataAddress);

        var captor = ArgumentCaptor.forClass(DataFlow.class);
        verify(store).save(captor.capture());
        var dataFlow = captor.getValue();
        assertThat(dataFlow.getId()).isEqualTo(request.getProcessId());
        assertThat(dataFlow.getSource()).isSameAs(request.getSourceDataAddress());
        assertThat(dataFlow.getDestination()).isSameAs(request.getDestinationDataAddress());
        assertThat(dataFlow.getCallbackAddress()).isEqualTo(URI.create("http://any"));
        assertThat(dataFlow.getProperties()).isEqualTo(request.getProperties());
        assertThat(dataFlow.getState()).isEqualTo(STARTED.code());
    }

    @Test
    void initiatePullDataFlow_shouldFail_whenEdrCreationFails() {
        var request = DataFlowStartMessage.Builder.newInstance()
                .id("1")
                .processId("1")
                .sourceDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .callbackAddress(URI.create("http://any"))
                .properties(Map.of("key", "value"))
                .flowType(FlowType.PULL)
                .build();

        when(authorizationService.createEndpointDataReference(request)).thenReturn(Result.failure("failure"));

        var result = manager.start(request);

        assertThat(result).isFailed().detail().contains("failure");

        verifyNoInteractions(store);
    }

    @Test
    void terminate_shouldTerminateDataFlow() {
        var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
        when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
        when(registry.resolveTransferService(any())).thenReturn(transferService);
        when(transferService.terminate(any())).thenReturn(StreamResult.success());

        var result = manager.terminate("dataFlowId");

        assertThat(result).isSucceeded();
        verify(store).save(argThat(d -> d.getState() == TERMINATED.code()));
        verify(transferService).terminate(dataFlow);
    }

    @Test
    void terminate_shouldTerminatePullDataFlow() {
        var dataFlow = dataFlowBuilder().state(RECEIVED.code()).id("dataFlowId").transferType(new TransferType("DestinationType", FlowType.PULL)).build();
        when(store.findByIdAndLease(dataFlow.getId())).thenReturn(StoreResult.success(dataFlow));
        when(authorizationService.revokeEndpointDataReference(dataFlow.getId(), null)).thenReturn(Result.success());

        var result = manager.terminate(dataFlow.getId(), null);

        assertThat(result).isSucceeded();
        verify(store).save(argThat(d -> d.getState() == TERMINATED.code()));
        verify(authorizationService).revokeEndpointDataReference(dataFlow.getId(), null);
    }

    @Test
    void terminate_shouldFailToTerminatePullDataFlow_whenRevocationFails() {
        var dataFlow = dataFlowBuilder().state(RECEIVED.code()).id("dataFlowId").transferType(new TransferType("DestinationType", FlowType.PULL)).build();
        when(store.findByIdAndLease(dataFlow.getId())).thenReturn(StoreResult.success(dataFlow));
        when(authorizationService.revokeEndpointDataReference(dataFlow.getId(), null)).thenReturn(Result.failure("failure"));

        var result = manager.terminate(dataFlow.getId(), null);

        assertThat(result).isFailed();
        verify(store, never()).save(any());
        verify(authorizationService).revokeEndpointDataReference(dataFlow.getId(), null);
    }

    @Test
    void terminate_shouldTerminateDataFlow_withReason() {
        var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
        when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
        when(registry.resolveTransferService(any())).thenReturn(transferService);
        when(transferService.terminate(any())).thenReturn(StreamResult.success());

        var result = manager.terminate("dataFlowId", "test-reason");

        assertThat(result).isSucceeded();
        verify(store).save(argThat(d -> d.getState() == TERMINATED.code() && d.getProperties().get(TERMINATION_REASON).equals("test-reason")));
        verify(transferService).terminate(dataFlow);
    }

    @Test
    void terminate_shouldReturnFatalError_whenDataFlowDoesNotExist() {
        when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.notFound("not found"));

        var result = manager.terminate("dataFlowId");

        assertThat(result).isFailed().extracting(ResponseFailure::status).isEqualTo(FATAL_ERROR);
        verify(store, never()).save(any());
        verifyNoInteractions(transferService);
    }

    @Test
    void terminate_shouldReturnRetryError_whenEntityCannotBeLeased() {
        when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.alreadyLeased("already leased"));

        var result = manager.terminate("dataFlowId");

        assertThat(result).isFailed().extracting(ResponseFailure::status).isEqualTo(ERROR_RETRY);
        verify(store, never()).save(any());
        verifyNoInteractions(transferService);
    }

    @Test
    void terminate_shouldReturnFatalError_whenTransferServiceNotFound() {
        var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
        when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
        when(registry.resolveTransferService(any())).thenReturn(null);

        var result = manager.terminate("dataFlowId");

        assertThat(result).isFailed().extracting(ResponseFailure::status).isEqualTo(FATAL_ERROR);
        verify(store, never()).save(any());
        verifyNoInteractions(transferService);
    }

    @Test
    void terminate_shouldReturnFatalError_whenDataFlowCannotBeTerminated() {
        var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
        when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
        when(registry.resolveTransferService(any())).thenReturn(transferService);
        when(transferService.terminate(any())).thenReturn(StreamResult.error("cannot be terminated"));

        var result = manager.terminate("dataFlowId");

        assertThat(result).isFailed().extracting(ResponseFailure::status).isEqualTo(FATAL_ERROR);
        verify(store, never()).save(any());
    }

    @Test
    void terminate_shouldStillTerminate_whenDataFlowHasNoSource() {
        var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
        when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
        when(registry.resolveTransferService(any())).thenReturn(transferService);
        when(transferService.terminate(any())).thenReturn(StreamResult.notFound());

        var result = manager.terminate("dataFlowId", "test-reason");

        assertThat(result).isSucceeded();
        verify(store).save(argThat(f -> f.getProperties().containsKey(TERMINATION_REASON)));
    }

    @Nested
    class Received {
        @Test
        void shouldStartTransferTransitionAndTransitionToStarted() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.nextNotLeased(anyInt(), stateIs(RECEIVED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
            when(store.findById(any())).thenReturn(dataFlow);
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.canHandle(any())).thenReturn(true);
            when(transferService.transfer(any())).thenReturn(new CompletableFuture<>());

            manager.start();

            await().untilAsserted(() -> {
                verify(transferService).transfer(isA(DataFlowStartMessage.class));
                verify(store).save(argThat(it -> it.getState() == STARTED.code()));
            });
        }

        @Test
        void shouldStarTransitionToCompleted_whenTransferSucceeds() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.nextNotLeased(anyInt(), stateIs(RECEIVED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
            when(store.findById(any())).thenReturn(dataFlow);
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.canHandle(any())).thenReturn(true);
            when(transferService.transfer(any())).thenReturn(completedFuture(StreamResult.success()));

            manager.start();

            await().untilAsserted(() -> {
                verify(transferService).transfer(isA(DataFlowStartMessage.class));
                verify(store, atLeastOnce()).save(argThat(it -> it.getState() == COMPLETED.code()));
            });
        }

        @Test
        void shouldStartTransferAndNotTransitionToCompleted_whenTransferSucceedsBecauseItsTermination() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            var terminatedDataFlow = dataFlowBuilder().state(TERMINATED.code()).build();
            when(store.nextNotLeased(anyInt(), stateIs(RECEIVED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
            when(store.findById(any())).thenReturn(terminatedDataFlow);
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.canHandle(any())).thenReturn(true);
            when(transferService.transfer(any())).thenReturn(completedFuture(StreamResult.success()));

            manager.start();

            await().untilAsserted(() -> {
                verify(transferService).transfer(isA(DataFlowStartMessage.class));
                verify(store, never()).save(argThat(it -> it.getState() == COMPLETED.code()));
            });
        }

        @Test
        void shouldNotChangeState_whenTransferGetsSuspended() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            var terminatedDataFlow = dataFlowBuilder().state(SUSPENDED.code()).build();
            when(store.nextNotLeased(anyInt(), stateIs(RECEIVED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
            when(store.findById(any())).thenReturn(terminatedDataFlow);
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.canHandle(any())).thenReturn(true);
            when(transferService.transfer(any())).thenReturn(completedFuture(StreamResult.success()));

            manager.start();

            await().untilAsserted(() -> {
                verify(transferService).transfer(isA(DataFlowStartMessage.class));
                verify(store, never()).save(argThat(it -> it.getState() == COMPLETED.code()));
            });
        }

        @Test
        void shouldStartTransferAndTransitionToFailed_whenTransferFails() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.nextNotLeased(anyInt(), stateIs(RECEIVED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
            when(store.findById(any())).thenReturn(dataFlow);
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.canHandle(any())).thenReturn(true);
            when(transferService.transfer(any())).thenReturn(completedFuture(StreamResult.error("an error")));

            manager.start();

            await().untilAsserted(() -> {
                verify(transferService).transfer(isA(DataFlowStartMessage.class));
                verify(store, atLeastOnce()).save(argThat(it -> it.getState() == FAILED.code() && it.getErrorDetail().equals(GENERAL_ERROR + ": an error")));
            });
        }

        @Test
        void shouldStartTransferAndTransitionToReceivedForRetrying_whenTransferFutureIsFailed() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.nextNotLeased(anyInt(), stateIs(RECEIVED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
            when(store.findById(any())).thenReturn(dataFlow);
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.canHandle(any())).thenReturn(true);
            when(transferService.transfer(any())).thenReturn(failedFuture(new RuntimeException("an error")));

            manager.start();

            await().untilAsserted(() -> {
                verify(transferService).transfer(isA(DataFlowStartMessage.class));
                verify(store, atLeastOnce()).save(argThat(it -> it.getState() == RECEIVED.code()));
            });
        }

        @Test
        void shouldTransitToFailedIfNoTransferServiceCanHandleStarted() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.nextNotLeased(anyInt(), stateIs(RECEIVED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
            when(store.findById(any())).thenReturn(dataFlow);
            when(registry.resolveTransferService(any())).thenReturn(null);

            manager.start();

            await().untilAsserted(() -> {
                verifyNoInteractions(transferService);
                verify(store, atLeastOnce()).save(argThat(it -> it.getState() == FAILED.code()));
            });
        }
    }

    @Test
    void completed_shouldNotifyResultToControlPlane() {
        var dataFlow = dataFlowBuilder().state(COMPLETED.code()).build();
        when(store.nextNotLeased(anyInt(), stateIs(COMPLETED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
        when(transferProcessApiClient.completed(any())).thenReturn(Result.success());

        manager.start();

        await().untilAsserted(() -> {
            verify(transferProcessApiClient).completed(any());
            verify(store).save(argThat(it -> it.getState() == NOTIFIED.code()));
        });
    }

    @Test
    void completed_shouldNotTransitionToNotified() {
        var dataFlow = dataFlowBuilder().state(COMPLETED.code()).build();
        when(store.nextNotLeased(anyInt(), stateIs(COMPLETED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
        when(transferProcessApiClient.completed(any())).thenReturn(Result.failure(""));

        manager.start();

        await().untilAsserted(() -> {
            verify(transferProcessApiClient).completed(any());
            verify(store).save(argThat(it -> it.getState() == COMPLETED.code()));
        });
    }

    @Test
    void failed_shouldNotifyResultToControlPlane() {
        var dataFlow = dataFlowBuilder().state(FAILED.code()).errorDetail("an error").build();
        when(store.nextNotLeased(anyInt(), stateIs(FAILED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
        when(store.findById(any())).thenReturn(dataFlow);

        when(transferProcessApiClient.failed(any(), eq("an error"))).thenReturn(Result.success());

        manager.start();

        await().untilAsserted(() -> {
            verify(transferProcessApiClient).failed(any(), eq("an error"));
            verify(store).save(argThat(it -> it.getState() == NOTIFIED.code()));
        });
    }

    @Test
    void failed_shouldNotTransitionToNotified() {
        var dataFlow = dataFlowBuilder().state(FAILED.code()).errorDetail("an error").build();
        when(store.nextNotLeased(anyInt(), stateIs(FAILED.code()))).thenReturn(List.of(dataFlow)).thenReturn(emptyList());
        when(store.findById(any())).thenReturn(dataFlow);

        when(transferProcessApiClient.failed(any(), eq("an error"))).thenReturn(Result.failure("an error"));

        manager.start();

        await().untilAsserted(() -> {
            verify(transferProcessApiClient).failed(any(), eq("an error"));
            verify(store).save(argThat(it -> it.getState() == FAILED.code()));
        });
    }

    private DataFlow.Builder dataFlowBuilder() {
        return DataFlow.Builder.newInstance()
                .source(DataAddress.Builder.newInstance().type("source").build())
                .destination(DataAddress.Builder.newInstance().type("destination").build())
                .callbackAddress(URI.create("http://any"))
                .transferType(new TransferType("DestinationType", FlowType.PUSH))
                .properties(Map.of("key", "value"));
    }

    private Criterion[] stateIs(int state) {
        return aryEq(new Criterion[]{ hasState(state) });
    }

    private DataFlowStartMessage createRequest() {
        return DataFlowStartMessage.Builder.newInstance()
                .id("1")
                .processId("1")
                .sourceDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .build();
    }

    @Nested
    class Suspend {

        @Test
        void shouldSuspendDataFlow() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.terminate(any())).thenReturn(StreamResult.success());

            var result = manager.suspend("dataFlowId");

            assertThat(result).isSucceeded();
            verify(store).save(argThat(d -> d.getState() == SUSPENDED.code()));
            verify(transferService).terminate(dataFlow);
        }

        @Test
        void shouldSuspendDataFlow_withReason() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.terminate(any())).thenReturn(StreamResult.success());

            var result = manager.suspend("dataFlowId");

            assertThat(result).isSucceeded();
            verify(store).save(argThat(d -> d.getState() == SUSPENDED.code()));
            verify(transferService).terminate(dataFlow);
        }

        @Test
        void shouldReturnFatalError_whenDataFlowDoesNotExist() {
            when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.notFound("not found"));

            var result = manager.suspend("dataFlowId");

            assertThat(result).isFailed().extracting(ResponseFailure::status).isEqualTo(FATAL_ERROR);
            verify(store, never()).save(any());
            verifyNoInteractions(transferService);
        }

        @Test
        void shouldReturnRetryError_whenEntityCannotBeLeased() {
            when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.alreadyLeased("already leased"));

            var result = manager.suspend("dataFlowId");

            assertThat(result).isFailed().extracting(ResponseFailure::status).isEqualTo(ERROR_RETRY);
            verify(store, never()).save(any());
            verifyNoInteractions(transferService);
        }

        @Test
        void shouldReturnFatalError_whenTransferServiceNotFound() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
            when(registry.resolveTransferService(any())).thenReturn(null);

            var result = manager.suspend("dataFlowId");

            assertThat(result).isFailed().extracting(ResponseFailure::status).isEqualTo(FATAL_ERROR);
            verify(store, never()).save(any());
            verifyNoInteractions(transferService);
        }

        @Test
        void shouldReturnFatalError_whenDataFlowCannotBeSuspended() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.terminate(any())).thenReturn(StreamResult.error("cannot be suspended"));

            var result = manager.suspend("dataFlowId");

            assertThat(result).isFailed().extracting(ResponseFailure::status).isEqualTo(FATAL_ERROR);
            verify(store, never()).save(any());
        }

        @Test
        void shouldStillSuspend_whenDataFlowHasNoSource() {
            var dataFlow = dataFlowBuilder().state(RECEIVED.code()).build();
            when(store.findByIdAndLease("dataFlowId")).thenReturn(StoreResult.success(dataFlow));
            when(registry.resolveTransferService(any())).thenReturn(transferService);
            when(transferService.terminate(any())).thenReturn(StreamResult.notFound());

            var result = manager.suspend("dataFlowId");

            assertThat(result).isSucceeded();
            verify(store).save(argThat(f -> f.getState() == SUSPENDED.code()));
        }
    }

}
