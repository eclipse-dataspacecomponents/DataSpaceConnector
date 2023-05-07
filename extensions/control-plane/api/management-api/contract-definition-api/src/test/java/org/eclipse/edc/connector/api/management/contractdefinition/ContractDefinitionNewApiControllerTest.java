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

package org.eclipse.edc.connector.api.management.contractdefinition;

import io.restassured.common.mapper.TypeRef;
import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.query.QuerySpecDto;
import org.eclipse.edc.connector.api.management.contractdefinition.model.ContractDefinitionRequestDto;
import org.eclipse.edc.connector.api.management.contractdefinition.model.ContractDefinitionResponseDto;
import org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.service.spi.result.ServiceResult;
import org.eclipse.edc.spi.asset.AssetSelectorExpression;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.eclipse.edc.web.spi.ApiErrorDetail;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.api.management.contractdefinition.model.ContractDefinitionRequestDto.Builder;
import static org.eclipse.edc.connector.api.management.contractdefinition.model.ContractDefinitionRequestDto.CONTRACT_DEFINITION_ACCESSPOLICY_ID;
import static org.eclipse.edc.connector.api.management.contractdefinition.model.ContractDefinitionRequestDto.CONTRACT_DEFINITION_CONTRACTPOLICY_ID;
import static org.eclipse.edc.connector.api.management.contractdefinition.model.ContractDefinitionRequestDto.CONTRACT_DEFINITION_CRITERIA;
import static org.eclipse.edc.connector.api.management.contractdefinition.model.ContractDefinitionRequestDto.CONTRACT_DEFINITION_TYPE;
import static org.eclipse.edc.connector.api.management.contractdefinition.model.ContractDefinitionRequestDto.CONTRACT_DEFINITION_VALIDITY;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ApiTest
class ContractDefinitionNewApiControllerTest extends RestControllerTestBase {

    private final JsonLd jsonLd = new TitaniumJsonLd(monitor);
    private final ContractDefinitionService service = mock(ContractDefinitionService.class);
    private final TypeTransformerRegistry transformerRegistry = mock(TypeTransformerRegistry.class);

    @ParameterizedTest
    @ValueSource(strings = { "", "{}" })
    void queryAllContractDefinitions(String body) {

        var dto = createContractDefinitionDto().build();
        when(service.query(any())).thenReturn(ServiceResult.success(Stream.of(createContractDefinition().build())));
        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinition.class), eq(ContractDefinitionResponseDto.class))).thenReturn(Result.success(dto));
        when(transformerRegistry.transform(any(ContractDefinitionResponseDto.class), eq(JsonObject.class))).thenReturn(Result.success(createExpandedJsonObject()));

        baseRequest()
                .contentType(JSON)
                .body(body)
                .post("/request")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));

        verify(service).query(eq(QuerySpec.Builder.newInstance().build()));
    }

    @Test
    void queryAll_queryTransformationFails() {
        when(service.query(any())).thenReturn(ServiceResult.success(Stream.of(createContractDefinition().build())));
        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.failure("test-failure"));

        baseRequest()
                .contentType(JSON)
                .body("{}")
                .post("/request")
                .then()
                .statusCode(400);

        verify(transformerRegistry).transform(any(QuerySpecDto.class), eq(QuerySpec.class));
        verify(service, never()).query(eq(QuerySpec.Builder.newInstance().build()));
    }

    @Test
    void queryAll_queryNotValid() {
        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(service.query(any())).thenReturn(ServiceResult.badRequest("test-message"));

        var error = baseRequest()
                .contentType(JSON)
                .body("{}")
                .post("/request")
                .then()
                .statusCode(400)
                .extract().body().as(new TypeRef<List<ApiErrorDetail>>() {
                })
                .get(0);

        assertThat(error.getMessage()).contains("test-message");

        verify(transformerRegistry).transform(any(QuerySpecDto.class), eq(QuerySpec.class));
        verify(service).query(eq(QuerySpec.Builder.newInstance().build()));
        verifyNoMoreInteractions(transformerRegistry);

    }

    @Test
    void getContractDefById_exists() {
        var dto = createContractDefinitionDto().id("test-id").build();
        var entity = createContractDefinition().id("test-id").build();

        when(service.findById(any())).thenReturn(entity);
        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinition.class), eq(ContractDefinitionResponseDto.class))).thenReturn(Result.success(dto));
        when(transformerRegistry.transform(any(ContractDefinitionResponseDto.class), eq(JsonObject.class))).thenReturn(Result.success(createExpandedJsonObject()));

        baseRequest()
                .get("/" + dto.getId())
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));

        verify(service).findById(eq(entity.getId()));
    }

    @Test
    void getContractDefById_notExists() {
        var dto = createContractDefinitionDto().id("test-id").build();

        when(service.findById(any())).thenReturn(null);
        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinitionResponseDto.class), eq(JsonObject.class))).thenReturn(Result.success(createExpandedJsonObject()));

        baseRequest()
                .get("/" + dto.getId())
                .then()
                .statusCode(404);

        verify(service).findById(dto.getId());
        verify(transformerRegistry, never()).transform(any(ContractDefinition.class), eq(ContractDefinitionResponseDto.class));
    }


    @Test
    void createContractDef() {
        var entity = createContractDefinition().build();

        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinitionRequestDto.class), eq(ContractDefinition.class))).thenReturn(Result.success(entity));
        when(transformerRegistry.transform(any(JsonObject.class), eq(ContractDefinitionRequestDto.class))).thenReturn(Result.success(Builder.newInstance().build()));
        when(service.create(any(ContractDefinition.class))).thenReturn(ServiceResult.success(entity));

        var requestJson = createExpandedJsonObject();

        baseRequest()
                .contentType(JSON)
                .body(requestJson)
                .post()
                .then()
                .statusCode(200)
                .body("id", Matchers.equalTo(entity.getId()));

        verify(service).create(any(ContractDefinition.class));
    }

    @Test
    void createContractDef_exists() {
        var entity = createContractDefinition().build();

        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinitionRequestDto.class), eq(ContractDefinition.class))).thenReturn(Result.success(entity));
        when(transformerRegistry.transform(any(JsonObject.class), eq(ContractDefinitionRequestDto.class))).thenReturn(Result.success(Builder.newInstance().build()));
        when(service.create(any(ContractDefinition.class))).thenReturn(ServiceResult.conflict("test-message"));

        var requestJson = createExpandedJsonObject();
        baseRequest()
                .contentType(JSON)
                .body(requestJson)
                .post()
                .then()
                .statusCode(409);

        verify(service).create(any(ContractDefinition.class));
    }

    @Test
    void createContractDef_transformationFails() {
        var entity = createContractDefinition().build();

        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinitionRequestDto.class), eq(ContractDefinition.class))).thenReturn(Result.success(entity));
        when(transformerRegistry.transform(any(JsonObject.class), eq(ContractDefinitionRequestDto.class))).thenReturn(Result.failure("test-failure"));

        var requestJson = Json.createObjectBuilder()
                .add(TYPE, CONTRACT_DEFINITION_TYPE)
                .add(CONTRACT_DEFINITION_ACCESSPOLICY_ID, "ap1")
                .add(CONTRACT_DEFINITION_CONTRACTPOLICY_ID, "cp1")
                .add(CONTRACT_DEFINITION_VALIDITY, 3600)
                .add(CONTRACT_DEFINITION_CRITERIA, createCriterionBuilder().build())
                .build();

        baseRequest()
                .contentType(JSON)
                .body(requestJson)
                .post()
                .then()
                .statusCode(400);

        verify(service, never()).create(any(ContractDefinition.class));
    }

    @Test
    void delete_exists() {
        var contractDefinition = createContractDefinition().build();
        when(service.delete(eq(contractDefinition.getId()))).thenReturn(ServiceResult.success(contractDefinition));

        baseRequest()
                .delete(contractDefinition.getId())
                .then()
                .statusCode(204);

        verify(service).delete(contractDefinition.getId());
    }

    @Test
    void delete_notExists() {
        var contractDefinition = createContractDefinition().build();
        when(service.delete(eq(contractDefinition.getId()))).thenReturn(ServiceResult.notFound("test-message"));

        baseRequest()
                .delete(contractDefinition.getId())
                .then()
                .statusCode(404);

        verify(service).delete(contractDefinition.getId());
    }

    @Test
    void delete_notPossible() {
        var contractDefinition = createContractDefinition().build();
        when(service.delete(eq(contractDefinition.getId()))).thenReturn(ServiceResult.conflict("test-message"));

        baseRequest()
                .delete(contractDefinition.getId())
                .then()
                .statusCode(409);

        verify(service).delete(contractDefinition.getId());
    }

    @Test
    void update_whenExists() {
        var entity = createContractDefinition().build();

        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinitionRequestDto.class), eq(ContractDefinition.class))).thenReturn(Result.success(entity));
        when(transformerRegistry.transform(any(JsonObject.class), eq(ContractDefinitionRequestDto.class))).thenReturn(Result.success(Builder.newInstance().build()));
        when(service.update(any(ContractDefinition.class))).thenReturn(ServiceResult.success());

        var requestJson = createExpandedJsonObject();

        baseRequest()
                .contentType(JSON)
                .body(requestJson)
                .put()
                .then()
                .statusCode(204);

        verify(service).update(eq(entity));
    }


    @Test
    void update_whenNotExists_shouldThrowException() {
        var entity = createContractDefinition().build();

        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinitionRequestDto.class), eq(ContractDefinition.class))).thenReturn(Result.success(entity));
        when(transformerRegistry.transform(any(JsonObject.class), eq(ContractDefinitionRequestDto.class))).thenReturn(Result.success(Builder.newInstance().build()));
        when(service.update(any(ContractDefinition.class))).thenReturn(ServiceResult.notFound("test-message"));

        var requestJson = createExpandedJsonObject();

        baseRequest()
                .contentType(JSON)
                .body(requestJson)
                .put()
                .then()
                .statusCode(404);

        verify(service).update(eq(entity));
    }

    @Test
    void update_whenTransformationFails_shouldThrowException() {
        var entity = createContractDefinition().build();

        when(transformerRegistry.transform(any(QuerySpecDto.class), eq(QuerySpec.class))).thenReturn(Result.success(QuerySpec.Builder.newInstance().build()));
        when(transformerRegistry.transform(any(ContractDefinitionRequestDto.class), eq(ContractDefinition.class))).thenReturn(Result.success(entity));
        when(transformerRegistry.transform(any(JsonObject.class), eq(ContractDefinitionRequestDto.class))).thenReturn(Result.failure("test-failure"));
        when(service.update(any(ContractDefinition.class))).thenReturn(ServiceResult.success());

        var requestJson = createExpandedJsonObject();

        baseRequest()
                .contentType(JSON)
                .body(requestJson)
                .put()
                .then()
                .statusCode(400);

        verify(service, never()).update(eq(entity));
    }


    @Override
    protected Object controller() {
        return new ContractDefinitionNewApiController(jsonLd, transformerRegistry, service, monitor);
    }

    private JsonArrayBuilder createCriterionBuilder() {
        return Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add(TYPE, EDC_NAMESPACE + "CriterionDto")
                        .add(EDC_NAMESPACE + "operandLeft", "foo")
                        .add(EDC_NAMESPACE + "operator", "=")
                        .add(EDC_NAMESPACE + "operandRight", "bar")
                );
    }

    private JsonObject createExpandedJsonObject() {
        return Json.createObjectBuilder()
                .add(TYPE, CONTRACT_DEFINITION_TYPE)
                .add(ID, "test-id")
                .add(CONTRACT_DEFINITION_ACCESSPOLICY_ID, "ap1")
                .add(CONTRACT_DEFINITION_CONTRACTPOLICY_ID, "cp1")
                .add(CONTRACT_DEFINITION_VALIDITY, 3600)
                .add(CONTRACT_DEFINITION_CRITERIA, createCriterionBuilder().build())
                .build();
    }

    private RequestSpecification baseRequest() {
        return given()
                .baseUri("http://localhost:" + port + "/v2/contractdefinitions")
                .when();
    }

    private ContractDefinition.Builder createContractDefinition() {
        return ContractDefinition.Builder.newInstance()
                .id("1")
                .accessPolicyId("ap-id")
                .contractPolicyId("cp-id")
                .selectorExpression(AssetSelectorExpression.SELECT_ALL)
                .validity(TimeUnit.MINUTES.toSeconds(10));
    }

    private ContractDefinitionResponseDto.Builder createContractDefinitionDto() {
        return ContractDefinitionResponseDto.Builder.newInstance()
                .id("1")
                .accessPolicyId("ap-id")
                .contractPolicyId("cp-id")
                .criteria(List.of())
                .validity(TimeUnit.MINUTES.toSeconds(10));
    }
}