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

package org.eclipse.edc.connector.dataplane.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.api.signaling.transform.from.JsonObjectFromDataFlowResponseMessageTransformer;
import org.eclipse.edc.connector.api.signaling.transform.from.JsonObjectFromDataFlowStartMessageTransformer;
import org.eclipse.edc.connector.api.signaling.transform.from.JsonObjectFromDataFlowSuspendMessageTransformer;
import org.eclipse.edc.connector.api.signaling.transform.from.JsonObjectFromDataFlowTerminateMessageTransformer;
import org.eclipse.edc.connector.api.signaling.transform.to.JsonObjectToDataFlowResponseMessageTransformer;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClient;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.spi.response.TransferErrorResponse;
import org.eclipse.edc.http.client.ControlApiHttpClientImpl;
import org.eclipse.edc.http.spi.ControlApiHttpClient;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowResponseMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.dspace.from.JsonObjectFromDataAddressDspaceTransformer;
import org.eclipse.edc.transform.transformer.dspace.to.JsonObjectToDataAddressDspaceTransformer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.http.client.testfixtures.HttpTestUtils.testHttpClient;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.jsonld.util.JacksonJsonLd.createObjectMapper;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.CONFLICT_409;
import static org.mockserver.model.HttpStatusCode.NO_CONTENT_204;
import static org.mockserver.stop.Stop.stopQuietly;

@ComponentTest
class DataPlaneSignalingClientTest {

    private static final ObjectMapper MAPPER = createObjectMapper();

    private static final int DATA_PLANE_API_PORT = getFreePort();
    private static final String DATA_PLANE_PATH = "/v1/dataflows";
    private static final String DATA_PLANE_API_URI = "http://localhost:" + DATA_PLANE_API_PORT + DATA_PLANE_PATH;

    private static final TypeTransformerRegistry TRANSFORMER_REGISTRY = new TypeTransformerRegistryImpl();
    private static final TitaniumJsonLd JSON_LD = new TitaniumJsonLd(mock(Monitor.class));
    private static ClientAndServer dataPlane;
    private final DataPlaneInstance instance = DataPlaneInstance.Builder.newInstance().url(DATA_PLANE_API_URI).build();
    private final ControlApiHttpClient httpClient = new ControlApiHttpClientImpl(testHttpClient(), mock());

    private final DataPlaneClient dataPlaneClient = new DataPlaneSignalingClient(httpClient, TRANSFORMER_REGISTRY,
            JSON_LD, MAPPER, instance);

    @BeforeAll
    public static void setUp() {
        var factory = Json.createBuilderFactory(Map.of());

        JSON_LD.registerNamespace(VOCAB, EDC_NAMESPACE);
        dataPlane = startClientAndServer(DATA_PLANE_API_PORT);
        TRANSFORMER_REGISTRY.register(new JsonObjectFromDataFlowTerminateMessageTransformer(factory));
        TRANSFORMER_REGISTRY.register(new JsonObjectFromDataFlowSuspendMessageTransformer(factory));
        TRANSFORMER_REGISTRY.register(new JsonObjectFromDataFlowStartMessageTransformer(factory, MAPPER));
        TRANSFORMER_REGISTRY.register(new JsonObjectFromDataFlowResponseMessageTransformer(factory));
        TRANSFORMER_REGISTRY.register(new JsonObjectToDataFlowResponseMessageTransformer());
        TRANSFORMER_REGISTRY.register(new JsonObjectFromDataAddressDspaceTransformer(factory, MAPPER));
        TRANSFORMER_REGISTRY.register(new JsonObjectToDataAddressDspaceTransformer());
    }

    @AfterAll
    public static void tearDown() {
        stopQuietly(dataPlane);
    }

    @AfterEach
    public void resetMockServer() {
        dataPlane.reset();
    }

    @Nested
    class Start {

        @Test
        void verifyReturnFatalErrorIfReceiveResponseWithNullBody() throws JsonProcessingException {
            var flowRequest = createDataFlowRequest();

            var expected = TRANSFORMER_REGISTRY.transform(flowRequest, JsonObject.class)
                    .compose(JSON_LD::compact)
                    .orElseThrow((e) -> new EdcException(e.getFailureDetail()));

            var httpRequest = new HttpRequest().withPath(DATA_PLANE_PATH).withBody(MAPPER.writeValueAsString(expected));
            dataPlane.when(httpRequest).respond(response().withStatusCode(HttpStatusCode.BAD_REQUEST_400.code()));

            var result = dataPlaneClient.start(flowRequest);

            dataPlane.verify(httpRequest);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailure().status()).isEqualTo(ResponseStatus.FATAL_ERROR);
            assertThat(result.getFailureMessages())
                    .anySatisfy(s -> assertThat(s).contains("400").contains(flowRequest.getProcessId()));
        }

        @Test
        void verifyReturnFatalErrorIfReceiveErrorInResponse() throws JsonProcessingException {
            var flowRequest = createDataFlowRequest();

            var expected = TRANSFORMER_REGISTRY.transform(flowRequest, JsonObject.class)
                    .compose(JSON_LD::compact)
                    .orElseThrow((e) -> new EdcException(e.getFailureDetail()));

            var httpRequest = new HttpRequest().withPath(DATA_PLANE_PATH).withBody(MAPPER.writeValueAsString(expected));
            var errorMsg = UUID.randomUUID().toString();
            dataPlane.when(httpRequest).respond(withResponse(errorMsg));

            var result = dataPlaneClient.start(flowRequest);

            dataPlane.verify(httpRequest);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailure().status()).isEqualTo(ResponseStatus.FATAL_ERROR);
            assertThat(result.getFailureMessages()).anySatisfy(s -> assertThat(s).contains("400").contains(flowRequest.getProcessId()));
        }

        @Test
        void verifyReturnFatalErrorIfTransformFails() {
            var flowRequest = createDataFlowRequest();
            TypeTransformerRegistry registry = mock();
            var dataPlaneClient = new DataPlaneSignalingClient(httpClient, registry, JSON_LD, MAPPER, instance);

            when(registry.transform(any(), any())).thenReturn(Result.failure("Transform Failure"));

            var result = dataPlaneClient.start(flowRequest);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailure().status()).isEqualTo(ResponseStatus.FATAL_ERROR);
            assertThat(result.getFailureMessages())
                    .anySatisfy(s -> assertThat(s)
                            .isEqualTo("Transform Failure")
                    );
        }

        @Test
        void verifyReturnFatalError_whenBadResponse() throws JsonProcessingException {
            var flowRequest = createDataFlowRequest();
            var expected = TRANSFORMER_REGISTRY.transform(flowRequest, JsonObject.class)
                    .compose(JSON_LD::compact)
                    .orElseThrow((e) -> new EdcException(e.getFailureDetail()));


            var httpRequest = new HttpRequest().withPath(DATA_PLANE_PATH).withBody(MAPPER.writeValueAsString(expected));
            dataPlane.when(httpRequest, once()).respond(response().withBody("{}").withStatusCode(HttpStatusCode.OK_200.code()));

            var result = dataPlaneClient.start(flowRequest);

            dataPlane.verify(httpRequest, VerificationTimes.once());

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailure().status()).isEqualTo(ResponseStatus.FATAL_ERROR);
            assertThat(result.getFailureMessages())
                    .anySatisfy(s -> assertThat(s)
                            .contains("Error expanding JSON-LD structure")
                    );
        }

        @Test
        void verifyTransferSuccess() throws JsonProcessingException {
            var flowRequest = createDataFlowRequest();
            var expected = TRANSFORMER_REGISTRY.transform(flowRequest, JsonObject.class)
                    .compose(JSON_LD::compact)
                    .orElseThrow((e) -> new EdcException(e.getFailureDetail()));

            var flowResponse = DataFlowResponseMessage.Builder.newInstance().dataAddress(DataAddress.Builder.newInstance().type("type").build()).build();
            var response = TRANSFORMER_REGISTRY.transform(flowResponse, JsonObject.class)
                    .compose(JSON_LD::compact)
                    .orElseThrow((e) -> new EdcException(e.getFailureDetail()));


            var httpRequest = new HttpRequest().withPath(DATA_PLANE_PATH).withBody(MAPPER.writeValueAsString(expected));
            dataPlane.when(httpRequest, once()).respond(response().withBody(MAPPER.writeValueAsString(response)).withStatusCode(HttpStatusCode.OK_200.code()));

            var result = dataPlaneClient.start(flowRequest);

            dataPlane.verify(httpRequest, VerificationTimes.once());

            assertThat(result).isSucceeded().extracting(DataFlowResponseMessage::getDataAddress).isNotNull();
        }

        @Test
        void verifyTransferSuccess_withoutDataAddress() throws JsonProcessingException {
            var flowRequest = createDataFlowRequest();
            var expected = TRANSFORMER_REGISTRY.transform(flowRequest, JsonObject.class)
                    .compose(JSON_LD::compact)
                    .orElseThrow((e) -> new EdcException(e.getFailureDetail()));

            var flowResponse = DataFlowResponseMessage.Builder.newInstance().build();
            var response = TRANSFORMER_REGISTRY.transform(flowResponse, JsonObject.class)
                    .compose(JSON_LD::compact)
                    .orElseThrow((e) -> new EdcException(e.getFailureDetail()));


            var httpRequest = new HttpRequest().withPath(DATA_PLANE_PATH).withBody(MAPPER.writeValueAsString(expected));
            dataPlane.when(httpRequest, once()).respond(response().withBody(MAPPER.writeValueAsString(response)).withStatusCode(HttpStatusCode.OK_200.code()));

            var result = dataPlaneClient.start(flowRequest);

            dataPlane.verify(httpRequest, VerificationTimes.once());

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent().getDataAddress()).isNull();
        }


        private HttpResponse withResponse(String errorMsg) throws JsonProcessingException {
            return response().withStatusCode(HttpStatusCode.BAD_REQUEST_400.code())
                    .withBody(MAPPER.writeValueAsString(new TransferErrorResponse(List.of(errorMsg))), MediaType.APPLICATION_JSON);
        }

        private DataFlowStartMessage createDataFlowRequest() {
            return DataFlowStartMessage.Builder.newInstance()
                    .id("123")
                    .processId("456")
                    .transferType(new TransferType("DestinationType", FlowType.PULL))
                    .assetId("assetId")
                    .agreementId("agreementId")
                    .participantId("participantId")
                    .callbackAddress(URI.create("http://void"))
                    .sourceDataAddress(DataAddress.Builder.newInstance().type("test").build())
                    .destinationDataAddress(DataAddress.Builder.newInstance().type("test").build())
                    .build();
        }

    }

    @Nested
    class Terminate {

        @Test
        void shouldCallTerminateOnAllTheAvailableDataPlanes() {
            var httpRequest = new HttpRequest().withMethod("POST").withPath(DATA_PLANE_PATH + "/processId/terminate");
            dataPlane.when(httpRequest, once()).respond(response().withStatusCode(NO_CONTENT_204.code()));

            var result = dataPlaneClient.terminate("processId");

            assertThat(result).isSucceeded();
            dataPlane.verify(httpRequest, VerificationTimes.once());
        }

        @Test
        void shouldFail_whenConflictResponse() {
            var httpRequest = new HttpRequest().withMethod("POST").withPath(DATA_PLANE_PATH + "/processId/terminate");
            dataPlane.when(httpRequest, once()).respond(response().withStatusCode(CONFLICT_409.code()));

            var result = dataPlaneClient.terminate("processId");

            assertThat(result).isFailed();
        }

        @Test
        void verifyReturnFatalErrorIfTransformFails() {
            TypeTransformerRegistry registry = mock();
            var dataPlaneClient = new DataPlaneSignalingClient(httpClient, registry, JSON_LD, MAPPER, instance);

            when(registry.transform(any(), any())).thenReturn(Result.failure("Transform Failure"));

            var result = dataPlaneClient.terminate("processId");

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailure().status()).isEqualTo(ResponseStatus.FATAL_ERROR);
            assertThat(result.getFailureMessages())
                    .anySatisfy(s -> assertThat(s)
                            .isEqualTo("Transform Failure")
                    );
        }

    }

    @Nested
    class Suspend {

        @Test
        void shouldCallSuspendOnAllTheAvailableDataPlanes() {
            var httpRequest = new HttpRequest().withMethod("POST").withPath(DATA_PLANE_PATH + "/processId/suspend");
            dataPlane.when(httpRequest, once()).respond(response().withStatusCode(NO_CONTENT_204.code()));

            var result = dataPlaneClient.suspend("processId");

            assertThat(result).isSucceeded();
            dataPlane.verify(httpRequest, VerificationTimes.once());
        }

        @Test
        void shouldFail_whenConflictResponse() {
            var httpRequest = new HttpRequest().withMethod("POST").withPath(DATA_PLANE_PATH + "/processId/suspend");
            dataPlane.when(httpRequest, once()).respond(response().withStatusCode(CONFLICT_409.code()));

            var result = dataPlaneClient.suspend("processId");

            assertThat(result).isFailed();
        }

        @Test
        void verifyReturnFatalErrorIfTransformFails() {
            TypeTransformerRegistry registry = mock();
            var dataPlaneClient = new DataPlaneSignalingClient(httpClient, registry, JSON_LD, MAPPER, instance);

            when(registry.transform(any(), any())).thenReturn(Result.failure("Transform Failure"));

            var result = dataPlaneClient.suspend("processId");

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailure().status()).isEqualTo(ResponseStatus.FATAL_ERROR);
            assertThat(result.getFailureMessages())
                    .anySatisfy(s -> assertThat(s)
                            .isEqualTo("Transform Failure")
                    );
        }
    }

    @Nested
    class CheckAvailability {
        @Test
        void shouldSucceed_whenDataPlaneIsAvailable() {
            dataPlane.when(request().withPath(DATA_PLANE_PATH + "/check").withMethod("GET")).respond(response().withStatusCode(204));

            var result = dataPlaneClient.checkAvailability();

            assertThat(result).isSucceeded();
        }

        @Test
        void shouldFail_whenDataPlaneIsNotAvailable() {
            dataPlane.when(request()).respond(response().withStatusCode(404));

            var result = dataPlaneClient.checkAvailability();

            assertThat(result).isFailed();
        }
    }
}
