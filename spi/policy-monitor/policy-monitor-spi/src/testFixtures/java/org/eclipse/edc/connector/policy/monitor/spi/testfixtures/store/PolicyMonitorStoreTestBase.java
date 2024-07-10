/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.policy.monitor.spi.testfixtures.store;

import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntry;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntryStates;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorStore;
import org.eclipse.edc.spi.entity.Entity;
import org.eclipse.edc.spi.entity.MutableEntity;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.result.StoreFailure;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntryStates.COMPLETED;
import static org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntryStates.STARTED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.persistence.StateEntityStore.hasState;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.ALREADY_LEASED;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.NOT_FOUND;
import static org.hamcrest.Matchers.hasSize;

public abstract class PolicyMonitorStoreTestBase {

    protected static final String CONNECTOR_NAME = "test-connector";

    /**
     * determines the amount of time (default = 500ms) before an async test using Awaitility fails. This may be useful if using remote
     * or non-self-contained databases.
     */
    protected Duration getTestTimeout() {
        return Duration.ofMillis(500);
    }

    protected abstract PolicyMonitorStore getStore();

    protected abstract void leaseEntity(String entityId, String owner, Duration duration);

    protected void leaseEntity(String entityId, String owner) {
        leaseEntity(entityId, owner, Duration.ofSeconds(60));
    }

    protected abstract boolean isLeasedBy(String entityId, String owner);

    private PolicyMonitorEntry createPolicyMonitorEntry(String id, PolicyMonitorEntryStates state) {
        return PolicyMonitorEntry.Builder.newInstance()
                .id(id)
                .contractId(UUID.randomUUID().toString())
                .state(state.code())
                .build();
    }

    @Nested
    class Create {

        @Test
        void shouldStoreEntity_whenItDoesNotAlreadyExist() {
            var entry = createPolicyMonitorEntry(UUID.randomUUID().toString(), STARTED);
            getStore().save(entry);

            var result = getStore().findById(entry.getId());

            assertThat(result).isNotNull().usingRecursiveComparison().isEqualTo(entry);
            assertThat(result.getCreatedAt()).isGreaterThan(0);
        }

        @Test
        void shouldUpdate_whenEntityAlreadyExist() {
            var entry = createPolicyMonitorEntry(UUID.randomUUID().toString(), STARTED);
            getStore().save(entry);

            entry.transitionToCompleted();
            getStore().save(entry);

            var result = getStore().findById(entry.getId());

            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo(COMPLETED.code());
        }
    }

    @Nested
    class NextNotLeased {
        @Test
        void shouldReturnNotLeasedItems() {
            var state = STARTED;
            var all = range(0, 5)
                    .mapToObj(i -> createPolicyMonitorEntry("id-" + i, state))
                    .peek(getStore()::save)
                    .peek(this::delayByTenMillis)
                    .toList();

            var leased = getStore().nextNotLeased(2, hasState(state.code()));

            assertThat(leased).hasSize(2).extracting(PolicyMonitorEntry::getId)
                    .isSubsetOf(all.stream().map(Entity::getId).toList())
                    .allMatch(id -> isLeasedBy(id, CONNECTOR_NAME));

            assertThat(leased).extracting(MutableEntity::getUpdatedAt).isSorted();
        }

        @Test
        void shouldReturnFreeEntities() {
            var state = STARTED;
            var all = range(0, 5)
                    .mapToObj(i -> createPolicyMonitorEntry("id-" + i, state))
                    .peek(getStore()::save)
                    .toList();

            var firstLeased = getStore().nextNotLeased(2, hasState(state.code()));
            var leased = getStore().nextNotLeased(2, hasState(state.code()));

            assertThat(leased.stream().map(Entity::getId)).hasSize(2)
                    .isSubsetOf(all.stream().map(Entity::getId).toList())
                    .doesNotContainAnyElementsOf(firstLeased.stream().map(Entity::getId).toList());
        }

        @Test
        void shouldReturnFreeItemInTheExpectedState() {
            range(0, 5)
                    .mapToObj(i -> createPolicyMonitorEntry("id-" + i, STARTED))
                    .forEach(getStore()::save);

            var leased = getStore().nextNotLeased(2, hasState(COMPLETED.code()));

            assertThat(leased).isEmpty();
        }

        @Test
        void shouldLeaseAgainAfterTimePassed() {
            var entry = createPolicyMonitorEntry(UUID.randomUUID().toString(), STARTED);
            getStore().save(entry);

            leaseEntity(entry.getId(), CONNECTOR_NAME, Duration.ofMillis(100));

            await().atMost(getTestTimeout())
                    .until(() -> getStore().nextNotLeased(1, hasState(STARTED.code())), hasSize(1));
        }

        @Test
        void shouldReturnReleasedEntityByUpdate() {
            var entry = createPolicyMonitorEntry(UUID.randomUUID().toString(), STARTED);
            getStore().save(entry);

            var firstLeased = getStore().nextNotLeased(1, hasState(STARTED.code()));
            assertThat(firstLeased).hasSize(1);

            var secondLeased = getStore().nextNotLeased(1, hasState(STARTED.code()));
            assertThat(secondLeased).isEmpty();

            getStore().save(firstLeased.get(0));

            var thirdLeased = getStore().nextNotLeased(1, hasState(STARTED.code()));
            assertThat(thirdLeased).hasSize(1);
        }

        @Test
        void shouldLeaseOrderByStateTimestamp() {

            var all = range(0, 10)
                    .mapToObj(i -> createPolicyMonitorEntry("id-" + i, STARTED))
                    .peek(getStore()::save)
                    .toList();

            all.stream().limit(5)
                    .peek(this::delayByTenMillis)
                    .sorted(Comparator.comparing(PolicyMonitorEntry::getStateTimestamp).reversed())
                    .forEach(f -> getStore().save(f));

            var elements = getStore().nextNotLeased(10, hasState(STARTED.code()));
            assertThat(elements).hasSize(10).extracting(PolicyMonitorEntry::getStateTimestamp).isSorted();
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
            getStore().save(createPolicyMonitorEntry(id, STARTED));

            var result = getStore().findByIdAndLease(id);

            assertThat(result).isSucceeded();
            assertThat(isLeasedBy(id, CONNECTOR_NAME)).isTrue();
        }

        @Test
        void shouldReturnNotFound_whenEntityDoesNotExist() {
            var result = getStore().findByIdAndLease("unexistent");

            assertThat(result).isFailed().extracting(StoreFailure::getReason).isEqualTo(NOT_FOUND);
        }

        @Test
        void shouldReturnAlreadyLeased_whenEntityIsAlreadyLeased() {
            var id = UUID.randomUUID().toString();
            getStore().save(createPolicyMonitorEntry(id, STARTED));
            leaseEntity(id, "other owner");

            var result = getStore().findByIdAndLease(id);

            assertThat(result).isFailed().extracting(StoreFailure::getReason).isEqualTo(ALREADY_LEASED);
        }
    }
}
