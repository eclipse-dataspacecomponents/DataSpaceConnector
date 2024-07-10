/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.edr.spi.store;

import org.eclipse.edc.edr.spi.TestFunctions;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Default test suite for {@link EndpointDataReferenceEntryIndex} implementors
 */
public abstract class EndpointDataReferenceEntryIndexTestBase {

    @Test
    void save() {

        var tpId = "tp1";
        var assetId = "asset1";

        var entry = TestFunctions.edrEntry(assetId, randomUUID().toString(), tpId, randomUUID().toString());

        getStore().save(entry);

        var results = getStore().findById(entry.getTransferProcessId());

        assertThat(results).isNotNull().usingRecursiveComparison().isEqualTo(entry);

    }

    @Test
    void update() {

        var tpId = "tp1";
        var assetId = "asset1";

        var entry = TestFunctions.edrEntry(assetId, randomUUID().toString(), tpId, randomUUID().toString());

        getStore().save(entry);

        var dbEntry = getStore().findById(entry.getTransferProcessId());
        assertThat(dbEntry).isNotNull().usingRecursiveComparison().isEqualTo(entry);

        entry = TestFunctions.edrEntry(assetId, randomUUID().toString(), tpId, randomUUID().toString());
        getStore().save(entry);

        dbEntry = getStore().findById(entry.getTransferProcessId());
        assertThat(dbEntry).isNotNull().usingRecursiveComparison().isEqualTo(entry);

        var results = getStore().query(QuerySpec.max());

        assertThat(results.succeeded()).isTrue();
        assertThat(results.getContent()).usingRecursiveFieldByFieldElementComparator().containsOnly(entry);
    }

    @Test
    void query_noQuerySpec() {
        var all = IntStream.range(0, 10)
                .mapToObj(i -> TestFunctions.edrEntry("assetId" + i, "agreementId" + i, "tpId" + i, "cnId" + i))
                .peek(entry -> getStore().save(entry))
                .collect(Collectors.toList());

        var results = getStore().query(QuerySpec.max());

        assertThat(results.succeeded()).isTrue();
        assertThat(results.getContent()).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrderElementsOf(all);

    }

    @ParameterizedTest
    @ArgumentsSource(FilterArgumentProvider.class)
    void query_withQuerySpec(String field, Function<EndpointDataReferenceEntry, String> mapping) {
        IntStream.range(0, 10)
                .mapToObj(i -> TestFunctions.edrEntry("assetId" + i, "agreementId" + i, "tpId" + i, "cnId" + i))
                .forEach(entry -> getStore().save(entry));


        var entry = TestFunctions.edrEntry("assetId", "agreementId", "tpId", "cnId");
        getStore().save(entry);

        var filter = Criterion.Builder.newInstance()
                .operandLeft(field)
                .operator("=")
                .operandRight(mapping.apply(entry))
                .build();

        var results = getStore().query(QuerySpec.Builder.newInstance().filter(filter).build());

        assertThat(results.succeeded()).isTrue();
        assertThat(results.getContent()).usingRecursiveFieldByFieldElementComparator().containsOnly(entry);

    }

    @Test
    void query_withOrderBy() {
        IntStream.range(0, 10)
                .mapToObj(i -> TestFunctions.edrEntry("assetId" + i, "agreementId" + i, "tpId" + i, "cnId" + i))
                .forEach(entry -> getStore().save(entry));

        var results = getStore().query(QuerySpec.Builder.newInstance().sortField("createdAt").sortOrder(SortOrder.DESC).build());

        assertThat(results.succeeded()).isTrue();
        assertThat(results.getContent())
                .isSortedAccordingTo(Comparator.comparing(EndpointDataReferenceEntry::getCreatedAt).reversed());

    }

    @Test
    void delete_shouldDelete_WhenFound() {

        var entry = TestFunctions.edrEntry("assetId", "agreementId", "tpId", "cnId");
        getStore().save(entry);

        assertThat(getStore().delete(entry.getTransferProcessId()))
                .extracting(StoreResult::getContent)
                .usingRecursiveComparison()
                .isEqualTo(entry);

        var results = getStore().query(QuerySpec.max());

        assertThat(results.succeeded()).isTrue();
        assertThat(results.getContent()).hasSize(0);

    }

    @Test
    void deleteX_shouldReturnError_whenNotFound() {
        assertThat(getStore().delete("notFound"))
                .extracting(StoreResult::reason)
                .isEqualTo(StoreFailure.Reason.NOT_FOUND);
    }

    protected abstract EndpointDataReferenceEntryIndex getStore();


    static class FilterArgumentProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            return Stream.of(
                    Arguments.of("agreementId", (Function<EndpointDataReferenceEntry, String>) EndpointDataReferenceEntry::getAgreementId),
                    Arguments.of("transferProcessId", (Function<EndpointDataReferenceEntry, String>) EndpointDataReferenceEntry::getTransferProcessId),
                    Arguments.of("assetId", (Function<EndpointDataReferenceEntry, String>) EndpointDataReferenceEntry::getAssetId),
                    Arguments.of("contractNegotiationId", (Function<EndpointDataReferenceEntry, String>) EndpointDataReferenceEntry::getContractNegotiationId),
                    Arguments.of("providerId", (Function<EndpointDataReferenceEntry, String>) EndpointDataReferenceEntry::getProviderId)
            );
        }
    }
}
