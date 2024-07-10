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

package org.eclipse.edc.connector.dataplane.selector.api.v3;

import io.restassured.specification.RequestSpecification;
import jakarta.json.JsonArray;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.junit.extensions.EdcExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.dataplane.selector.TestFunctions.createInstance;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.util.io.Ports.getFreePort;

@ApiTest
@ExtendWith(EdcExtension.class)
public class DataPlaneSelectorApiV3ControllerTest {

    private final int port = 8181;

    @BeforeEach
    void setup(EdcExtension extension) {
        extension.setConfiguration(Map.of(
                "web.http.port", String.valueOf(getFreePort()),
                "web.http.management.port", String.valueOf(port),
                "web.http.management.path", "/management"
        ));
    }

    @Test
    void getAll(DataPlaneInstanceStore store) {
        var list = List.of(createInstance("test-id1"), createInstance("test-id2"), createInstance("test-id3"));
        list.forEach(store::save);

        var array = baseRequest()
                .get()
                .then()
                .statusCode(200)
                .extract().body().as(JsonArray.class);

        assertThat(array).hasSize(3)
                .allSatisfy(jo -> assertThat(jo.asJsonObject().getString(ID))
                        .isIn("test-id1", "test-id2", "test-id3"));
    }

    @Test
    void getAll_noneExist() {
        var array = baseRequest()
                .get()
                .then()
                .statusCode(200)
                .extract().body().as(JsonArray.class);

        assertThat(array).isNotNull().isEmpty();
    }

    protected RequestSpecification baseRequest() {
        return given()
                .port(port)
                .baseUri("http://localhost:" + port + "/management/v3/dataplanes")
                .when();
    }

}
