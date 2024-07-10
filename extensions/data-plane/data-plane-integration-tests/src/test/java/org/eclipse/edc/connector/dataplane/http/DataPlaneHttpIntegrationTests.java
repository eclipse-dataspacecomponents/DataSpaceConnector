/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.edc.connector.dataplane.http;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.mockserver.model.Parameters;
import org.mockserver.verify.VerificationTimes;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.restassured.RestAssured.given;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.Collections.emptyMap;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.dataplane.http.testfixtures.TestFunctions.createRequest;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.NOTIFIED;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.KeyMatchStyle.MATCHING_KEY;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.mockserver.model.MediaType.APPLICATION_OCTET_STREAM;
import static org.mockserver.model.MediaType.PLAIN_TEXT_UTF_8;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * System Test for Data Plane HTTP extension.
 */
@ComponentTest
public class DataPlaneHttpIntegrationTests {

    private static final int VALIDATION_API_PORT = getFreePort();
    private static final String VALIDATION_API_HOST = "http://localhost:" + VALIDATION_API_PORT;
    private static final int PUBLIC_API_PORT = getFreePort();
    private static final int CONTROL_API_PORT = getFreePort();
    private static final int HTTP_SOURCE_API_PORT = getFreePort();
    private static final int HTTP_SINK_API_PORT = getFreePort();
    private static final String CONTROL_API_HOST = "http://localhost:" + CONTROL_API_PORT;
    private static final String HTTP_SOURCE_API_HOST = "http://localhost:" + HTTP_SOURCE_API_PORT;
    private static final String HTTP_SINK_API_HOST = "http://localhost:" + HTTP_SINK_API_PORT;
    private static final String CONTROL_PATH = "/control";
    private static final String PUBLIC_PATH = "/public";
    private static final String PUBLIC_API_HOST = "http://localhost:" + PUBLIC_API_PORT;
    private static final String TRANSFER_PATH = format("%s/transfer", CONTROL_PATH);
    private static final String TRANSFER_RESULT_PATH = format("%s/{processId}", TRANSFER_PATH);
    private static final String PROCESS_ID = "processId";
    private static final String HTTP_API_PATH = "sample";
    private static final String AUTH_HEADER_KEY = AUTHORIZATION.toString();
    private static final String SOURCE_AUTH_VALUE = "source-auth-key";
    private static final String SINK_AUTH_VALUE = "sink-auth-key";
    private static ClientAndServer httpSourceMockServer;
    private static ClientAndServer httpSinkMockServer;
    private static ClientAndServer validationApiMockServer;
    private final Duration timeout = Duration.ofSeconds(30);

    private static final EmbeddedRuntime RUNTIME = new EmbeddedRuntime(
            "data-plane-server",
            Map.of(
                    "web.http.public.port", valueOf(PUBLIC_API_PORT),
                    "web.http.public.path", PUBLIC_PATH,
                    "web.http.control.port", valueOf(CONTROL_API_PORT),
                    "web.http.control.path", CONTROL_PATH,
                    "edc.dataplane.token.validation.endpoint", VALIDATION_API_HOST,
                    "edc.transfer.proxy.token.verifier.publickey.alias", "alias",
                    "edc.transfer.proxy.token.signer.privatekey.alias", "alias",
                    "edc.core.retry.retries.max", "0"
            ),
            ":extensions:common:metrics:micrometer-core",
            ":core:data-plane:data-plane-core",
            ":extensions:common:api:control-api-configuration",
            ":extensions:common:http",
            ":extensions:common:json-ld",
            ":extensions:common:configuration:configuration-filesystem",
            ":extensions:control-plane:api:control-plane-api-client",
            ":extensions:data-plane:data-plane-http",
            ":extensions:data-plane:data-plane-control-api",
            ":extensions:data-plane:data-plane-public-api"
    );

    @BeforeAll
    public static void setUp() {
        httpSourceMockServer = startClientAndServer(HTTP_SOURCE_API_PORT);
        httpSinkMockServer = startClientAndServer(HTTP_SINK_API_PORT);
        validationApiMockServer = startClientAndServer(VALIDATION_API_PORT);
    }

    @AfterAll
    public static void tearDown() {
        stopQuietly(httpSourceMockServer);
        stopQuietly(httpSinkMockServer);
        stopQuietly(validationApiMockServer);
    }

    @AfterEach
    public void resetMockServer() {
        httpSourceMockServer.reset();
        httpSinkMockServer.reset();
        validationApiMockServer.reset();
    }

    @Nested
    class Pull {

        @RegisterExtension
        static RuntimeExtension dataPlane = new RuntimePerClassExtension(RUNTIME);

        @Test
        void transfer_pull_withSourceQueryParamsAndPath_success(TypeManager typeManager) {
            // prepare data source and validation servers
            var token = UUID.randomUUID().toString();
            var responseBody = "some info";
            var queryParams = Map.of(
                    "param1", "foo",
                    "param2", "bar"
            );
            var sourceDataAddress = sourceDataAddress();

            var sourceRequest = getRequest(queryParams, HTTP_API_PATH);
            httpSourceMockServer.when(sourceRequest, once()).respond(successfulResponse(responseBody, PLAIN_TEXT_UTF_8));

            // prepare validation server of the control plane
            var validationRequest = request().withMethod(HttpMethod.GET.name()).withHeader(AUTH_HEADER_KEY, token);
            validationApiMockServer.when(validationRequest, once())
                    .respond(successfulResponse(typeManager.writeValueAsString(sourceDataAddress), PLAIN_TEXT_UTF_8));

            given()
                    .baseUri(PUBLIC_API_HOST)
                    .contentType(ContentType.JSON)
                    .when()
                    .queryParams(queryParams)
                    .header(AUTHORIZATION.toString(), token)
                    .get(format("%s/%s", PUBLIC_PATH, HTTP_API_PATH))
                    .then()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK)
                    .body(equalTo(responseBody));

            httpSourceMockServer.verify(sourceRequest, VerificationTimes.once());
        }

        @Test
        void transfer_invalidInput_failure(TypeManager typeManager) {
            var processId = UUID.randomUUID().toString();
            var invalidRequest = transferRequestPayload(processId, typeManager).remove("processId");

            given()
                    .baseUri(CONTROL_API_HOST)
                    .contentType(ContentType.JSON)
                    .body(invalidRequest)
                    .when()
                    .post(TRANSFER_PATH)
                    .then()
                    .assertThat().statusCode(HttpStatus.SC_BAD_REQUEST);
        }

        @Test
        void shouldProxyMethodAndBody_whenSet(TypeManager typeManager) {
            var sourceAddress = HttpDataAddress.Builder.newInstance()
                    .baseUrl(HTTP_SOURCE_API_HOST)
                    .proxyMethod(TRUE.toString())
                    .proxyPath(TRUE.toString())
                    .proxyBody(TRUE.toString())
                    .build();
            httpSourceMockServer.when(request(), once()).respond(successfulResponse("any", PLAIN_TEXT_UTF_8));
            validationApiMockServer.when(request(), once())
                    .respond(successfulResponse(typeManager.writeValueAsString(sourceAddress), PLAIN_TEXT_UTF_8));

            given()
                    .baseUri(PUBLIC_API_HOST)
                    .contentType(ContentType.JSON)
                    .when()
                    .header(AUTHORIZATION.toString(), "any")
                    .body("body")
                    .put(format("%s/%s", PUBLIC_PATH, HTTP_API_PATH))
                    .then()
                    .log().ifError()
                    .statusCode(HttpStatus.SC_OK);

            httpSourceMockServer.verify(request().withMethod("PUT").withBody("body"), VerificationTimes.once());
        }
    }

    @Nested
    class Push {

        @RegisterExtension
        static RuntimeExtension dataPlane = new RuntimePerClassExtension(RUNTIME);

        @Test
        void transfer_toHttpSink_success(TypeManager typeManager) {
            var body = UUID.randomUUID().toString();
            var processId = UUID.randomUUID().toString();
            httpSourceMockServer.when(getRequest(HTTP_API_PATH), once()).respond(successfulResponse(body, APPLICATION_JSON));

            // HTTP Sink Request & Response
            httpSinkMockServer.when(request(), once()).respond(successfulResponse());

            initiateTransfer(transferRequestPayload(processId, typeManager));

            await().atMost(timeout).untilAsserted(() -> expectState(processId, NOTIFIED));
            // Verify HTTP Source server called exactly once.
            httpSourceMockServer.verify(getRequest(HTTP_API_PATH), VerificationTimes.once());
            // Verify HTTP Sink server called exactly once.
            httpSinkMockServer.verify(postRequest(body, APPLICATION_JSON), VerificationTimes.once());
        }

        @Test
        void transfer_toHttpSink_withSourceQueryParams_success(TypeManager typeManager) {
            // HTTP Source Request & Response
            var body = UUID.randomUUID().toString();
            var processId = UUID.randomUUID().toString();
            var queryParams = Map.of(
                    "param1", "any value",
                    "param2", "any other value"
            );

            httpSourceMockServer.when(getRequest(queryParams, HTTP_API_PATH), once()).respond(successfulResponse(body, APPLICATION_OCTET_STREAM));

            // HTTP Sink Request & Response
            httpSinkMockServer.when(postRequest(body, APPLICATION_OCTET_STREAM), once()).respond(successfulResponse());

            initiateTransfer(transferRequestPayload(processId, queryParams, typeManager));

            await().atMost(timeout).untilAsserted(() -> expectState(processId, NOTIFIED));
            // Verify HTTP Source server called exactly once.
            httpSourceMockServer.verify(getRequest(queryParams, HTTP_API_PATH), VerificationTimes.once());
            // Verify HTTP Sink server called exactly once.
            httpSinkMockServer.verify(postRequest(body, APPLICATION_OCTET_STREAM), VerificationTimes.once());
        }

        @Test
        void transfer_toHttpSink_sourceNotAvailable_noInteractionWithSink(TypeManager typeManager) {
            var processId = UUID.randomUUID().toString();
            // HTTP Source Request & Error Response
            httpSourceMockServer.when(getRequest(HTTP_API_PATH)).error(withDropConnection());

            initiateTransfer(transferRequestPayload(processId, typeManager));

            await().atMost(timeout).untilAsserted(() -> expectState(processId, NOTIFIED));
            // Verify HTTP Source server called at lest once.
            httpSourceMockServer.verify(getRequest(HTTP_API_PATH), VerificationTimes.atLeast(1));
            // Verify zero interaction with HTTP Sink.
            httpSinkMockServer.verifyZeroInteractions();
        }

        /**
         * Validate if intermittently source is dropping connection than DPF server retries to fetch data.
         */
        @Test
        void transfer_toHttpSink_sourceTemporaryDropConnection_success(TypeManager typeManager) {
            var processId = UUID.randomUUID().toString();
            // First two calls to HTTP Source returns a failure response.
            httpSourceMockServer.when(getRequest(HTTP_API_PATH), exactly(2)).error(withDropConnection());

            // Next call to HTTP Source returns a valid response.
            var body = UUID.randomUUID().toString();
            httpSourceMockServer.when(getRequest(HTTP_API_PATH), once()).respond(successfulResponse(body, PLAIN_TEXT_UTF_8));

            // HTTP Sink Request & Response
            httpSinkMockServer.when(postRequest(body, APPLICATION_OCTET_STREAM), once()).respond(successfulResponse());

            initiateTransfer(transferRequestPayload(processId, typeManager));

            await().atMost(timeout).untilAsserted(() -> expectState(processId, NOTIFIED));
            // Verify HTTP Source server called exactly 3 times.
            httpSourceMockServer.verify(getRequest(HTTP_API_PATH), VerificationTimes.exactly(3));
            // Verify HTTP Sink server called exactly once.
            httpSinkMockServer.verify(postRequest(body, PLAIN_TEXT_UTF_8), VerificationTimes.once());
        }

        private void initiateTransfer(Object payload) {
            given()
                    .baseUri(CONTROL_API_HOST)
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when()
                    .post(TRANSFER_PATH)
                    .then()
                    .assertThat().statusCode(HttpStatus.SC_OK);
        }

        private void expectState(String processId, DataFlowStates expectedState) {
            given()
                    .baseUri(CONTROL_API_HOST)
                    .pathParam(PROCESS_ID, processId)
                    .when()
                    .get(TRANSFER_RESULT_PATH)
                    .then()
                    .assertThat().statusCode(HttpStatus.SC_OK)
                    .body(containsString(expectedState.name()));
        }

        private HttpError withDropConnection() {
            return error()
                    .withDropConnection(true);
        }
    }

    /**
     * Request payload to initiate DPF transfer.
     *
     * @param processId ProcessID of transfer.See {@link DataFlowStartMessage}
     * @return JSON object. see {@link ObjectNode}.
     */
    private ObjectNode transferRequestPayload(String processId, TypeManager typeManager) {
        return transferRequestPayload(processId, emptyMap(), typeManager);
    }

    /**
     * Request payload with query params to initiate DPF transfer.
     *
     * @param processId   ProcessID of transfer.See {@link DataFlowStartMessage}
     * @param queryParams Query params name and value as key-value entries.
     * @return JSON object. see {@link ObjectNode}.
     */
    private ObjectNode transferRequestPayload(String processId, Map<String, String> queryParams, TypeManager typeManager) {
        var requestProperties = new HashMap<String, String>();
        requestProperties.put(DataFlowRequestSchema.METHOD, HttpMethod.GET.name());
        requestProperties.put(DataFlowRequestSchema.PATH, HTTP_API_PATH);

        if (!queryParams.isEmpty()) {
            requestProperties.put(DataFlowRequestSchema.QUERY_PARAMS, queryParams.entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&")));
        }

        var destinationDataAddress = HttpDataAddress.Builder.newInstance()
                .baseUrl(HTTP_SINK_API_HOST)
                .authKey(AUTH_HEADER_KEY)
                .authCode(SINK_AUTH_VALUE)
                .build();

        // Create valid dataflow request instance.
        var request = createRequest(requestProperties, sourceDataAddress(), destinationDataAddress)
                .processId(processId)
                .build();

        return typeManager.getMapper().convertValue(request, ObjectNode.class);
    }

    private HttpDataAddress sourceDataAddress() {
        return HttpDataAddress.Builder.newInstance()
                .baseUrl(HTTP_SOURCE_API_HOST)
                .proxyPath(TRUE.toString())
                .proxyQueryParams(TRUE.toString())
                .authKey(AUTH_HEADER_KEY)
                .authCode(SOURCE_AUTH_VALUE)
                .build();
    }

    /**
     * Mock HTTP GET request for source.
     *
     * @return see {@link HttpRequest}
     */
    private HttpRequest getRequest(String path) {
        return getRequest(emptyMap(), path);
    }

    /**
     * Mock HTTP GET request with query params for source.
     *
     * @return see {@link HttpRequest}
     */
    private HttpRequest getRequest(Map<String, String> queryParams, String path) {
        var request = request();

        var paramsList = queryParams.entrySet()
                .stream()
                .map(entry -> param(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        request.withQueryStringParameters(new Parameters(paramsList).withKeyMatchStyle(MATCHING_KEY));

        return request
                .withMethod(HttpMethod.GET.name())
                .withHeader(AUTH_HEADER_KEY, SOURCE_AUTH_VALUE)
                .withPath("/" + path);
    }

    private HttpRequest postRequest(String responseBody, MediaType contentType) {
        return request()
                .withMethod(HttpMethod.POST.name())
                .withHeader(AUTH_HEADER_KEY, SINK_AUTH_VALUE)
                .withContentType(contentType)
                .withBody(binary(responseBody.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Mock http OK response from sink.
     *
     * @return see {@link HttpResponse}
     */
    private HttpResponse successfulResponse() {
        return response()
                .withStatusCode(HttpStatusCode.OK_200.code());
    }

    private HttpResponse successfulResponse(String responseBody, MediaType contentType) {
        return successfulResponse()
                .withHeader(HttpHeaderNames.CONTENT_TYPE.toString(), contentType.toString())
                .withBody(responseBody);
    }
}
