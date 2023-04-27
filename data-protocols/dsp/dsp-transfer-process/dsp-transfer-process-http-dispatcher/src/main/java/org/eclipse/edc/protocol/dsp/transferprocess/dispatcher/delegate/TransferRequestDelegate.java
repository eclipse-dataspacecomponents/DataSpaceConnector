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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.edc.connector.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.jsonld.transformer.JsonLdTransformerRegistry;
import org.eclipse.edc.protocol.dsp.spi.dispatcher.DspHttpDispatcherDelegate;
import org.eclipse.edc.spi.EdcException;

import java.io.IOException;
import java.util.function.Function;

import static org.eclipse.edc.jsonld.util.JsonLdUtil.expand;
import static org.eclipse.edc.protocol.dsp.transferprocess.dispatcher.DelegateUtil.toCompactedJson;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.BASE_PATH;
import static org.eclipse.edc.protocol.dsp.transferprocess.spi.TransferProcessApiPaths.TRANSFER_INITIAL_REQUEST;

public class TransferRequestDelegate implements DspHttpDispatcherDelegate<TransferRequestMessage, TransferProcess> {

    private final ObjectMapper mapper;

    private final JsonLdTransformerRegistry registry;

    public TransferRequestDelegate(ObjectMapper mapper, JsonLdTransformerRegistry registry) {
        this.mapper = mapper;
        this.registry = registry;
    }

    @Override
    public Class<TransferRequestMessage> getMessageType() {
        return TransferRequestMessage.class;
    }

    @Override
    public Request buildRequest(TransferRequestMessage message) {
        var transferRequest = registry.transform(message, JsonObject.class);

        if (transferRequest.failed()) {
            throw new EdcException("Failed to create request body for transfer request message");
        }
    
        var requestBody = RequestBody.create(toCompactedJson(transferRequest.getContent(), jsonLdContext(), mapper),
                MediaType.get(jakarta.ws.rs.core.MediaType.APPLICATION_JSON));

        return new Request.Builder()
                .url(message.getCallbackAddress() + BASE_PATH + TRANSFER_INITIAL_REQUEST)
                .post(requestBody)
                .build();
    }

    @Override
    public Function<Response, TransferProcess> parseResponse() {
        return response -> {
            try {
                var jsonObject = mapper.readValue(response.body().bytes(), JsonObject.class);
                var result = registry.transform(expand(jsonObject).get(0), TransferProcess.class);

                if (result.succeeded()) {
                    return result.getContent();
                } else {
                    throw new EdcException("Failed to read response body from transfer request");
                }

            } catch (RuntimeException | IOException e) {
                throw new EdcException("Failed to read response body from contract request.", e);
            }
        };
    }

    private JsonObject jsonLdContext() {
        return Json.createObjectBuilder()
//                .add(DCT_PREFIX, DCT_SCHEMA) TODO Waiting for PR#2759
//                .add(DSPACE_PREFIX, DSPACE_SCHEMA) TODO Waiting for PR#2759
                .build();
    }
}
