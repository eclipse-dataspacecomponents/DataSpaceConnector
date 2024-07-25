/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
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

package org.eclipse.edc.connector.controlplane.defaults.storage.contractnegotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.store.InMemoryStatefulEntityStore;
import org.eclipse.edc.store.ReflectionBasedQueryResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * An in-memory, threadsafe process store. This implementation is intended for testing purposes only.
 */
public class InMemoryContractNegotiationStore extends InMemoryStatefulEntityStore<ContractNegotiation> implements ContractNegotiationStore {

    private final QueryResolver<ContractNegotiation> negotiationQueryResolver;
    private final QueryResolver<ContractAgreement> agreementQueryResolver;

    public InMemoryContractNegotiationStore(Clock clock, CriterionOperatorRegistry criterionOperatorRegistry) {
        this(UUID.randomUUID().toString(), clock, criterionOperatorRegistry);
    }

    public InMemoryContractNegotiationStore(String leaseHolder, Clock clock, CriterionOperatorRegistry criterionOperatorRegistry) {
        super(ContractNegotiation.class, leaseHolder, clock, criterionOperatorRegistry);
        agreementQueryResolver = new ReflectionBasedQueryResolver<>(ContractAgreement.class, criterionOperatorRegistry);
        negotiationQueryResolver = new ReflectionBasedQueryResolver<>(ContractNegotiation.class, criterionOperatorRegistry);
    }

    @Override
    public @Nullable ContractNegotiation findForCorrelationId(String correlationId) {
        return super.findAll().filter(p -> correlationId.equals(p.getCorrelationId())).findFirst().orElse(null);
    }

    @Override
    public @Nullable ContractAgreement findContractAgreement(String contractId) {
        return super.findAll()
                .map(ContractNegotiation::getContractAgreement)
                .filter(Objects::nonNull)
                .filter(a -> Objects.equals(contractId, a.getId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void delete(String negotiationId) {
        var negotiation = findById(negotiationId);
        if (negotiation != null && negotiation.getContractAgreement() != null) {
            throw new IllegalStateException(format("Cannot delete ContractNegotiation [%s]: ContractAgreement already created.", negotiationId));
        }
        super.delete(negotiationId);
    }

    @Override
    public @NotNull Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec) {
        return negotiationQueryResolver.query(super.findAll(), querySpec);
    }

    @Override
    public @NotNull Stream<ContractAgreement> queryAgreements(QuerySpec querySpec) {
        return agreementQueryResolver.query(getAgreements(), querySpec);
    }

    @NotNull
    private Stream<ContractAgreement> getAgreements() {
        return super.findAll()
                .map(ContractNegotiation::getContractAgreement)
                .filter(Objects::nonNull);
    }

}
