/*
 *  Copyright (c) 2022 - 2023 ZF Friedrichshafen AG
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       ZF Friedrichshafen AG - Initial API and Implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *       SAP SE - add private properties to contract definition
 *
 */

package org.eclipse.edc.connector.controlplane.store.sql.policydefinition.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.controlplane.store.sql.policydefinition.store.schema.SqlPolicyStoreStatements;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.policy.model.Prohibition;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.lang.String.format;

public class SqlPolicyDefinitionStore extends AbstractSqlStore implements PolicyDefinitionStore {

    private final SqlPolicyStoreStatements statements;
    private final TypeReference<List<Permission>> permissionListType = new TypeReference<>() {
    };
    private final TypeReference<List<Prohibition>> prohibitionListType = new TypeReference<>() {
    };
    private final TypeReference<List<Duty>> dutyListType = new TypeReference<>() {
    };
    private final TypeReference<List<String>> profileListType = new TypeReference<>() {
    };
    private final TypeReference<PolicyType> policyType = new TypeReference<>() {
    };
    private final TypeReference<Map<String, Object>> extensiblePropertiesType = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, Object>> PRIVATE_PROPERTIES_TYPE = new TypeReference<>() {
    };

    public SqlPolicyDefinitionStore(DataSourceRegistry dataSourceRegistry, String dataSourceName, TransactionContext transactionContext,
                                    ObjectMapper objectMapper, SqlPolicyStoreStatements sqlPolicyStoreStatements, QueryExecutor queryExecutor) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        statements = Objects.requireNonNull(sqlPolicyStoreStatements);
    }

    @Override
    public PolicyDefinition findById(String id) {
        return transactionContext.execute(() -> {
            var query = QuerySpec.Builder.newInstance().filter(List.of(new Criterion("id", "=", id))).build();
            try {
                var queryStatement = statements.createQuery(query);
                return queryExecutor.single(getConnection(), true, this::mapResultSet, queryStatement.getQueryAsString(), queryStatement.getParameters());
            } catch (SQLException exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public Stream<PolicyDefinition> findAll(QuerySpec querySpec) {
        Objects.requireNonNull(querySpec);

        return transactionContext.execute(() -> {
            try {
                var queryStatement = statements.createQuery(querySpec);
                return queryExecutor.query(getConnection(), true, this::mapResultSet, queryStatement.getQueryAsString(), queryStatement.getParameters());
            } catch (SQLException exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public StoreResult<PolicyDefinition> create(PolicyDefinition policy) {
        Objects.requireNonNull(policy);
        var policyId = policy.getId();
        return transactionContext.execute(() -> {
            if (findById(policyId) != null) {
                return StoreResult.alreadyExists(format(POLICY_ALREADY_EXISTS, policyId));
            } else {
                insert(policy);
                return StoreResult.success(policy);
            }
        });
    }

    @Override
    public StoreResult<PolicyDefinition> update(PolicyDefinition policyDefinition) {
        var policyId = policyDefinition.getId();
        if (findById(policyId) != null) {
            return transactionContext.execute(() -> {
                updateInternal(policyDefinition);
                return StoreResult.success(policyDefinition);
            });
        }
        return StoreResult.notFound(format(POLICY_NOT_FOUND, policyId));
    }

    @Override
    public StoreResult<PolicyDefinition> delete(String policyId) {
        Objects.requireNonNull(policyId);
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var entity = findById(policyId);
                if (entity != null) {
                    queryExecutor.execute(connection, statements.getDeleteTemplate(), policyId);
                    return StoreResult.success(entity);
                }
                return StoreResult.notFound(format(POLICY_NOT_FOUND, policyId));
            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    private void insert(PolicyDefinition def) {
        transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var policy = def.getPolicy();
                var id = def.getId();
                queryExecutor.execute(connection, statements.getInsertTemplate(),
                        id,
                        toJson(policy.getPermissions(), permissionListType),
                        toJson(policy.getProhibitions(), prohibitionListType),
                        toJson(policy.getObligations(), dutyListType),
                        toJson(policy.getProfiles(), profileListType),
                        toJson(policy.getExtensibleProperties()),
                        policy.getInheritsFrom(),
                        policy.getAssigner(),
                        policy.getAssignee(),
                        policy.getTarget(),
                        toJson(policy.getType(), policyType),
                        def.getCreatedAt(),
                        toJson(def.getPrivateProperties()));
            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    private void updateInternal(PolicyDefinition def) {
        transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var policy = def.getPolicy();
                var id = def.getId();
                queryExecutor.execute(connection, statements.getUpdateTemplate(),
                        toJson(policy.getPermissions(), permissionListType),
                        toJson(policy.getProhibitions(), prohibitionListType),
                        toJson(policy.getObligations(), dutyListType),
                        toJson(policy.getProfiles(), profileListType),
                        toJson(policy.getExtensibleProperties()),
                        policy.getInheritsFrom(),
                        policy.getAssigner(),
                        policy.getAssignee(),
                        policy.getTarget(),
                        toJson(policy.getType(), policyType),
                        toJson(def.getPrivateProperties()),
                        id);
            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    private PolicyDefinition mapResultSet(ResultSet resultSet) throws SQLException {
        var policy = Policy.Builder.newInstance()
                .permissions(fromJson(resultSet.getString(statements.getPermissionsColumn()), permissionListType))
                .prohibitions(fromJson(resultSet.getString(statements.getProhibitionsColumn()), prohibitionListType))
                .duties(fromJson(resultSet.getString(statements.getDutiesColumn()), dutyListType))
                .profiles(fromJson(resultSet.getString(statements.getProfilesColumn()), profileListType))
                .extensibleProperties(fromJson(resultSet.getString(statements.getExtensiblePropertiesColumn()), extensiblePropertiesType))
                .inheritsFrom(resultSet.getString(statements.getInheritsFromColumn()))
                .assigner(resultSet.getString(statements.getAssignerColumn()))
                .assignee(resultSet.getString(statements.getAssigneeColumn()))
                .target(resultSet.getString(statements.getTargetColumn()))
                .type(fromJson(resultSet.getString(statements.getTypeColumn()), policyType))
                .build();

        return PolicyDefinition.Builder.newInstance()
                .id(resultSet.getString(statements.getPolicyIdColumn()))
                .policy(policy)
                .createdAt(resultSet.getLong(statements.getCreatedAtColumn()))
                .privateProperties(fromJson(resultSet.getString(statements.getPrivatePropertiesColumn()), PRIVATE_PROPERTIES_TYPE))
                .build();
    }

}

