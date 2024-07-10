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

package org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store;

import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store.TestFunctions.createContract;
import static org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store.TestFunctions.createContractBuilder;
import static org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store.TestFunctions.createNegotiation;
import static org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store.TestFunctions.createNegotiationBuilder;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.CONSUMER;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.persistence.StateEntityStore.hasState;
import static org.eclipse.edc.spi.query.Criterion.criterion;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.ALREADY_LEASED;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.NOT_FOUND;

public abstract class ContractNegotiationStoreTestBase {

    protected static final String CONNECTOR_NAME = "test-connector";
    private static final String ASSET_ID = "TEST_ASSET_ID";
    protected final Clock clock = Clock.systemUTC();

    protected abstract ContractNegotiationStore getContractNegotiationStore();

    protected abstract void leaseEntity(String negotiationId, String owner, Duration duration);

    protected void leaseEntity(String negotiationId, String owner) {
        leaseEntity(negotiationId, owner, Duration.ofSeconds(60));
    }

    protected abstract boolean isLeasedBy(String negotiationId, String owner);

    @Nested
    class FindById {

        @Test
        void shouldFindEntityById() {
            var id = "test-cn1";
            var negotiation = createNegotiation(id);
            getContractNegotiationStore().save(negotiation);

            var actual = getContractNegotiationStore().findById(id);

            assertThat(actual).usingRecursiveComparison().isEqualTo(negotiation);
        }

        @Test
        @DisplayName("Verify that an entity is found by ID even when leased")
        void findById_whenLeased_shouldReturnEntity() {
            var id = "test-cn1";
            var negotiation = createNegotiation(id);
            getContractNegotiationStore().save(negotiation);

            leaseEntity(id, CONNECTOR_NAME);
            assertThat(getContractNegotiationStore().findById(id))
                    .usingRecursiveComparison()
                    .isEqualTo(negotiation);


            var id2 = "test-cn2";
            var negotiation2 = createNegotiation(id2);
            getContractNegotiationStore().save(negotiation2);

            leaseEntity(id2, "someone-else");
            assertThat(getContractNegotiationStore().findById(id2))
                    .usingRecursiveComparison()
                    .isEqualTo(negotiation2);

        }

        @Test
        @DisplayName("Verify that null is returned when entity not found")
        void findById_notExist() {
            assertThat(getContractNegotiationStore().findById("not-exist")).isNull();
        }
    }

    @Nested
    class FindForCorrelationId {
        @Test
        @DisplayName("Find entity by its correlation ID")
        void findForCorrelationId() {
            var negotiation = createNegotiation("test-cn1");
            getContractNegotiationStore().save(negotiation);

            assertThat(getContractNegotiationStore().findForCorrelationId(negotiation.getCorrelationId()))
                    .usingRecursiveComparison()
                    .isEqualTo(negotiation);
        }
    }

    @Nested
    class FindContractAgreement {
        @Test
        @DisplayName("Find ContractAgreement by contract ID")
        void findContractAgreement() {
            var agreement = createContract(ContractOfferId.create("test-cd1", "test-as1"));
            var negotiation = createNegotiation("test-cn1", agreement);
            getContractNegotiationStore().save(negotiation);

            assertThat(getContractNegotiationStore().findContractAgreement(agreement.getId()))
                    .usingRecursiveComparison()
                    .isEqualTo(agreement);
        }

        @Test
        void findContractAgreement_shouldReturnNull_whenContractAgreementNotFound() {
            var result = getContractNegotiationStore().findContractAgreement("not-exist");

            assertThat(result).isNull();
        }
    }

    @Nested
    class Save {
        @Test
        void shouldSaveEntity() {
            var negotiation = createNegotiationBuilder("test-id1")
                    .type(ContractNegotiation.Type.PROVIDER)
                    .build();
            getContractNegotiationStore().save(negotiation);

            assertThat(getContractNegotiationStore().findById(negotiation.getId()))
                    .usingRecursiveComparison()
                    .isEqualTo(negotiation);
        }

        @Test
        @DisplayName("Verify that entity is stored with callbacks")
        void verifyCallbacks() {
            var callbacks = List.of(CallbackAddress.Builder.newInstance().uri("test").events(Set.of("event")).build());

            var negotiation = createNegotiationBuilder("test-id1")
                    .type(ContractNegotiation.Type.CONSUMER)
                    .callbackAddresses(callbacks)
                    .build();

            getContractNegotiationStore().save(negotiation);

            var contract = getContractNegotiationStore().findById(negotiation.getId());

            assertThat(contract)
                    .isNotNull()
                    .usingRecursiveComparison()
                    .isEqualTo(negotiation);

            assertThat(contract.getCallbackAddresses()).hasSize(1).usingRecursiveFieldByFieldElementComparator().containsAll(callbacks);
        }

        @Test
        @DisplayName("Verify that entity and related entities are stored")
        void withContract() {
            var agreement = createContract(ContractOfferId.create("definition", "asset"));
            var negotiation = createNegotiation("test-negotiation", agreement);
            getContractNegotiationStore().save(negotiation);

            var actual = getContractNegotiationStore().findById(negotiation.getId());
            assertThat(actual)
                    .isNotNull()
                    .usingRecursiveComparison()
                    .isEqualTo(negotiation);
            assertThat(actual.getContractAgreement()).usingRecursiveComparison().isEqualTo(agreement);
        }
    }

    @Nested
    class Update {
        @Test
        @DisplayName("Verify that an existing entity is updated instead")
        void save_exists_shouldUpdate() {
            var id = "test-id1";
            var negotiation = createNegotiation(id);
            getContractNegotiationStore().save(negotiation);

            var newNegotiation = ContractNegotiation.Builder.newInstance()
                    .type(ContractNegotiation.Type.CONSUMER)
                    .id(id)
                    .stateCount(420) //modified
                    .state(800) //modified
                    .correlationId("corr-test-id2")
                    .counterPartyAddress("consumer")
                    .counterPartyId("consumerId")
                    .protocol("protocol")
                    .build();

            getContractNegotiationStore().save(newNegotiation);

            var actual = getContractNegotiationStore().findById(negotiation.getId());
            assertThat(actual).isNotNull();
            assertThat(actual.getCorrelationId()).isEqualTo("corr-test-id2");
            assertThat(actual.getStateCount()).isEqualTo(420);
            assertThat(actual.getState()).isEqualTo(800);
        }

        @Test
        @DisplayName("Verify that updating an entity breaks the lease (if lease by self)")
        void leasedBySelf_shouldBreakLease() {
            var id = "test-id1";
            var builder = createNegotiationBuilder(id);
            var negotiation = builder.build();
            getContractNegotiationStore().save(negotiation);

            leaseEntity(id, CONNECTOR_NAME);

            var newNegotiation = builder
                    .stateCount(420) //modified
                    .state(800) //modified
                    .updatedAt(clock.millis())
                    .build();

            // update should break lease
            getContractNegotiationStore().save(newNegotiation);

            assertThat(isLeasedBy(id, CONNECTOR_NAME)).isFalse();

            var next = getContractNegotiationStore().nextNotLeased(10, hasState(800));
            assertThat(next).usingRecursiveFieldByFieldElementComparatorIgnoringFields("updatedAt").containsOnly(newNegotiation);

        }

        @Test
        @DisplayName("Verify that updating an entity throws an exception if leased by someone else")
        void leasedByOther_shouldThrowException() {
            var id = "test-id1";
            var negotiation = createNegotiation(id);
            getContractNegotiationStore().save(negotiation);

            leaseEntity(id, "someone-else");

            var newNegotiation = ContractNegotiation.Builder.newInstance()
                    .type(ContractNegotiation.Type.CONSUMER)
                    .id(id)
                    .stateCount(420) //modified
                    .state(800) //modified
                    .correlationId("corr-test-id1")
                    .counterPartyAddress("consumer")
                    .counterPartyId("consumerId")
                    .protocol("protocol")
                    .build();

            // update should break lease
            assertThat(isLeasedBy(id, "someone-else")).isTrue();
            assertThatThrownBy(() -> getContractNegotiationStore().save(newNegotiation)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Should persist the agreement when a negotiation is updated")
        void addsAgreement_shouldPersist() {
            var negotiationId = "test-cn1";
            var negotiation = createNegotiation(negotiationId);
            getContractNegotiationStore().save(negotiation);

            // now add the agreement
            var agreement = createContract(ContractOfferId.create("definition", "asset"));
            var updatedNegotiation = createNegotiation(negotiationId, agreement);

            getContractNegotiationStore().save(updatedNegotiation); //should perform an update + insert

            assertThat(getContractNegotiationStore().queryAgreements(QuerySpec.none()))
                    .hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(agreement);

            assertThat(Objects.requireNonNull(getContractNegotiationStore().findById(negotiationId)).getContractAgreement()).usingRecursiveComparison().isEqualTo(agreement);
        }

        @Test
        @DisplayName("Should persist update the callbacks if changed")
        void changeCallbacks() {
            var negotiationId = "test-cn1";
            var negotiation = createNegotiation(negotiationId);
            getContractNegotiationStore().save(negotiation);

            // one callback
            assertThat(Objects.requireNonNull(getContractNegotiationStore().findById(negotiationId)).getCallbackAddresses()).hasSize(1);

            // remove callbacks
            var updatedNegotiation = createNegotiationBuilder(negotiationId).callbackAddresses(List.of()).build();

            getContractNegotiationStore().save(updatedNegotiation); //should perform an update + insert

            assertThat(Objects.requireNonNull(getContractNegotiationStore().findById(negotiationId)).getCallbackAddresses()).isEmpty();
        }

        @Test
        void create_and_cancel_contractAgreement() {
            var negotiationId = "test-cn1";
            var negotiation = createNegotiation(negotiationId);
            getContractNegotiationStore().save(negotiation);

            // now add the agreement
            var agreement = createContract(ContractOfferId.create("definition", "asset"));
            var updatedNegotiation = createNegotiation(negotiationId, agreement);

            getContractNegotiationStore().save(updatedNegotiation);
            assertThat(getContractNegotiationStore().queryAgreements(QuerySpec.none()))
                    .hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(agreement);

            // cancel the agreement
            updatedNegotiation.transitionTerminating("Cancelled");
            getContractNegotiationStore().save(updatedNegotiation);
        }

        @Test
        @DisplayName("Should update the agreement when a negotiation is updated")
        void whenAgreementExists_shouldUpdate() {
            var negotiationId = "test-cn1";
            var agreement = createContract(ContractOfferId.create("definition", "asset"));
            var negotiation = createNegotiation(negotiationId, null);
            getContractNegotiationStore().save(negotiation);
            var dbNegotiation = getContractNegotiationStore().findById(negotiationId);
            assertThat(dbNegotiation).isNotNull().satisfies(n ->
                    assertThat(n.getContractAgreement()).isNull()
            );

            dbNegotiation.setContractAgreement(agreement);
            getContractNegotiationStore().save(dbNegotiation);

            var updatedNegotiation = getContractNegotiationStore().findById(negotiationId);
            assertThat(updatedNegotiation).isNotNull();
            assertThat(updatedNegotiation.getContractAgreement()).isNotNull();
        }
    }

    @Nested
    class Delete {
        @Test
        void shouldDeleteTheEntity() {
            var id = UUID.randomUUID().toString();
            var n = createNegotiation(id);
            getContractNegotiationStore().save(n);

            assertThat(getContractNegotiationStore().findById(id)).isNotNull().usingRecursiveComparison().isEqualTo(n);

            getContractNegotiationStore().delete(id);

            assertThat(getContractNegotiationStore().findById(id)).isNull();
        }

        @Test
        @DisplayName("Verify that an entity cannot be deleted when leased by self")
        void whenLeasedBySelf_shouldThrowException() {
            var id = UUID.randomUUID().toString();
            var n = createNegotiation(id);
            getContractNegotiationStore().save(n);

            leaseEntity(id, CONNECTOR_NAME);

            assertThatThrownBy(() -> getContractNegotiationStore().delete(id)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Verify that an entity cannot be deleted when leased by other")
        void whenLeasedByOther_shouldThrowException() {
            var id = UUID.randomUUID().toString();
            var n = createNegotiation(id);
            getContractNegotiationStore().save(n);

            leaseEntity(id, "someone-else");

            assertThatThrownBy(() -> getContractNegotiationStore().delete(id)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Verify that attempting to delete a negotiation with a contract raises an exception")
        void contractExists() {
            var id = UUID.randomUUID().toString();
            var contract = createContract(ContractOfferId.create("definition", "asset"));
            var n = createNegotiation(id, contract);
            getContractNegotiationStore().save(n);

            assertThat(getContractNegotiationStore().findById(id)).isNotNull().usingRecursiveComparison().isEqualTo(n);
            assertThatThrownBy(() -> getContractNegotiationStore().delete(id)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot delete ContractNegotiation")
                    .hasMessageContaining("ContractAgreement already created.");
        }
    }

    @Nested
    class QueryNegotiations {

        @Test
        void shouldPaginateResults() {
            var querySpec = QuerySpec.Builder.newInstance()
                    .limit(10).offset(5).build();
            range(0, 100)
                    .mapToObj(i -> createNegotiation(String.valueOf(i)))
                    .forEach(cn -> getContractNegotiationStore().save(cn));

            var result = getContractNegotiationStore().queryNegotiations(querySpec);

            assertThat(result).hasSize(10);
        }

        @Test
        void shouldFilterItems_whenCriterionIsPassed() {
            range(0, 10).forEach(i -> getContractNegotiationStore().save(TestFunctions.createNegotiation("test-neg-" + i)));
            var querySpec = QuerySpec.Builder.newInstance().filter(criterion("id", "=", "test-neg-3")).build();

            var result = getContractNegotiationStore().queryNegotiations(querySpec);

            assertThat(result).extracting(ContractNegotiation::getId).containsOnly("test-neg-3");
        }

        @Test
        @DisplayName("Verify that paging and sorting is used")
        void withPagingAndSorting() {
            var querySpec = QuerySpec.Builder.newInstance()
                    .sortField("id")
                    .limit(10).offset(5).build();

            range(0, 100)
                    .mapToObj(i -> createNegotiation(String.valueOf(i)))
                    .forEach(cn -> getContractNegotiationStore().save(cn));

            var result = getContractNegotiationStore().queryNegotiations(querySpec);

            assertThat(result).hasSize(10)
                    .extracting(ContractNegotiation::getId)
                    .isSorted();
        }

        @Test
        void withAgreementOnAsset_negotiationWithAgreement() {
            var agreement = createContract(ContractOfferId.create("definition", "asset"));
            var negotiation = createNegotiation("negotiation1", agreement);
            var assetId = agreement.getAssetId();

            getContractNegotiationStore().save(negotiation);

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("contractAgreement.assetId", "=", assetId)))
                    .build();
            var result = getContractNegotiationStore().queryNegotiations(query);

            assertThat(result).hasSize(1).usingRecursiveFieldByFieldElementComparator().containsOnly(negotiation);

        }

        @Test
        void withAgreementOnAsset_negotiationWithoutAgreement() {
            var assetId = UUID.randomUUID().toString();
            var negotiation = createNegotiation("negotiation1");

            getContractNegotiationStore().save(negotiation);

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("contractAgreement.assetId", "=", assetId)))
                    .build();
            var result = getContractNegotiationStore().queryNegotiations(query);

            assertThat(result).isEmpty();
            assertThat(getContractNegotiationStore().queryAgreements(QuerySpec.none())).isEmpty();
        }

        @Test
        void withAgreementOnAsset_multipleNegotiationsSameAsset() {
            var assetId = UUID.randomUUID().toString();
            var negotiation1 = createNegotiation("negotiation1", createContractBuilder("contract1").assetId(assetId).build());
            var negotiation2 = createNegotiation("negotiation2", createContractBuilder("contract2").assetId(assetId).build());

            getContractNegotiationStore().save(negotiation1);
            getContractNegotiationStore().save(negotiation2);

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("contractAgreement.assetId", "=", assetId)))
                    .build();
            var result = getContractNegotiationStore().queryNegotiations(query);

            assertThat(result).hasSize(2)
                    .extracting(ContractNegotiation::getId).containsExactlyInAnyOrder("negotiation1", "negotiation2");

        }

        @Test
        @DisplayName("Verify that paging is used (with ContractAgreement)")
        void withAgreement() {
            var querySpec = QuerySpec.Builder.newInstance().limit(10).offset(5).build();

            range(0, 100)
                    .mapToObj(i -> {
                        var agreement = createContract(ContractOfferId.create("definition" + 1, "asset"));
                        return createNegotiation(String.valueOf(i), agreement);
                    })
                    .forEach(cn -> getContractNegotiationStore().save(cn));

            var result = getContractNegotiationStore().queryNegotiations(querySpec);

            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("Verify that out-of-bounds paging parameters produce empty result")
        void offsetTooLarge() {
            var querySpec = QuerySpec.Builder.newInstance().limit(10).offset(50).build();

            range(0, 10)
                    .mapToObj(i -> createNegotiation(String.valueOf(i)))
                    .forEach(cn -> getContractNegotiationStore().save(cn));

            var result = getContractNegotiationStore().queryNegotiations(querySpec);

            assertThat(result).isEmpty();
        }

        @Test
        void byAgreementId() {
            var contractId1 = ContractOfferId.create("def1", "asset");
            var contractId2 = ContractOfferId.create("def2", "asset");
            var negotiation1 = createNegotiation("neg1", createContract(contractId1));
            var negotiation2 = createNegotiation("neg2", createContract(contractId2));
            getContractNegotiationStore().save(negotiation1);
            getContractNegotiationStore().save(negotiation2);
            var expression = criterion("contractAgreement.id", "=", contractId1.toString());
            var query = QuerySpec.Builder.newInstance().filter(expression).build();

            var result = getContractNegotiationStore().queryNegotiations(query);

            assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(negotiation1);
        }

        @Test
        void byPolicyAssignee() {
            var policy = Policy.Builder.newInstance()
                    .assignee("test-assignee")
                    .assigner("test-assigner")
                    .permission(Permission.Builder.newInstance()
                            .action(Action.Builder.newInstance()
                                    .type("use")
                                    .build())
                            .constraint(AtomicConstraint.Builder.newInstance()
                                    .leftExpression(new LiteralExpression("foo"))
                                    .operator(Operator.EQ)
                                    .rightExpression(new LiteralExpression("bar"))
                                    .build())
                            .build())
                    .build();

            var agreement1 = createContractBuilder("agr1").policy(policy).build();
            var agreement2 = createContractBuilder("agr2").policy(policy).build();
            var negotiation1 = createNegotiation("neg1", agreement1);
            var negotiation2 = createNegotiation("neg2", agreement2);
            getContractNegotiationStore().save(negotiation1);
            getContractNegotiationStore().save(negotiation2);
            var expression = criterion("contractAgreement.policy.assignee", "=", "test-assignee");
            var query = QuerySpec.Builder.newInstance().filter(expression).build();

            var result = getContractNegotiationStore().queryNegotiations(query);

            assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(negotiation1, negotiation2);
        }

        @Test
        void shouldReturnEmpty_whenCriteriaLeftOperandIsInvalid() {
            var contractId = ContractOfferId.create("definition", "asset");
            var agreement1 = createContract(contractId);
            var negotiation1 = createNegotiation("neg1", agreement1);
            getContractNegotiationStore().save(negotiation1);

            var expression = criterion("contractAgreement.notexist", "=", contractId.toString());
            var query = QuerySpec.Builder.newInstance().filter(expression).build();

            var result = getContractNegotiationStore().queryNegotiations(query);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldThrowException_whenSortFieldIsInvalid() {
            range(0, 10).forEach(i -> getContractNegotiationStore().save(TestFunctions.createNegotiation("test-neg-" + i)));
            var query = QuerySpec.Builder.newInstance().sortField("notexist").sortOrder(SortOrder.DESC).build();

            assertThatThrownBy(() -> getContractNegotiationStore().queryNegotiations(query)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class QueryAgreements {
        @Test
        void shouldReturnAllItems_whenQuerySpecHasNoFilter() {
            range(0, 10).forEach(i -> {
                var contractAgreement = createContract(ContractOfferId.create(UUID.randomUUID().toString(), ASSET_ID));
                var negotiation = createNegotiation(UUID.randomUUID().toString(), contractAgreement);
                getContractNegotiationStore().save(negotiation);
            });

            var all = getContractNegotiationStore().queryAgreements(QuerySpec.Builder.newInstance().build());

            assertThat(all).hasSize(10);
        }

        @Test
        void withQuerySpec() {
            range(0, 10).mapToObj(i -> "asset-" + i).forEach(assetId -> {
                var contractId = ContractOfferId.create(UUID.randomUUID().toString(), assetId).toString();
                var contractAgreement = createContractBuilder(contractId).assetId(assetId).build();
                var negotiation = createNegotiation(UUID.randomUUID().toString(), contractAgreement);
                getContractNegotiationStore().save(negotiation);
            });

            var query = QuerySpec.Builder.newInstance().filter(criterion("assetId", "=", "asset-2")).build();
            var all = getContractNegotiationStore().queryAgreements(query);

            assertThat(all).hasSize(1);
        }

        @Test
        void verifyPaging() {
            range(0, 10).forEach(i -> {
                var contractAgreement = createContract(ContractOfferId.create(UUID.randomUUID().toString(), ASSET_ID));
                var negotiation = createNegotiation(UUID.randomUUID().toString(), contractAgreement);
                getContractNegotiationStore().save(negotiation);
            });

            // page size fits
            assertThat(getContractNegotiationStore().queryAgreements(QuerySpec.Builder.newInstance().offset(3).limit(4).build())).hasSize(4);

            // page size too large
            assertThat(getContractNegotiationStore().queryAgreements(QuerySpec.Builder.newInstance().offset(5).limit(100).build())).hasSize(5);
        }

        @Test
        void verifySorting() {
            range(0, 9).forEach(i -> {
                var contractId = ContractOfferId.create(UUID.randomUUID().toString(), UUID.randomUUID().toString()).toString();
                var contractAgreement = createContractBuilder(contractId).consumerId(String.valueOf(i)).build();
                var negotiation = createNegotiationBuilder(UUID.randomUUID().toString()).contractAgreement(contractAgreement).build();
                getContractNegotiationStore().save(negotiation);
            });

            var queryAsc = QuerySpec.Builder.newInstance().sortField("consumerId").sortOrder(SortOrder.ASC).build();
            assertThat(getContractNegotiationStore().queryAgreements(queryAsc)).hasSize(9).isSortedAccordingTo(Comparator.comparing(ContractAgreement::getConsumerId));

            var queryDesc = QuerySpec.Builder.newInstance().sortField("consumerId").sortOrder(SortOrder.DESC).build();
            assertThat(getContractNegotiationStore().queryAgreements(queryDesc)).hasSize(9).isSortedAccordingTo((c1, c2) -> c2.getConsumerId().compareTo(c1.getConsumerId()));
        }

        @Test
        void shouldReturnEmpty_whenCriterionLeftOperandIsInvalid() {
            range(0, 10).mapToObj(i -> "asset-" + i).forEach(assetId -> {
                var contractAgreement = createContractBuilder(ContractOfferId.create(UUID.randomUUID().toString(), assetId).toString())
                        .assetId(assetId)
                        .build();
                var negotiation = createNegotiation(UUID.randomUUID().toString(), contractAgreement);
                getContractNegotiationStore().save(negotiation);
            });
            var query = QuerySpec.Builder.newInstance().filter(criterion("notexistprop", "=", "asset-2")).build();

            var result = getContractNegotiationStore().queryAgreements(query);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldThrowException_whenSortFieldIsInvalid() {
            var query = QuerySpec.Builder.newInstance().sortField("notexist").sortOrder(SortOrder.DESC).build();

            assertThatThrownBy(() -> getContractNegotiationStore().queryAgreements(query)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class NextNotLeased {
        @Test
        @DisplayName("Verify that nextNotLeased returns the correct amount of items")
        void shouldReturnNotLeasedItems() {
            var negotiations = range(0, 10)
                    .mapToObj(i -> createNegotiation("id" + i))
                    .toList();
            negotiations.forEach(getContractNegotiationStore()::save);

            var batch = getContractNegotiationStore().nextNotLeased(5, hasState(REQUESTED.code()));

            assertThat(batch).hasSize(5).isSubsetOf(negotiations);
        }

        @Test
        void typeFilter() {
            range(0, 5).mapToObj(it -> createNegotiationBuilder("1" + it)
                    .state(REQUESTED.code())
                    .type(PROVIDER)
                    .build()).forEach(getContractNegotiationStore()::save);
            range(5, 10).mapToObj(it -> createNegotiationBuilder("1" + it)
                    .state(REQUESTED.code())
                    .type(CONSUMER)
                    .build()).forEach(getContractNegotiationStore()::save);
            var criteria = new Criterion[]{ hasState(REQUESTED.code()), new Criterion("type", "=", "CONSUMER") };

            var result = getContractNegotiationStore().nextNotLeased(10, criteria);

            assertThat(result).hasSize(5).allMatch(it -> it.getType() == CONSUMER);
        }

        @Test
        @DisplayName("nextNotLeased: verify that only non-leased entities are returned")
        void withLeasedEntity() {
            var negotiations = range(0, 10)
                    .mapToObj(i -> createNegotiation(String.valueOf(i)))
                    .collect(Collectors.toList());
            negotiations.forEach(getContractNegotiationStore()::save);

            // mark a few as "leased"
            getContractNegotiationStore().nextNotLeased(5, hasState(REQUESTED.code()));

            var batch2 = getContractNegotiationStore().nextNotLeased(10, hasState(REQUESTED.code()));
            assertThat(batch2)
                    .hasSize(5)
                    .isSubsetOf(negotiations)
                    .extracting(ContractNegotiation::getId)
                    .map(Integer::parseInt)
                    .allMatch(i -> i >= 5);
        }

        @Test
        @DisplayName("nextNotLeased: verify that an expired lease is re-acquired")
        void withLeasedEntity_expiredLease() throws InterruptedException {
            var negotiations = range(0, 5)
                    .mapToObj(i -> createNegotiation(String.valueOf(i)))
                    .collect(Collectors.toList());
            negotiations.forEach(getContractNegotiationStore()::save);

            // mark them as "leased"
            negotiations.forEach(n -> leaseEntity(n.getId(), CONNECTOR_NAME, Duration.ofMillis(10)));

            // let enough time pass
            Thread.sleep(50);

            var leasedNegotiations = getContractNegotiationStore().nextNotLeased(5, hasState(REQUESTED.code()));
            assertThat(leasedNegotiations)
                    .hasSize(5)
                    .containsAll(negotiations);

            assertThat(leasedNegotiations).allMatch(n -> isLeasedBy(n.getId(), CONNECTOR_NAME));
        }

        @Test
        @DisplayName("Verify that nextNotLeased returns the agreement")
        void withAgreement() {
            var contractAgreement = createContract(ContractOfferId.create(UUID.randomUUID().toString(), ASSET_ID));
            var negotiation = createNegotiationBuilder(UUID.randomUUID().toString())
                    .contractAgreement(contractAgreement)
                    .state(ContractNegotiationStates.AGREED.code())
                    .build();

            getContractNegotiationStore().save(negotiation);

            var batch = getContractNegotiationStore().nextNotLeased(1, hasState(ContractNegotiationStates.AGREED.code()));

            assertThat(batch).hasSize(1).usingRecursiveFieldByFieldElementComparator().containsExactly(negotiation);
        }

        @Test
        void avoidsStarvation() {
            range(0, 10).forEach(i -> {
                var negotiation = TestFunctions.createNegotiation("test-negotiation-" + i);
                negotiation.transitionRequested();
                getContractNegotiationStore().save(negotiation);
            });

            var list1 = getContractNegotiationStore().nextNotLeased(5, hasState(REQUESTED.code()));
            var list2 = getContractNegotiationStore().nextNotLeased(5, hasState(REQUESTED.code()));

            assertThat(list1).isNotEqualTo(list2).doesNotContainAnyElementsOf(list2);
        }

        @Test
        void shouldLeaseOrderByStateTimestamp() {

            var all = range(0, 10)
                    .mapToObj(i -> createNegotiation("id-" + i))
                    .peek(getContractNegotiationStore()::save)
                    .toList();

            all.stream().limit(5)
                    .peek(this::delayByTenMillis)
                    .sorted(Comparator.comparing(ContractNegotiation::getStateTimestamp).reversed())
                    .forEach(f -> getContractNegotiationStore().save(f));

            var elements = getContractNegotiationStore().nextNotLeased(10, hasState(REQUESTED.code()));
            assertThat(elements).hasSize(10).extracting(ContractNegotiation::getStateTimestamp).isSorted();
        }

        private void delayByTenMillis(StatefulEntity<?> t) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                // noop
            }
            t.updateStateTimestamp();
        }
    }

    @Nested
    class FindByIdAndLease {
        @Test
        void shouldReturnTheEntityAndLeaseIt() {
            var id = UUID.randomUUID().toString();
            getContractNegotiationStore().save(createNegotiation(id));

            var result = getContractNegotiationStore().findByIdAndLease(id);

            assertThat(result).isSucceeded();
            assertThat(isLeasedBy(id, CONNECTOR_NAME)).isTrue();
        }

        @Test
        void shouldReturnNotFound_whenEntityDoesNotExist() {
            var result = getContractNegotiationStore().findByIdAndLease("unexistent");

            assertThat(result).isFailed().extracting(StoreFailure::getReason).isEqualTo(NOT_FOUND);
        }

        @Test
        void shouldReturnAlreadyLeased_whenEntityIsAlreadyLeased() {
            var id = UUID.randomUUID().toString();
            getContractNegotiationStore().save(createNegotiation(id));
            leaseEntity(id, "other owner");

            var result = getContractNegotiationStore().findByIdAndLease(id);

            assertThat(result).isFailed().extracting(StoreFailure::getReason).isEqualTo(ALREADY_LEASED);
        }
    }

    @Deprecated(since = "0.4.1")
    @Nested
    class FindByCorrelationIdAndLease {
        @Test
        void shouldReturnTheEntityAndLeaseIt() {
            var id = UUID.randomUUID().toString();
            var correlationId = UUID.randomUUID().toString();
            getContractNegotiationStore().save(createNegotiationBuilder(id).correlationId(correlationId).build());

            var result = getContractNegotiationStore().findByCorrelationIdAndLease(correlationId);

            assertThat(result).isSucceeded();
            assertThat(isLeasedBy(id, CONNECTOR_NAME)).isTrue();
        }

        @Test
        void shouldReturnNotFound_whenEntityDoesNotExist() {
            var result = getContractNegotiationStore().findByCorrelationIdAndLease("unexistent");

            assertThat(result).isFailed().extracting(StoreFailure::getReason).isEqualTo(NOT_FOUND);
        }

        @Test
        void shouldReturnAlreadyLeased_whenEntityIsAlreadyLeased() {
            var id = UUID.randomUUID().toString();
            var correlationId = UUID.randomUUID().toString();
            getContractNegotiationStore().save(createNegotiationBuilder(id).correlationId(correlationId).build());
            leaseEntity(id, "other owner");

            var result = getContractNegotiationStore().findByCorrelationIdAndLease(correlationId);

            assertThat(result).isFailed().extracting(StoreFailure::getReason).isEqualTo(ALREADY_LEASED);
        }
    }

}
