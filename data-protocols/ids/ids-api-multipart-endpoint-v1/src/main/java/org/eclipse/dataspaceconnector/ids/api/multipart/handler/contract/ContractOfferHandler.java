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

package org.eclipse.dataspaceconnector.ids.api.multipart.handler.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iais.eis.ContractOffer;
import de.fraunhofer.iais.eis.ContractOfferMessage;
import de.fraunhofer.iais.eis.Message;
import org.eclipse.dataspaceconnector.ids.api.multipart.handler.Handler;
import org.eclipse.dataspaceconnector.ids.api.multipart.handler.ResponseMessageUtil;
import org.eclipse.dataspaceconnector.ids.api.multipart.message.MultipartRequest;
import org.eclipse.dataspaceconnector.ids.api.multipart.message.MultipartResponse;
import org.eclipse.dataspaceconnector.spi.iam.VerificationResult;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

import static org.eclipse.dataspaceconnector.ids.api.multipart.util.RejectionMessageUtil.badParameters;

/**
 * This class handles and processes incoming IDS {@link ContractOfferMessage}s.
 */
public class ContractOfferHandler implements Handler {

    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private final String connectorId;

    public ContractOfferHandler(
            @NotNull Monitor monitor,
            @NotNull String connectorId,
            @NotNull ObjectMapper objectMapper) {
        this.monitor = Objects.requireNonNull(monitor);
        this.connectorId = Objects.requireNonNull(connectorId);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public boolean canHandle(@NotNull MultipartRequest multipartRequest) {
        Objects.requireNonNull(multipartRequest);

        return multipartRequest.getHeader() instanceof ContractOfferMessage;
    }

    @Override
    public @Nullable MultipartResponse handleRequest(@NotNull MultipartRequest multipartRequest, @NotNull VerificationResult verificationResult) {
        Objects.requireNonNull(multipartRequest);
        Objects.requireNonNull(verificationResult);

        var message = (ContractOfferMessage) multipartRequest.getHeader();

        ContractOffer contractOffer = null;
        try {
            contractOffer = objectMapper.readValue(multipartRequest.getPayload(), ContractOffer.class);
        } catch (IOException e) {
            monitor.debug("ContractOfferHandler: Contract Offer is invalid");
            return createBadParametersErrorMultipartResponse(message);
        }

        // TODO
        // var correlationId = contractOfferMessage.getTransferContract();
        // Create contract offer request
        // Start negotiation process: ProviderContractNegotiationManagerImpl.offered

        return MultipartResponse.Builder.newInstance()
                .header(ResponseMessageUtil.createRequestInProcessMessage(connectorId, message))
                .build();
    }

    private MultipartResponse createBadParametersErrorMultipartResponse(Message message) {
        return MultipartResponse.Builder.newInstance()
                .header(badParameters(message, connectorId))
                .build();
    }
}
