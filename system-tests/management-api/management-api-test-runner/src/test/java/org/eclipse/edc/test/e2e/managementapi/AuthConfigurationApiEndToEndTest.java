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

package org.eclipse.edc.test.e2e.managementapi;

import io.restassured.http.ContentType;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_PREFIX;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.hamcrest.Matchers.is;

/**
 * Auth configuration end-to-end tests
 */
public class AuthConfigurationApiEndToEndTest {

    private static final String API_KEY = "apiKey";

    abstract static class Tests {


        @Test
        void queryAsset(ManagementEndToEndTestContext context) {

            var query = createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(EDC_PREFIX, EDC_NAMESPACE))
                    .add("filterExpression", createArrayBuilder()).build();

            context.baseRequest()
                    .header("x-api-key", API_KEY)
                    .contentType(ContentType.JSON)
                    .body(query)
                    .post("/v3/assets/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(0));
        }


    }

    static class AuthExtension extends ManagementEndToEndExtension {

        protected AuthExtension() {
            super(context());
        }

        private static ManagementEndToEndTestContext context() {
            var managementPort = getFreePort();
            var protocolPort = getFreePort();

            var runtime = new EmbeddedRuntime(
                    "control-plane",
                    new HashMap<>() {
                        {
                            put("web.http.path", "/");
                            put("web.http.port", String.valueOf(getFreePort()));
                            put("web.http.protocol.path", "/protocol");
                            put("web.http.protocol.port", String.valueOf(protocolPort));
                            put("web.http.control.port", String.valueOf(getFreePort()));
                            put("edc.dsp.callback.address", "http://localhost:" + protocolPort + "/protocol");
                            put("web.http.management.path", "/management");
                            put("web.http.management.port", String.valueOf(managementPort));
                            put("web.http.management.auth.type", "tokenbased");
                            put("web.http.management.auth.key", API_KEY);
                            put("web.http.management.auth.context", "management-api");
                        }
                    },
                    ":system-tests:management-api:management-api-test-runtime",
                    ":extensions:common:auth:auth-configuration",
                    ":extensions:common:auth:auth-tokenbased"
            );

            return new ManagementEndToEndTestContext(runtime, managementPort, protocolPort);
        }

    }

    @Nested
    @EndToEndTest
    @ExtendWith(AuthExtension.class)
    class InMemory extends Tests {
    }
}
