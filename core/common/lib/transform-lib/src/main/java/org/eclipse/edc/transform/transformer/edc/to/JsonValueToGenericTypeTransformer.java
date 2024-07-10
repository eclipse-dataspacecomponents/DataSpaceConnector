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

package org.eclipse.edc.transform.transformer.edc.to;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;

/**
 * Converts from a generic property as a {@link JsonObject} in JSON-LD expanded form to a Java Object.
 */
public class JsonValueToGenericTypeTransformer extends AbstractJsonLdTransformer<JsonValue, Object> {
    private final ObjectMapper mapper;

    public JsonValueToGenericTypeTransformer(ObjectMapper mapper) {
        super(JsonValue.class, Object.class);
        this.mapper = mapper;
    }

    @Override
    public Object transform(@NotNull JsonValue value, @NotNull TransformerContext context) {
        if (value instanceof JsonObject object) {
            if (object.containsKey(VALUE)) {
                var valueField = object.get(VALUE);
                if (valueField == null) {
                    // parse it as a generic object type
                    return toJavaType(object, context);
                }
                return transform(valueField, context);
            } else {
                return toJavaType(object, context);
            }
        } else if (value instanceof JsonArray jsonArray) {
            return jsonArray.stream().map(entry -> transform(entry, context)).collect(toList());
        } else if (value instanceof JsonString jsonString) {
            return jsonString.getString();
        } else if (value instanceof JsonNumber jsonNumber) {
            return jsonNumber.doubleValue(); // use to double to avoid loss of precision
        }
        return null;
    }

    private Object toJavaType(JsonObject object, TransformerContext context) {
        try {
            return mapper.readValue(object.toString(), Object.class);
        } catch (JsonProcessingException e) {
            context.reportProblem(format("Failed to read value: %s", e.getMessage()));
            return null;
        }
    }

}
