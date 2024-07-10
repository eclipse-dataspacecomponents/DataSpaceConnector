/*
 *  Copyright (c) 2021 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial API and Implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.edc.connector.controlplane.catalog;

import org.assertj.core.api.iterable.ThrowingExtractor;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetResolver;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.connector.controlplane.catalog.spi.DistributionResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.ContractDefinitionResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.dataaddress.httpdata.spi.HttpDataAddressSchema;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.agent.ParticipantAgent;
import org.eclipse.edc.spi.message.Range;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.policy.model.PolicyType.OFFER;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasetResolverImplTest {

    private final ContractDefinitionResolver contractDefinitionResolver = mock(ContractDefinitionResolver.class);
    private final AssetIndex assetIndex = mock(AssetIndex.class);
    private final PolicyDefinitionStore policyStore = mock(PolicyDefinitionStore.class);
    private final DistributionResolver distributionResolver = mock(DistributionResolver.class);

    private DatasetResolver datasetResolver;

    @BeforeEach
    void setUp() {
        datasetResolver = new DatasetResolverImpl(contractDefinitionResolver, assetIndex, policyStore, distributionResolver,
                CriterionOperatorRegistryImpl.ofDefaults());
    }

    @Test
    void search_shouldReturnOneDatasetPerAsset() {
        var dataService = createDataService();
        var contractDefinition = contractDefinitionBuilder("definitionId").contractPolicyId("contractPolicyId").build();
        var contractPolicy = Policy.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance().dataService(dataService).format("format").build();
        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(contractDefinition));
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenReturn(Stream.of(createAsset("assetId").property("key", "value").build()));
        when(policyStore.findById("contractPolicyId")).thenReturn(PolicyDefinition.Builder.newInstance().policy(contractPolicy).build());
        when(distributionResolver.getDistributions(isA(Asset.class))).thenReturn(List.of(distribution));

        var datasets = datasetResolver.query(createParticipantAgent(), QuerySpec.none());

        assertThat(datasets).isNotNull().hasSize(1).first().satisfies(dataset -> {
            assertThat(dataset.getId()).isEqualTo("assetId");
            assertThat(dataset.getDistributions()).hasSize(1).first().isEqualTo(distribution);
            assertThat(dataset.getOffers()).hasSize(1).allSatisfy((id, policy) -> {
                assertThat(ContractOfferId.parseId(id)).isSucceeded().extracting(ContractOfferId::definitionPart).asString().isEqualTo("definitionId");
                assertThat(policy.getType()).isEqualTo(OFFER);
                assertThat(policy.getTarget()).isEqualTo(null);
            });
            assertThat(dataset.getProperties()).contains(entry("key", "value"));
        });
    }

    @Test
    void query_shouldReturnNoDataset_whenPolicyNotFound() {
        var contractDefinition = contractDefinitionBuilder("definitionId").contractPolicyId("contractPolicyId").build();
        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(contractDefinition));
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenReturn(Stream.of(createAsset("id").build()));
        when(policyStore.findById("contractPolicyId")).thenReturn(null);

        var datasets = datasetResolver.query(createParticipantAgent(), QuerySpec.none());

        assertThat(datasets).isNotNull().isEmpty();
    }

    @Test
    void query_shouldReturnOneDataset_whenMultipleDefinitionsOnSameAsset() {
        var policy1 = Policy.Builder.newInstance().inheritsFrom("inherits1").build();
        var policy2 = Policy.Builder.newInstance().inheritsFrom("inherits2").build();
        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(
                contractDefinitionBuilder("definition1").contractPolicyId("policy1").build(),
                contractDefinitionBuilder("definition2").contractPolicyId("policy2").build()
        ));
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenAnswer(i -> Stream.of(createAsset("assetId").build()));
        when(policyStore.findById("policy1")).thenReturn(PolicyDefinition.Builder.newInstance().policy(policy1).build());
        when(policyStore.findById("policy2")).thenReturn(PolicyDefinition.Builder.newInstance().policy(policy2).build());

        var datasets = datasetResolver.query(createParticipantAgent(), QuerySpec.none());

        assertThat(datasets).hasSize(1).first().satisfies(dataset -> {
            assertThat(dataset.getId()).isEqualTo("assetId");
            assertThat(dataset.getOffers()).hasSize(2)
                    .anySatisfy((id, policy) -> {
                        assertThat(ContractOfferId.parseId(id)).isSucceeded().extracting(ContractOfferId::definitionPart).asString().isEqualTo("definition1");
                        assertThat(policy.getInheritsFrom()).isEqualTo("inherits1");
                    })
                    .anySatisfy((id, policy) -> {
                        assertThat(ContractOfferId.parseId(id)).isSucceeded().extracting(ContractOfferId::definitionPart).asString().isEqualTo("definition2");
                        assertThat(policy.getInheritsFrom()).isEqualTo("inherits2");
                    });
        });
    }

    @Test
    void query_shouldFilterAssetsByPassedCriteria() {
        var definitionCriterion = new Criterion(EDC_NAMESPACE + "id", "=", "id");
        var contractDefinition = contractDefinitionBuilder("definitionId")
                .assetsSelector(List.of(definitionCriterion))
                .contractPolicyId("contractPolicyId")
                .build();
        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(contractDefinition));
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenReturn(Stream.of(createAsset("id").property("key", "value").build()));
        when(policyStore.findById("contractPolicyId")).thenReturn(PolicyDefinition.Builder.newInstance().policy(Policy.Builder.newInstance().build()).build());
        var additionalCriterion = new Criterion(EDC_NAMESPACE + "key", "=", "value");
        var querySpec = QuerySpec.Builder.newInstance().filter(additionalCriterion).build();

        datasetResolver.query(createParticipantAgent(), querySpec);

        verify(assetIndex).queryAssets(and(
                isA(QuerySpec.class),
                argThat(q -> q.getFilterExpression().contains(additionalCriterion))
        ));
    }

    @Test
    void query_shouldLimitDataset_whenSingleDefinitionAndMultipleAssets_contained() {
        var contractDefinition = contractDefinitionBuilder("definitionId").contractPolicyId("contractPolicyId").build();
        var contractPolicy = Policy.Builder.newInstance().build();
        var assets = range(0, 10).mapToObj(it -> createAsset(String.valueOf(it)).build()).toList();
        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(contractDefinition));
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenAnswer(i -> assets.stream());
        when(policyStore.findById("contractPolicyId")).thenReturn(PolicyDefinition.Builder.newInstance().policy(contractPolicy).build());
        var querySpec = QuerySpec.Builder.newInstance().range(new Range(2, 5)).build();

        var datasets = datasetResolver.query(createParticipantAgent(), querySpec);

        assertThat(datasets).hasSize(3).map(getId()).containsExactly("2", "3", "4");
    }

    @Test
    void query_shouldLimitDataset_whenSingleDefinitionAndMultipleAssets_overflowing() {
        var contractDefinition = contractDefinitionBuilder("definitionId").contractPolicyId("contractPolicyId").build();
        var contractPolicy = Policy.Builder.newInstance().build();
        var assets = range(0, 10).mapToObj(it -> createAsset(String.valueOf(it)).build()).toList();
        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(contractDefinition));
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenAnswer(i -> assets.stream());
        when(policyStore.findById(any())).thenReturn(PolicyDefinition.Builder.newInstance().policy(contractPolicy).build());
        var querySpec = QuerySpec.Builder.newInstance().range(new Range(7, 15)).build();

        var datasets = datasetResolver.query(createParticipantAgent(), querySpec);

        assertThat(datasets).hasSize(3).map(getId()).containsExactly("7", "8", "9");
    }

    @Test
    void query_shouldLimitDataset_whenMultipleDefinitionAndMultipleAssets_across() {
        var contractDefinitions = range(0, 2).mapToObj(it -> contractDefinitionBuilder(String.valueOf(it)).build()).toList();
        var contractPolicy = Policy.Builder.newInstance().build();
        var assets = range(0, 20).mapToObj(it -> createAsset(String.valueOf(it)).build()).toList();
        when(contractDefinitionResolver.definitionsFor(any())).thenAnswer(it -> contractDefinitions.stream());
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenAnswer(i -> assets.stream());
        when(policyStore.findById(any())).thenReturn(PolicyDefinition.Builder.newInstance().policy(contractPolicy).build());
        var querySpec = QuerySpec.Builder.newInstance().range(new Range(6, 14)).build();

        var datasets = datasetResolver.query(createParticipantAgent(), querySpec);

        assertThat(datasets).hasSize(8).map(getId()).containsExactly("6", "7", "8", "9", "10", "11", "12", "13");
    }

    @Test
    void query_shouldLimitDataset_whenMultipleDefinitionsWithSameAssets() {
        var contractDefinitions = range(0, 2).mapToObj(it -> contractDefinitionBuilder(String.valueOf(it)).build()).toList();
        var contractPolicy = Policy.Builder.newInstance().build();
        var assets = range(0, 10).mapToObj(it -> createAsset(String.valueOf(it)).build()).toList();
        when(contractDefinitionResolver.definitionsFor(any())).thenAnswer(it -> contractDefinitions.stream());
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenAnswer(i -> assets.stream());
        when(policyStore.findById(any())).thenReturn(PolicyDefinition.Builder.newInstance().policy(contractPolicy).build());
        var querySpec = QuerySpec.Builder.newInstance().range(new Range(6, 8)).build();

        var datasets = datasetResolver.query(createParticipantAgent(), querySpec);

        assertThat(datasets).hasSize(2)
                .allSatisfy(dataset -> assertThat(dataset.getOffers()).hasSize(2))
                .map(getId()).containsExactly("6", "7");
    }

    @Test
    void query_shouldReturnCatalogWithinCatalog_whenAssetIsCatalogAsset() {
        var contractDefinition = contractDefinitionBuilder("definitionId").contractPolicyId("contractPolicyId").build();
        var contractPolicy = Policy.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance().dataService(DataService.Builder.newInstance()
                        .endpointDescription("test-asset-desc")
                        .endpointUrl("https://foo.bar/baz")
                        .build())
                .format(HttpDataAddressSchema.HTTP_DATA_TYPE).build();

        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(contractDefinition));
        when(assetIndex.queryAssets(isA(QuerySpec.class))).thenReturn(Stream.of(createAsset("assetId")
                .property(Asset.PROPERTY_IS_CATALOG, true)
                .dataAddress(DataAddress.Builder.newInstance().type(HttpDataAddressSchema.HTTP_DATA_TYPE).build())
                .build()));
        when(policyStore.findById("contractPolicyId")).thenReturn(PolicyDefinition.Builder.newInstance().policy(contractPolicy).build());
        when(distributionResolver.getDistributions(isA(Asset.class))).thenReturn(List.of(distribution));

        var datasets = datasetResolver.query(createParticipantAgent(), QuerySpec.none());

        assertThat(datasets).isNotNull().hasSize(1).first().satisfies(dataset -> {
            assertThat(dataset).isInstanceOf(Catalog.class);
            assertThat(dataset.getId()).isEqualTo("assetId");
            assertThat(dataset.getOffers()).hasSize(1).allSatisfy((id, policy) -> {
                assertThat(ContractOfferId.parseId(id)).isSucceeded().extracting(ContractOfferId::definitionPart).asString().isEqualTo("definitionId");
                assertThat(policy.getType()).isEqualTo(OFFER);
                assertThat(policy.getTarget()).isEqualTo(null);
            });
        });
    }

    @Test
    void getById_shouldReturnDataset() {
        var policy1 = Policy.Builder.newInstance().inheritsFrom("inherits1").build();
        var policy2 = Policy.Builder.newInstance().inheritsFrom("inherits2").build();
        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(
                contractDefinitionBuilder("definition1").contractPolicyId("policy1").build(),
                contractDefinitionBuilder("definition2").contractPolicyId("policy2").build()
        ));
        when(assetIndex.findById(any())).thenReturn(createAsset("datasetId").build());
        when(policyStore.findById("policy1")).thenReturn(PolicyDefinition.Builder.newInstance().policy(policy1).build());
        when(policyStore.findById("policy2")).thenReturn(PolicyDefinition.Builder.newInstance().policy(policy2).build());
        var participantAgent = createParticipantAgent();

        var dataset = datasetResolver.getById(participantAgent, "datasetId");

        assertThat(dataset).isNotNull();
        assertThat(dataset.getId()).isEqualTo("datasetId");
        assertThat(dataset.getOffers()).hasSize(2)
                .anySatisfy((id, policy) -> {
                    assertThat(ContractOfferId.parseId(id)).isSucceeded().extracting(ContractOfferId::definitionPart).isEqualTo("definition1");
                    assertThat(policy.getInheritsFrom()).isEqualTo("inherits1");
                })
                .anySatisfy((id, policy) -> {
                    assertThat(ContractOfferId.parseId(id)).isSucceeded().extracting(ContractOfferId::definitionPart).isEqualTo("definition2");
                    assertThat(policy.getInheritsFrom()).isEqualTo("inherits2");
                });
        verify(assetIndex).findById("datasetId");
        verify(contractDefinitionResolver).definitionsFor(participantAgent);
    }

    @Test
    void getById_shouldReturnNull_whenAssetNotFound() {
        when(contractDefinitionResolver.definitionsFor(any())).thenReturn(Stream.of(
                contractDefinitionBuilder("definition1").contractPolicyId("policy1").build()
        ));
        when(assetIndex.findById(any())).thenReturn(null);
        var participantAgent = createParticipantAgent();

        var dataset = datasetResolver.getById(participantAgent, "datasetId");

        assertThat(dataset).isNull();
    }

    private ContractDefinition.Builder contractDefinitionBuilder(String id) {
        return ContractDefinition.Builder.newInstance()
                .id(id)
                .accessPolicyId("access")
                .contractPolicyId("contract");
    }

    private Asset.Builder createAsset(String id) {
        return Asset.Builder.newInstance().id(id).name("test asset " + id);
    }

    private ParticipantAgent createParticipantAgent() {
        return new ParticipantAgent(emptyMap(), emptyMap());
    }

    private DataService createDataService() {
        return DataService.Builder.newInstance().build();
    }

    @NotNull
    private ThrowingExtractor<Dataset, Object, RuntimeException> getId() {
        return it -> it.getProperty(Asset.PROPERTY_ID);
    }

}
