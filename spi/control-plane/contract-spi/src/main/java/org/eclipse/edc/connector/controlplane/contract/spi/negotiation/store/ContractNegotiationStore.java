/*
 *  Copyright (c) 2021 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - add functionalities
 *
 */

package org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.persistence.StateEntityStore;
import org.eclipse.edc.spi.query.QuerySpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Stores {@link ContractNegotiation}s and their associated types such as {@link ContractAgreement}s.
 * <p>
 */
@ExtensionPoint
public interface ContractNegotiationStore extends StateEntityStore<ContractNegotiation> {

    /**
     * Returns the contract negotiation for the correlation id provided by the consumer or null.
     *
     * @deprecated will be removed in the next versions.
     */
    @Deprecated(since = "0.8.0")
    @Nullable
    ContractNegotiation findForCorrelationId(String correlationId);

    /**
     * Returns the contract agreement for the contract id or null.
     */
    @Nullable
    ContractAgreement findContractAgreement(String contractId);

    /**
     * Removes a contract negotiation for the given id.
     */
    void delete(String negotiationId);

    /**
     * Finds all contract negotiations that are covered by a specific {@link QuerySpec}. If no
     * {@link QuerySpec#getSortField()} is specified, results are not explicitly sorted.
     * <p>
     * The general order of precedence of the query parameters is:
     * <pre>
     * filter &gt; sort &gt; limit
     * </pre>
     * <p>
     *
     * @param querySpec The query spec, e.g. paging, filtering, etc.
     * @return a stream of ContractNegotiation, cannot be null.
     */
    @NotNull
    Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec);


    /**
     * Finds all contract agreement that are covered by a specific {@link QuerySpec}. If no
     * {@link QuerySpec#getSortField()} is specified, results are not explicitly sorted.
     *
     * @param querySpec The query spec, e.g. paging, filtering, etc.
     * @return a stream of ContractAgreement, cannot be null.
     */
    @NotNull
    Stream<ContractAgreement> queryAgreements(QuerySpec querySpec);

}
