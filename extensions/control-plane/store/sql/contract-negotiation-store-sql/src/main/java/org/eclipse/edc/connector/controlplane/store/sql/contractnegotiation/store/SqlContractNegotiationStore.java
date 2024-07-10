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

package org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.ContractNegotiationStatements;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.entity.ProtocolMessages;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.ResultSetMapper;
import org.eclipse.edc.sql.lease.SqlLeaseContextBuilder;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * SQL-based implementation of the {@link ContractNegotiationStore}
 */
public class SqlContractNegotiationStore extends AbstractSqlStore implements ContractNegotiationStore {

    private final ContractNegotiationStatements statements;
    private final SqlLeaseContextBuilder leaseContext;
    private final Clock clock;

    public SqlContractNegotiationStore(DataSourceRegistry dataSourceRegistry, String dataSourceName,
                                       TransactionContext transactionContext, ObjectMapper objectMapper,
                                       ContractNegotiationStatements statements, String connectorId, Clock clock,
                                       QueryExecutor queryExecutor) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.statements = statements;
        this.clock = clock;
        leaseContext = SqlLeaseContextBuilder.with(transactionContext, connectorId, statements, clock, queryExecutor);
    }

    @Override
    public @Nullable ContractNegotiation findById(String negotiationId) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                return findInternal(connection, negotiationId);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public @NotNull List<ContractNegotiation> nextNotLeased(int max, Criterion... criteria) {
        return transactionContext.execute(() -> {
            var filter = Arrays.stream(criteria).toList();
            var querySpec = QuerySpec.Builder.newInstance().filter(filter).sortField("stateTimestamp").limit(max).build();
            var statement = statements.createNegotiationsQuery(querySpec)
                    .addWhereClause(statements.getNotLeasedFilter(), clock.millis());

            try (
                    var connection = getConnection();
                    var stream = queryExecutor.query(getConnection(), true, contractNegotiationWithAgreementMapper(connection), statement.getQueryAsString(), statement.getParameters())
            ) {
                var negotiations = stream.collect(toList());
                negotiations.forEach(cn -> leaseContext.withConnection(connection).acquireLease(cn.getId()));
                return negotiations;
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public StoreResult<ContractNegotiation> findByIdAndLease(String id) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var entity = findInternal(connection, id);
                if (entity == null) {
                    return StoreResult.notFound(format("ContractNegotiation %s not found", id));
                }

                leaseContext.withConnection(connection).acquireLease(id);
                return StoreResult.success(entity);
            } catch (IllegalStateException e) {
                return StoreResult.alreadyLeased(format("ContractNegotiation %s is already leased", id));
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public void save(ContractNegotiation negotiation) {
        var id = negotiation.getId();
        transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var existing = findInternal(connection, id);
                if (existing == null) {
                    insert(connection, negotiation);
                } else {
                    leaseContext.withConnection(connection).breakLease(id);
                    update(connection, id, negotiation);
                }
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });

    }

    @Override
    public @Nullable ContractNegotiation findForCorrelationId(String correlationId) {
        return transactionContext.execute(() -> {
            // utilize the generic query api
            var query = correlationIdQuerySpec(correlationId);
            try (var stream = queryNegotiations(query)) {
                return single(stream.collect(toList()));
            }
        });
    }

    @Override
    public @Nullable ContractAgreement findContractAgreement(String contractId) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                return findContractAgreementInternal(connection, contractId);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public void delete(String negotiationId) {
        transactionContext.execute(() -> {
            var existing = findById(negotiationId);

            //if exists, attempt delete
            if (existing != null) {
                if (existing.getContractAgreement() != null) {
                    throw new IllegalStateException(format("Cannot delete ContractNegotiation [ID=%s] - ContractAgreement already created.", negotiationId));
                }
                try (var connection = getConnection()) {

                    // attempt to acquire lease - should fail if someone else holds the lease
                    leaseContext.withConnection(connection).acquireLease(negotiationId);

                    var stmt = statements.getDeleteTemplate();
                    queryExecutor.execute(connection, stmt, negotiationId);

                    //necessary to delete the row in edc_lease
                    leaseContext.withConnection(connection).breakLease(negotiationId);

                    // return existing;
                } catch (SQLException e) {
                    throw new EdcPersistenceException(e);
                }
            }
        });
    }

    @Override
    public @NotNull Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec) {
        return transactionContext.execute(() -> {
            try {
                return queryNegotiations(querySpec, getConnection());
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public @NotNull Stream<ContractAgreement> queryAgreements(QuerySpec querySpec) {
        return transactionContext.execute(() -> {
            try {
                var statement = statements.createAgreementsQuery(querySpec);
                return queryExecutor.query(getConnection(), true, this::mapContractAgreement, statement.getQueryAsString(), statement.getParameters());
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public StoreResult<ContractNegotiation> findByCorrelationIdAndLease(String correlationId) {
        return transactionContext.execute(() -> {
            var querySpec = correlationIdQuerySpec(correlationId);
            try (
                    var connection = getConnection();
                    var stream = queryNegotiations(querySpec, connection);
            ) {
                var entity = stream.findFirst().orElse(null);
                if (entity == null) {
                    return StoreResult.notFound(format("ContractNegotiation with correlationId %s not found", correlationId));
                }

                leaseContext.withConnection(connection).acquireLease(entity.getId());
                return StoreResult.success(entity);
            } catch (IllegalStateException e) {
                return StoreResult.alreadyLeased(format("ContractNegotiation with correlationId %s is already leased", correlationId));
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    private QuerySpec correlationIdQuerySpec(String correlationId) {
        return QuerySpec.Builder.newInstance().filter(List.of(new Criterion("correlationId", "=", correlationId))).build();
    }

    private Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec, Connection connection) {
        var statement = statements.createNegotiationsQuery(querySpec);
        return queryExecutor.query(connection, true, contractNegotiationMapper(), statement.getQueryAsString(), statement.getParameters());
    }

    private ContractAgreement findContractAgreementInternal(Connection connection, String contractId) {
        var stmt = statements.getFindContractAgreementTemplate();
        return queryExecutor.single(connection, false, this::mapContractAgreement, stmt, contractId);
    }

    private @Nullable ContractNegotiation findInternal(Connection connection, String id) {
        var sql = statements.getFindTemplate();
        return queryExecutor.single(connection, false, contractNegotiationMapper(), sql, id);
    }

    private void update(Connection connection, String negotiationId, ContractNegotiation updatedValues) {
        var stmt = statements.getUpdateNegotiationTemplate();

        if (updatedValues.getContractAgreement() != null) {
            upsertAgreement(updatedValues.getContractAgreement());
        }

        queryExecutor.execute(connection, stmt,
                updatedValues.getState(),
                updatedValues.getStateCount(),
                updatedValues.getStateTimestamp(),
                updatedValues.getErrorDetail(),
                toJson(updatedValues.getContractOffers()),
                toJson(updatedValues.getCallbackAddresses()),
                toJson(updatedValues.getTraceContext()),
                ofNullable(updatedValues.getContractAgreement()).map(ContractAgreement::getId).orElse(null),
                updatedValues.getUpdatedAt(),
                updatedValues.isPending(),
                updatedValues.getCorrelationId(),
                toJson(updatedValues.getProtocolMessages()),
                negotiationId);
    }

    private void insert(Connection connection, ContractNegotiation negotiation) {
        String agrId = null;
        var agreement = negotiation.getContractAgreement();
        if (agreement != null) {
            agrId = agreement.getId();
            upsertAgreement(agreement);
        }

        var stmt = statements.getInsertNegotiationTemplate();
        queryExecutor.execute(connection, stmt,
                negotiation.getId(),
                negotiation.getCorrelationId(),
                negotiation.getCounterPartyId(),
                negotiation.getCounterPartyAddress(),
                negotiation.getType().name(),
                negotiation.getProtocol(),
                negotiation.getState(),
                negotiation.getStateCount(),
                negotiation.getStateTimestamp(),
                negotiation.getErrorDetail(),
                agrId,
                toJson(negotiation.getContractOffers()),
                toJson(negotiation.getCallbackAddresses()),
                toJson(negotiation.getTraceContext()),
                negotiation.getCreatedAt(),
                negotiation.getUpdatedAt(),
                negotiation.isPending(),
                toJson(negotiation.getProtocolMessages()));
    }

    private void upsertAgreement(ContractAgreement contractAgreement) {
        transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var agrId = contractAgreement.getId();

                if (findContractAgreement(agrId) == null) {
                    // insert agreement
                    var sql = statements.getInsertAgreementTemplate();
                    queryExecutor.execute(connection, sql, contractAgreement.getId(),
                            contractAgreement.getProviderId(),
                            contractAgreement.getConsumerId(),
                            contractAgreement.getContractSigningDate(),
                            contractAgreement.getAssetId(),
                            toJson(contractAgreement.getPolicy())
                    );
                } else {
                    // update agreement
                    var query = statements.getUpdateAgreementTemplate();
                    queryExecutor.execute(connection, query, contractAgreement.getProviderId(),
                            contractAgreement.getConsumerId(),
                            contractAgreement.getContractSigningDate(),
                            contractAgreement.getAssetId(),
                            toJson(contractAgreement.getPolicy()),
                            agrId);
                }

            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });

    }

    @Nullable
    private <T> T single(List<T> list) {
        if (list.size() > 1) {
            throw new IllegalStateException(getMultiplicityError(1, list.size()));
        }

        return list.isEmpty() ? null : list.get(0);
    }

    private ContractAgreement mapContractAgreement(ResultSet resultSet) throws SQLException {
        return ContractAgreement.Builder.newInstance()
                .id(resultSet.getString(statements.getContractAgreementIdColumn()))
                .providerId(resultSet.getString(statements.getProviderAgentColumn()))
                .consumerId(resultSet.getString(statements.getConsumerAgentColumn()))
                .assetId(resultSet.getString(statements.getAssetIdColumn()))
                .contractSigningDate(resultSet.getLong(statements.getSigningDateColumn()))
                .policy(fromJson(resultSet.getString(statements.getPolicyColumn()), Policy.class))
                .build();
    }

    private String getMultiplicityError(int expectedSize, int actualSize) {
        return format("Expected to find %d items, but found %d", expectedSize, actualSize);
    }

    private ResultSetMapper<ContractNegotiation> contractNegotiationMapper() {
        return resultSet -> mapContractNegotiation(resultSet, this::extractContractAgreement);
    }

    private ResultSetMapper<ContractNegotiation> contractNegotiationWithAgreementMapper(Connection connection) {
        return (resultSet -> mapContractNegotiation(resultSet, rs -> {
            var agreementId = rs.getString(statements.getContractAgreementIdFkColumn());
            if (agreementId == null) {
                return null;
            } else {
                return findContractAgreementInternal(connection, agreementId);
            }
        }));
    }

    private ContractNegotiation mapContractNegotiation(ResultSet resultSet, ResultSetMapper<ContractAgreement> agreementMapper) throws Exception {
        return ContractNegotiation.Builder.newInstance()
                .id(resultSet.getString(statements.getIdColumn()))
                .counterPartyId(resultSet.getString(statements.getCounterPartyIdColumn()))
                .counterPartyAddress(resultSet.getString(statements.getCounterPartyAddressColumn()))
                .protocol(resultSet.getString(statements.getProtocolColumn()))
                .correlationId(resultSet.getString(statements.getCorrelationIdColumn()))
                .contractAgreement(agreementMapper.mapResultSet(resultSet))
                .state(resultSet.getInt(statements.getStateColumn()))
                .stateCount(resultSet.getInt(statements.getStateCountColumn()))
                .stateTimestamp(resultSet.getLong(statements.getStateTimestampColumn()))
                .contractOffers(fromJson(resultSet.getString(statements.getContractOffersColumn()), new TypeReference<>() {
                }))
                .callbackAddresses(fromJson(resultSet.getString(statements.getCallbackAddressesColumn()), new TypeReference<>() {
                }))
                .errorDetail(resultSet.getString(statements.getErrorDetailColumn()))
                .traceContext(fromJson(resultSet.getString(statements.getTraceContextColumn()), new TypeReference<>() {
                }))
                // will throw an exception if the value is outside the Type.values() range
                .type(ContractNegotiation.Type.valueOf(resultSet.getString(statements.getTypeColumn())))
                .createdAt(resultSet.getLong(statements.getCreatedAtColumn()))
                .updatedAt(resultSet.getLong(statements.getUpdatedAtColumn()))
                .pending(resultSet.getBoolean(statements.getPendingColumn()))
                .protocolMessages(fromJson(resultSet.getString(statements.getProtocolMessagesColumn()), ProtocolMessages.class))
                .build();
    }

    private ContractAgreement extractContractAgreement(ResultSet resultSet) throws SQLException {
        return resultSet.getString(statements.getContractAgreementIdFkColumn()) == null ? null : mapContractAgreement(resultSet);
    }

}
