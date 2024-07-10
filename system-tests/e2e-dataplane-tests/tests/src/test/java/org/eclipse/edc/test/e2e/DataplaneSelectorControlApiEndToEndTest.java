/*
 *  Copyright (c) 2024 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial implementation
 *
 */

package org.eclipse.edc.test.e2e;

import io.restassured.http.ContentType;
import jakarta.json.JsonArray;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerMethodExtension;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.Mockito.mock;

@EndToEndTest
public class DataplaneSelectorControlApiEndToEndTest {

    private final int controlPlaneControlPort = getFreePort();

    @RegisterExtension
    private final RuntimeExtension controlPlane = new RuntimePerMethodExtension(new EmbeddedRuntime(
            "control-plane",
            Map.of(
                    "web.http.control.port", String.valueOf(controlPlaneControlPort),
                    "web.http.control.path", "/control"
            ),
            ":core:control-plane:control-plane-core",
            ":core:data-plane-selector:data-plane-selector-core",
            ":extensions:control-plane:transfer:transfer-data-plane-signaling",
            ":extensions:common:iam:iam-mock",
            ":extensions:common:http",
            ":extensions:common:api:control-api-configuration",
            ":extensions:data-plane-selector:data-plane-selector-control-api"
    )).registerServiceMock(ProtocolWebhook.class, mock());

    @RegisterExtension
    private final RuntimeExtension dataPlane = new RuntimePerMethodExtension(new EmbeddedRuntime(
            "data-plane",
            Map.of(
                    "web.http.port", String.valueOf(getFreePort()),
                    "web.http.path", "/api",
                    "web.http.control.port", String.valueOf(getFreePort()),
                    "web.http.control.path", "/control",
                    "edc.dpf.selector.url", String.format("http://localhost:%d/control/v1/dataplanes", controlPlaneControlPort),
                    "edc.transfer.proxy.token.verifier.publickey.alias", "alias",
                    "edc.transfer.proxy.token.signer.privatekey.alias", "alias"
            ),
            ":system-tests:e2e-dataplane-tests:runtimes:data-plane",
            ":extensions:data-plane:data-plane-self-registration",
            ":extensions:data-plane-selector:data-plane-selector-client"
    ));

    @Test
    void shouldReturnSelfRegisteredDataplane() {
        var result = given()
                .baseUri("http://localhost:" + controlPlaneControlPort + "/control")
                .when()
                .get("/v1/dataplanes")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().body().as(JsonArray.class);

        assertThat(result).hasSize(1);
    }
}
