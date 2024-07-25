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

package org.eclipse.edc.connector.controlplane.transfer.spi.store;

import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.persistence.StateEntityStore;
import org.eclipse.edc.spi.query.QuerySpec;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Manages persistent storage of {@link TransferProcess} state.
 */
@ExtensionPoint
public interface TransferProcessStore extends StateEntityStore<TransferProcess> {

    /**
     * Returns the transfer process for the correlation id or null if not found.
     */
    @Nullable
    TransferProcess findForCorrelationId(String correlationId);

    /**
     * Deletes a transfer process.
     */
    void delete(String processId);

    /**
     * Returns all the transfer processes in the store that are covered by a given {@link QuerySpec}.
     * <p>
     * Note: supplying a sort field that does not exist on the {@link TransferProcess} may cause some implementations
     * to return an empty Stream, others will return an unsorted Stream, depending on the backing storage implementation.
     */
    Stream<TransferProcess> findAll(QuerySpec querySpec);

}
