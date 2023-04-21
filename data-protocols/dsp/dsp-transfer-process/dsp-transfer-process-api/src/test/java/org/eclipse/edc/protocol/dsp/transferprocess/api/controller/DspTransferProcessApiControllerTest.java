/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.transferprocess.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.connector.spi.transferprocess.TransferProcessProtocolService;
import org.eclipse.edc.connector.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferCompletionMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferRemoteMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferTerminationMessage;
import org.eclipse.edc.jsonld.transformer.JsonLdTransformerRegistry;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.service.spi.result.ServiceResult;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.JsonLdExtension.TYPE_MANAGER_CONTEXT_JSON_LD;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.BASE_PATH;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.TRANSFER_COMPLETION;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.TRANSFER_INITIAL_REQUEST;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.TRANSFER_START;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.TRANSFER_SUSPENSION;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.TRANSFER_TERMINATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ApiTest
class DspTransferProcessApiControllerTest extends RestControllerTestBase {
    
    private final ObjectMapper mapper = mock(ObjectMapper.class);
    private final IdentityService identityService = mock(IdentityService.class);
    private final JsonLdTransformerRegistry registry = mock(JsonLdTransformerRegistry.class);
    private final TransferProcessProtocolService protocolService = mock(TransferProcessProtocolService.class);
    private final String callbackAddress = "http://callback";
    private final String authHeader = "auth";
    
    private JsonObject request = Json.createObjectBuilder()
            .add("http://schema/key", "value")
            .build();
    
    @Override
    protected Object controller() {
        var typeManager = mock(TypeManager.class);
        when(typeManager.getMapper(TYPE_MANAGER_CONTEXT_JSON_LD)).thenReturn(mapper);
        
        return new DspTransferProcessApiController(mock(Monitor.class), typeManager, registry, protocolService, identityService, callbackAddress);
    }
    
    @Test
    void getTransferProcess_shouldReturnNotImplemented_whenOperationNotSupported() {
        //operation not yet supported
        baseRequest()
                .get(BASE_PATH + "testId")
                .then()
                .statusCode(501);
    }
    
    @Test
    void initiateTransferProcess_shouldReturnTransferProcess_whenValidRequest() {
        var token = token();
        var message = transferRequestMessage();
        var process = transferProcess();
        var json = Json.createObjectBuilder().build();
        var map = new HashMap<String, Object>() {{
                put("key", "value");
            }};
        
        when(identityService.verifyJwtToken(any(TokenRepresentation.class), eq(callbackAddress)))
                .thenReturn(Result.success(token));
        when(registry.transform(any(JsonObject.class), eq(TransferRequestMessage.class)))
                .thenReturn(Result.success(message));
        when(protocolService.notifyRequested(message, token)).thenReturn(ServiceResult.success(process));
        when(registry.transform(any(TransferProcess.class), eq(JsonObject.class))).thenReturn(Result.success(json));
        when(mapper.convertValue(any(JsonObject.class), eq(Map.class))).thenReturn(map);
        
        var result = baseRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post(BASE_PATH + TRANSFER_INITIAL_REQUEST)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract().as(Map.class);
        
        assertThat(result).isEqualTo(map);
        verify(protocolService, times(1)).notifyRequested(message, token);
    }
    
    @Test
    void initiateTransferProcess_shouldReturnInternalServerError_whenTransferProcessTransformationFails() {
        var token = token();
        var message = transferRequestMessage();
        var process = transferProcess();
        
        when(identityService.verifyJwtToken(any(TokenRepresentation.class), eq(callbackAddress)))
                .thenReturn(Result.success(token));
        when(registry.transform(any(JsonObject.class), eq(TransferRequestMessage.class)))
                .thenReturn(Result.success(message));
        when(protocolService.notifyRequested(message, token)).thenReturn(ServiceResult.success(process));
        when(registry.transform(any(TransferProcess.class), eq(JsonObject.class))).thenReturn(Result.failure("error"));
        
        baseRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post(BASE_PATH + TRANSFER_INITIAL_REQUEST)
                .then()
                .statusCode(500);
    }
    
    @Test
    void initiateTransferProcess_shouldReturnInternalServerError_whenConvertingResultFails() {
        var token = token();
        var message = transferRequestMessage();
        var process = transferProcess();
        var json = Json.createObjectBuilder().build();
        
        when(identityService.verifyJwtToken(any(TokenRepresentation.class), eq(callbackAddress)))
                .thenReturn(Result.success(token));
        when(registry.transform(any(JsonObject.class), eq(TransferRequestMessage.class)))
                .thenReturn(Result.success(message));
        when(protocolService.notifyRequested(message, token)).thenReturn(ServiceResult.success(process));
        when(registry.transform(any(TransferProcess.class), eq(JsonObject.class))).thenReturn(Result.success(json));
        when(mapper.convertValue(any(JsonObject.class), eq(Map.class))).thenThrow(IllegalArgumentException.class);
        
        baseRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post(BASE_PATH + TRANSFER_INITIAL_REQUEST)
                .then()
                .statusCode(500);
    }
    
    @Test
    void consumerTransferProcessSuspension_shouldReturnNotImplemented_whenOperationNotSupported() {
        baseRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post(BASE_PATH + "testId" + TRANSFER_SUSPENSION)
                .then()
                .statusCode(501);
    }
    
    /**
     * Verifies that an endpoint returns 401 if the identity service cannot verify the identity token.
     *
     * @param path the request path to the endpoint
     */
    @ParameterizedTest
    @MethodSource("controllerMethodArguments")
    void callEndpoint_shouldReturnUnauthorized_whenNotAuthorized(String path) {
        when(identityService.verifyJwtToken(any(TokenRepresentation.class), eq(callbackAddress)))
                .thenReturn(Result.failure("error"));
        
        baseRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post(path)
                .then()
                .statusCode(401);
    }
    
    /**
     * Verifies that an endpoint returns 400 if the incoming message cannot be transformed.
     *
     * @param path the request path to the endpoint
     */
    @ParameterizedTest
    @MethodSource("controllerMethodArguments")
    void callEndpoint_shouldReturnBadRequest_whenRequestTransformationFails(String path) {
        var token = token();
        
        when(identityService.verifyJwtToken(any(TokenRepresentation.class), eq(callbackAddress)))
                .thenReturn(Result.success(token));
        when(registry.transform(any(JsonObject.class), argThat(TransferRemoteMessage.class::isAssignableFrom)))
                .thenReturn(Result.failure("error"));
        
        baseRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post(path)
                .then()
                .statusCode(400);
    }
    
    /**
     * Verifies that an endpoint returns 204 if the call to the service was successful. Also verifies
     * that the correct service method was called. This is only applicable for endpoints that do not
     * return a response body.
     *
     * @param path the request path to the endpoint
     * @param message the request message to be returned by the transformer registry
     * @param serviceMethod reference to the service method that should be called, required to verify that it was called
     */
    @ParameterizedTest
    @MethodSource("controllerMethodArgumentsForServiceCall")
    void callEndpoint_shouldCallService_whenValidRequest(String path, TransferRemoteMessage message, Method serviceMethod) throws Exception {
        var token = token();
        
        when(identityService.verifyJwtToken(any(TokenRepresentation.class), eq(callbackAddress)))
                .thenReturn(Result.success(token));
        when(registry.transform(any(JsonObject.class), argThat(TransferRemoteMessage.class::isAssignableFrom)))
                .thenReturn(Result.success(message));
        
        when(protocolService.notifyStarted(any(TransferStartMessage.class), eq(token))).thenReturn(ServiceResult.success(transferProcess()));
        when(protocolService.notifyCompleted(any(TransferCompletionMessage.class), eq(token))).thenReturn(ServiceResult.success(transferProcess()));
        when(protocolService.notifyTerminated(any(TransferTerminationMessage.class), eq(token))).thenReturn(ServiceResult.success(transferProcess()));
        
        baseRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post(path)
                .then()
                .statusCode(204);
        
        var verify = verify(protocolService, times(1));
        serviceMethod.invoke(verify, message, token);
    }
    
    /**
     * Verifies that an endpoint returns 500 if there is an error in the service. Also verifies
     * that the correct service method was called.
     *
     * @param path the request path to the endpoint
     * @param message the request message to be returned by the transformer registry
     * @param serviceMethod reference to the service method that should be called, required to verify that it was called
     */
    @ParameterizedTest
    @MethodSource("controllerMethodArgumentsForServiceError")
    void callEndpoint_shouldReturnConflict_whenServiceResultConflict(String path, TransferRemoteMessage message, Method serviceMethod) throws Exception {
        var token = token();
        
        when(identityService.verifyJwtToken(any(TokenRepresentation.class), eq(callbackAddress)))
                .thenReturn(Result.success(token));
        when(registry.transform(any(JsonObject.class), argThat(TransferRemoteMessage.class::isAssignableFrom)))
                .thenReturn(Result.success(message));
        
        when(protocolService.notifyRequested(any(TransferRequestMessage.class), eq(token))).thenReturn(ServiceResult.conflict("error"));
        when(protocolService.notifyStarted(any(TransferStartMessage.class), eq(token))).thenReturn(ServiceResult.conflict("error"));
        when(protocolService.notifyCompleted(any(TransferCompletionMessage.class), eq(token))).thenReturn(ServiceResult.conflict("error"));
        when(protocolService.notifyTerminated(any(TransferTerminationMessage.class), eq(token))).thenReturn(ServiceResult.conflict("error"));
        
        baseRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post(path)
                .then()
                .statusCode(409);
        
        var verify = verify(protocolService, times(1));
        serviceMethod.invoke(verify, message, token);
    }
    
    private static Stream<Arguments> controllerMethodArguments() {
        return Stream.of(
                Arguments.of(BASE_PATH + TRANSFER_INITIAL_REQUEST),
                Arguments.of(BASE_PATH + "testId" + TRANSFER_START),
                Arguments.of(BASE_PATH + "testId" + TRANSFER_COMPLETION),
                Arguments.of(BASE_PATH + "testId" + TRANSFER_TERMINATION)
        );
    }
    
    private static Stream<Arguments> controllerMethodArgumentsForServiceCall() throws Exception {
        return Stream.of(
                Arguments.of(BASE_PATH + "testId" + TRANSFER_START, transferStartMessage(),
                        TransferProcessProtocolService.class.getDeclaredMethod("notifyStarted", TransferStartMessage.class, ClaimToken.class)),
                Arguments.of(BASE_PATH + "testId" + TRANSFER_COMPLETION, transferCompletionMessage(),
                        TransferProcessProtocolService.class.getDeclaredMethod("notifyCompleted", TransferCompletionMessage.class, ClaimToken.class)),
                Arguments.of(BASE_PATH + "testId" + TRANSFER_TERMINATION, transferTerminationMessage(),
                        TransferProcessProtocolService.class.getDeclaredMethod("notifyTerminated", TransferTerminationMessage.class, ClaimToken.class))
        );
    }
    
    private static Stream<Arguments> controllerMethodArgumentsForServiceError() throws Exception {
        return Stream.of(
                Arguments.of(BASE_PATH + TRANSFER_INITIAL_REQUEST, transferRequestMessage(),
                        TransferProcessProtocolService.class.getDeclaredMethod("notifyRequested", TransferRequestMessage.class, ClaimToken.class)),
                Arguments.of(BASE_PATH + "testId" + TRANSFER_START, transferStartMessage(),
                        TransferProcessProtocolService.class.getDeclaredMethod("notifyStarted", TransferStartMessage.class, ClaimToken.class)),
                Arguments.of(BASE_PATH + "testId" + TRANSFER_COMPLETION, transferCompletionMessage(),
                        TransferProcessProtocolService.class.getDeclaredMethod("notifyCompleted", TransferCompletionMessage.class, ClaimToken.class)),
                Arguments.of(BASE_PATH + "testId" + TRANSFER_TERMINATION, transferTerminationMessage(),
                        TransferProcessProtocolService.class.getDeclaredMethod("notifyTerminated", TransferTerminationMessage.class, ClaimToken.class))
        );
    }
    
    private RequestSpecification baseRequest() {
        return given()
                .baseUri("http://localhost:" + port)
                .basePath("/")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .when();
    }
    
    private static ClaimToken token() {
        return ClaimToken.Builder.newInstance().build();
    }
    
    private TransferProcess transferProcess() {
        return TransferProcess.Builder.newInstance().id("id").build();
    }
    
    private static TransferRequestMessage transferRequestMessage() {
        return TransferRequestMessage.Builder.newInstance()
                .protocol("protocol")
                .callbackAddress("http://connector")
                .build();
    }
    
    private static TransferStartMessage transferStartMessage() {
        return TransferStartMessage.Builder.newInstance()
                .protocol("protocol")
                .callbackAddress("http://connector")
                .processId("processId")
                .build();
    }
    
    private static TransferCompletionMessage transferCompletionMessage() {
        return TransferCompletionMessage.Builder.newInstance()
                .protocol("protocol")
                .callbackAddress("http://connector")
                .processId("processId")
                .build();
    }
    
    private static TransferTerminationMessage transferTerminationMessage() {
        return TransferTerminationMessage.Builder.newInstance()
                .protocol("protocol")
                .callbackAddress("http://connector")
                .processId("processId")
                .build();
    }
}
