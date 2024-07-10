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

import jakarta.json.Json;
import jakarta.json.JsonString;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_DEST_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_SOURCE_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_TRANSFER_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_STATE;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_STATE_TIMESTAMP;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.LAST_ACTIVE;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.PROPERTIES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.TURN_COUNT;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.URL;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates.AVAILABLE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.util.JacksonJsonLd.createObjectMapper;
import static org.mockito.Mockito.mock;

class JsonObjectFromDataPlaneInstanceTransformerTest {

    private final TransformerContext context = mock();
    private JsonObjectFromDataPlaneInstanceTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new JsonObjectFromDataPlaneInstanceTransformer(Json.createBuilderFactory(Map.of()), createObjectMapper());
    }

    @Test
    void transform() {
        var dpi = DataPlaneInstance.Builder.newInstance()
                .id("test-id")
                .url("http://foo.bar")
                .allowedSourceType("test-source-type")
                .allowedDestType("test-dest-type")
                .allowedTransferType("test-transfer-type")
                .lastActive(15)
                .turnCount(42)
                .property("foo", "bar")
                .build();

        var jsonObject = transformer.transform(dpi, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getString(ID)).isEqualTo("test-id");
        assertThat(jsonObject.getString(URL)).isEqualTo("http://foo.bar");
        assertThat(jsonObject.getJsonArray(ALLOWED_SOURCE_TYPES)).hasSize(1).allMatch(v -> ((JsonString) v).getString().equals("test-source-type"));
        assertThat(jsonObject.getJsonArray(ALLOWED_DEST_TYPES)).hasSize(1).allMatch(v -> ((JsonString) v).getString().equals("test-dest-type"));
        assertThat(jsonObject.getJsonArray(ALLOWED_TRANSFER_TYPES)).hasSize(1).allMatch(v -> ((JsonString) v).getString().equals("test-transfer-type"));
        assertThat(jsonObject.getJsonNumber(LAST_ACTIVE).intValue()).isEqualTo(15);
        assertThat(jsonObject.getJsonNumber(TURN_COUNT).intValue()).isEqualTo(42);
        assertThat(jsonObject.getJsonObject(PROPERTIES).getJsonString("foo").getString()).isEqualTo("bar");

    }

    @Test
    void transform_withState() {
        var dpi = DataPlaneInstance.Builder.newInstance()
                .id("test-id")
                .url("http://foo.bar")
                .allowedSourceType("test-source-type")
                .allowedDestType("test-dest-type")
                .allowedTransferType("test-transfer-type")
                .lastActive(15)
                .turnCount(42)
                .state(AVAILABLE.code())
                .property("foo", "bar")
                .build();

        var jsonObject = transformer.transform(dpi, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getString(ID)).isEqualTo("test-id");
        assertThat(jsonObject.getString(URL)).isEqualTo("http://foo.bar");
        assertThat(jsonObject.getJsonArray(ALLOWED_SOURCE_TYPES)).hasSize(1).allMatch(v -> ((JsonString) v).getString().equals("test-source-type"));
        assertThat(jsonObject.getJsonArray(ALLOWED_DEST_TYPES)).hasSize(1).allMatch(v -> ((JsonString) v).getString().equals("test-dest-type"));
        assertThat(jsonObject.getJsonArray(ALLOWED_TRANSFER_TYPES)).hasSize(1).allMatch(v -> ((JsonString) v).getString().equals("test-transfer-type"));
        assertThat(jsonObject.getJsonNumber(LAST_ACTIVE).intValue()).isEqualTo(15);
        assertThat(jsonObject.getJsonNumber(TURN_COUNT).intValue()).isEqualTo(42);
        assertThat(jsonObject.getString(DATAPLANE_INSTANCE_STATE)).isEqualTo(AVAILABLE.name());
        assertThat(jsonObject.getJsonNumber(DATAPLANE_INSTANCE_STATE_TIMESTAMP).longValue()).isEqualTo(dpi.getStateTimestamp());
        assertThat(jsonObject.getJsonObject(PROPERTIES).getJsonString("foo").getString()).isEqualTo("bar");

    }
}
