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

package org.eclipse.edc.protocol.dsp.transferprocess.dispatcher.delegate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.jsonld.transformer.JsonLdTransformerRegistry;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.jsonld.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.BASE_PATH;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.TRANSFER_START;
import static org.eclipse.edc.protocol.dsp.transferprocess.transformer.DspTransferProcessPropertyAndTypeNames.DSPACE_PREFIX;
import static org.eclipse.edc.protocol.dsp.transferprocess.transformer.DspTransferProcessPropertyAndTypeNames.DSPACE_SCHEMA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferStartDelegateTest {

    private ObjectMapper mapper = mock(ObjectMapper.class);
    private JsonLdTransformerRegistry registry = mock(JsonLdTransformerRegistry.class);

    private TransferStartDelegate startDelegate;

    @BeforeEach
    void setUp() {
        startDelegate = new TransferStartDelegate(mapper, registry);
    }

    @Test
    void getMessageType_returnCatalogRequest() {
        assertThat(startDelegate.getMessageType()).isEqualTo(TransferStartMessage.class);
    }

    @Test
    void buildRequest_returnRequest() throws IOException {
        var jsonObject = Json.createObjectBuilder()
                .add(DSPACE_SCHEMA + "key", "value")
                .build();
        var requestBody = "request body";
        
        when(registry.transform(any(TransferStartMessage.class), eq(JsonObject.class))).thenReturn(Result.success(jsonObject));
        when(mapper.writeValueAsString(any(JsonObject.class))).thenReturn(requestBody);
        
        var message = getTransferStartMessage();
        var request = startDelegate.buildRequest(message);
        
        assertThat(request.url().url()).hasToString(message.getCallbackAddress() + BASE_PATH + message.getProcessId() + TRANSFER_START);
        assertThat(readRequestBody(request)).isEqualTo(requestBody);
        
        verify(registry, times(1)).transform(any(TransferStartMessage.class), eq(JsonObject.class));
        verify(mapper, times(1))
                .writeValueAsString(argThat(json -> ((JsonObject) json).get(CONTEXT) != null && ((JsonObject) json).get(DSPACE_PREFIX + ":key") != null));
    }
    
    @Test
    void buildRequest_transformationFails_throwException() {
        when(registry.transform(any(TransferStartMessage.class), eq(JsonObject.class))).thenReturn(Result.failure("error"));
        
        assertThatThrownBy(() -> startDelegate.buildRequest(getTransferStartMessage())).isInstanceOf(EdcException.class);
    }
    
    @Test
    void buildRequest_writingJsonFails_throwException() throws JsonProcessingException {
        when(registry.transform(any(TransferStartMessage.class), eq(JsonObject.class))).thenReturn(Result.success(Json.createObjectBuilder().build()));
        when(mapper.writeValueAsString(any(JsonObject.class))).thenThrow(JsonProcessingException.class);
    
        assertThatThrownBy(() -> startDelegate.buildRequest(getTransferStartMessage())).isInstanceOf(EdcException.class);
    }
    
    @Test
    void parseResponse_returnNull() {
        var response = mock(Response.class);
        
        assertThat(startDelegate.parseResponse().apply(response)).isNull();
    }

    private TransferStartMessage getTransferStartMessage() {
        return TransferStartMessage.Builder.newInstance()
                .processId("testId")
                .protocol("dataspace-protocol")
                .callbackAddress("http://test-connector-address")
                .build();
    }
    
    private String readRequestBody(Request request) throws IOException {
        var buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }
}
