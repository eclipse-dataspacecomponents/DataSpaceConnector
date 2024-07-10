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

package org.eclipse.edc.connector.dataplane.selector.api.v2;

import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.eclipse.edc.connector.dataplane.selector.api.model.SelectionRequest;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.connector.dataplane.selector.spi.strategy.SelectionStrategy;
import org.eclipse.edc.connector.dataplane.selector.spi.strategy.SelectionStrategyRegistry;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.junit.extensions.EdcExtension;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.dataplane.selector.TestFunctions.createInstance;
import static org.eclipse.edc.connector.dataplane.selector.TestFunctions.createInstanceBuilder;
import static org.eclipse.edc.connector.dataplane.selector.TestFunctions.createInstanceJson;
import static org.eclipse.edc.connector.dataplane.selector.TestFunctions.createInstanceJsonBuilder;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.hamcrest.Matchers.equalTo;

@ApiTest
@ExtendWith(EdcExtension.class)
public class DataPlaneSelectorApiV2ControllerTest {

    private final int port = getFreePort();

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
        List.of(createInstance("test-id1"), createInstance("test-id2"), createInstance("test-id3"))
                .forEach(store::save);

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

    @Test
    void addEntry(DataPlaneInstanceStore store) {
        var dpi = createInstanceJson("test-id");

        var result = baseRequest()
                .body(dpi)
                .contentType(JSON)
                .post()
                .then()
                .statusCode(200)
                .extract().body().as(JsonObject.class);

        assertThat(result.getString(ID)).isEqualTo("test-id");
        assertThat(store.getAll()).hasSize(1)
                .allMatch(d -> d.getId().equals("test-id"));
    }

    @Test
    void addEntry_fails_whenMissingUrl() {
        var dpi = createInstanceJsonBuilder("test-id").build();

        baseRequest()
                .body(dpi)
                .contentType(JSON)
                .post()
                .then()
                .statusCode(400);
    }

    @Test
    void addEntry_exists_shouldOverwrite(DataPlaneInstanceStore store) {
        var dpi1 = createInstance("test-id1");
        var dpi2 = createInstance("test-id2");
        var dpi3 = createInstance("test-id3");
        List.of(dpi1, dpi2, dpi3).forEach(store::save);

        var newDpi = Json.createObjectBuilder(createInstanceJson("test-id2"))
                .add(DataPlaneInstance.ALLOWED_SOURCE_TYPES, Json.createValue("test-src-type"))
                .build();

        baseRequest()
                .body(newDpi)
                .contentType(JSON)
                .post()
                .then()
                .statusCode(200);

        assertThat(store.getAll()).hasSize(3)
                .anyMatch(it -> it.getAllowedSourceTypes().contains("test-src-type"));
    }

    @Test
    void select(DataPlaneInstanceStore store) {
        var dpi = createInstanceBuilder("test-id1")
                .allowedSourceType("test-src1")
                .allowedDestType("test-dst1")
                .allowedTransferType("transfer-type-1")
                .build();
        var dpi2 = createInstanceBuilder("test-id2")
                .allowedSourceType("test-src2")
                .allowedDestType("test-dst2")
                .allowedTransferType("transfer-type-2")
                .build();
        Stream.of(dpi, dpi2).peek(DataPlaneInstance::transitionToAvailable).forEach(store::save);

        var rq = createSelectionRequestJson("test-src1", "test-dst1", null, "transfer-type-1");

        var result = baseRequest()
                .body(rq)
                .contentType(JSON)
                .post("/select")
                .then()
                .statusCode(200)
                .extract().body().as(JsonObject.class);
        assertThat(result.getString(ID)).isEqualTo("test-id1");
    }

    @Test
    void select_noneCanHandle_shouldReturnEmpty(DataPlaneInstanceStore store) {
        var dpi = createInstanceBuilder("test-id")
                .allowedSourceType("test-src1")
                .allowedDestType("test-dst1")
                .build();
        var dpi2 = createInstanceBuilder("test-id")
                .allowedSourceType("test-src2")
                .allowedDestType("test-dst2")
                .build();
        List.of(dpi, dpi2).forEach(store::save);

        var rq = createSelectionRequestJson("notexist-src", "test-dst2", null, "transfer-type");

        baseRequest()
                .body(rq)
                .contentType(JSON)
                .post("/select")
                .then()
                .statusCode(204)
                .body(equalTo(""));
    }

    @Test
    void select_selectionStrategyNotFound(DataPlaneInstanceStore store) {
        var dpi = createInstanceBuilder("test-id")
                .allowedSourceType("test-src1")
                .allowedDestType("test-dst1")
                .build();
        var dpi2 = createInstanceBuilder("test-id")
                .allowedSourceType("test-src2")
                .allowedDestType("test-dst2")
                .build();
        List.of(dpi, dpi2).forEach(store::save);

        var rq = createSelectionRequestJson("test-src1", "test-dst2", "notexist", "transfer-type1");

        baseRequest()
                .body(rq)
                .contentType(JSON)
                .post("/select")
                .then()
                .statusCode(204);
    }

    @Test
    void select_withCustomStrategy(DataPlaneInstanceStore store, SelectionStrategyRegistry selectionStrategyRegistry) {
        var dpi = createInstanceBuilder("test-id1")
                .allowedSourceType("test-src1")
                .allowedDestType("test-dst1")
                .allowedTransferType("transfer-type-1")
                .build();
        var dpi2 = createInstanceBuilder("test-id2")
                .allowedSourceType("test-src1")
                .allowedDestType("test-dst1")
                .allowedTransferType("transfer-type-2")
                .build();
        Stream.of(dpi, dpi2).peek(DataPlaneInstance::transitionToAvailable).forEach(store::save);

        var myCustomStrategy = new SelectionStrategy() {
            @Override
            public DataPlaneInstance apply(List<DataPlaneInstance> instances) {
                return instances.stream().filter(i -> i.getId().equals("test-id1")).findFirst().orElseThrow();
            }

            @Override
            public String getName() {
                return "myCustomStrategy";
            }
        };

        selectionStrategyRegistry.add(myCustomStrategy);

        var rq = createSelectionRequestJson("test-src1", "test-dst1", "myCustomStrategy", "transfer-type-1");

        var result = baseRequest()
                .body(rq)
                .contentType(JSON)
                .post("/select")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .extract().body().as(JsonObject.class);

        assertThat(result).isNotNull();
        assertThat(result.getString(ID)).isEqualTo("test-id1");
    }

    private RequestSpecification baseRequest() {
        return given()
                .port(port)
                .basePath("/management/v2/dataplanes")
                .when();
    }

    private JsonObject createSelectionRequestJson(String srcType, String destType, String strategy, String transferType) {
        var builder = createObjectBuilder()
                .add(SelectionRequest.SOURCE_ADDRESS, createDataAddress(srcType))
                .add(SelectionRequest.DEST_ADDRESS, createDataAddress(destType))
                .add(SelectionRequest.TRANSFER_TYPE, transferType);

        if (strategy != null) {
            builder.add(SelectionRequest.STRATEGY, strategy);
        }
        return builder.build();
    }

    private JsonObjectBuilder createDataAddress(String type) {
        return createObjectBuilder()
                .add(TYPE, EDC_NAMESPACE + "DataAddress")
                .add(DataAddress.EDC_DATA_ADDRESS_TYPE_PROPERTY, type);
    }

}
