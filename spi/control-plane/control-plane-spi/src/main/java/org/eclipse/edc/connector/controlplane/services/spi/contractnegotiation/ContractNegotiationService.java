/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.command.TerminateNegotiationCommand;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;

import java.util.List;

public interface ContractNegotiationService {

    /**
     * Returns a contract negotiation by its id
     *
     * @param contractNegotiationId id of the contract negotiation
     * @return the contract negotiation, null if it's not found
     */
    ContractNegotiation findbyId(String contractNegotiationId);

    /**
     * Search contract negotiations
     *
     * @param query request
     * @return the collection of contract negotiations that match the query
     */
    ServiceResult<List<ContractNegotiation>> search(QuerySpec query);

    /**
     * Get negotiation state
     *
     * @param negotiationId the id of contract negotiation
     * @return the state of the contract negotiation, null if it does not exist
     */
    String getState(String negotiationId);

    /**
     * Fetches the {@linkplain ContractAgreement} for a given {@linkplain ContractNegotiation}, or null if either the
     * negotiation does not exist, or no agreement has yet been reached.
     *
     * @param negotiationId the id of contract negotiation
     * @return the contract agreement, null if the negotiation does not exist or no agreement attached.
     */
    ContractAgreement getForNegotiation(String negotiationId);

    /**
     * Initiate contract negotiation
     *
     * @param request the contract offer request
     * @return the contract negotiation initiated
     */
    ContractNegotiation initiateNegotiation(ContractRequest request);

    /**
     * Terminate a contract negotiation
     *
     * @param command the termination command.
     * @return successful result if the contract negotiation is terminated correctly, failure otherwise
     */
    ServiceResult<Void> terminate(TerminateNegotiationCommand command);
}
