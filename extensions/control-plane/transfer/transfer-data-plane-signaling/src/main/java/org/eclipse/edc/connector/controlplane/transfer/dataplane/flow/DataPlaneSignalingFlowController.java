/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.controlplane.transfer.dataplane.flow;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowPropertiesProvider;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.TransferTypeParser;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClient;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.web.spi.configuration.context.ControlApiUrl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;

/**
 * Implementation of {@link DataFlowController} that is compliant with the data plane signaling.
 * <p>
 * It handles all the transfer process where the transferType met the criteria defined in the format mapping of the
 * signaling spec
 *
 * @see <a href="https://github.com/eclipse-edc/Connector/blob/main/docs/developer/data-plane-signaling/data-plane-signaling.md">Data plane signaling</a>
 * @see <a href="https://github.com/eclipse-edc/Connector/blob/main/docs/developer/data-plane-signaling/data-plane-signaling-mapping.md">Data plane signaling transfer type mapping</a>
 */
public class DataPlaneSignalingFlowController implements DataFlowController {

    private final ControlApiUrl callbackUrl;
    private final DataPlaneSelectorService selectorClient;
    private final DataPlaneClientFactory clientFactory;
    private final DataFlowPropertiesProvider propertiesProvider;
    private final String selectionStrategy;
    private final TransferTypeParser transferTypeParser;

    public DataPlaneSignalingFlowController(ControlApiUrl callbackUrl, DataPlaneSelectorService selectorClient,
                                            DataFlowPropertiesProvider propertiesProvider, DataPlaneClientFactory clientFactory,
                                            String selectionStrategy, TransferTypeParser transferTypeParser) {
        this.callbackUrl = callbackUrl;
        this.selectorClient = selectorClient;
        this.propertiesProvider = propertiesProvider;
        this.clientFactory = clientFactory;
        this.selectionStrategy = selectionStrategy;
        this.transferTypeParser = transferTypeParser;
    }

    @Override
    public boolean canHandle(TransferProcess transferProcess) {
        return transferTypeParser.parse(transferProcess.getTransferType()).succeeded();
    }

    @Override
    public @NotNull StatusResult<DataFlowResponse> start(TransferProcess transferProcess, Policy policy) {
        var transferTypeParse = transferTypeParser.parse(transferProcess.getTransferType());
        if (transferTypeParse.failed()) {
            return StatusResult.failure(FATAL_ERROR, transferTypeParse.getFailureDetail());
        }

        var propertiesResult = propertiesProvider.propertiesFor(transferProcess, policy);
        if (propertiesResult.failed()) {
            return StatusResult.failure(FATAL_ERROR, propertiesResult.getFailureDetail());
        }

        var selection = selectorClient.select(transferProcess.getContentDataAddress(), transferProcess.getTransferType(), selectionStrategy);
        if (!selection.succeeded()) {
            return StatusResult.failure(FATAL_ERROR, selection.getFailureDetail());
        }

        var dataFlowRequest = DataFlowStartMessage.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .processId(transferProcess.getId())
                .sourceDataAddress(transferProcess.getContentDataAddress())
                .destinationDataAddress(transferProcess.getDataDestination())
                .participantId(policy.getAssignee())
                .agreementId(transferProcess.getContractId())
                .assetId(transferProcess.getAssetId())
                .transferType(transferTypeParse.getContent())
                .callbackAddress(callbackUrl != null ? callbackUrl.get() : null)
                .properties(propertiesResult.getContent())
                .build();

        var dataPlaneInstance = selection.getContent();
        return clientFactory.createClient(dataPlaneInstance)
                .start(dataFlowRequest)
                .map(it -> DataFlowResponse.Builder.newInstance()
                        .dataAddress(it.getDataAddress())
                        .dataPlaneId(dataPlaneInstance.getId())
                        .build()
                );
    }

    @Override
    public StatusResult<Void> suspend(TransferProcess transferProcess) {
        return getClientForDataplane(transferProcess.getDataPlaneId())
                .map(client -> client.suspend(transferProcess.getId()))
                .orElse(f -> {
                    var message = "Failed to select the data plane for suspending the transfer process %s. %s"
                            .formatted(transferProcess.getId(), f.getFailureDetail());
                    return StatusResult.failure(FATAL_ERROR, message);
                });
    }

    @Override
    public StatusResult<Void> terminate(TransferProcess transferProcess) {
        return getClientForDataplane(transferProcess.getDataPlaneId())
                .map(client -> client.terminate(transferProcess.getId()))
                .orElse(f -> {
                    var message = "Failed to select the data plane for terminating the transfer process %s. %s"
                            .formatted(transferProcess.getId(), f.getFailureDetail());
                    return StatusResult.failure(FATAL_ERROR, message);
                });
    }

    @Override
    public Set<String> transferTypesFor(Asset asset) {
        var result = selectorClient.getAll();
        if (result.failed()) {
            return emptySet();
        }

        return result.getContent().stream()
                .filter(it -> it.getAllowedSourceTypes().contains(asset.getDataAddress().getType()))
                .map(DataPlaneInstance::getAllowedTransferTypes)
                .flatMap(Collection::stream)
                .collect(toSet());
    }

    private StatusResult<DataPlaneClient> getClientForDataplane(String id) {
        return selectorClient.findById(id)
                .map(clientFactory::createClient)
                .map(StatusResult::success)
                .orElse(f -> StatusResult.failure(FATAL_ERROR, "No data-plane found with id %s. %s".formatted(id, f.getFailureDetail())));
    }

}
