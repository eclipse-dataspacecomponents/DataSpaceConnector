/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
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

package org.eclipse.dataspaceconnector.transfer.core.transfer;

import org.eclipse.dataspaceconnector.core.base.CommandProcessor;
import org.eclipse.dataspaceconnector.core.manager.EntitiesProcessor;
import org.eclipse.dataspaceconnector.spi.command.CommandQueue;
import org.eclipse.dataspaceconnector.spi.command.CommandRunner;
import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.proxy.DataProxyManager;
import org.eclipse.dataspaceconnector.spi.proxy.DataProxyRequest;
import org.eclipse.dataspaceconnector.spi.proxy.ProxyEntry;
import org.eclipse.dataspaceconnector.spi.proxy.ProxyEntryHandlerRegistry;
import org.eclipse.dataspaceconnector.spi.response.ResponseStatus;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.transfer.TransferInitiateResult;
import org.eclipse.dataspaceconnector.spi.transfer.TransferProcessManager;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowManager;
import org.eclipse.dataspaceconnector.spi.transfer.observe.TransferProcessObservable;
import org.eclipse.dataspaceconnector.spi.transfer.provision.ProvisionManager;
import org.eclipse.dataspaceconnector.spi.transfer.provision.ResourceManifestGenerator;
import org.eclipse.dataspaceconnector.spi.transfer.retry.TransferWaitStrategy;
import org.eclipse.dataspaceconnector.spi.transfer.store.TransferProcessStore;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.DataAddress;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataRequest;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.ProvisionResponse;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.ProvisionedDataDestinationResource;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.ProvisionedResource;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.StatusCheckerRegistry;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcess;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.command.TransferProcessCommand;
import org.jetbrains.annotations.NotNull;

import java.net.ConnectException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.eclipse.dataspaceconnector.spi.response.ResponseStatus.ERROR_RETRY;
import static org.eclipse.dataspaceconnector.spi.response.ResponseStatus.FATAL_ERROR;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcess.Type.CONSUMER;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcess.Type.PROVIDER;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates.COMPLETED;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates.DEPROVISIONED;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates.DEPROVISIONING_REQ;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates.ERROR;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates.INITIAL;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates.IN_PROGRESS;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates.PROVISIONED;
import static org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates.REQUESTED_ACK;

/**
 * This transfer process manager receives a {@link TransferProcess} and transitions it through its internal state machine (cf {@link TransferProcessStates}.
 * When submitting a new {@link TransferProcess} it gets created and inserted into the {@link TransferProcessStore}, then returns to the caller.
 * <p>
 * All subsequent state transitions happen asynchronously, the {@code AsyncTransferProcessManager#initiate*Request()} will return immediately.
 * <p>
 * A data transfer processes transitions through a series of states, which allows the system to model both terminating and non-terminating (e.g. streaming) transfers. Transitions
 * occur asynchronously, since long-running processes such as resource provisioning may need to be completed before transitioning to a subsequent state. The permissible state
 * transitions are defined by {@link org.eclipse.dataspaceconnector.spi.types.domain.transfer.TransferProcessStates}.
 * <br/>
 * The transfer manager performs continual iterations, which seek to advance the state of transfer processes, including recovery, in a FIFO state-based ordering.
 * Each iteration will seek to transition a set number of processes for each state to avoid situations where an excessive number of processes in one state block progress of
 * processes in other states.
 * <br/>
 * If no processes need to be transitioned, the transfer manager will wait according to the the defined {@link TransferWaitStrategy} before conducting the next iteration.
 * A wait strategy may implement a backoff scheme.
 */
public class TransferProcessManagerImpl implements TransferProcessManager {
    private final AtomicBoolean active = new AtomicBoolean();

    private int batchSize = 5;
    private TransferWaitStrategy waitStrategy = () -> 5000L;  // default wait five seconds
    private ResourceManifestGenerator manifestGenerator;
    private ProvisionManager provisionManager;
    private TransferProcessStore transferProcessStore;
    private RemoteMessageDispatcherRegistry dispatcherRegistry;
    private DataFlowManager dataFlowManager;
    private ExecutorService executor;
    private StatusCheckerRegistry statusCheckerRegistry;
    private Vault vault;
    private TypeManager typeManager;
    private DataProxyManager dataProxyManager;
    private ProxyEntryHandlerRegistry proxyEntryHandlers;
    private TransferProcessObservable observable;
    private CommandQueue<TransferProcessCommand> commandQueue;
    private CommandRunner<TransferProcessCommand> commandRunner;
    private CommandProcessor<TransferProcessCommand> commandProcessor;
    private Monitor monitor;

    private TransferProcessManagerImpl() {
    }

    public void start(TransferProcessStore processStore) {
        transferProcessStore = processStore;
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
     * Initiate a consumer request TransferProcess.
     * <p>
     * If the request is sync, instead of inserting a {@link TransferProcess} into - and having it traverse through -
     * it returns immediately (= "synchronously"). The {@link TransferProcess} is created in the
     * {@link TransferProcessStates#COMPLETED} state.
     * <p>
     * There is a set of {@link org.eclipse.dataspaceconnector.spi.proxy.ProxyEntryHandler} instances, that receive the resulting {@link ProxyEntry} object.
     * If a {@link org.eclipse.dataspaceconnector.spi.proxy.ProxyEntryHandler} is registered, the {@link ProxyEntry} is forwarded to it, and if no {@link org.eclipse.dataspaceconnector.spi.proxy.ProxyEntryHandler}
     * is registered, the {@link ProxyEntry} object is returned.
     */
    @Override
    public TransferInitiateResult initiateConsumerRequest(DataRequest dataRequest) {
        if (dataRequest.isSync()) {
            return initiateConsumerSyncRequest(dataRequest);
        } else {
            return initiateRequest(CONSUMER, dataRequest);
        }
    }

    /**
     * Initiate a provider request TransferProcess.
     * <p>
     * If the request is sync, instead of inserting a {@link TransferProcess} into - and having it traverse through -
     * it returns immediately (= "synchronously"). The {@link TransferProcess} is created in the
     * {@link TransferProcessStates#COMPLETED} state.
     * <p>
     * The {@link DataProxyManager} checks if a {@link org.eclipse.dataspaceconnector.spi.proxy.DataProxy}
     * is registered for a particular request and if so, calls it.
     */
    @Override
    public TransferInitiateResult initiateProviderRequest(DataRequest dataRequest) {
        if (dataRequest.isSync()) {
            return initiateProviderSyncRequest(dataRequest);
        } else {
            return initiateRequest(PROVIDER, dataRequest);
        }
    }

    @Override
    public void enqueueCommand(TransferProcessCommand command) {
        commandQueue.enqueue(command);
    }

    void onProvisionComplete(String processId, List<ProvisionResponse> responses) {
        var transferProcess = transferProcessStore.find(processId);
        if (transferProcess == null) {
            monitor.severe("TransferProcessManager: no TransferProcess found for deprovisioned resources");
            return;
        }

        if (transferProcess.getState() == ERROR.code()) {
            monitor.severe(format("TransferProcessManager: transfer process %s is in ERROR state, so provisioning could not be completed", transferProcess.getId()));
            return;
        }

        responses.stream()
                .map(response -> {
                    var destinationResource = response.getResource();
                    var secretToken = response.getSecretToken();

                    if (destinationResource instanceof ProvisionedDataDestinationResource) {
                        var dataDestinationResource = (ProvisionedDataDestinationResource) destinationResource;
                        DataAddress dataDestination = dataDestinationResource.createDataDestination();

                        if (secretToken != null) {
                            String keyName = dataDestinationResource.getResourceName();
                            vault.storeSecret(keyName, typeManager.writeValueAsString(secretToken));
                            dataDestination.setKeyName(keyName);
                        }

                        transferProcess.getDataRequest().updateDestination(dataDestination);
                    }

                    return destinationResource;
                })
                .forEach(transferProcess::addProvisionedResource);

        if (transferProcess.provisioningComplete()) {
            transferProcess.transitionProvisioned();
        }

        transferProcessStore.update(transferProcess);
    }

    void onDeprovisionComplete(String processId) {
        monitor.info("Deprovisioning successfully completed.");

        TransferProcess transferProcess = transferProcessStore.find(processId);
        if (transferProcess == null) {
            monitor.severe("TransferProcessManager: no TransferProcess found for provisioned resources");
            return;
        }

        if (transferProcess.getState() == ERROR.code()) {
            monitor.severe(format("TransferProcessManager: transfer process %s is in ERROR state, so deprovisioning could not be completed", transferProcess.getId()));
            return;
        }

        transferProcess.transitionDeprovisioned();
        transferProcessStore.update(transferProcess);
        observable.invokeForEach(l -> l.deprovisioned(transferProcess));
    }

    private void transitionRequestAck(String processId) {
        TransferProcess transferProcess = transferProcessStore.find(processId);
        if (transferProcess == null) {
            monitor.severe("TransferProcessManager: no TransferProcess found for acked request");
            return;
        }

        transferProcess.transitionRequestAck();
        transferProcessStore.update(transferProcess);
    }

    private TransferInitiateResult initiateRequest(TransferProcess.Type type, DataRequest dataRequest) {
        // make the request idempotent: if the process exists, return
        var processId = transferProcessStore.processIdForTransferId(dataRequest.getId());
        if (processId != null) {
            return TransferInitiateResult.success(processId);
        }
        var id = randomUUID().toString();
        var process = TransferProcess.Builder.newInstance().id(id).dataRequest(dataRequest).type(type).build();
        if (process.getState() == TransferProcessStates.UNSAVED.code()) {
            process.transitionInitial();
        }
        transferProcessStore.create(process);
        observable.invokeForEach(l -> l.created(process));
        return TransferInitiateResult.success(process.getId());
    }

    @NotNull
    private TransferInitiateResult initiateProviderSyncRequest(DataRequest dataRequest) {
        var process = createCompletedTransferProcess(dataRequest, PROVIDER);

        transferProcessStore.create(process);

        var dataProxy = dataProxyManager.getProxy(dataRequest.getDestinationType());
        if (dataProxy == null) {
            return TransferInitiateResult.error(process.getId(), FATAL_ERROR, "There is not DataProxy for destination " + dataRequest.getDestinationType());
        } else {
            var proxyDataResult = dataProxy.getData(createDataProxyRequest(dataRequest));
            if (proxyDataResult.failed()) {
                return TransferInitiateResult.error(process.getId(), ERROR_RETRY, "Failed to get data from proxy");
            }
            return TransferInitiateResult.success(process.getId(), proxyDataResult.getContent());
        }
    }

    @NotNull
    private TransferInitiateResult initiateConsumerSyncRequest(DataRequest dataRequest) {
        TransferProcess transferProcess = createCompletedTransferProcess(dataRequest, CONSUMER);

        transferProcessStore.create(transferProcess);

        var proxyConversion = dispatcherRegistry.send(Object.class, dataRequest, transferProcess::getId)
                .thenApply(this::extractPayloadAsProxyEntry)
                .thenApply(proxyEntry -> {
                    String type = proxyEntry.getType();
                    return Optional.ofNullable(proxyEntryHandlers.get(type))
                            .map(handler -> handler.accept(createDataProxyRequest(dataRequest), proxyEntry))
                            .orElse(proxyEntry);
                });

        try {
            var result = proxyConversion.join();
            return TransferInitiateResult.success(dataRequest.getId(), result);
        } catch (Exception e) {
            var status = isRetryable(e.getCause()) ? ResponseStatus.ERROR_RETRY : FATAL_ERROR;
            return TransferInitiateResult.error(dataRequest.getId(), status, e.getMessage());
        }
    }

    private TransferProcess createCompletedTransferProcess(DataRequest dataRequest, TransferProcess.Type type) {
        var id = UUID.randomUUID().toString();

        return TransferProcess.Builder.newInstance()
                .id(id)
                .dataRequest(dataRequest)
                .state(COMPLETED.code())
                .type(type)
                .build();
    }

    private ProxyEntry extractPayloadAsProxyEntry(Object result) {
        try {
            var payloadField = result.getClass().getDeclaredField("payload");
            payloadField.setAccessible(true);
            var payload = payloadField.get(result).toString();

            return typeManager.readValue(payload, ProxyEntry.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return ProxyEntry.Builder.newInstance().build();
        }
    }

    private DataProxyRequest createDataProxyRequest(DataRequest request) {
        return new DataProxyRequest(request.getConnectorAddress(), request.getContractId(), request.getDataDestination());
    }

    private boolean isRetryable(Throwable ex) {
        return ex instanceof ConnectException; //we might need to add more retryable exceptions
    }

    private void run() {
        while (active.get()) {
            try {
                long initial = onTransfersInState(INITIAL).doProcess(this::processInitial);
                long provisioned = onTransfersInState(PROVISIONED).doProcess(this::processProvisioned);
                long ackRequested = onTransfersInState(REQUESTED_ACK).doProcess(this::processAckRequested);
                long inProgress = onTransfersInState(IN_PROGRESS).doProcess(this::processInProgress);
                long deprovisioningRequest = onTransfersInState(DEPROVISIONING_REQ).doProcess(this::processDeprovisioningRequest);
                long deprovisioned = onTransfersInState(DEPROVISIONED).doProcess(this::processDeprovisioned);

                long commandsProcessed = onCommands().doProcess(this::processCommand);

                var totalProcessed = initial + provisioned + ackRequested + inProgress + deprovisioningRequest + deprovisioned + commandsProcessed;
                if (totalProcessed == 0) {
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
                monitor.severe("Error caught in transfer process manager", e);
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

    private EntitiesProcessor<TransferProcess> onTransfersInState(TransferProcessStates state) {
        return new EntitiesProcessor<>(() -> transferProcessStore.nextForState(state.code(), batchSize));
    }

    private EntitiesProcessor<TransferProcessCommand> onCommands() {
        return new EntitiesProcessor<>(() -> commandQueue.dequeue(5));
    }

    private boolean processCommand(TransferProcessCommand command) {
        return commandProcessor.processCommandQueue(command);
    }

    private boolean processDeprovisioned(TransferProcess process) {
        process.transitionEnded();
        transferProcessStore.update(process);
        observable.invokeForEach(l -> l.ended(process));
        monitor.debug("Process " + process.getId() + " is now " + TransferProcessStates.from(process.getState()));
        return true;
    }

    private boolean processDeprovisioningRequest(TransferProcess process) {
        process.transitionDeprovisioning();
        transferProcessStore.update(process);
        observable.invokeForEach(l -> l.deprovisioning(process));
        monitor.debug("Process " + process.getId() + " is now " + TransferProcessStates.from(process.getState()));
        provisionManager.deprovision(process)
                .whenComplete((responses, throwable) -> {
                    if (throwable == null) {
                        onDeprovisionComplete(process.getId());
                    } else {
                        monitor.severe("Error during deprovisioning", throwable);
                        process.transitionError("Error during deprovisioning: " + throwable.getCause().getLocalizedMessage());
                        transferProcessStore.update(process);
                    }
                });
        return true;
    }

    private boolean processAckRequested(TransferProcess process) {
        if (!process.getDataRequest().isManagedResources() || (process.getProvisionedResourceSet() != null && !process.getProvisionedResourceSet().empty())) {

            if (process.getDataRequest().getTransferType().isFinite()) {
                process.transitionInProgress();
            } else {
                process.transitionStreaming();
            }
            transferProcessStore.update(process);
            observable.invokeForEach(l -> l.inProgress(process));
            monitor.debug("Process " + process.getId() + " is now " + TransferProcessStates.from(process.getState()));
            return true;
        } else {
            monitor.debug("Process " + process.getId() + " does not yet have provisioned resources, will stay in " + TransferProcessStates.REQUESTED_ACK);
            return false;
        }
    }

    private boolean processInProgress(TransferProcess process) {
        if (process.getType() != CONSUMER) {
            return false;
        }

        var checker = statusCheckerRegistry.resolve(process.getDataRequest().getDestinationType());
        if (checker == null) {
            if (process.getDataRequest().isManagedResources()) {
                monitor.info(format("No checker found for process %s. The process will not advance to the COMPLETED state.", process.getId()));
                return false;
            } else {
                //no checker, transition the process to the COMPLETED state automatically
                transitionToCompleted(process);
            }
            return true;
        } else {
            List<ProvisionedResource> resources = process.getDataRequest().isManagedResources() ? process.getProvisionedResourceSet().getResources() : emptyList();
            if (checker.isComplete(process, resources)) {
                transitionToCompleted(process);
                return true;
            } else {
                process.transitionInProgress();
                transferProcessStore.update(process);
                monitor.info(format("Transfer process %s not COMPLETED yet. The process will not advance to the COMPLETED state.", process.getId()));
                return false;
            }
        }
    }

    private void transitionToCompleted(TransferProcess process) {
        process.transitionCompleted();
        monitor.debug("Process " + process.getId() + " is now " + COMPLETED);
        transferProcessStore.update(process);
        observable.invokeForEach(listener -> listener.completed(process));
    }

    /**
     * Performs consumer-side or provider side provisioning for a service.
     * <br/>
     * On a consumer, provisioning may entail setting up a data destination and supporting infrastructure. On a provider, provisioning is initiated when a request is received and
     * map involve preprocessing data or other operations.
     */
    private boolean processInitial(TransferProcess process) {
        var manifest = manifestGenerator.generateResourceManifest(process);
        process.transitionProvisioning(manifest);
        transferProcessStore.update(process);
        observable.invokeForEach(l -> l.provisioning(process));

        provisionManager.provision(process).whenComplete((responses, throwable) -> {
            if (throwable == null) {
                onProvisionComplete(process.getId(), responses);
            } else {
                monitor.severe("Error during provisioning", throwable);
                process.transitionError("Error during provisioning: " + throwable.getCause().getLocalizedMessage());
                transferProcessStore.update(process);
            }
        });

        return true;
    }

    private boolean processProvisioned(TransferProcess process) {
        DataRequest dataRequest = process.getDataRequest();
        if (CONSUMER == process.getType()) {
            sendConsumerRequest(process, dataRequest);
        } else {
            processProviderRequest(process, dataRequest);
        }
        return true;
    }

    private void processProviderRequest(TransferProcess process, DataRequest dataRequest) {
        var response = dataFlowManager.initiate(dataRequest);
        if (response.succeeded()) {
            if (process.getDataRequest().getTransferType().isFinite()) {
                process.transitionInProgress();
            } else {
                process.transitionStreaming();
            }
            transferProcessStore.update(process);
            observable.invokeForEach(l -> l.inProgress(process));
        } else {
            if (ResponseStatus.ERROR_RETRY == response.getFailure().status()) {
                monitor.severe("Error processing transfer request. Setting to retry: " + process.getId());
                process.transitionProvisioned();
                transferProcessStore.update(process);
                observable.invokeForEach(l -> l.provisioned(process));
            } else {
                monitor.severe(format("Fatal error processing transfer request: %s. Error details: %s", process.getId(), String.join(", ", response.getFailureMessages())));
                process.transitionError(response.getFailureMessages().stream().findFirst().orElse(""));
                transferProcessStore.update(process);
                observable.invokeForEach(l -> l.error(process));
            }
        }
    }

    private void sendConsumerRequest(TransferProcess process, DataRequest dataRequest) {
        process.transitionRequested();
        transferProcessStore.update(process);   // update before sending to accommodate synchronous transports; reliability will be managed by retry and idempotency
        observable.invokeForEach(l -> l.requested(process));
        dispatcherRegistry.send(Object.class, dataRequest, process::getId)
                .thenApply(o -> {
                    transitionRequestAck(process.getId());
                    transferProcessStore.update(process);
                    return o;
                })
                .whenComplete((o, throwable) -> {
                    if (o != null) {
                        monitor.info("Object received: " + o);
                        if (dataRequest.getTransferType().isFinite()) {
                            process.transitionInProgress();
                        } else {
                            process.transitionStreaming();
                        }
                        transferProcessStore.update(process);
                    }
                });
    }

    public static class Builder {
        private final TransferProcessManagerImpl manager;

        private Builder() {
            manager = new TransferProcessManagerImpl();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder batchSize(int size) {
            manager.batchSize = size;
            return this;
        }

        public Builder waitStrategy(TransferWaitStrategy waitStrategy) {
            manager.waitStrategy = waitStrategy;
            return this;
        }

        public Builder manifestGenerator(ResourceManifestGenerator manifestGenerator) {
            manager.manifestGenerator = manifestGenerator;
            return this;
        }

        public Builder provisionManager(ProvisionManager provisionManager) {
            manager.provisionManager = provisionManager;
            return this;
        }

        public Builder dataFlowManager(DataFlowManager dataFlowManager) {
            manager.dataFlowManager = dataFlowManager;
            return this;
        }

        public Builder dispatcherRegistry(RemoteMessageDispatcherRegistry registry) {
            manager.dispatcherRegistry = registry;
            return this;
        }

        public Builder monitor(Monitor monitor) {
            manager.monitor = monitor;
            return this;
        }

        public Builder statusCheckerRegistry(StatusCheckerRegistry statusCheckerRegistry) {
            manager.statusCheckerRegistry = statusCheckerRegistry;
            return this;
        }

        public Builder vault(Vault vault) {
            manager.vault = vault;
            return this;
        }

        public Builder typeManager(TypeManager typeManager) {
            manager.typeManager = typeManager;
            return this;
        }

        public Builder commandQueue(CommandQueue<TransferProcessCommand> queue) {
            manager.commandQueue = queue;
            return this;
        }

        public Builder commandRunner(CommandRunner<TransferProcessCommand> runner) {
            manager.commandRunner = runner;
            return this;
        }

        public Builder dataProxyManager(DataProxyManager dataProxyManager) {
            manager.dataProxyManager = dataProxyManager;
            return this;
        }

        public Builder proxyEntryHandlerRegistry(ProxyEntryHandlerRegistry proxyEntryHandlerRegistry) {
            manager.proxyEntryHandlers = proxyEntryHandlerRegistry;
            return this;
        }

        public Builder observable(TransferProcessObservable observable) {
            manager.observable = observable;
            return this;
        }

        public TransferProcessManagerImpl build() {
            Objects.requireNonNull(manager.manifestGenerator, "manifestGenerator");
            Objects.requireNonNull(manager.provisionManager, "provisionManager");
            Objects.requireNonNull(manager.dataFlowManager, "dataFlowManager");
            Objects.requireNonNull(manager.dispatcherRegistry, "dispatcherRegistry");
            Objects.requireNonNull(manager.monitor, "monitor");
            Objects.requireNonNull(manager.commandQueue, "commandQueue cannot be null");
            Objects.requireNonNull(manager.commandRunner, "commandRunner cannot be null");
            Objects.requireNonNull(manager.statusCheckerRegistry, "StatusCheckerRegistry cannot be null!");
            Objects.requireNonNull(manager.dataProxyManager, "DataProxyManager cannot be null!");
            Objects.requireNonNull(manager.proxyEntryHandlers, "ProxyEntryHandlerRegistry cannot be null!");
            Objects.requireNonNull(manager.observable, "Observable cannot be null");
            
            manager.commandProcessor = new CommandProcessor<>(manager.commandQueue, manager.commandRunner, manager.monitor);
            
            return manager;
        }
    }

}
