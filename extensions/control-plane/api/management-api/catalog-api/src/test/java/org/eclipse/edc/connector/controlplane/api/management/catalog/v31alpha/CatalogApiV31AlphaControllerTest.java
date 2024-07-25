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

package org.eclipse.edc.connector.controlplane.api.management.catalog.v31alpha;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.api.management.catalog.BaseCatalogApiControllerTest;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequest;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogApiV31AlphaControllerTest extends BaseCatalogApiControllerTest {

    @Test
    void requestCatalog_withAdditionalScopes() {
        var request = CatalogRequest.Builder.newInstance().counterPartyAddress("http://url").build();
        when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
        when(transformerRegistry.transform(argThat(o -> o instanceof JsonObject jo && jo.containsKey(CatalogRequest.CATALOG_REQUEST_ADDITIONAL_SCOPES)), eq(CatalogRequest.class))).thenReturn(Result.success(request));
        when(service.requestCatalog(any(), any(), any(), any())).thenReturn(completedFuture(StatusResult.success("{}".getBytes())));
        var requestBody = Json.createObjectBuilder()
                .add(CatalogRequest.CATALOG_REQUEST_PROTOCOL, "any")
                .add(CatalogRequest.CATALOG_REQUEST_ADDITIONAL_SCOPES, Json.createArrayBuilder(List.of("scope1", "scope2")).build())
                .build();

        given()
                .port(port)
                .contentType(JSON)
                .body(requestBody)
                .post(baseUrl() + "/request")
                .then()
                .statusCode(200)
                .contentType(JSON);
        var captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(transformerRegistry).transform(captor.capture(), eq(CatalogRequest.class));

        var jo = captor.getValue();
        assertThat(jo).containsKey(CatalogRequest.CATALOG_REQUEST_ADDITIONAL_SCOPES);
    }

    @Override
    protected String baseUrl() {
        return "/v3.1alpha/catalog";
    }

    @Override
    protected Object controller() {
        return new CatalogApiV31AlphaController(service, transformerRegistry, validatorRegistry);
    }
}