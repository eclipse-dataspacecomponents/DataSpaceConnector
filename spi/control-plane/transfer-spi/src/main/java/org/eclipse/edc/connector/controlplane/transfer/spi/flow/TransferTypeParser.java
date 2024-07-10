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

package org.eclipse.edc.connector.controlplane.transfer.spi.flow;

import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;

public interface TransferTypeParser {

    /**
     * Parse the {@link TransferType}.
     *
     * @param transferType the transfer type string representation.
     * @return the {@link TransferType}, failure if the operation failed.
     */
    Result<TransferType> parse(String transferType);
}
