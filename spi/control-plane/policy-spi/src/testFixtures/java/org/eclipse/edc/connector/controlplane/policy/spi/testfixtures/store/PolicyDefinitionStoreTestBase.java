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
 *       Microsoft Corporation - updates, refactoring
 *
 */

package org.eclipse.edc.connector.controlplane.policy.spi.testfixtures.store;

import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.controlplane.policy.spi.testfixtures.TestFunctions;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.Prohibition;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.spi.query.Criterion.criterion;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.ALREADY_EXISTS;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.NOT_FOUND;


public abstract class PolicyDefinitionStoreTestBase {

    @Nested
    class Create {

        @Test
        @DisplayName("Save a single policy that does not exist")
        void notExisting() {
            var policy = TestFunctions.createPolicy(getRandomId());

            getPolicyDefinitionStore().create(policy);

            var policyFromDb = getPolicyDefinitionStore().findById(policy.getId());
            assertThat(policy).usingRecursiveComparison().isEqualTo(policyFromDb);
        }

        @Test
        @DisplayName("Save (update) a single policy that already exists")
        void alreadyExists() {
            var id = getRandomId();
            var policy1 = PolicyDefinition.Builder.newInstance()
                    .policy(Policy.Builder.newInstance()
                            .target("Target1")
                            .build())
                    .id(id)
                    .build();
            var policy2 = PolicyDefinition.Builder.newInstance()
                    .policy(Policy.Builder.newInstance()
                            .target("Target2")
                            .build())
                    .id(id)
                    .build();
            var spec = QuerySpec.Builder.newInstance().build();

            var store = getPolicyDefinitionStore();
            store.create(policy1);

            var result = store.create(policy2);
            assertThat(result.succeeded()).isFalse();
            assertThat(result.reason()).isEqualTo(ALREADY_EXISTS);

            var policyFromDb = store.findAll(spec);

            assertThat(policyFromDb).hasSize(1).first()
                    .satisfies(policy -> assertThat(policy.getPolicy().getTarget()).isEqualTo("Target1"))
                    .extracting(PolicyDefinition::getCreatedAt).isEqualTo(policy1.getCreatedAt());
        }

        @Test
        @DisplayName("Save a single policy that does not exist")
        void notExisting_with_privateProperties() {
            var policy = TestFunctions.createPolicy(getRandomId(), null, Map.of("key1", "value1", "key2", "value2"));

            getPolicyDefinitionStore().create(policy);

            var policyFromDb = getPolicyDefinitionStore().findById(policy.getId());
            assertThat(policy).usingRecursiveComparison().isEqualTo(policyFromDb);

            assertThat(policyFromDb.getPrivateProperties()).hasSize(2);
            assertThat(policyFromDb.getPrivateProperties().get("key1")).usingRecursiveComparison().isEqualTo("value1");
            assertThat(policyFromDb.getPrivateProperties().get("key2")).usingRecursiveComparison().isEqualTo("value2");
        }

        @Test
        @DisplayName("Save a single policy that does not exist")
        void notExisting_with_Profile() {
            var policy = TestFunctions.createPolicy(getRandomId(), null, List.of("value1"));

            getPolicyDefinitionStore().create(policy);

            var policyFromDb = getPolicyDefinitionStore().findById(policy.getId());
            assertThat(policy).usingRecursiveComparison().isEqualTo(policyFromDb);

            assertThat(policyFromDb.getPolicy().getProfiles()).hasSize(1);
            assertThat(policyFromDb.getPolicy().getProfiles().get(0)).isEqualTo("value1");
        }

    }

    @Nested
    class Update {
        @Test
        @DisplayName("Update Policy that does not yet exist")
        void policyDoesNotExist() {
            var id = getRandomId();
            var policy = createPolicyDef(id, "target");

            var updated = getPolicyDefinitionStore().update(policy);
            assertThat(updated).isNotNull().extracting(StoreResult::succeeded).isEqualTo(false);
            assertThat(updated.reason()).isEqualTo(NOT_FOUND);
        }

        @Test
        @DisplayName("Update a Policy that exists, adding a property")
        void policyExists() {
            var id = getRandomId();
            var policy = createPolicyDef(id, "target");

            var store = getPolicyDefinitionStore();
            store.create(policy);

            var newPolicy = TestFunctions.createPolicy(id, "target2");
            var result = store.update(newPolicy);

            assertThat(result.succeeded()).isTrue();

            var spec = QuerySpec.Builder.newInstance().build();
            var policyFromDb = store.findAll(spec);

            assertThat(policyFromDb).hasSize(1).first();
            assertThat(result.getContent().getPolicy().getTarget()).isEqualTo("target2");
            assertThat(result.getContent()).usingRecursiveComparison().isEqualTo(newPolicy);
        }

        @Test
        @DisplayName("Update a Policy that exists, adding a property")
        void policyExists_addProperty() {
            var id = getRandomId();
            var policy = createPolicyDef(id, "target");

            var store = getPolicyDefinitionStore();
            store.create(policy);

            var spec = QuerySpec.Builder.newInstance().build();
            var policyFromDb = store.findAll(spec);

            assertThat(policyFromDb).hasSize(1).first();
            policy.getPrivateProperties().put("newKey", "newValue");
            var updated = getPolicyDefinitionStore().update(policy);
            assertThat(updated).isNotNull();

            var definitionFound = getPolicyDefinitionStore().findById(id);

            assertThat(definitionFound).isNotNull();
            assertThat(definitionFound).usingRecursiveComparison().isEqualTo(policy);

        }

        @Test
        @DisplayName("Update a Policy that exists, removing a property")
        void policyExists_removeProperty() {
            var id = getRandomId();
            var policy = createPolicyDef(id, "target");
            policy.getPrivateProperties().put("newKey", "newValue");
            var created = getPolicyDefinitionStore().create(policy);
            assertThat(created).isNotNull();

            var definitionFound = getPolicyDefinitionStore().findById(id);

            assertThat(definitionFound).isNotNull();
            assertThat(definitionFound).usingRecursiveComparison().isEqualTo(policy);

            policy.getPrivateProperties().remove("newKey");
            var updated = getPolicyDefinitionStore().update(policy);
            definitionFound = getPolicyDefinitionStore().findById(id);
            assertThat(updated).isNotNull();
            assertThat(definitionFound).isNotNull();
            assertThat(definitionFound).usingRecursiveComparison().isEqualTo(policy);
            assertThat(definitionFound.getPrivateProperties()).doesNotContainKey("newKey");
        }

        @Test
        @DisplayName("Update a Policy that exists, replacing a property")
        void policyExists_replaceProperty() {
            var id = getRandomId();
            var policy = createPolicyDef(id, "target");
            policy.getPrivateProperties().put("newKey", "originalValue");
            var created = getPolicyDefinitionStore().create(policy);
            assertThat(created).isNotNull();

            var definitionFound = getPolicyDefinitionStore().findById(id);

            assertThat(definitionFound).isNotNull();
            assertThat(definitionFound).usingRecursiveComparison().isEqualTo(policy);

            policy.getPrivateProperties().put("newKey", "newValue");
            var updated = getPolicyDefinitionStore().update(policy);
            definitionFound = getPolicyDefinitionStore().findById(id);
            assertThat(updated).isNotNull();
            assertThat(definitionFound).isNotNull();
            assertThat(definitionFound).usingRecursiveComparison().isEqualTo(policy);
            assertThat(definitionFound.getPrivateProperties()).containsEntry("newKey", "newValue");
        }

        @Test
        void whenPolicyNotExists() {
            var updated = TestFunctions.createPolicy("another-id");

            var result = getPolicyDefinitionStore().update(updated);

            assertThat(result).extracting(StoreResult::reason).isEqualTo(NOT_FOUND);
        }

        @Test
        void whenPolicyExists_updatingPolicyFields() {
            var policy = TestFunctions.createPolicy("test-id");
            var store = getPolicyDefinitionStore();
            store.create(policy);

            var action = Action.Builder.newInstance().type("play").build();
            var updatedPermission = Permission.Builder.newInstance().action(action).build();
            var updatedDuty = Duty.Builder.newInstance().action(action).build();
            var updatedProhibition = Prohibition.Builder.newInstance().action(action).build();

            policy.getPolicy().getPermissions().add(updatedPermission);
            policy.getPolicy().getProhibitions().add(updatedProhibition);
            policy.getPolicy().getObligations().add(updatedDuty);
            policy.getPolicy().getExtensibleProperties().put("updatedKey", "updatedValue");

            var result = store.update(policy);
            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent()).isEqualTo(policy);

            var updatedPolicy = result.getContent().getPolicy();
            assertThat(updatedPolicy.getExtensibleProperties()).containsEntry("updatedKey", "updatedValue");
            assertThat(updatedPolicy.getPermissions().get(1)).isEqualTo(updatedPermission);
            assertThat(updatedPolicy.getProhibitions().get(1)).isEqualTo(updatedProhibition);
            assertThat(updatedPolicy.getObligations().get(1)).isEqualTo(updatedDuty);
        }

        @Test
        void whenPolicyExists_removingPolicyFields() {
            var policy = TestFunctions.createPolicy("test-id");
            getPolicyDefinitionStore().create(policy);

            var updatedPolicy = createPolicyDef("test-id", "updatedTarget");
            var result = getPolicyDefinitionStore().update(updatedPolicy);

            assertThat(result.succeeded()).isTrue();

            var content = result.getContent().getPolicy();
            assertThat(result.getContent()).isEqualTo(updatedPolicy);
            assertThat(content.getTarget()).isEqualTo("updatedTarget");
            assertThat(content.getPermissions().size()).isEqualTo(0);
            assertThat(content.getProhibitions().size()).isEqualTo(0);
            assertThat(content.getObligations().size()).isEqualTo(0);
        }

        @Test
        void whenPolicyExists_replacingAllFields() {
            var policyId = "test-id";
            var policy = TestFunctions.createPolicy(policyId);
            var store = getPolicyDefinitionStore();
            store.create(policy);

            var updatedPermission = TestFunctions.createPermissionBuilder("updated-id").build();
            var updatedProhibition = TestFunctions.createProhibitionBuilder("updated-id").build();
            var updatedDuty = TestFunctions.createDutyBuilder("updated-id").build();
            var updatedPolicy = Policy.Builder.newInstance()
                    .target("updatedTarget")
                    .permission(updatedPermission)
                    .prohibition(updatedProhibition)
                    .duty(updatedDuty)
                    .extensibleProperty("updatedKey", "updatedValue")
                    .build();

            var result = store.update(PolicyDefinition.Builder.newInstance().id(policyId).policy(updatedPolicy).build());
            assertThat(result.succeeded()).isTrue();
            var content = result.getContent().getPolicy();
            assertThat(content).isEqualTo(updatedPolicy);

            assertThat(content.getExtensibleProperties()).containsEntry("updatedKey", "updatedValue");
            assertThat(content.getPermissions().get(0)).isEqualTo(updatedPermission);
            assertThat(content.getProhibitions().get(0)).isEqualTo(updatedProhibition);
            assertThat(content.getObligations().get(0)).isEqualTo(updatedDuty);
        }
    }

    @Nested
    class FindById {
        @Test
        @DisplayName("Find policy by ID that exists")
        void whenPresent() {
            var policy = TestFunctions.createPolicy(getRandomId());
            getPolicyDefinitionStore().create(policy);

            var policyFromDb = getPolicyDefinitionStore().findById(policy.getId());

            assertThat(policy).usingRecursiveComparison().isEqualTo(policyFromDb);
        }

        @Test
        @DisplayName("Find policy by ID when not exists")
        void whenNonexistent() {
            assertThat(getPolicyDefinitionStore().findById("nonexistent")).isNull();
        }
    }

    @Nested
    class FindAll {
        @Test
        @DisplayName("Find all policies with limit and offset")
        void withSpec() {
            var limit = 20;

            var definitionsExpected = TestFunctions.createPolicies(50);
            definitionsExpected.forEach(getPolicyDefinitionStore()::create);

            var spec = QuerySpec.Builder.newInstance()
                    .limit(limit)
                    .offset(20)
                    .build();

            var policiesFromDb = getPolicyDefinitionStore().findAll(spec);

            assertThat(policiesFromDb).hasSize(limit);
        }

        @Test
        @DisplayName("Find policies when page size larger than DB collection")
        void pageSizeLargerThanDbCollection() {
            var pageSize = 15;

            var definitionsExpected = TestFunctions.createPolicies(10);
            definitionsExpected.forEach(getPolicyDefinitionStore()::create);

            var spec = QuerySpec.Builder.newInstance()
                    .offset(pageSize)
                    .build();

            var policiesFromDb = getPolicyDefinitionStore().findAll(spec);

            assertThat(policiesFromDb).isEmpty();
        }

        @Test
        @DisplayName("Find policies when page size oversteps DB collection size")
        void pageSizeLarger() {
            var limit = 5;

            var definitionsExpected = TestFunctions.createPolicies(10);
            definitionsExpected.forEach(getPolicyDefinitionStore()::create);

            var spec = QuerySpec.Builder.newInstance()
                    .offset(7)
                    .limit(limit)
                    .build();

            var policiesFromDb = getPolicyDefinitionStore().findAll(spec);

            assertThat(policiesFromDb).size().isLessThanOrEqualTo(limit);
        }

        @Test
        void shouldReturnEmpty_whenPropertyDoesNotExist() {
            var policy = TestFunctions.createPolicyBuilder("test-policy")
                    .assigner("test-assigner")
                    .assignee("test-assignee")
                    .build();
            var policyDef1 = PolicyDefinition.Builder.newInstance().id("test-policy").policy(policy).build();
            getPolicyDefinitionStore().create(policyDef1);

            var criterion = criterion("notexist", "=", "foobar");
            var querySpec = QuerySpec.Builder.newInstance().filter(criterion).build();

            var result = getPolicyDefinitionStore().findAll(querySpec);

            assertThat(result).isEmpty();
        }

        @Test
        void sorting_nonExistentProperty() {
            IntStream.range(0, 10).mapToObj(i -> TestFunctions.createPolicy("test-policy")).forEach((d) -> getPolicyDefinitionStore().create(d));

            var query = QuerySpec.Builder.newInstance().sortField("notexist").sortOrder(SortOrder.DESC).build();

            assertThatThrownBy(() -> getPolicyDefinitionStore().findAll(query).toList())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void queryByProhibitions() {
            var p = TestFunctions.createPolicyBuilder("test-policy")
                    .prohibition(TestFunctions.createProhibitionBuilder("prohibition1")
                            .action(TestFunctions.createAction("test-action-type"))
                            .build())
                    .build();

            var policyDef = PolicyDefinition.Builder.newInstance().id("test-policy").policy(p).build();
            getPolicyDefinitionStore().create(policyDef);

            // query by prohibition assignee
            var query = createQuery(Criterion.criterion("policy.prohibitions.action.type", "=", "test-action-type"));
            var result = getPolicyDefinitionStore().findAll(query);
            assertThat(result).hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(policyDef);

            //query by prohibition action constraint
            var query2 = createQuery(Criterion.criterion("policy.prohibitions.action.constraint.leftExpression.value", "=", "foo"));
            var result2 = getPolicyDefinitionStore().findAll(query2);
            assertThat(result2).hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(policyDef);
        }

        @Test
        void queryByProhibitions_valueNotExist() {
            var p = TestFunctions.createPolicyBuilder("test-policy")
                    .prohibition(TestFunctions.createProhibitionBuilder("prohibition1")
                            .action(TestFunctions.createAction("test-action-type"))
                            .build())
                    .build();

            var policyDef = PolicyDefinition.Builder.newInstance().id("test-policy").policy(p).build();
            getPolicyDefinitionStore().create(policyDef);

            // query by prohibition assignee
            var query = createQuery(Criterion.criterion("policy.prohibitions.action.constraint.leftExpression.value", "=", "someval"));
            var result = getPolicyDefinitionStore().findAll(query);
            assertThat(result).isEmpty();
        }

        @Test
        void queryByPermissions() {
            var p = TestFunctions.createPolicyBuilder("test-policy")
                    .permission(TestFunctions.createPermissionBuilder("permission1")
                            .action(TestFunctions.createAction("test-action-type"))
                            .build())
                    .build();

            var policyDef = PolicyDefinition.Builder.newInstance().id("test-policy").policy(p).build();
            getPolicyDefinitionStore().create(policyDef);

            // query by prohibition assignee
            var query = createQuery(Criterion.criterion("policy.permissions.action.type", "=", "test-action-type"));
            var result = getPolicyDefinitionStore().findAll(query);
            assertThat(result).hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(policyDef);

            //query by prohibition action constraint
            var query2 = createQuery(Criterion.criterion("policy.permissions.action.constraint.leftExpression.value", "=", "foo"));
            var result2 = getPolicyDefinitionStore().findAll(query2);
            assertThat(result2).hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(policyDef);
        }

        @Test
        void queryByPermissions_valueNotExist() {
            var p = TestFunctions.createPolicyBuilder("test-policy")
                    .permission(TestFunctions.createPermissionBuilder("permission1")
                            .action(TestFunctions.createAction("test-action-type"))
                            .build())
                    .build();

            var policyDef = PolicyDefinition.Builder.newInstance().id("test-policy").policy(p).build();
            getPolicyDefinitionStore().create(policyDef);

            // query by prohibition assignee
            var query = createQuery(Criterion.criterion("policy.permissions.action.constraint.leftExpression", "=", "someval"));
            var result = getPolicyDefinitionStore().findAll(query);
            assertThat(result).isEmpty();
        }

        @Test
        void queryByDuties() {
            var p = TestFunctions.createPolicyBuilder("test-policy")
                    .duty(TestFunctions.createDutyBuilder("prohibition1")
                            .action(TestFunctions.createAction("test-action-type"))
                            .build())
                    .build();

            var policyDef = PolicyDefinition.Builder.newInstance().id("test-policy").policy(p).build();
            getPolicyDefinitionStore().create(policyDef);
            getPolicyDefinitionStore().create(TestFunctions.createPolicy("another-policy"));

            // query by prohibition assignee
            var query = createQuery(Criterion.criterion("policy.obligations.action.type", "=", "test-action-type"));
            var result = getPolicyDefinitionStore().findAll(query);
            assertThat(result).hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(policyDef);

            //query by prohibition action constraint
            var query2 = createQuery(Criterion.criterion("policy.obligations.action.constraint.rightExpression.value", "=", "bar"));
            var result2 = getPolicyDefinitionStore().findAll(query2);
            assertThat(result2).hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(policyDef);
        }

        @Test
        void queryByDuties_valueNotExist() {
            var p = TestFunctions.createPolicyBuilder("test-policy")
                    .duty(TestFunctions.createDutyBuilder("prohibition1")
                            .action(TestFunctions.createAction("test-action-type"))
                            .build())
                    .build();

            var policyDef = PolicyDefinition.Builder.newInstance().id("test-policy").policy(p).build();
            getPolicyDefinitionStore().create(policyDef);

            // query by prohibition assignee
            var query = createQuery(Criterion.criterion("policy.obligations.action.constraint.rightExpression.value", "=", "notexist"));
            var result = getPolicyDefinitionStore().findAll(query);
            assertThat(result).isEmpty();
        }

        @Test
        void queryByProperty() {
            var p1 = TestFunctions.createPolicyBuilder("test-policy")
                    .assigner("test-assigner")
                    .assignee("test-assignee")
                    .build();

            var policyDef1 = PolicyDefinition.Builder.newInstance().id("test-policy").policy(p1).build();
            var p2 = TestFunctions.createPolicyBuilder("test-policy")
                    .assigner("another-test-assigner")
                    .assignee("another-test-assignee")
                    .build();

            var policyDef2 = PolicyDefinition.Builder.newInstance().id("test-policy2").policy(p2).build();
            getPolicyDefinitionStore().create(policyDef1);
            getPolicyDefinitionStore().create(policyDef2);

            // query by prohibition assignee
            assertThat(getPolicyDefinitionStore().findAll(createQuery(Criterion.criterion("policy.assignee", "=", "test-assignee"))))
                    .hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(policyDef1);
        }

        @Test
        void queryByProperty_valueNotFound() {
            var policy = TestFunctions.createPolicyBuilder("test-policy")
                    .assigner("test-assigner")
                    .assignee("test-assignee")
                    .build();

            var policyDef1 = PolicyDefinition.Builder.newInstance().id("test-policy").policy(policy).build();
            getPolicyDefinitionStore().create(policyDef1);

            // query by prohibition assignee
            var query = createQuery(Criterion.criterion("policy.assigner", "=", "notexist"));

            assertThat(getPolicyDefinitionStore().findAll(query)).isEmpty();
        }

        @Test
        void whenNoFiltersWithLimitAndOffset() {
            var policy1 = TestFunctions.createPolicy(getRandomId());
            var policy2 = TestFunctions.createPolicy(getRandomId());
            var policy3 = TestFunctions.createPolicy(getRandomId());
            getPolicyDefinitionStore().create(policy1);
            getPolicyDefinitionStore().create(policy2);
            getPolicyDefinitionStore().create(policy3);

            var list = getPolicyDefinitionStore().findAll(QuerySpec.Builder.newInstance().limit(3).offset(1).build()).collect(Collectors.toList());
            assertThat(list).hasSize(2).usingRecursiveFieldByFieldElementComparator().isSubsetOf(policy1, policy2, policy3);
        }

        @Test
        @DisplayName("Find all with a property ")
        void findAll_with_privateProperties() {
            var policy1 = TestFunctions.createPolicy(getRandomId(), null, Map.of("key1", "value1", "key2", "value2"));
            var policy2 = TestFunctions.createPolicy(getRandomId(), null, Map.of("key3", "value3", "key4", "value4"));

            getPolicyDefinitionStore().create(policy1);
            getPolicyDefinitionStore().create(policy2);

            var list = getPolicyDefinitionStore().findAll(QuerySpec.Builder.newInstance().limit(3).offset(0).build()).collect(Collectors.toList());
            assertThat(list).hasSize(2).usingRecursiveFieldByFieldElementComparator().isSubsetOf(policy1, policy2);
        }


        @Test
        void shouldReturn_with_private_propertiesFilter() {
            var policy1 = TestFunctions.createPolicy(getRandomId(), null, Map.of("key1", "value1", "key2", "value2"));
            var policy2 = TestFunctions.createPolicy(getRandomId(), null, Map.of("key3", "value3", "key4", "value4"));


            getPolicyDefinitionStore().create(policy1);
            getPolicyDefinitionStore().create(policy2);
            var spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("privateProperties.key1", "=", "value1"))
                    .build();

            var definitionsRetrieved = getPolicyDefinitionStore().findAll(spec);
            assertThat(definitionsRetrieved).isNotNull().hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsOnly(policy1);

            spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("privateProperties.key2", "=", "value2"))
                    .build();

            definitionsRetrieved = getPolicyDefinitionStore().findAll(spec);
            assertThat(definitionsRetrieved).isNotNull().hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsOnly(policy1);

            spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("privateProperties.key3", "=", "value3"))
                    .build();

            definitionsRetrieved = getPolicyDefinitionStore().findAll(spec);
            assertThat(definitionsRetrieved).isNotNull().hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsOnly(policy2);


            spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("privateProperties.key3", "=", "value4"))
                    .build();

            definitionsRetrieved = getPolicyDefinitionStore().findAll(spec);
            assertThat(definitionsRetrieved).isNotNull().hasSize(0);
        }

        @Test
        void shouldReturn_with_complex_private_propertiesFilter() {
            var policy1 = TestFunctions.createPolicy(getRandomId(), null, Map.of("myProp", Map.of("description", "test desc 1", "number", 42)));
            var policy2 = TestFunctions.createPolicy(getRandomId(), null, Map.of("myProp", Map.of("description", "test desc 2", "number", 42)));

            getPolicyDefinitionStore().create(policy1);
            getPolicyDefinitionStore().create(policy2);

            var spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("privateProperties.'myProp'.'description'", "=", "test desc 1"))
                    .build();

            var definitionsRetrieved = getPolicyDefinitionStore().findAll(spec);
            assertThat(definitionsRetrieved).isNotNull().hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsOnly(policy1);

            spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("privateProperties.'myProp'.'description'", "=", "test desc 2"))
                    .build();

            definitionsRetrieved = getPolicyDefinitionStore().findAll(spec);
            assertThat(definitionsRetrieved).isNotNull().hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsOnly(policy2);

            spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("privateProperties.'myProp'.'description'", "=", "test desc 3"))
                    .build();

            definitionsRetrieved = getPolicyDefinitionStore().findAll(spec);
            assertThat(definitionsRetrieved).isNotNull().hasSize(0);
        }

        @Test
        void whenEqualFilter() {
            var policy1 = TestFunctions.createPolicy(getRandomId());
            var policy2 = TestFunctions.createPolicy(getRandomId());
            var policy3 = TestFunctions.createPolicy(getRandomId());
            getPolicyDefinitionStore().create(policy1);
            getPolicyDefinitionStore().create(policy2);
            getPolicyDefinitionStore().create(policy3);

            var querySpec = QuerySpec.Builder.newInstance().filter(Criterion.criterion("id", "=", policy1.getId())).build();

            assertThat(getPolicyDefinitionStore().findAll(querySpec)).usingRecursiveFieldByFieldElementComparator().containsExactly(policy1);
        }

        @Test
        void whenSort() {
            var policy1 = TestFunctions.createPolicy("C");
            var policy2 = TestFunctions.createPolicy("A");
            var policy3 = TestFunctions.createPolicy("B");

            getPolicyDefinitionStore().create(policy1);
            getPolicyDefinitionStore().create(policy2);
            getPolicyDefinitionStore().create(policy3);

            assertThat(getPolicyDefinitionStore().findAll(QuerySpec.Builder.newInstance().sortField("id").sortOrder(SortOrder.ASC).build())).usingRecursiveFieldByFieldElementComparator().containsExactly(policy2, policy3, policy1);
        }

        @Test
        void allFilters() {
            var policy1 = TestFunctions.createPolicy("1C", "target1");
            var policy2 = TestFunctions.createPolicy("1A", "target1");
            var policy3 = TestFunctions.createPolicy("1B", "target1");
            var policyX = TestFunctions.createPolicy("2X", "target2");
            var policyY = TestFunctions.createPolicy("2Y", "target2");
            getPolicyDefinitionStore().create(policy1);
            getPolicyDefinitionStore().create(policy2);
            getPolicyDefinitionStore().create(policy3);
            getPolicyDefinitionStore().create(policyX);
            getPolicyDefinitionStore().create(policyY);

            var querySpec = QuerySpec.Builder.newInstance()
                    .filter(Criterion.criterion("policy.target", "=", "target1"))
                    .sortField("id")
                    .sortOrder(SortOrder.DESC)
                    .offset(1)
                    .limit(1)
                    .build();

            var result = getPolicyDefinitionStore().findAll(querySpec);

            assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactly(policy3);
        }
    }

    @Nested
    class DeleteById {
        @Test
        @DisplayName("Delete existing policy")
        void whenExists() {
            var policy = TestFunctions.createPolicy(getRandomId());
            var store = getPolicyDefinitionStore();
            store.create(policy);

            var result = store.delete(policy.getId());
            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent()).usingRecursiveComparison().isEqualTo(policy);
            assertThat(store.findById(policy.getId())).isNull();
        }

        @Test
        @DisplayName("Delete an existing policy with properties")
        void whenExists_WithProperties() {
            var policy = TestFunctions.createPolicy(getRandomId(), null, Map.of("key1", "value1", "key2", "value2"));
            var store = getPolicyDefinitionStore();
            store.create(policy);

            var result = store.delete(policy.getId());
            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent()).usingRecursiveComparison().isEqualTo(policy);
            assertThat(store.findById(policy.getId())).isNull();
        }

        @Test
        @DisplayName("Delete a non existing policy")
        void whenNonexistent() {
            assertThat(getPolicyDefinitionStore().delete("nonexistent"))
                    .isNotNull()
                    .extracting(StoreResult::reason)
                    .isEqualTo(NOT_FOUND);
        }
    }

    protected abstract PolicyDefinitionStore getPolicyDefinitionStore();

    private String getRandomId() {
        return UUID.randomUUID().toString();
    }

    private PolicyDefinition createPolicyDef(String id, String target) {
        return PolicyDefinition.Builder.newInstance().id(id).policy(Policy.Builder.newInstance().target(target).build()).build();
    }

    private QuerySpec createQuery(Criterion criterion) {
        return QuerySpec.Builder.newInstance().filter(criterion).build();
    }
}
