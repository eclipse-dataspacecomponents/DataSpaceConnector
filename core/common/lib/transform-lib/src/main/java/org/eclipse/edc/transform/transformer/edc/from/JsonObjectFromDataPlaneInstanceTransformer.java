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

package org.eclipse.edc.transform.transformer.edc.from;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_DEST_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_SOURCE_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_TRANSFER_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_STATE;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_STATE_TIMESTAMP;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.LAST_ACTIVE;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.PROPERTIES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.TURN_COUNT;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.URL;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;

public class JsonObjectFromDataPlaneInstanceTransformer extends AbstractJsonLdTransformer<DataPlaneInstance, JsonObject> {
    private final JsonBuilderFactory jsonFactory;
    private final ObjectMapper mapper;

    public JsonObjectFromDataPlaneInstanceTransformer(JsonBuilderFactory jsonFactory, ObjectMapper mapper) {
        super(DataPlaneInstance.class, JsonObject.class);
        this.jsonFactory = jsonFactory;
        this.mapper = mapper;
    }

    @Override
    public @Nullable JsonObject transform(@NotNull DataPlaneInstance dataPlaneInstance, @NotNull TransformerContext context) {
        var builder = jsonFactory.createObjectBuilder()
                .add(ID, dataPlaneInstance.getId())
                .add(TYPE, DataPlaneInstance.DATAPLANE_INSTANCE_TYPE)
                .add(URL, dataPlaneInstance.getUrl().toString())
                .add(LAST_ACTIVE, dataPlaneInstance.getLastActive())
                .add(TURN_COUNT, dataPlaneInstance.getTurnCount());

        //properties
        if (dataPlaneInstance.getProperties() != null && !dataPlaneInstance.getProperties().isEmpty()) {
            var propBuilder = jsonFactory.createObjectBuilder();
            transformProperties(dataPlaneInstance.getProperties(), propBuilder, mapper, context);
            builder.add(PROPERTIES, propBuilder);
        }

        var srcBldr = jsonFactory.createArrayBuilder(dataPlaneInstance.getAllowedSourceTypes());
        builder.add(ALLOWED_SOURCE_TYPES, srcBldr);

        var dstBldr = jsonFactory.createArrayBuilder(dataPlaneInstance.getAllowedDestTypes());
        builder.add(ALLOWED_DEST_TYPES, dstBldr);

        var transferTypesBldr = jsonFactory.createArrayBuilder(dataPlaneInstance.getAllowedTransferTypes());
        builder.add(ALLOWED_TRANSFER_TYPES, transferTypesBldr);

        var state = Optional.ofNullable(DataPlaneInstanceStates.from(dataPlaneInstance.getState()))
                .map(Enum::name)
                .orElse(null);

        addIfNotNull(state, DATAPLANE_INSTANCE_STATE, builder);

        builder.add(DATAPLANE_INSTANCE_STATE_TIMESTAMP, dataPlaneInstance.getStateTimestamp());

        return builder.build();
    }
}
