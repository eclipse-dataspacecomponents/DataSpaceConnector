/*
 *  Copyright (c) 2022 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.dataspaceconnector.transfer.dataplane.sync.validation;

import org.eclipse.dataspaceconnector.spi.contract.negotiation.store.ContractNegotiationStore;
import org.eclipse.dataspaceconnector.spi.iam.ClaimToken;
import org.eclipse.dataspaceconnector.spi.jwt.TokenValidationRule;
import org.eclipse.dataspaceconnector.spi.result.Result;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.eclipse.dataspaceconnector.transfer.dataplane.spi.DataPlaneTransferConstants.CONTRACT_ID;

/**
 * Assert that contract still allows access to the data. As of current implementation it only validates the contract end date.
 */
public class ContractValidationRule implements TokenValidationRule {

    private final ContractNegotiationStore contractNegotiationStore;
    private final Clock clock;

    public ContractValidationRule(ContractNegotiationStore contractNegotiationStore, Clock clock) {
        this.contractNegotiationStore = contractNegotiationStore;
        this.clock = clock;
    }

    @Override
    public Result<Void> checkRule(@NotNull ClaimToken toVerify, @Nullable Map<String, Object> additional) {
        String contractId = (String) toVerify.getClaims().get(CONTRACT_ID);

        if (contractId == null) {
            return Result.failure(String.format("Missing contract id claim `%s`", CONTRACT_ID));
        }

        var contractAgreement = contractNegotiationStore.findContractAgreement(contractId);
        if (contractAgreement == null) {
            return Result.failure("No contract agreement found for id: " + contractId);
        }

        if (clock.instant().isAfter(Instant.ofEpochSecond(contractAgreement.getContractEndDate()))) {
            return Result.failure("Contract has expired");
        }

        return Result.success();
    }
}
