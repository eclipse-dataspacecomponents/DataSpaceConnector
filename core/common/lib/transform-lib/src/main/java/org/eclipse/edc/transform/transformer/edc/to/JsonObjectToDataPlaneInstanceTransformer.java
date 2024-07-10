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

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_DEST_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_SOURCE_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_TRANSFER_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.Builder;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_STATE_TIMESTAMP;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.LAST_ACTIVE;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.PROPERTIES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.TURN_COUNT;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.URL;

public class JsonObjectToDataPlaneInstanceTransformer extends AbstractJsonLdTransformer<JsonObject, DataPlaneInstance> {
    public JsonObjectToDataPlaneInstanceTransformer() {
        super(JsonObject.class, DataPlaneInstance.class);
    }

    @Override
    public @Nullable DataPlaneInstance transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var builder = Builder.newInstance();
        builder.id(nodeId(jsonObject));
        visitProperties(jsonObject, (key, jsonValue) -> transformProperties(key, jsonValue, builder, context));

        return builder.build();
    }

    private void transformProperties(String key, JsonValue jsonValue, DataPlaneInstance.Builder builder, TransformerContext context) {
        switch (key) {
            case URL -> {
                try {
                    builder.url(new URL(Objects.requireNonNull(transformString(jsonValue, context))));
                } catch (MalformedURLException e) {
                    context.reportProblem(e.getMessage());
                }
            }
            case LAST_ACTIVE -> transformLong(context, jsonValue, builder::lastActive);
            case TURN_COUNT -> builder.turnCount(transformInt(jsonValue, context));
            case ALLOWED_DEST_TYPES -> {
                var set = jsonValue.asJsonArray().stream().map(jv -> transformString(jv, context)).collect(Collectors.toSet());
                builder.allowedDestTypes(set);
            }
            case ALLOWED_SOURCE_TYPES -> {
                var set = jsonValue.asJsonArray().stream().map(jv -> transformString(jv, context)).collect(Collectors.toSet());
                builder.allowedSourceTypes(set);
            }
            case ALLOWED_TRANSFER_TYPES -> {
                var set = jsonValue.asJsonArray().stream().map(jv -> transformString(jv, context)).collect(Collectors.toSet());
                builder.allowedTransferType(set);
            }
            case DATAPLANE_INSTANCE_STATE_TIMESTAMP -> transformLong(context, jsonValue, builder::stateTimestamp);
            case PROPERTIES -> {
                var props = jsonValue.asJsonArray().getJsonObject(0);
                visitProperties(props, (k, val) -> transformProperties(k, val, builder, context));
            }
            default -> builder.property(key, transformGenericProperty(jsonValue, context));
        }
    }

    private void transformLong(@NotNull TransformerContext context, JsonValue jsonValue, Consumer<Long> consumer) {
        if (jsonValue instanceof JsonArray) {
            jsonValue = jsonValue.asJsonArray().getJsonObject(0);
            consumer.accept(((JsonObject) jsonValue).getJsonNumber("@value").longValue());
        } else if (jsonValue instanceof JsonNumber) {
            consumer.accept(((JsonNumber) jsonValue).longValue());
        } else {
            context.reportProblem("Cannot convert a " + jsonValue.getValueType() + " to a long!");
        }
    }
}
