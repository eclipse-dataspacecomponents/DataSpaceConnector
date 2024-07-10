/*
 *  Copyright (c) 2022 Microsoft Corporation
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

package org.eclipse.edc.connector.dataplane.store.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.store.DataPlaneStore;
import org.eclipse.edc.connector.dataplane.store.sql.schema.DataPlaneStatements;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.lease.SqlLeaseContextBuilder;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.eclipse.edc.spi.query.Criterion.criterion;

/**
 * SQL implementation of {@link DataPlaneStore}
 */
public class SqlDataPlaneStore extends AbstractSqlStore implements DataPlaneStore {

    private final DataPlaneStatements statements;
    private final SqlLeaseContextBuilder leaseContext;
    private final Clock clock;
    private final String leaseHolderName;

    public SqlDataPlaneStore(DataSourceRegistry dataSourceRegistry, String dataSourceName, TransactionContext transactionContext,
                             DataPlaneStatements statements, ObjectMapper objectMapper, Clock clock, QueryExecutor queryExecutor,
                             String leaseHolderName) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.statements = statements;
        this.clock = clock;
        this.leaseHolderName = leaseHolderName;
        leaseContext = SqlLeaseContextBuilder.with(transactionContext, leaseHolderName, statements, clock, queryExecutor);
    }

    @Override
    public @Nullable DataFlow findById(String id) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                return findByIdInternal(connection, id);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public @NotNull List<DataFlow> nextNotLeased(int max, Criterion... criteria) {
        return transactionContext.execute(() -> {
            var filter = Arrays.stream(criteria).collect(toList());
            var querySpec = QuerySpec.Builder.newInstance().filter(filter).sortField("stateTimestamp").limit(max).build();
            var statement = statements.createQuery(querySpec)
                    .addWhereClause(statements.getNotLeasedFilter(), clock.millis());

            try (
                    var connection = getConnection();
                    var stream = queryExecutor.query(connection, true, this::mapDataFlow, statement.getQueryAsString(), statement.getParameters())
            ) {
                var entries = stream.collect(Collectors.toList());
                entries.forEach(entry -> leaseContext.withConnection(connection).acquireLease(entry.getId()));
                return entries;
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public StoreResult<DataFlow> findByIdAndLease(String id) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var entity = findByIdInternal(connection, id);
                if (entity == null) {
                    return StoreResult.notFound(format("DataFlow %s not found", id));
                }

                leaseContext.withConnection(connection).acquireLease(entity.getId());
                return StoreResult.success(entity);
            } catch (IllegalStateException e) {
                return StoreResult.alreadyLeased(format("DataFlow %s is already leased", id));
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    @Override
    public void save(DataFlow entity) {
        transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var existing = findByIdInternal(connection, entity.getId());
                if (existing != null) {
                    leaseContext.by(leaseHolderName).withConnection(connection).breakLease(entity.getId());
                    update(connection, entity);
                } else {
                    insert(connection, entity);
                }
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    private void insert(Connection connection, DataFlow dataFlow) {
        var sql = statements.getInsertTemplate();
        queryExecutor.execute(connection, sql,
                dataFlow.getId(),
                dataFlow.getState(),
                dataFlow.getCreatedAt(),
                dataFlow.getUpdatedAt(),
                dataFlow.getStateCount(),
                dataFlow.getStateTimestamp(),
                toJson(dataFlow.getTraceContext()),
                dataFlow.getErrorDetail(),
                Optional.ofNullable(dataFlow.getCallbackAddress()).map(URI::toString).orElse(null),
                toJson(dataFlow.getSource()),
                toJson(dataFlow.getDestination()),
                toJson(dataFlow.getProperties()),
                dataFlow.getTransferType().flowType().toString(),
                dataFlow.getTransferType().destinationType()
        );
    }

    private void update(Connection connection, DataFlow dataFlow) {
        var sql = statements.getUpdateTemplate();
        queryExecutor.execute(connection, sql,
                dataFlow.getState(),
                dataFlow.getUpdatedAt(),
                dataFlow.getStateCount(),
                dataFlow.getStateTimestamp(),
                toJson(dataFlow.getTraceContext()),
                dataFlow.getErrorDetail(),
                Optional.ofNullable(dataFlow.getCallbackAddress()).map(URI::toString).orElse(null),
                toJson(dataFlow.getSource()),
                toJson(dataFlow.getDestination()),
                toJson(dataFlow.getProperties()),
                dataFlow.getTransferType().flowType().toString(),
                dataFlow.getTransferType().destinationType(),
                dataFlow.getId());
    }

    private DataFlow mapDataFlow(ResultSet resultSet) throws SQLException {
        return DataFlow.Builder.newInstance()
                .id(resultSet.getString(statements.getIdColumn()))
                .createdAt(resultSet.getLong(statements.getCreatedAtColumn()))
                .updatedAt(resultSet.getLong(statements.getUpdatedAtColumn()))
                .state(resultSet.getInt(statements.getStateColumn()))
                .stateTimestamp(resultSet.getLong(statements.getStateTimestampColumn()))
                .stateCount(resultSet.getInt(statements.getStateCountColumn()))
                .traceContext(fromJson(resultSet.getString(statements.getTraceContextColumn()), getTypeRef()))
                .errorDetail(resultSet.getString(statements.getErrorDetailColumn()))
                .callbackAddress(Optional.ofNullable(resultSet.getString(statements.getCallbackAddressColumn())).map(URI::create).orElse(null))
                .source(fromJson(resultSet.getString(statements.getSourceColumn()), DataAddress.class))
                .destination(fromJson(resultSet.getString(statements.getDestinationColumn()), DataAddress.class))
                .properties(fromJson(resultSet.getString(statements.getPropertiesColumn()), getTypeRef()))
                .transferType(new TransferType(
                        resultSet.getString(statements.getTransferTypeDestinationColumn()),
                        FlowType.valueOf(resultSet.getString(statements.getFlowTypeColumn()))
                ))
                .build();
    }

    private @Nullable DataFlow findByIdInternal(Connection conn, String id) {
        return transactionContext.execute(() -> {
            var querySpec = QuerySpec.Builder.newInstance().filter(criterion("id", "=", id)).build();
            var statement = statements.createQuery(querySpec);
            return queryExecutor.query(conn, true, this::mapDataFlow, statement.getQueryAsString(), statement.getParameters())
                    .findFirst().orElse(null);
        });
    }

}
