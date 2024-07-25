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

package org.eclipse.edc.connector.controlplane.services.spi.contractagreement;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;

import java.util.List;

/**
 * Service that permits actions and queries on ContractAgreement entity.
 */
public interface ContractAgreementService {

    /**
     * Returns a contract agreement by its id
     *
     * @param contractAgreementId id of the contract agreement
     * @return the contract agreement, null if it's not found
     */
    ContractAgreement findById(String contractAgreementId);

    /**
     * Search contract agreements
     *
     * @param query request
     * @return the collection of contract agreements that match the query
     */
    ServiceResult<List<ContractAgreement>> search(QuerySpec query);

    /**
     * Returns a contract negotiation by the agreement id.
     *
     * @param contractAgreementId the contract agreement id.
     * @return the contract negotiation, null if it's not found.
     */
    ContractNegotiation findNegotiation(String contractAgreementId);

}
