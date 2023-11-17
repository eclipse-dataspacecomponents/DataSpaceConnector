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

package org.eclipse.edc.test.e2e.sts.api;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.EdcRuntimeExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.identitytrust.SelfIssuedTokenConstants.ACCESS_TOKEN;
import static org.eclipse.edc.junit.testfixtures.TestUtils.getFreePort;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.AUDIENCE;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.CLIENT_ID;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.EXPIRATION_TIME;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.ISSUED_AT;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.ISSUER;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.JWT_ID;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.SUBJECT;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@EndToEndTest
public class StsApiEndToEndTest extends StsEndToEndTestBase {

    public static final int PORT = getFreePort();
    public static final String BASE_STS = "http://localhost:" + PORT + "/sts";
    private static final String GRANT_TYPE = "client_credentials";

    @RegisterExtension
    static EdcRuntimeExtension sts = new EdcRuntimeExtension(
            ":system-tests:sts-api:sts-api-test-runtime",
            "sts",
            new HashMap<>() {
                {
                    put("web.http.path", "/");
                    put("web.http.port", String.valueOf(getFreePort()));
                    put("web.http.sts.path", "/sts");
                    put("web.http.sts.port", String.valueOf(PORT));
                }
            }
    );

    @Test
    void requestToken() throws ParseException {
        var audience = "audience";
        var clientSecret = "client_secret";
        var expiresIn = 300;

        var client = initClient(clientSecret);

        var params = Map.of(
                "client_id", client.getClientId(),
                "audience", audience,
                "client_secret", clientSecret);

        var token = tokenRequest(params)
                .statusCode(200)
                .contentType(JSON)
                .body("access_token", notNullValue())
                .body("expires_in", is(expiresIn))
                .extract()
                .body()
                .jsonPath().getString("access_token");

        assertThat(parseClaims(token))
                .containsEntry(ISSUER, client.getId())
                .containsEntry(SUBJECT, client.getId())
                .containsEntry(AUDIENCE, List.of(audience))
                .containsEntry(CLIENT_ID, client.getClientId())
                .containsKeys(JWT_ID, EXPIRATION_TIME, ISSUED_AT);
    }


    @Test
    void requestToken_withBearerScope() throws ParseException {
        var clientSecret = "client_secret";
        var audience = "audience";
        var bearerAccessScope = "org.test.Member:read org.test.GoldMember:read";
        var expiresIn = 300;

        var client = initClient(clientSecret);


        var params = Map.of(
                "client_id", client.getClientId(),
                "audience", audience,
                "bearer_access_scope", bearerAccessScope,
                "client_secret", clientSecret);

        var token = tokenRequest(params)
                .statusCode(200)
                .contentType(JSON)
                .body("access_token", notNullValue())
                .body("expires_in", is(expiresIn))
                .extract()
                .body()
                .jsonPath().getString("access_token");


        assertThat(parseClaims(token))
                .containsEntry(ISSUER, client.getId())
                .containsEntry(SUBJECT, client.getId())
                .containsEntry(AUDIENCE, List.of(audience))
                .containsEntry(CLIENT_ID, client.getClientId())
                .containsKeys(JWT_ID, EXPIRATION_TIME, ISSUED_AT)
                .hasEntrySatisfying(ACCESS_TOKEN, (accessToken) -> {
                    assertThat(parseClaims((String) accessToken))
                            .containsEntry(ISSUER, client.getId())
                            .containsEntry(SUBJECT, audience)
                            .containsEntry(AUDIENCE, List.of(client.getClientId()))
                            .containsKeys(JWT_ID, EXPIRATION_TIME, ISSUED_AT);
                });
    }

    @Test
    void requestToken_withAttachedAccessScope() throws IOException, ParseException {
        var clientSecret = "client_secret";
        var audience = "audience";
        var accessToken = "test_token";
        var expiresIn = 300;
        var client = initClient(clientSecret);


        var params = Map.of(
                "client_id", client.getClientId(),
                "audience", audience,
                "access_token", accessToken,
                "client_secret", clientSecret);

        var token = tokenRequest(params)
                .statusCode(200)
                .contentType(JSON)
                .body("access_token", notNullValue())
                .body("expires_in", is(expiresIn))
                .extract()
                .body()
                .jsonPath().getString("access_token");

        
        assertThat(parseClaims(token))
                .containsEntry(ISSUER, client.getId())
                .containsEntry(SUBJECT, client.getId())
                .containsEntry(AUDIENCE, List.of(audience))
                .containsEntry(CLIENT_ID, client.getClientId())
                .containsEntry(ACCESS_TOKEN, accessToken)
                .containsKeys(JWT_ID, EXPIRATION_TIME, ISSUED_AT);
    }

    @Test
    void requestToken_shouldReturnError_whenClientNotFound() {

        var clientId = "client_id";
        var clientSecret = "client_secret";
        var audience = "audience";

        var params = Map.of(
                "client_id", clientId,
                "audience", audience,
                "client_secret", clientSecret);

        tokenRequest(params)
                .statusCode(401)
                .contentType(JSON);
    }

    protected ValidatableResponse tokenRequest(Map<String, String> params) {

        var req = baseRequest()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", GRANT_TYPE);
        params.forEach(req::formParam);
        return req.post("/token").then();
    }

    protected RequestSpecification baseRequest() {
        return given()
                .port(PORT)
                .baseUri(BASE_STS)
                .when();
    }

    @Override
    protected EdcRuntimeExtension getRuntime() {
        return sts;
    }

}
