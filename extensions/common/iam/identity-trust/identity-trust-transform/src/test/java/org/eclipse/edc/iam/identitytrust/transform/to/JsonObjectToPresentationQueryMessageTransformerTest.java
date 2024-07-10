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

package org.eclipse.edc.iam.identitytrust.transform.to;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.transform.TransformerContextImpl;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.iam.identitytrust.spi.DcpConstants.DCP_CONTEXT_URL;
import static org.mockito.Mockito.mock;

class JsonObjectToPresentationQueryMessageTransformerTest {
    private final ObjectMapper mapper = JacksonJsonLd.createObjectMapper();
    private final JsonObjectToPresentationQueryTransformer transformer = new JsonObjectToPresentationQueryTransformer(mapper);
    private final JsonLd jsonLd = new TitaniumJsonLd(mock());
    private final TypeTransformerRegistry trr = new TypeTransformerRegistryImpl();
    private final TransformerContext context = new TransformerContextImpl(trr);


    @BeforeEach
    void setUp() {
        jsonLd.registerCachedDocument("https://identity.foundation/presentation-exchange/submission/v1", TestUtils.getFileFromResourceName("presentation_ex.json").toURI());
        jsonLd.registerCachedDocument(DCP_CONTEXT_URL, TestUtils.getFileFromResourceName("document/dcp.v08.jsonld").toURI());
        // delegate to the generic transformer

        trr.register(new JsonValueToGenericTypeTransformer(mapper));
    }

    @Test
    void transform_withScopes() throws JsonProcessingException {
        var obj = """
                {
                  "@context": [
                    "https://identity.foundation/presentation-exchange/submission/v1",
                    "https://w3id.org/tractusx-trust/v0.8"
                  ],
                  "@type": "PresentationQueryMessage",
                  "scope": [
                    "org.eclipse.edc.vc.type:TestCredential:read",
                    "org.eclipse.edc.vc.type:AnotherCredential:all"
                  ]
                }
                """;
        var json = mapper.readValue(obj, JsonObject.class);
        var jo = jsonLd.expand(json);
        assertThat(jo.succeeded()).withFailMessage(jo::getFailureDetail).isTrue();

        var query = transformer.transform(jo.getContent(), context);
        assertThat(query).isNotNull();
        assertThat(query.getScopes()).hasSize(2)
                .containsExactlyInAnyOrder(
                        "org.eclipse.edc.vc.type:TestCredential:read",
                        "org.eclipse.edc.vc.type:AnotherCredential:all");
        assertThat(query.getPresentationDefinition()).isNull();
    }


    @Test
    void transform_withEmptyScopes() throws JsonProcessingException {
        var obj = """
                {
                  "@context": [
                    "https://identity.foundation/presentation-exchange/submission/v1",
                    "https://w3id.org/tractusx-trust/v0.8"
                  ],
                  "@type": "PresentationQueryMessage",
                  "scope": []
                }
                """;
        var json = mapper.readValue(obj, JsonObject.class);
        var jo = jsonLd.expand(json);
        assertThat(jo.succeeded()).withFailMessage(jo::getFailureDetail).isTrue();

        var query = transformer.transform(jo.getContent(), context);
        assertThat(query).isNotNull();
        assertThat(query.getScopes()).isEmpty();
        assertThat(query.getPresentationDefinition()).isNull();
    }

    @Test
    void transform_withNullScopes() throws JsonProcessingException {
        var obj = """
                {
                  "@context": [
                    "https://identity.foundation/presentation-exchange/submission/v1",
                    "https://w3id.org/tractusx-trust/v0.8"
                  ],
                  "@type": "PresentationQueryMessage"
                }
                """;
        var json = mapper.readValue(obj, JsonObject.class);
        var jo = jsonLd.expand(json);
        assertThat(jo.succeeded()).withFailMessage(jo::getFailureDetail).isTrue();

        var query = transformer.transform(jo.getContent(), context);
        assertThat(query).isNotNull();
        assertThat(query.getScopes()).isEmpty();
        assertThat(query.getPresentationDefinition()).isNull();
    }


    @Test
    void transform_withScopes_separatedByWhitespace() throws JsonProcessingException {
        var obj = """
                {
                  "@context": [
                    "https://identity.foundation/presentation-exchange/submission/v1",
                    "https://w3id.org/tractusx-trust/v0.8"
                  ],
                  "@type": "PresentationQueryMessage",
                  "scope": [
                    "org.eclipse.edc.vc.type:TestCredential:read org.eclipse.edc.vc.type:AnotherCredential:all"
                  ]
                }
                """;
        var json = mapper.readValue(obj, JsonObject.class);
        var jo = jsonLd.expand(json);
        assertThat(jo.succeeded()).withFailMessage(jo::getFailureDetail).isTrue();

        var query = transformer.transform(jo.getContent(), context);
        assertThat(query).isNotNull();
        assertThat(query.getScopes()).hasSize(2)
                .containsExactlyInAnyOrder(
                        "org.eclipse.edc.vc.type:TestCredential:read",
                        "org.eclipse.edc.vc.type:AnotherCredential:all");
        assertThat(query.getPresentationDefinition()).isNull();
    }

    @Test
    void transform_withPresentationDefinition() throws JsonProcessingException {
        var json = """
                {
                  "@context": [
                    "https://identity.foundation/presentation-exchange/submission/v1",
                    "https://w3id.org/tractusx-trust/v0.8"
                  ],
                  "@type": "PresentationQueryMessage",
                   "presentationDefinition": {
                       "id": "first simple example",
                       "input_descriptors": [
                         {
                           "id": "descriptor-id-1",
                           "name": "A specific type of VC",
                           "purpose": "We want a VC of this type",
                           "constraints": {
                             "fields": [
                               {
                                 "path": [
                                   "$.type"
                                 ],
                                 "filter": {
                                   "type": "string",
                                   "pattern": "<the type of VC e.g. degree certificate>"
                                 }
                               }
                             ]
                           }
                         }
                       ]
                     }
                }
                """;
        var jobj = mapper.readValue(json, JsonObject.class);

        var expansion = jsonLd.expand(jobj);
        assertThat(expansion.succeeded()).withFailMessage(expansion::getFailureDetail).isTrue();

        var query = transformer.transform(expansion.getContent(), context);
        assertThat(query).isNotNull();
        assertThat(query.getScopes()).isNotNull().isEmpty();
        assertThat(query.getPresentationDefinition()).isNotNull();
        assertThat(query.getPresentationDefinition().getInputDescriptors()).isNotEmpty()
                .allSatisfy(id -> assertThat(id.getId()).isEqualTo("descriptor-id-1"));

    }

    @Test
    void transform_withScopesAndPresDef() throws JsonProcessingException {
        var json = """
                {
                  "@context": [
                    "https://identity.foundation/presentation-exchange/submission/v1",
                    "https://w3id.org/tractusx-trust/v0.8"
                  ],
                  "@type": "PresentationQueryMessage",
                  "scope": ["test-scope1"],
                   "presentationDefinition": {
                       "id": "first simple example",
                       "input_descriptors": [
                         {
                           "id": "descriptor-id-1",
                           "name": "A specific type of VC",
                           "purpose": "We want a VC of this type",
                           "constraints": {
                             "fields": [
                               {
                                 "path": [
                                   "$.type"
                                 ],
                                 "filter": {
                                   "type": "string",
                                   "pattern": "<the type of VC e.g. degree certificate>"
                                 }
                               }
                             ]
                           }
                         }
                       ]
                     }
                }
                """;
        var jobj = mapper.readValue(json, JsonObject.class);

        var expansion = jsonLd.expand(jobj);
        assertThat(expansion.succeeded()).withFailMessage(expansion::getFailureDetail).isTrue();

        var query = transformer.transform(expansion.getContent(), context);
        assertThat(query).isNotNull();
        assertThat(query.getScopes()).isNotNull().containsExactly("test-scope1");
        assertThat(query.getPresentationDefinition()).isNotNull();
        assertThat(query.getPresentationDefinition().getInputDescriptors()).isNotEmpty()
                .allSatisfy(id -> assertThat(id.getId()).isEqualTo("descriptor-id-1"));
    }
}
