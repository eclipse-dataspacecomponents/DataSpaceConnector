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

package org.eclipse.edc.connector.api.management.version;

import io.restassured.common.mapper.TypeRef;
import io.restassured.specification.RequestSpecification;
import org.eclipse.edc.connector.api.management.version.v1.VersionApiController;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.spi.system.apiversion.VersionRecord;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ApiTest
class VersionApiControllerTest extends RestControllerTestBase {

    private final ApiVersionService apiServiceMock = mock();

    @BeforeEach
    void setup() {
    }

    @Test
    void getVersion() {
        when(apiServiceMock.getRecords()).thenReturn(Map.of("test-api", List.of(new VersionRecord("1.0.0", "/v1", Instant.now(), "deprecated"))));
        var result = baseRequest()
                .get("/v1/version")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(new TypeRef<Map<String, List<VersionRecord>>>() {
                });

        assertThat(result).hasSize(1);
    }

    @Override
    protected Object controller() {
        return new VersionApiController(apiServiceMock);
    }


    private RequestSpecification baseRequest() {
        return given()
                .baseUri("http://localhost:" + port)
                .when();
    }
}
