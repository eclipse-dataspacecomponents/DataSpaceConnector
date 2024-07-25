/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Fraunhofer Institute for Software and Systems Engineering - initiate provider process
 *
 */

package org.eclipse.edc.connector.controlplane.services.spi.transferprocess;

import org.eclipse.edc.connector.controlplane.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.ResumeTransferCommand;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.SuspendTransferCommand;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.TerminateTransferCommand;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mediates access to and modification of {@link TransferProcess}es.
 */
public interface TransferProcessService {

    /**
     * Returns a transferProcess by its id.
     *
     * @param transferProcessId id of the transferProcess
     * @return the transferProcess, null if it's not found
     */
    @Nullable
    TransferProcess findById(String transferProcessId);

    /**
     * Search transferProcess.
     *
     * @param query request
     * @return the collection of transferProcesses that match the query
     */
    ServiceResult<List<TransferProcess>> search(QuerySpec query);

    /**
     * Returns the state of a transferProcess by its id.
     *
     * @param transferProcessId id of the transferProcess
     * @return the transferProcess state name, null if it's not found
     */
    @Nullable
    String getState(String transferProcessId);

    /**
     * Asynchronously requests completion of the transfer process.
     * <p>
     * The return result status only reflects the successful submission of the command.
     *
     * @param transferProcessId id of the transferProcess
     * @return a result that is successful if the transfer process was found and is in a state that can be completed
     */
    @NotNull
    ServiceResult<Void> complete(String transferProcessId);

    /**
     * Terminate Transfer Process.
     *
     * @param command the Command
     * @return success if the Transfer has been terminated, failure otherwise
     */
    @NotNull
    ServiceResult<Void> terminate(TerminateTransferCommand command);

    /**
     * Suspend Transfer Process.
     *
     * @param command the Command
     * @return success if the Transfer has been suspended, failure otherwise
     */
    @NotNull
    ServiceResult<Void> suspend(SuspendTransferCommand command);

    /**
     * Resume Transfer Process.
     *
     * @param command the command.
     * @return success if the Transfer has been resumed, failure otherwise
     */
    @NotNull
    ServiceResult<Void> resume(ResumeTransferCommand command);

    /**
     * Asynchronously requests deprovisioning of the transfer process.
     * <p>
     * The return result status only reflects the successful submission of the command.
     *
     * @param transferProcessId id of the transferProcess
     * @return a result that is successful if the transfer process was found and is in a state that can be deprovisioned
     */
    @NotNull
    ServiceResult<Void> deprovision(String transferProcessId);

    /**
     * Initiate transfer request for type consumer.
     *
     * @param request for the transfer.
     * @return a result that is successful if the transfer process was initiated with the created TransferProcess.
     */
    @NotNull
    ServiceResult<TransferProcess> initiateTransfer(TransferRequest request);

    /**
     * Asynchronously informs the system that the {@link DeprovisionedResource} has been provisioned
     *
     * @param transferProcessId The transfer process id
     * @param resource          The {@link DeprovisionedResource} to deprovision
     * @return a result that is successful if the transfer process was found
     */
    ServiceResult<Void> completeDeprovision(String transferProcessId, DeprovisionedResource resource);

    /**
     * Asynchronously handles a {@link ProvisionResponse} received from an external system
     *
     * @param transferProcessId The transfer process id
     * @param response          The {@link ProvisionResponse} to handle
     * @return a result that is successful if the transfer process was found
     */
    ServiceResult<Void> addProvisionedResource(String transferProcessId, ProvisionResponse response);

}
