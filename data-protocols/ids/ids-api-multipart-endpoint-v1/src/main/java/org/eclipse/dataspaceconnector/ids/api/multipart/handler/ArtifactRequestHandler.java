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

package org.eclipse.dataspaceconnector.ids.api.multipart.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iais.eis.ArtifactRequestMessage;
import org.eclipse.dataspaceconnector.common.string.StringUtils;
import org.eclipse.dataspaceconnector.ids.api.multipart.message.MultipartRequest;
import org.eclipse.dataspaceconnector.ids.api.multipart.message.MultipartResponse;
import org.eclipse.dataspaceconnector.ids.spi.IdsIdParser;
import org.eclipse.dataspaceconnector.ids.spi.IdsType;
import org.eclipse.dataspaceconnector.ids.spi.Protocols;
import org.eclipse.dataspaceconnector.ids.spi.spec.extension.ArtifactRequestMessagePayload;
import org.eclipse.dataspaceconnector.spi.contract.negotiation.store.ContractNegotiationStore;
import org.eclipse.dataspaceconnector.spi.contract.validation.ContractValidationService;
import org.eclipse.dataspaceconnector.spi.iam.ClaimToken;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.transfer.TransferProcessManager;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.eclipse.dataspaceconnector.ids.api.multipart.util.MultipartResponseUtil.createBadParametersErrorMultipartResponse;
import static org.eclipse.dataspaceconnector.ids.api.multipart.util.ResponseMessageUtil.createResponseMessageForStatusResult;
import static org.eclipse.dataspaceconnector.ids.spi.IdsConstants.IDS_WEBHOOK_ADDRESS_PROPERTY;

public class ArtifactRequestHandler implements Handler {

    private final TransferProcessManager transferProcessManager;
    private final String connectorId;
    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private final ContractValidationService contractValidationService;
    private final ContractNegotiationStore contractNegotiationStore;
    private final Vault vault;

    public ArtifactRequestHandler(
            @NotNull Monitor monitor,
            @NotNull String connectorId,
            @NotNull ObjectMapper objectMapper,
            @NotNull ContractNegotiationStore contractNegotiationStore,
            @NotNull ContractValidationService contractValidationService,
            @NotNull TransferProcessManager transferProcessManager,
            @NotNull Vault vault) {
        this.monitor = Objects.requireNonNull(monitor);
        this.connectorId = Objects.requireNonNull(connectorId);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.contractNegotiationStore = Objects.requireNonNull(contractNegotiationStore);
        this.contractValidationService = Objects.requireNonNull(contractValidationService);
        this.transferProcessManager = Objects.requireNonNull(transferProcessManager);
        this.vault = Objects.requireNonNull(vault);
    }

    @Override
    public boolean canHandle(@NotNull MultipartRequest multipartRequest) {
        Objects.requireNonNull(multipartRequest);

        return multipartRequest.getHeader() instanceof ArtifactRequestMessage;
    }

    @Override
    public @Nullable MultipartResponse handleRequest(@NotNull MultipartRequest multipartRequest, @NotNull ClaimToken claimToken) {
        Objects.requireNonNull(multipartRequest);
        Objects.requireNonNull(claimToken);

        var artifactRequestMessage = (ArtifactRequestMessage) multipartRequest.getHeader();

        var artifactUri = artifactRequestMessage.getRequestedArtifact();
        var artifactIdsId = IdsIdParser.parse(artifactUri.toString());
        if (artifactIdsId.getType() != IdsType.ARTIFACT) {
            monitor.debug("ArtifactRequestHandler: Requested artifact URI not of type artifact.");
            return createBadParametersErrorMultipartResponse(connectorId, multipartRequest.getHeader());
        }

        var contractUri = artifactRequestMessage.getTransferContract();
        var contractIdsId = IdsIdParser.parse(contractUri.toString());
        if (contractIdsId.getType() != IdsType.CONTRACT) {
            monitor.debug("ArtifactRequestHandler: Transfer contract URI not of type contract.");
            return createBadParametersErrorMultipartResponse(connectorId, multipartRequest.getHeader());
        }

        var contractAgreement = contractNegotiationStore.findContractAgreement(contractIdsId.getValue());
        if (contractAgreement == null) {
            monitor.debug(String.format("ArtifactRequestHandler: No contract agreement with id %s found.", contractIdsId.getValue()));
            return createBadParametersErrorMultipartResponse(connectorId, multipartRequest.getHeader());
        }

        var isContractValid = contractValidationService.validate(claimToken, contractAgreement);
        if (!isContractValid) {
            monitor.debug("ArtifactRequestHandler: Contract is invalid");
            return createBadParametersErrorMultipartResponse(connectorId, multipartRequest.getHeader());
        }

        if (!artifactIdsId.getValue().equals(contractAgreement.getAssetId())) {
            monitor.debug(String.format("ArtifactRequestHandler: invalid artifact id specified %s for contract: %s", artifactIdsId.getValue(), contractIdsId.getValue()));
            return createBadParametersErrorMultipartResponse(connectorId, multipartRequest.getHeader());
        }

        ArtifactRequestMessagePayload artifactRequestMessagePayload;
        try {
            artifactRequestMessagePayload =
                    objectMapper.readValue(multipartRequest.getPayload(), ArtifactRequestMessagePayload.class);
        } catch (IOException e) {
            return createBadParametersErrorMultipartResponse(connectorId, artifactRequestMessage);
        }

        var dataAddress = artifactRequestMessagePayload.getDataDestination();

        Map<String, String> props = new HashMap<>();
        if (artifactRequestMessage.getProperties() != null) {
            artifactRequestMessage.getProperties().forEach((k, v) -> props.put(k, v.toString()));
        }

        var idsWebhookAddress = Optional.ofNullable(props.remove(IDS_WEBHOOK_ADDRESS_PROPERTY))
                .map(Object::toString)
                .orElse(null);
        if (StringUtils.isNullOrBlank(idsWebhookAddress)) {
            var msg = "Ids webhook address is invalid";
            monitor.debug(String.format("%s: %s", getClass().getSimpleName(), msg));
            return createBadParametersErrorMultipartResponse(connectorId, artifactRequestMessage, msg);
        }

        // NB: DO NOT use the asset id provided by the client as that can open aan attack vector where a client references an artifact that
        //     is different from the one specified by the contract

        var dataRequest = DataRequest.Builder.newInstance()
                .id(artifactRequestMessage.getId().toString())
                .protocol(Protocols.IDS_MULTIPART)
                .dataDestination(dataAddress)
                .connectorId(connectorId)
                .assetId(contractAgreement.getAssetId())
                .contractId(contractAgreement.getId())
                .properties(props)
                .connectorAddress(idsWebhookAddress)
                .build();

        var transferInitiateResult = transferProcessManager.initiateProviderRequest(dataRequest);

        if (artifactRequestMessagePayload.getSecret() != null) {
            vault.storeSecret(dataAddress.getKeyName(), artifactRequestMessagePayload.getSecret());
        }

        return MultipartResponse.Builder.newInstance()
                .header(createResponseMessageForStatusResult(transferInitiateResult, artifactRequestMessage, connectorId))
                .build();
    }
}
