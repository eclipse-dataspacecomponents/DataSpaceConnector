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
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.manager.DataPlaneManager;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamFailure;
import org.eclipse.edc.connector.dataplane.spi.registry.TransferServiceRegistry;
import org.eclipse.edc.connector.dataplane.spi.store.DataPlaneStore;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowResponseMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.statemachine.AbstractStateEntityManager;
import org.eclipse.edc.statemachine.Processor;
import org.eclipse.edc.statemachine.ProcessorImpl;
import org.eclipse.edc.statemachine.StateMachineManager;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.String.format;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.COMPLETED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.FAILED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.RECEIVED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.STARTED;
import static org.eclipse.edc.spi.persistence.StateEntityStore.hasState;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;

/**
 * Default data manager implementation.
 */
public class DataPlaneManagerImpl extends AbstractStateEntityManager<DataFlow, DataPlaneStore> implements DataPlaneManager {

    private DataPlaneAuthorizationService authorizationService;
    private TransferServiceRegistry transferServiceRegistry;
    private TransferProcessApiClient transferProcessClient;

    private DataPlaneManagerImpl() {

    }

    @Override
    public Result<Boolean> validate(DataFlowStartMessage dataRequest) {
        // TODO for now no validation for pull scenario, since the transfer service registry
        //  is not applicable here. Probably validation only on the source part required.
        if (FlowType.PULL.equals(dataRequest.getFlowType())) {
            return Result.success(true);
        } else {
            var transferService = transferServiceRegistry.resolveTransferService(dataRequest);
            return transferService != null ?
                    transferService.validate(dataRequest) :
                    Result.failure(format("Cannot find a transfer Service that can handle %s source and %s destination",
                            dataRequest.getSourceDataAddress().getType(), dataRequest.getDestinationDataAddress().getType()));
        }
    }

    @Override
    public Result<DataFlowResponseMessage> start(DataFlowStartMessage startMessage) {
        var dataFlowBuilder = DataFlow.Builder.newInstance()
                .id(startMessage.getProcessId())
                .source(startMessage.getSourceDataAddress())
                .destination(startMessage.getDestinationDataAddress())
                .callbackAddress(startMessage.getCallbackAddress())
                .traceContext(telemetry.getCurrentTraceContext())
                .properties(startMessage.getProperties())
                .transferType(startMessage.getTransferType());

        var response = switch (startMessage.getFlowType()) {
            case PULL -> handlePull(startMessage, dataFlowBuilder);
            case PUSH -> handlePush(dataFlowBuilder);
        };

        return response.onSuccess(m -> update(dataFlowBuilder.build()));
    }

    @Override
    public DataFlowStates getTransferState(String processId) {
        return Optional.ofNullable(store.findById(processId)).map(StatefulEntity::getState)
                .map(DataFlowStates::from).orElse(null);
    }

    @Override
    public StatusResult<Void> suspend(String dataFlowId) {
        return stop(dataFlowId)
                .map(dataFlow -> {
                    dataFlow.transitToSuspended();
                    store.save(dataFlow);
                    return null;
                });
    }

    @Override
    public StatusResult<Void> terminate(String dataFlowId, @Nullable String reason) {
        return stop(dataFlowId, reason)
                .map(dataFlow -> {
                    dataFlow.transitToTerminated(reason);
                    store.save(dataFlow);
                    return null;
                });
    }

    @Override
    protected StateMachineManager.Builder configureStateMachineManager(StateMachineManager.Builder builder) {
        return builder
                .processor(processDataFlowInState(RECEIVED, this::processReceived))
                .processor(processDataFlowInState(COMPLETED, this::processCompleted))
                .processor(processDataFlowInState(FAILED, this::processFailed));
    }

    private StatusResult<DataFlow> stop(String dataFlowId) {
        return stop(dataFlowId, null);
    }

    private StatusResult<DataFlow> stop(String dataFlowId, String reason) {
        var result = store.findByIdAndLease(dataFlowId);
        if (result.failed()) {
            return StatusResult.from(result).mapFailure();
        }

        var dataFlow = result.getContent();

        if (FlowType.PUSH.equals(dataFlow.getTransferType().flowType())) {
            var transferService = transferServiceRegistry.resolveTransferService(dataFlow.toRequest());

            if (transferService == null) {
                return StatusResult.failure(FATAL_ERROR, "TransferService cannot be resolved for DataFlow %s".formatted(dataFlowId));
            }

            var terminateResult = transferService.terminate(dataFlow);
            if (terminateResult.failed()) {
                if (terminateResult.reason().equals(StreamFailure.Reason.NOT_FOUND)) {
                    monitor.warning("No source was found for DataFlow '%s'. This may indicate an inconsistent state.".formatted(dataFlowId));
                } else {
                    return StatusResult.failure(FATAL_ERROR, "DataFlow %s cannot be terminated: %s".formatted(dataFlowId, terminateResult.getFailureDetail()));
                }
            }
        } else {
            var revokeResult = authorizationService.revokeEndpointDataReference(dataFlowId, reason);
            if (revokeResult.failed()) {
                return StatusResult.failure(FATAL_ERROR, "DataFlow %s cannot be terminated: %s".formatted(dataFlowId, revokeResult.getFailureDetail()));
            }
        }

        return StatusResult.success(dataFlow);
    }

    private Result<DataFlowResponseMessage> handlePull(DataFlowStartMessage startMessage, DataFlow.Builder dataFlowBuilder) {
        return authorizationService.createEndpointDataReference(startMessage)
                .onSuccess(dataAddress -> dataFlowBuilder.state(STARTED.code()))
                .onFailure(f -> monitor.warning("Error obtaining EDR DataAddress: %s".formatted(f.getFailureDetail())))
                .map(dataAddress -> DataFlowResponseMessage.Builder.newInstance()
                        .dataAddress(dataAddress)
                        .build());
    }

    private Result<DataFlowResponseMessage> handlePush(DataFlow.Builder dataFlowBuilder) {
        dataFlowBuilder.state(RECEIVED.code());

        return Result.success(DataFlowResponseMessage.Builder.newInstance()
                .dataAddress(null)
                .build());
    }

    private boolean processReceived(DataFlow dataFlow) {
        var request = dataFlow.toRequest();
        var transferService = transferServiceRegistry.resolveTransferService(request);

        if (transferService == null) {
            dataFlow.transitToFailed("No transferService available for DataFlow " + dataFlow.getId());
            update(dataFlow);
            return true;
        }

        dataFlow.transitionToStarted();
        store.save(dataFlow);

        return entityRetryProcessFactory.doAsyncProcess(dataFlow, () -> transferService.transfer(request))
                .entityRetrieve(id -> store.findById(id))
                .onSuccess((f, r) -> {
                    if (f.getState() != STARTED.code()) {
                        return;
                    }

                    if (r.succeeded()) {
                        f.transitToCompleted();
                    } else {
                        f.transitToFailed(r.getFailureDetail());
                    }
                    update(f);
                })
                .onFailure((f, t) -> {
                    f.transitToReceived();
                    update(f);
                })
                .onRetryExhausted((f, t) -> {
                    f.transitToFailed(t.getMessage());
                    update(f);
                })
                .execute("start data flow");
    }

    private boolean processCompleted(DataFlow dataFlow) {
        var response = transferProcessClient.completed(dataFlow.toRequest());
        if (response.succeeded()) {
            dataFlow.transitToNotified();
            update(dataFlow);
        } else {
            dataFlow.transitToCompleted();
            update(dataFlow);
        }
        return true;
    }

    private boolean processFailed(DataFlow dataFlow) {
        var response = transferProcessClient.failed(dataFlow.toRequest(), dataFlow.getErrorDetail());
        if (response.succeeded()) {
            dataFlow.transitToNotified();
            update(dataFlow);
        } else {
            dataFlow.transitToFailed(dataFlow.getErrorDetail());
            update(dataFlow);
        }
        return true;
    }

    private Processor processDataFlowInState(DataFlowStates state, Function<DataFlow, Boolean> function) {
        var filter = new Criterion[]{ hasState(state.code()) };
        return ProcessorImpl.Builder.newInstance(() -> store.nextNotLeased(batchSize, filter))
                .process(telemetry.contextPropagationMiddleware(function))
                .onNotProcessed(this::breakLease)
                .build();
    }

    public static class Builder extends AbstractStateEntityManager.Builder<DataFlow, DataPlaneStore, DataPlaneManagerImpl, Builder> {

        private Builder() {
            super(new DataPlaneManagerImpl());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public DataPlaneManagerImpl build() {
            Objects.requireNonNull(manager.transferProcessClient);
            return manager;
        }

        public Builder transferServiceRegistry(TransferServiceRegistry transferServiceRegistry) {
            manager.transferServiceRegistry = transferServiceRegistry;
            return this;
        }

        public Builder transferProcessClient(TransferProcessApiClient transferProcessClient) {
            manager.transferProcessClient = transferProcessClient;
            return this;
        }

        public Builder authorizationService(DataPlaneAuthorizationService authorizationService) {
            manager.authorizationService = authorizationService;
            return this;
        }
    }

}
