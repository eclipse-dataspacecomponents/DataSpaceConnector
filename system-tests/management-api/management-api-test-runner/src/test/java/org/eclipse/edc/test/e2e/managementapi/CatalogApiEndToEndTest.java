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

package org.eclipse.edc.test.e2e.managementapi;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_PREFIX;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class CatalogApiEndToEndTest {

    abstract static class Tests {

        @Test
        void requestCatalog_shouldReturnCatalog_withoutQuerySpec(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(EDC_PREFIX, EDC_NAMESPACE))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl())
                    .add("protocol", "dataspace-protocol-http")
                    .build();

            context.baseRequest()
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v3/catalog/request")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(TYPE, is("dcat:Catalog"));
        }

        @Test
        void requestCatalog_shouldReturnCatalog_withQuerySpec(ManagementEndToEndTestContext context, AssetIndex assetIndex,
                                                              PolicyDefinitionStore policyDefinitionStore,
                                                              ContractDefinitionStore contractDefinitionStore) {
            var policyId = UUID.randomUUID().toString();

            var cd = ContractDefinition.Builder.newInstance()
                    .id(UUID.randomUUID().toString())
                    .contractPolicyId(policyId)
                    .accessPolicyId(policyId)
                    .build();

            var policy = Policy.Builder.newInstance()
                    .build();

            policyDefinitionStore.create(PolicyDefinition.Builder.newInstance().id(policyId).policy(policy).build());
            contractDefinitionStore.save(cd);

            assetIndex.create(createAsset("id-1", "test-type").build());
            assetIndex.create(createAsset("id-2", "test-type").build());

            var criteria = createArrayBuilder()
                    .add(createObjectBuilder()
                            .add(TYPE, "Criterion")
                            .add("operandLeft", EDC_NAMESPACE + "id")
                            .add("operator", "=")
                            .add("operandRight", "id-2")
                            .build()
                    )
                    .build();

            var querySpec = createObjectBuilder()
                    .add(TYPE, "QuerySpec")
                    .add("filterExpression", criteria)
                    .add("limit", 1);

            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(EDC_PREFIX, EDC_NAMESPACE))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl())
                    .add("protocol", "dataspace-protocol-http")
                    .add("querySpec", querySpec)
                    .build();

            context.baseRequest()
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v3/catalog/request")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(TYPE, is("dcat:Catalog"))
                    .body("'dcat:dataset'.id", is("id-2"));
        }

        @Test
        void requestCatalog_whenAssetIsCatalogAsset_shouldReturnCatalogOfCatalogs(ManagementEndToEndTestContext context, AssetIndex assetIndex,
                                                                                  PolicyDefinitionStore policyDefinitionStore,
                                                                                  ContractDefinitionStore contractDefinitionStore) {
            // create and store policy
            var policyId = UUID.randomUUID().toString();
            var policy = Policy.Builder.newInstance().build();
            policyDefinitionStore.create(PolicyDefinition.Builder.newInstance().id(policyId).policy(policy).build());

            // create CatalogAsset
            var catalogAssetId = "catalog-asset-" + UUID.randomUUID();
            var httpData = createAsset(catalogAssetId, "HttpData")
                    .property(Asset.PROPERTY_IS_CATALOG, true)
                    .build();
            httpData.getDataAddress().getProperties().put(EDC_NAMESPACE + "baseUrl", "http://quizzqua.zz/buzz");
            assetIndex.create(httpData);

            // create conventional asset
            var normalAssetId = "normal-asset-" + UUID.randomUUID();
            assetIndex.create(createAsset(normalAssetId, "test-type").build());

            // create ContractDefinition
            var cd = ContractDefinition.Builder.newInstance()
                    .id(UUID.randomUUID().toString())
                    .contractPolicyId(policyId)
                    .accessPolicyId(policyId)
                    .assetsSelector(List.of(Criterion.criterion("id", "in", List.of(catalogAssetId, normalAssetId))))
                    .build();
            contractDefinitionStore.save(cd);

            // request all assets
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(EDC_PREFIX, EDC_NAMESPACE))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl())
                    .add("protocol", "dataspace-protocol-http")
                    .build();

            context.baseRequest()
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v3/catalog/request")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(TYPE, is("dcat:Catalog"))
                    .body("'dcat:service'", notNullValue())
                    // findAll is the restAssured way to express JSON Path filters
                    .body("'dcat:dataset'", hasSize(2))
                    .body("'dcat:dataset'.findAll { it -> it.'@type' == 'dcat:Catalog' }.isCatalog", contains(true))
                    .body("'dcat:dataset'.findAll { it -> it.'@type' == 'dcat:Catalog' }.'@id'", contains(catalogAssetId))
                    .body("'dcat:dataset'.findAll { it -> it.'@type' == 'dcat:Catalog' }.'dcat:service'.'dcat:endpointUrl'", contains("http://quizzqua.zz/buzz"))
                    .body("'dcat:dataset'.findAll { it -> it.'@type' == 'dcat:Catalog' }.'dcat:distribution'.'dcat:accessService'.'@id'", contains(Base64.getUrlEncoder().encodeToString(catalogAssetId.getBytes())));
        }

        @Test
        void getDataset_shouldReturnDataset(ManagementEndToEndTestContext context, AssetIndex assetIndex,
                                            DataPlaneInstanceStore dataPlaneInstanceStore) {
            var dataPlaneInstance = DataPlaneInstance.Builder.newInstance().url("http://localhost/any")
                    .allowedDestType("any").allowedSourceType("test-type").allowedTransferType("any").build();
            dataPlaneInstanceStore.save(dataPlaneInstance);

            assetIndex.create(createAsset("asset-id", "test-type").build());
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(EDC_PREFIX, EDC_NAMESPACE))
                    .add(TYPE, "DatasetRequest")
                    .add(ID, "asset-id")
                    .add("counterPartyAddress", context.providerProtocolUrl())
                    .add("protocol", "dataspace-protocol-http")
                    .build();

            context.baseRequest()
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v3/catalog/dataset/request")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(ID, is("asset-id"))
                    .body(TYPE, is("dcat:Dataset"))
                    .body("'dcat:distribution'.'dcat:accessService'.@id", notNullValue());
        }


        private Asset.Builder createAsset(String id, String sourceType) {
            return Asset.Builder.newInstance()
                    .dataAddress(DataAddress.Builder.newInstance().type(sourceType).build())
                    .id(id);
        }

    }

    @Nested
    @EndToEndTest
    @ExtendWith(ManagementEndToEndExtension.InMemory.class)
    class InMemory extends Tests {
    }

    @Nested
    @PostgresqlIntegrationTest
    @ExtendWith(ManagementEndToEndExtension.Postgres.class)
    class Postgres extends Tests {
    }

}
