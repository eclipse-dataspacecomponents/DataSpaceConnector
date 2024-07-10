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

package org.eclipse.edc.connector.controlplane.transform.edc.from;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.transform.Payload;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_DATA_ADDRESS;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_PRIVATE_PROPERTIES;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_PROPERTIES;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_CATALOG_ASSET_TYPE;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.PROPERTY_IS_CATALOG;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.util.JacksonJsonLd.createObjectMapper;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.types.domain.DataAddress.EDC_DATA_ADDRESS_TYPE_PROPERTY;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonObjectFromAssetTransformerTest {

    private static final String TEST_CONTENT_TYPE = "application/json";
    private static final String TEST_ASSET_ID = "test-asset-id";
    private static final String TEST_DESCRIPTION = "test-description";
    private static final String TEST_VERSION = "0.6.9";
    private static final String TEST_ASSET_NAME = "test-asset";
    private final TransformerContext context = mock(TransformerContext.class);
    private JsonObjectFromAssetTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new JsonObjectFromAssetTransformer(Json.createBuilderFactory(Map.of()), createObjectMapper());
    }

    @Test
    void transform_noCustomProperties() {
        when(context.transform(isA(DataAddress.class), eq(JsonObject.class)))
                .thenReturn(createObjectBuilder().add(EDC_DATA_ADDRESS_TYPE_PROPERTY, value("address-type")).build());
        var dataAddress = DataAddress.Builder.newInstance().type("address-type").build();
        var asset = createAssetBuilder()
                .dataAddress(dataAddress)
                .build();

        var jsonObject = transformer.transform(asset, context);

        assertThat(jsonObject).isNotNull();

        var propsJson = jsonObject.getJsonObject(EDC_ASSET_PROPERTIES);
        assertThat(jsonObject.getJsonString(ID).getString()).isEqualTo(TEST_ASSET_ID);
        assertThat(jsonObject.getJsonString(TYPE).getString()).isEqualTo(Asset.EDC_ASSET_TYPE);
        assertThat(propsJson.getJsonString(EDC_NAMESPACE + "id").getString()).isEqualTo(TEST_ASSET_ID);
        assertThat(propsJson.getJsonString(EDC_NAMESPACE + "contenttype").getString()).isEqualTo(TEST_CONTENT_TYPE);
        assertThat(propsJson.getJsonString(EDC_NAMESPACE + "description").getString()).isEqualTo(TEST_DESCRIPTION);
        assertThat(propsJson.getJsonString(EDC_NAMESPACE + "name").getString()).isEqualTo(TEST_ASSET_NAME);
        assertThat(propsJson.getJsonString(EDC_NAMESPACE + "version").getString()).isEqualTo(TEST_VERSION);
        assertThat(jsonObject.getJsonObject(EDC_ASSET_DATA_ADDRESS).getJsonArray(EDC_DATA_ADDRESS_TYPE_PROPERTY).get(0).asJsonObject().getString(VALUE)).isEqualTo("address-type");
        verify(context).transform(dataAddress, JsonObject.class);
    }

    @Test
    void transform_customProperties_simpleTypes() {
        var asset = createAssetBuilder()
                .property("some-key", "some-value")
                .build();

        var jsonObject = transformer.transform(asset, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getJsonObject(EDC_ASSET_PROPERTIES).getJsonString("some-key").getString()).isEqualTo("some-value");
    }

    @Test
    void transform_withPrivateProperties_simpleTypes() {
        var asset = createAssetBuilder()
                .privateProperty("some-key", "some-value")
                .build();

        var jsonObject = transformer.transform(asset, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getJsonObject(EDC_ASSET_PRIVATE_PROPERTIES).getJsonString("some-key").getString()).isEqualTo("some-value");
    }

    @Test
    void transform_customProperties_withExpandedNamespace() {
        var asset = createAssetBuilder()
                .property("https://foo.bar.org/schema/some-key", "some-value")
                .build();

        var jsonObject = transformer.transform(asset, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getJsonObject(EDC_ASSET_PROPERTIES).getJsonString("https://foo.bar.org/schema/some-key").getString()).isEqualTo("some-value");
    }

    @Test
    void transform_customProperties_withCustomObject() {
        var asset = createAssetBuilder()
                .property("https://foo.bar.org/schema/payload", new Payload("foo-bar", 42))
                .build();

        var jsonObject = transformer.transform(asset, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getJsonObject(EDC_ASSET_PROPERTIES).getJsonObject("https://foo.bar.org/schema/payload")).isInstanceOf(JsonObject.class)
                .satisfies(payload -> {
                    assertThat(payload.get("name")).satisfies(name -> {
                        assertThat(name.getValueType()).isEqualTo(JsonValue.ValueType.STRING);
                        assertThat(name).asInstanceOf(type(JsonString.class)).extracting(JsonString::getString).isEqualTo("foo-bar");
                    });
                    assertThat(payload.get("age")).satisfies(age -> {
                        assertThat(age.getValueType()).isEqualTo(JsonValue.ValueType.NUMBER);
                        assertThat(age).asInstanceOf(type(JsonNumber.class)).extracting(JsonNumber::doubleValue).isEqualTo(42d);
                    });
                });
    }

    @Test
    void transform_shouldSetType_whenAssetIsCatalog() {
        when(context.transform(isA(DataAddress.class), eq(JsonObject.class)))
                .thenReturn(createObjectBuilder()
                        .add(EDC_DATA_ADDRESS_TYPE_PROPERTY, value("address-type"))
                        .build());
        var dataAddress = DataAddress.Builder.newInstance().type("address-type").build();
        var asset = createAssetBuilder()
                .dataAddress(dataAddress)
                .property(PROPERTY_IS_CATALOG, true)
                .build();

        var jsonObject = transformer.transform(asset, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getString(TYPE)).isEqualTo(EDC_CATALOG_ASSET_TYPE);
    }

    private Asset.Builder createAssetBuilder() {
        return Asset.Builder.newInstance()
                .id(TEST_ASSET_ID)
                .version(TEST_VERSION)
                .contentType(TEST_CONTENT_TYPE)
                .description(TEST_DESCRIPTION)
                .name(TEST_ASSET_NAME);
    }

    private JsonArrayBuilder value(String value) {
        return createArrayBuilder().add(createObjectBuilder().add(VALUE, value));
    }

}
