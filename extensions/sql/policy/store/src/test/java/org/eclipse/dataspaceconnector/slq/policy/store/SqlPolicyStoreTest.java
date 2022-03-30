/*
 *  Copyright (c) 2022 ZF Friedrichshafen AG
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       ZF Friedrichshafen AG - Initial API and Implementation
 *
 */

package org.eclipse.dataspaceconnector.slq.policy.store;

import org.eclipse.dataspaceconnector.policy.model.Duty;
import org.eclipse.dataspaceconnector.policy.model.Permission;
import org.eclipse.dataspaceconnector.policy.model.Policy;
import org.eclipse.dataspaceconnector.policy.model.Prohibition;
import org.eclipse.dataspaceconnector.policy.model.PolicyType;
import org.eclipse.dataspaceconnector.spi.monitor.ConsoleMonitor;
import org.eclipse.dataspaceconnector.spi.persistence.EdcPersistenceException;
import org.eclipse.dataspaceconnector.spi.query.QuerySpec;
import org.eclipse.dataspaceconnector.spi.transaction.TransactionContext;
import org.eclipse.dataspaceconnector.spi.transaction.datasource.DataSourceRegistry;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.sql.SqlQueryExecutor;
import org.eclipse.dataspaceconnector.sql.datasource.ConnectionFactoryDataSource;
import org.eclipse.dataspaceconnector.sql.datasource.ConnectionPoolDataSource;
import org.eclipse.dataspaceconnector.sql.policy.store.PostgressStatements;
import org.eclipse.dataspaceconnector.sql.policy.store.SqlPolicyStore;
import org.eclipse.dataspaceconnector.sql.pool.ConnectionPool;
import org.eclipse.dataspaceconnector.sql.pool.commons.CommonsConnectionPool;
import org.eclipse.dataspaceconnector.sql.pool.commons.CommonsConnectionPoolConfig;
import org.eclipse.dataspaceconnector.transaction.local.DataSourceResource;
import org.eclipse.dataspaceconnector.transaction.local.LocalDataSourceRegistry;
import org.eclipse.dataspaceconnector.transaction.local.LocalTransactionContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlPolicyStoreTest {

    private static final String DATASOURCE_NAME = "policy";

    private SqlPolicyStore sqlPolicyStore;
    private ConnectionPool connectionPool;

    @BeforeEach
    void setUp() throws SQLException {
        var monitor = new ConsoleMonitor();
        var txManager = new LocalTransactionContext(monitor);
        DataSourceRegistry dataSourceRegistry;
        dataSourceRegistry = new LocalDataSourceRegistry(txManager);
        var transactionContext = (TransactionContext) txManager;
        var jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:");

        var connection = jdbcDataSource.getConnection();
        var dataSource = new ConnectionFactoryDataSource(() -> connection);
        connectionPool = new CommonsConnectionPool(dataSource, CommonsConnectionPoolConfig.Builder.newInstance().build());
        var poolDataSource = new ConnectionPoolDataSource(connectionPool);
        dataSourceRegistry.register(DATASOURCE_NAME, poolDataSource);
        txManager.registerResource(new DataSourceResource(poolDataSource));
        sqlPolicyStore = new SqlPolicyStore(dataSourceRegistry, DATASOURCE_NAME, transactionContext, new TypeManager(), new PostgressStatements());

        try (var inputStream = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            var schema = new String(Objects.requireNonNull(inputStream).readAllBytes(), StandardCharsets.UTF_8);
            transactionContext.execute(() -> SqlQueryExecutor.executeQuery(connection, schema));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        connectionPool.close();
    }

    @Test
    @DisplayName("Save a single policy that not exists")
    void save_notExisting() {
        var policy = getDummyPolicy(getRandomId());

        sqlPolicyStore.save(policy);

        var policyFromDb = sqlPolicyStore.findById(policy.getUid());
        assertThat(policyFromDb).isNotNull();
        assertThat(policy.getUid()).isEqualTo(policyFromDb.getUid());
    }

    @Test
    @DisplayName("Save a single a single policy that already exists")
    void save_alreadyExists() {
        var id = getRandomId();
        var policy = getDummyPolicy(id);

        sqlPolicyStore.save(policy);

        assertThatThrownBy(() -> sqlPolicyStore.save(policy)).isInstanceOf(EdcPersistenceException.class);
    }

    @Test
    @DisplayName("Find policy by ID that exists")
    void findById_whenPresent() {
        Policy policy =  getDummyPolicy(getRandomId());
        sqlPolicyStore.save(policy);

        var policyFromDb = sqlPolicyStore.findById(policy.getUid());

        assertThat(policy.getUid()).isEqualTo(policyFromDb.getUid());
    }

    @Test
    @DisplayName("Find policy by ID when not exists")
    void findById_whenNonexistent() {
        assertThat(sqlPolicyStore.findById("nonexistent")).isNull();
    }

    @Test
    @DisplayName("Find all policies with limit and offset")
    void findAll_withSpec() {
        var limit = 20;

        var definitionsExpected = getDummyPolicies(50);
        definitionsExpected.forEach(sqlPolicyStore::save);

        var spec = QuerySpec.Builder.newInstance()
                .limit(limit)
                .offset(20)
                .build();

        var policiesFromDB = sqlPolicyStore.findAll(spec).collect(Collectors.toList());

        assertThat(policiesFromDB).hasSize(limit);
    }

    @Test
    @DisplayName("Delete existing policy")
    void deleteById_whenExists() {
        var policy = getDummyPolicy(getRandomId());

        sqlPolicyStore.save(policy);
        assertThat(sqlPolicyStore.findById(policy.getUid()).getUid()).isEqualTo(policy.getUid());

        assertThat(sqlPolicyStore.delete(policy.getUid()).getUid()).isEqualTo(policy.getUid());
        assertThat(sqlPolicyStore.findById(policy.getUid())).isNull();
    }

    @Test
    @DisplayName("Delete a non existing policy")
    void deleteById_whenNonexistent() {
        assertThat(sqlPolicyStore.delete("nonexistent")).isNull();
    }

    private String getRandomId() {
        return UUID.randomUUID().toString();
    }

    private Policy getDummyPolicy(String id) {
        var permission = Permission.Builder.newInstance()
                .uid(id)
                .build();

        var prohibition = Prohibition.Builder.newInstance()
                .uid(id)
                .build();

        var duty = Duty.Builder.newInstance()
                .uid(id)
                .build();

        return Policy.Builder.newInstance()
                .id(id)
                .permission(permission)
                .prohibition(prohibition)
                .duties(List.of(duty))
                .inheritsFrom("sampleInheritsFrom")
                .assigner("sampleAssigner")
                .assignee("sampleAssignee")
                .target("sampleTarget")
                .type(PolicyType.SET)
                .build();
    }

    private ArrayList<Policy> getDummyPolicies(int count) {
        var policies = new ArrayList<Policy>();

        for (int i = 0; i < count; i++) {
            policies.add(getDummyPolicy(getRandomId()));
        }

        return policies;
    }

}
