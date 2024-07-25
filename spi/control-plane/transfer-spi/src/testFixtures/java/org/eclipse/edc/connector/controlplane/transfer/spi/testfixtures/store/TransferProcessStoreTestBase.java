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
 *       Mercedes-Benz Tech Innovation GmbH - connector id removal
 *
 */

package org.eclipse.edc.connector.controlplane.transfer.spi.testfixtures.store;

import org.awaitility.Awaitility;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DeprovisionedResource;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ProvisionedResourceSet;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceManifest;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.connector.controlplane.transfer.spi.testfixtures.store.TestFunctions.createTransferProcess;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.PROVISIONING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.persistence.StateEntityStore.hasState;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.ALREADY_LEASED;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.NOT_FOUND;
import static org.hamcrest.Matchers.hasSize;

public abstract class TransferProcessStoreTestBase {

    protected static final String CONNECTOR_NAME = "test-connector";
    protected final Clock clock = Clock.systemUTC();

    /**
     * determines the amount of time (default = 500ms) before an async test using Awaitility fails. This may be useful if using remote
     * or non-self-contained databases.
     */
    protected Duration getTestTimeout() {
        return Duration.ofMillis(500);
    }

    protected abstract TransferProcessStore getTransferProcessStore();

    protected abstract void leaseEntity(String negotiationId, String owner, Duration duration);

    protected void leaseEntity(String negotiationId, String owner) {
        leaseEntity(negotiationId, owner, Duration.ofSeconds(60));
    }

    protected abstract boolean isLeasedBy(String negotiationId, String owner);

    @Nested
    class Create {
        @Test
        void shouldCreateTheEntity() {
            var transferProcess = TestFunctions.createTransferProcessBuilder("test-id")
                    .correlationId("data-request-id")
                    .privateProperties(Map.of("key", "value")).build();
            getTransferProcessStore().save(transferProcess);

            var retrieved = getTransferProcessStore().findById("test-id");

            assertThat(retrieved).isNotNull().usingRecursiveComparison().isEqualTo(transferProcess);
            assertThat(retrieved.getCreatedAt()).isNotEqualTo(0L);
        }

        @Test
        void verifyCallbacks() {

            var callbacks = List.of(CallbackAddress.Builder.newInstance().uri("test").events(Set.of("event")).build());

            var t = TestFunctions.createTransferProcessBuilder("test-id").privateProperties(Map.of("key", "value")).callbackAddresses(callbacks).build();
            getTransferProcessStore().save(t);

            var all = getTransferProcessStore().findAll(QuerySpec.none()).collect(Collectors.toList());
            assertThat(all).containsExactly(t);
            assertThat(all.get(0)).usingRecursiveComparison().isEqualTo(t);
            assertThat(all.get(0).getCallbackAddresses()).hasSize(1).usingRecursiveFieldByFieldElementComparator().containsAll(callbacks);
        }

        @Test
        void verifyTransferType() {
            var t = TestFunctions.createTransferProcessBuilder("test-id").transferType("transferType").build();
            getTransferProcessStore().save(t);

            var all = getTransferProcessStore().findAll(QuerySpec.none()).collect(Collectors.toList());
            assertThat(all).containsExactly(t);
            assertThat(all.get(0)).usingRecursiveComparison().isEqualTo(t);
            assertThat(all.get(0).getTransferType()).isEqualTo("transferType");
        }

        @Test
        void verifyDataPlaneId() {
            var t = TestFunctions.createTransferProcessBuilder("test-id").dataPlaneId("dataPlaneId").build();
            getTransferProcessStore().save(t);

            var all = getTransferProcessStore().findAll(QuerySpec.none()).collect(Collectors.toList());
            assertThat(all).containsExactly(t);
            assertThat(all.get(0)).usingRecursiveComparison().isEqualTo(t);
            assertThat(all.get(0).getDataPlaneId()).isEqualTo("dataPlaneId");
        }

        @Test
        void withSameIdExists_shouldReplace() {
            var t = createTransferProcess("id1", INITIAL);
            getTransferProcessStore().save(t);

            var t2 = createTransferProcess("id1", PROVISIONING);
            getTransferProcessStore().save(t2);

            assertThat(getTransferProcessStore().findAll(QuerySpec.none())).hasSize(1).containsExactly(t2);
        }
    }

    @Nested
    class NextNotLeased {
        @Test
        void shouldReturnNotLeasedItems() {
            var state = STARTED;
            var all = range(0, 10)
                    .mapToObj(i -> createTransferProcess("id" + i, state))
                    .peek(getTransferProcessStore()::save)
                    .toList();

            assertThat(getTransferProcessStore().nextNotLeased(5, hasState(state.code())))
                    .hasSize(5)
                    .extracting(TransferProcess::getId)
                    .isSubsetOf(all.stream().map(TransferProcess::getId).collect(Collectors.toList()))
                    .allMatch(id -> isLeasedBy(id, CONNECTOR_NAME));
        }

        @Test
        void shouldOnlyReturnFreeItems() {
            var state = STARTED;
            var all = range(0, 10)
                    .mapToObj(i -> createTransferProcess("id" + i, state))
                    .peek(getTransferProcessStore()::save)
                    .collect(Collectors.toList());

            // lease a few
            var leasedTp = all.stream().skip(5).peek(tp -> leaseEntity(tp.getId(), CONNECTOR_NAME)).toList();

            // should not contain leased TPs
            assertThat(getTransferProcessStore().nextNotLeased(10, hasState(state.code())))
                    .hasSize(5)
                    .isSubsetOf(all)
                    .doesNotContainAnyElementsOf(leasedTp);
        }

        @Test
        void noFreeItem_shouldReturnEmpty() {
            var state = STARTED;
            range(0, 3)
                    .mapToObj(i -> createTransferProcess("id" + i, state))
                    .forEach(getTransferProcessStore()::save);

            // first time works
            assertThat(getTransferProcessStore().nextNotLeased(10, hasState(state.code()))).hasSize(3);
            // second time returns empty list
            assertThat(getTransferProcessStore().nextNotLeased(10, hasState(state.code()))).isEmpty();
        }

        @Test
        void noneInDesiredState() {
            range(0, 3)
                    .mapToObj(i -> createTransferProcess("id" + i, STARTED))
                    .forEach(getTransferProcessStore()::save);

            var nextNotLeased = getTransferProcessStore().nextNotLeased(10, hasState(TERMINATED.code()));

            assertThat(nextNotLeased).isEmpty();
        }

        @Test
        void batchSizeLimits() {
            var state = STARTED;
            range(0, 10)
                    .mapToObj(i -> createTransferProcess("id" + i, state))
                    .forEach(getTransferProcessStore()::save);

            // first time works
            var result = getTransferProcessStore().nextNotLeased(3, hasState(state.code()));
            assertThat(result).hasSize(3);
        }

        @Test
        void verifyTemporalOrdering() {
            var state = STARTED;
            range(0, 10)
                    .mapToObj(i -> createTransferProcess(String.valueOf(i), state))
                    .peek(this::delayByTenMillis)
                    .forEach(getTransferProcessStore()::save);

            assertThat(getTransferProcessStore().nextNotLeased(20, hasState(state.code())))
                    .extracting(TransferProcess::getId)
                    .map(Integer::parseInt)
                    .isSortedAccordingTo(Integer::compareTo);
        }

        @Test
        void verifyMostRecentlyUpdatedIsLast() throws InterruptedException {
            var all = range(0, 10)
                    .mapToObj(i -> createTransferProcess("id" + i, STARTED))
                    .peek(getTransferProcessStore()::save)
                    .toList();

            Thread.sleep(100);

            var fourth = all.get(3);
            fourth.updateStateTimestamp();
            getTransferProcessStore().save(fourth);

            var next = getTransferProcessStore().nextNotLeased(20, hasState(STARTED.code()));
            assertThat(next.indexOf(fourth)).isEqualTo(9);
        }

        @Test
        @DisplayName("Verifies that calling nextNotLeased locks the TP for any subsequent calls")
        void locksEntity() {
            var t = createTransferProcess("id1", INITIAL);
            getTransferProcessStore().save(t);

            getTransferProcessStore().nextNotLeased(100, hasState(INITIAL.code()));

            assertThat(isLeasedBy(t.getId(), CONNECTOR_NAME)).isTrue();
        }

        @Test
        void expiredLease() {
            var t = createTransferProcess("id1", INITIAL);
            getTransferProcessStore().save(t);

            leaseEntity(t.getId(), CONNECTOR_NAME, Duration.ofMillis(100));

            Awaitility.await().atLeast(Duration.ofMillis(100))
                    .atMost(getTestTimeout())
                    .until(() -> getTransferProcessStore().nextNotLeased(10, hasState(INITIAL.code())), hasSize(1));
        }

        @Test
        void shouldLeaseEntityUntilUpdate() {
            var initialTransferProcess = TestFunctions.initialTransferProcess();
            getTransferProcessStore().save(initialTransferProcess);

            var firstQueryResult = getTransferProcessStore().nextNotLeased(1, hasState(INITIAL.code()));
            assertThat(firstQueryResult).hasSize(1);

            var secondQueryResult = getTransferProcessStore().nextNotLeased(1, hasState(INITIAL.code()));
            assertThat(secondQueryResult).hasSize(0);

            var retrieved = firstQueryResult.get(0);
            getTransferProcessStore().save(retrieved);

            var thirdQueryResult = getTransferProcessStore().nextNotLeased(1, hasState(INITIAL.code()));
            assertThat(thirdQueryResult).hasSize(1);
        }

        @Test
        void avoidsStarvation() throws InterruptedException {
            for (int i = 0; i < 10; i++) {
                var process = createTransferProcess("test-process-" + i);
                getTransferProcessStore().save(process);
            }

            var list1 = getTransferProcessStore().nextNotLeased(5, hasState(INITIAL.code()));
            Thread.sleep(50); //simulate a short delay to generate different timestamps
            list1.forEach(tp -> {
                tp.updateStateTimestamp();
                getTransferProcessStore().save(tp);
            });
            var list2 = getTransferProcessStore().nextNotLeased(5, hasState(INITIAL.code()));
            assertThat(list1).isNotEqualTo(list2).doesNotContainAnyElementsOf(list2);
        }

        @Test
        void shouldLeaseOrderByStateTimestamp() {

            var all = range(0, 10)
                    .mapToObj(i -> createTransferProcess("id-" + i))
                    .peek(getTransferProcessStore()::save)
                    .toList();

            all.stream().limit(5)
                    .peek(this::delayByTenMillis)
                    .sorted(Comparator.comparing(TransferProcess::getStateTimestamp).reversed())
                    .forEach(f -> getTransferProcessStore().save(f));

            var elements = getTransferProcessStore().nextNotLeased(10, hasState(INITIAL.code()));
            assertThat(elements).hasSize(10).extracting(TransferProcess::getStateTimestamp).isSorted();
        }

        private void delayByTenMillis(TransferProcess t) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                // noop
            }
            t.updateStateTimestamp();
        }
    }

    @Nested
    class FindById {
        @Test
        void shouldFindEntityById() {
            var t = createTransferProcess("id1");
            getTransferProcessStore().save(t);

            var result = getTransferProcessStore().findById("id1");

            assertThat(result).usingRecursiveComparison().isEqualTo(t);
        }

        @Test
        void notExist() {
            var result = getTransferProcessStore().findById("not-exist");

            assertThat(result).isNull();
        }
    }

    @Nested
    class FindForCorrelationId {
        @Test
        void shouldFindEntityByCorrelationId() {
            var transferProcess = TestFunctions.createTransferProcessBuilder("id1").correlationId("correlationId").build();
            getTransferProcessStore().save(transferProcess);

            var res = getTransferProcessStore().findForCorrelationId("correlationId");

            assertThat(res).usingRecursiveComparison().isEqualTo(transferProcess);
        }

        @Test
        void notExist() {
            assertThat(getTransferProcessStore().findForCorrelationId("not-exist")).isNull();
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdate() {
            var transferProcess = createTransferProcess("id1", STARTED);
            getTransferProcessStore().save(transferProcess);
            //modify
            transferProcess.transitionCompleted();
            transferProcess.protocolMessageReceived("messageId");

            getTransferProcessStore().save(transferProcess);

            assertThat(getTransferProcessStore().findAll(QuerySpec.none()))
                    .hasSize(1)
                    .first().satisfies(actual -> {
                        assertThat(actual.getState()).isEqualTo(COMPLETED.code());
                        assertThat(actual.getProtocolMessages().isAlreadyReceived("messageId")).isTrue();
                    });
        }

        @Test
        @DisplayName("Verify that the lease on a TP is cleared by an update")
        void shouldBreakLease() {
            var t1 = createTransferProcess("id1");
            getTransferProcessStore().save(t1);
            // acquire lease
            leaseEntity(t1.getId(), CONNECTOR_NAME);

            t1.transitionProvisioning(ResourceManifest.Builder.newInstance().build()); //modify
            getTransferProcessStore().save(t1);

            // lease should be broken
            var notLeased = getTransferProcessStore().nextNotLeased(10, hasState(PROVISIONING.code()));

            assertThat(notLeased).usingRecursiveFieldByFieldElementComparator().containsExactly(t1);
        }

        @Test
        void leasedByOther_shouldThrowException() {
            var tpId = "id1";
            var t1 = createTransferProcess(tpId);
            getTransferProcessStore().save(t1);
            leaseEntity(tpId, "someone");

            t1.transitionProvisioning(ResourceManifest.Builder.newInstance().build()); //modify

            // leased by someone else -> throw exception
            assertThatThrownBy(() -> getTransferProcessStore().save(t1)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldReplaceDataRequest_whenItGetsTheIdUpdated() {
            var builder = TestFunctions.createTransferProcessBuilder("id1").state(STARTED.code());
            getTransferProcessStore().save(builder.build());
            var newTransferProcess = builder.correlationId("new-dr-id")
                    .assetId("new-asset")
                    .contractId("new-contract")
                    .protocol("test-protocol").build();
            getTransferProcessStore().save(newTransferProcess);

            var result = getTransferProcessStore().findAll(QuerySpec.none());

            assertThat(result)
                    .hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(newTransferProcess);
        }
    }

    @Nested
    class Delete {
        @Test
        void shouldDeleteTheEntityById() {
            var t1 = createTransferProcess("id1");
            getTransferProcessStore().save(t1);

            getTransferProcessStore().delete("id1");
            assertThat(getTransferProcessStore().findAll(QuerySpec.none())).isEmpty();
        }

        @Test
        void isLeasedBySelf_shouldThrowException() {
            var t1 = createTransferProcess("id1");
            getTransferProcessStore().save(t1);
            leaseEntity(t1.getId(), CONNECTOR_NAME);


            assertThatThrownBy(() -> getTransferProcessStore().delete("id1")).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void isLeasedByOther_shouldThrowException() {
            var t1 = createTransferProcess("id1");
            getTransferProcessStore().save(t1);

            leaseEntity(t1.getId(), "someone-else");

            assertThatThrownBy(() -> getTransferProcessStore().delete("id1")).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void notExist() {
            getTransferProcessStore().delete("not-exist");
            //no exception should be raised
        }
    }

    @Nested
    class FindAll {
        @Test
        void noQuerySpec() {
            var all = range(0, 10)
                    .mapToObj(i -> createTransferProcess("id" + i))
                    .peek(getTransferProcessStore()::save)
                    .collect(Collectors.toList());

            assertThat(getTransferProcessStore().findAll(QuerySpec.none())).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void verifyFiltering() {
            range(0, 10).forEach(i -> getTransferProcessStore().save(createTransferProcess("test-neg-" + i)));
            var querySpec = QuerySpec.Builder.newInstance().filter(Criterion.criterion("id", "=", "test-neg-3")).build();

            var result = getTransferProcessStore().findAll(querySpec);

            assertThat(result).extracting(TransferProcess::getId).containsOnly("test-neg-3");
        }

        @Test
        void shouldThrowException_whenInvalidOperator() {
            range(0, 10).forEach(i -> getTransferProcessStore().save(createTransferProcess("test-neg-" + i)));
            var querySpec = QuerySpec.Builder.newInstance().filter(Criterion.criterion("id", "foobar", "other")).build();

            assertThatThrownBy(() -> getTransferProcessStore().findAll(querySpec).toList()).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void queryByState() {
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1").state(800).build();
            getTransferProcessStore().save(tp);

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("state", "=", 800)))
                    .build();

            var result = getTransferProcessStore().findAll(query).toList();
            assertThat(result).hasSize(1).usingRecursiveFieldByFieldElementComparator().containsExactly(tp);
        }

        @Test
        void queryByTransferType() {
            range(0, 10).forEach(i -> getTransferProcessStore().save(TestFunctions.createTransferProcessBuilder("test-tp-" + i)
                    .transferType("type" + i)
                    .build()));
            var querySpec = QuerySpec.Builder.newInstance().filter(Criterion.criterion("transferType", "=", "type4")).build();

            var result = getTransferProcessStore().findAll(querySpec);

            assertThat(result).extracting(TransferProcess::getTransferType).containsOnly("type4");
        }

        @Test
        void verifySorting() {
            range(0, 10).forEach(i -> getTransferProcessStore().save(createTransferProcess("test-neg-" + i)));

            assertThat(getTransferProcessStore().findAll(QuerySpec.Builder.newInstance().sortField("id").sortOrder(SortOrder.ASC).build())).hasSize(10).isSortedAccordingTo(Comparator.comparing(TransferProcess::getId));
            assertThat(getTransferProcessStore().findAll(QuerySpec.Builder.newInstance().sortField("id").sortOrder(SortOrder.DESC).build())).hasSize(10).isSortedAccordingTo((c1, c2) -> c2.getId().compareTo(c1.getId()));
        }

        @Test
        void verifyPaging() {
            range(0, 10)
                    .mapToObj(i -> createTransferProcess(String.valueOf(i)))
                    .forEach(getTransferProcessStore()::save);

            var qs = QuerySpec.Builder.newInstance().limit(5).offset(3).build();
            assertThat(getTransferProcessStore().findAll(qs)).hasSize(5)
                    .extracting(TransferProcess::getId)
                    .map(Integer::parseInt)
                    .allMatch(id -> id >= 3 && id < 8);
        }

        @Test
        void verifyPaging_pageSizeLargerThanCollection() {

            range(0, 10)
                    .mapToObj(i -> createTransferProcess(String.valueOf(i)))
                    .forEach(getTransferProcessStore()::save);

            var qs = QuerySpec.Builder.newInstance().limit(20).offset(3).build();
            assertThat(getTransferProcessStore().findAll(qs))
                    .hasSize(7)
                    .extracting(TransferProcess::getId)
                    .map(Integer::parseInt)
                    .allMatch(id -> id >= 3 && id < 10);
        }

        @Test
        void verifyPaging_pageSizeOutsideCollection() {

            range(0, 10)
                    .mapToObj(i -> createTransferProcess(String.valueOf(i)))
                    .forEach(getTransferProcessStore()::save);

            var qs = QuerySpec.Builder.newInstance().limit(10).offset(12).build();
            assertThat(getTransferProcessStore().findAll(qs)).isEmpty();

        }

        @Test
        void queryByDataAddressProperty() {
            var da = TestFunctions.createDataAddressBuilder("test-type")
                    .property("key", "value")
                    .build();
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .contentDataAddress(da)
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("contentDataAddress.properties.key", "=", "value")))
                    .build();

            assertThat(getTransferProcessStore().findAll(query))
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(tp);
        }

        @Test
        void queryByDataAddress_propNotExist() {
            var da = TestFunctions.createDataAddressBuilder("test-type")
                    .property("key", "value")
                    .build();
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .contentDataAddress(da)
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("contentDataAddress.properties.notexist", "=", "value")))
                    .build();

            assertThat(getTransferProcessStore().findAll(query)).isEmpty();
        }

        @Test
        void queryByDataAddress_invalidKey_valueNotExist() {
            var da = TestFunctions.createDataAddressBuilder("test-type")
                    .property("key", "value")
                    .build();
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .contentDataAddress(da)
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("contentDataAddress.properties.key", "=", "notexist")))
                    .build();

            assertThat(getTransferProcessStore().findAll(query)).isEmpty();
        }

        @Test
        void queryByCorrelationId() {
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .correlationId("counterPartyId")
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("correlationId", "=", "counterPartyId")))
                    .build();

            var result = getTransferProcessStore().findAll(query);

            assertThat(result).usingRecursiveFieldByFieldElementComparatorIgnoringFields("deprovisionedResources").containsOnly(tp);
        }

        @Test
        void queryByDataRequestProperty_protocol() {
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .protocol("test-protocol")
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("protocol", "like", "test-protocol")))
                    .build();

            var result = getTransferProcessStore().findAll(query);

            assertThat(result).usingRecursiveFieldByFieldElementComparatorIgnoringFields("deprovisionedResources").containsOnly(tp);
        }

        @Test
        void queryByDataRequest_valueNotExist() {
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("dataRequest.id", "=", "notexist")))
                    .build();

            assertThat(getTransferProcessStore().findAll(query)).isEmpty();
        }

        @Test
        void queryByResourceManifestProperty() {
            var rm = ResourceManifest.Builder.newInstance()
                    .definitions(List.of(TestFunctions.TestResourceDef.Builder.newInstance().id("rd-id").transferProcessId("testprocess1").build())).build();
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .resourceManifest(rm)
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("resourceManifest.definitions.id", "=", "rd-id")))
                    .build();

            var result = getTransferProcessStore().findAll(query);
            assertThat(result).usingRecursiveFieldByFieldElementComparatorIgnoringFields("deprovisionedResources").containsOnly(tp);
        }

        @Test
        void queryByResourceManifest_valueNotExist() {
            var rm = ResourceManifest.Builder.newInstance()
                    .definitions(List.of(TestFunctions.TestResourceDef.Builder.newInstance().id("rd-id").transferProcessId("testprocess1").build())).build();
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .resourceManifest(rm)
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            // throws exception when an explicit mapping exists
            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("resourceManifest.definitions.id", "=", "someval")))
                    .build();
            assertThat(getTransferProcessStore().findAll(query)).isEmpty();
        }

        @Test
        void queryByProvisionedResourceSetProperty() {
            var resource = TestFunctions.TestProvisionedResource.Builder.newInstance()
                    .resourceDefinitionId("rd-id")
                    .transferProcessId("testprocess1")
                    .id("pr-id")
                    .build();
            var prs = ProvisionedResourceSet.Builder.newInstance()
                    .resources(List.of(resource))
                    .build();
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .provisionedResourceSet(prs)
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("provisionedResourceSet.resources.transferProcessId", "=", "testprocess1")))
                    .build();

            var result = getTransferProcessStore().findAll(query);
            assertThat(result).usingRecursiveFieldByFieldElementComparatorIgnoringFields("deprovisionedResources").containsOnly(tp);
        }

        @Test
        void queryByProvisionedResourceSet_valueNotExist() {
            var resource = TestFunctions.TestProvisionedResource.Builder.newInstance()
                    .resourceDefinitionId("rd-id")
                    .transferProcessId("testprocess1")
                    .id("pr-id")
                    .build();
            var prs = ProvisionedResourceSet.Builder.newInstance()
                    .resources(List.of(resource))
                    .build();
            var tp = TestFunctions.createTransferProcessBuilder("testprocess1")
                    .provisionedResourceSet(prs)
                    .build();
            getTransferProcessStore().save(tp);
            getTransferProcessStore().save(createTransferProcess("testprocess2"));


            // returns empty when the invalid value is embedded in JSON
            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("provisionedResourceSet.resources.id", "=", "someval")))
                    .build();

            assertThat(getTransferProcessStore().findAll(query)).isEmpty();
        }

        @Test
        void queryByDeprovisionedResourcesProperty() {
            var dp1 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid1")
                    .inProcess(true)
                    .build();
            var dp2 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid2")
                    .inProcess(false)
                    .build();
            var dp3 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid3")
                    .inProcess(false)
                    .build();

            var process1 = TestFunctions.createTransferProcessBuilder("test-pid1")
                    .deprovisionedResources(List.of(dp1, dp2))
                    .build();
            var process2 = TestFunctions.createTransferProcessBuilder("test-pid2")
                    .deprovisionedResources(List.of(dp3))
                    .build();

            getTransferProcessStore().save(process1);
            getTransferProcessStore().save(process2);

            var query = QuerySpec.Builder.newInstance()
                    .filter(Criterion.criterion("deprovisionedResources.inProcess", "=", true))
                    .build();

            var result = getTransferProcessStore().findAll(query);

            assertThat(result).hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(process1);
        }

        @Test
        void queryByDeprovisionedResourcesProperty_multipleCriteria() {
            var dp1 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid1")
                    .inProcess(true)
                    .build();
            var dp2 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid2")
                    .inProcess(false)
                    .build();
            var dp3 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid3")
                    .inProcess(false)
                    .build();

            var process1 = TestFunctions.createTransferProcessBuilder("test-pid1")
                    .deprovisionedResources(List.of(dp1, dp2))
                    .build();
            var process2 = TestFunctions.createTransferProcessBuilder("test-pid2")
                    .deprovisionedResources(List.of(dp3))
                    .build();

            getTransferProcessStore().save(process1);
            getTransferProcessStore().save(process2);

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(
                            new Criterion("deprovisionedResources.inProcess", "=", false),
                            new Criterion("id", "=", "test-pid1")
                    ))
                    .build();

            var result = getTransferProcessStore().findAll(query).collect(Collectors.toList());

            assertThat(result).hasSize(1)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactly(process1);
        }

        @Test
        void queryByDeprovisionedResourcesProperty_multipleResults() {
            var dp1 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid1")
                    .inProcess(true)
                    .build();
            var dp2 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid2")
                    .inProcess(false)
                    .build();
            var dp3 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid3")
                    .inProcess(false)
                    .build();

            var process1 = TestFunctions.createTransferProcessBuilder("test-pid1")
                    .deprovisionedResources(List.of(dp1, dp2))
                    .build();
            var process2 = TestFunctions.createTransferProcessBuilder("test-pid2")
                    .deprovisionedResources(List.of(dp3))
                    .build();

            getTransferProcessStore().save(process1);
            getTransferProcessStore().save(process2);

            var query = QuerySpec.Builder.newInstance()
                    .filter(Criterion.criterion("deprovisionedResources.inProcess", "=", false))
                    .build();

            var result = getTransferProcessStore().findAll(query).collect(Collectors.toList());

            assertThat(result).hasSize(2)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactlyInAnyOrder(process1, process2);
        }

        @Test
        void queryByDeprovisionedResources_propNotExist() {
            var dp1 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid1")
                    .inProcess(true)
                    .build();
            var dp2 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid2")
                    .inProcess(false)
                    .build();

            var process1 = TestFunctions.createTransferProcessBuilder("test-pid1")
                    .deprovisionedResources(List.of(dp1, dp2))
                    .build();
            getTransferProcessStore().save(process1);

            var query = QuerySpec.Builder.newInstance()
                    .filter(Criterion.criterion("deprovisionedResources.foobar", "=", "barbaz"))
                    .build();

            assertThat(getTransferProcessStore().findAll(query)).isEmpty();
        }

        @Test
        void queryByDeprovisionedResources_valueNotExist() {
            var dp1 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid1")
                    .inProcess(true)
                    .errorMessage("not enough resources")
                    .build();
            var dp2 = DeprovisionedResource.Builder.newInstance()
                    .provisionedResourceId("test-rid2")
                    .inProcess(false)
                    .errorMessage("undefined error")
                    .build();

            var process1 = TestFunctions.createTransferProcessBuilder("test-pid1")
                    .deprovisionedResources(List.of(dp1, dp2))
                    .build();
            getTransferProcessStore().save(process1);

            var query = QuerySpec.Builder.newInstance()
                    .filter(Criterion.criterion("deprovisionedResources.errorMessage", "=", "notexist"))
                    .build();

            var result = getTransferProcessStore().findAll(query).collect(Collectors.toList());

            assertThat(result).isEmpty();
        }

        @Test
        void queryByLease() {
            getTransferProcessStore().save(createTransferProcess("testprocess1"));

            var query = QuerySpec.Builder.newInstance()
                    .filter(List.of(new Criterion("lease.leasedBy", "=", "foobar")))
                    .build();

            assertThat(getTransferProcessStore().findAll(query)).isEmpty();
        }

        @Test
        void shouldThrowException_whenSortingByNotExistentField() {
            range(0, 10).forEach(i -> getTransferProcessStore().save(createTransferProcess("test-neg-" + i)));

            var query = QuerySpec.Builder.newInstance().sortField("notexist").sortOrder(SortOrder.DESC).build();

            assertThatThrownBy(() -> getTransferProcessStore().findAll(query).toList())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class FindByIdAndLease {
        @Test
        void shouldReturnTheEntityAndLeaseIt() {
            var id = UUID.randomUUID().toString();
            getTransferProcessStore().save(createTransferProcess(id));

            var result = getTransferProcessStore().findByIdAndLease(id);

            assertThat(result).isSucceeded();
            assertThat(isLeasedBy(id, CONNECTOR_NAME)).isTrue();
        }

        @Test
        void shouldReturnNotFound_whenEntityDoesNotExist() {
            var result = getTransferProcessStore().findByIdAndLease("unexistent");

            assertThat(result).isFailed().extracting(StoreFailure::getReason).isEqualTo(NOT_FOUND);
        }

        @Test
        void shouldReturnAlreadyLeased_whenEntityIsAlreadyLeased() {
            var id = UUID.randomUUID().toString();
            getTransferProcessStore().save(createTransferProcess(id));
            leaseEntity(id, "other owner");

            var result = getTransferProcessStore().findByIdAndLease(id);

            assertThat(result).isFailed().extracting(StoreFailure::getReason).isEqualTo(ALREADY_LEASED);
        }
    }

}
