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

package org.eclipse.edc.protocol.dsp.negotiation.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import okhttp3.ResponseBody;
import org.eclipse.edc.connector.contract.spi.types.protocol.ContractNegotiationAck;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.protocol.dsp.spi.dispatcher.response.DspHttpResponseBodyExtractor;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.Failure;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Function;

/**
 * Extract {@link ContractNegotiationAck} from {@link ResponseBody}
 */
public class ContractNegotiationBodyExtractor implements DspHttpResponseBodyExtractor<ContractNegotiationAck> {
    private final ObjectMapper objectMapper;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry transformerRegistry;

    public ContractNegotiationBodyExtractor(ObjectMapper objectMapper, JsonLd jsonLd, TypeTransformerRegistry transformerRegistry) {
        this.objectMapper = objectMapper;
        this.jsonLd = jsonLd;
        this.transformerRegistry = transformerRegistry;
    }

    @Override
    public ContractNegotiationAck extractBody(ResponseBody responseBody) {
        try {
            var jsonObject = objectMapper.readValue(responseBody.byteStream(), JsonObject.class);
            var expanded = jsonLd.expand(jsonObject).orElseThrow(exception("Cannot expand json-ld"));
            return transformerRegistry.transform(expanded, ContractNegotiationAck.class)
                    .orElseThrow(exception("Cannot transform json to ContractNegotiationAck"));

        } catch (IOException e) {
            throw new EdcException("Cannot deserialize response body as JsonObject", e);
        }
    }

    @NotNull
    private Function<Failure, EdcException> exception(String message) {
        return f -> new EdcException("%s: %s".formatted(message, f.getFailureDetail()));
    }
}
