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

package org.eclipse.edc.connector.controlplane.transfer.flow;

import org.eclipse.edc.connector.controlplane.transfer.spi.flow.TransferTypeParser;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;

import java.util.Optional;

public class TransferTypeParserImpl implements TransferTypeParser {

    @Override
    public Result<TransferType> parse(String transferType) {
        Optional<Result<TransferType>> parsed = Optional.ofNullable(transferType)
                .map(type -> type.split("-"))
                .filter(tokens -> tokens.length == 2)
                .map(tokens -> parseFlowType(tokens[1]).map(flowType -> new TransferType(tokens[0], flowType)));

        return parsed.orElse(Result.failure("Failed to extract flow type from transferType %s".formatted(transferType)));
    }

    private Result<FlowType> parseFlowType(String flowType) {
        try {
            return Result.success(FlowType.valueOf(flowType));
        } catch (Exception e) {
            return Result.failure("Unknown flow type %s".formatted(flowType));
        }
    }
}
