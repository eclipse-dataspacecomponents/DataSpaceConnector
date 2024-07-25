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

package org.eclipse.edc.protocol.dsp.negotiation.transform.to;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.transform.spi.ProblemBuilder;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_OFFER;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_OFFER_MESSAGE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CALLBACK_ADDRESS;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonObjectToContractOfferMessageTransformerTest {

    private static final String CALLBACK_ADDRESS = "https://test.com";
    private static final String MESSAGE_ID = "messageId";
    private static final String ASSET_ID = "assetId";
    private static final String CONTRACT_OFFER_ID = "assetId";

    private final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Map.of());
    private final TransformerContext context = mock();

    private final JsonObjectToContractOfferMessageTransformer transformer = new JsonObjectToContractOfferMessageTransformer();

    @BeforeEach
    void setUp() {
        when(context.problem()).thenReturn(new ProblemBuilder(context));
    }

    @Test
    void transform_shouldReturnMessage_whenValidJsonObject() {
        var message = jsonFactory.createObjectBuilder()
                .add(ID, MESSAGE_ID)
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_OFFER_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, CALLBACK_ADDRESS)
                .add(DSPACE_PROPERTY_OFFER, jsonFactory.createObjectBuilder()
                        .add(ID, CONTRACT_OFFER_ID)
                        .build())
                .build();
        var policy = policy();

        when(context.transform(any(JsonObject.class), eq(Policy.class))).thenReturn(policy);

        var result = transformer.transform(message, context);

        assertThat(result).isNotNull();
        assertThat(result.getProtocol()).isNotEmpty();
        assertThat(result.getConsumerPid()).isEqualTo("consumerPid");
        assertThat(result.getProviderPid()).isEqualTo("providerPid");
        assertThat(result.getCallbackAddress()).isEqualTo(CALLBACK_ADDRESS);

        var contractOffer = result.getContractOffer();
        assertThat(contractOffer).isNotNull();
        assertThat(contractOffer.getId()).isEqualTo(CONTRACT_OFFER_ID);
        assertThat(contractOffer.getPolicy()).isEqualTo(policy);
        assertThat(contractOffer.getAssetId()).isEqualTo(ASSET_ID);

        verify(context, never()).reportProblem(anyString());
        verify(context).setData(Policy.class, TYPE, PolicyType.OFFER);
    }

    @Test
    void transform_shouldReportProblem_whenMissingProcessId() {
        var message = jsonFactory.createObjectBuilder()
                .add(ID, MESSAGE_ID)
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_OFFER_MESSAGE)
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, CALLBACK_ADDRESS)
                .add(DSPACE_PROPERTY_OFFER, jsonFactory.createObjectBuilder()
                        .add(ID, CONTRACT_OFFER_ID)
                        .build())
                .build();

        var result = transformer.transform(message, context);

        assertThat(result).isNull();
        verify(context, times(1)).reportProblem(any());
    }

    @Test
    void transform_shouldReportProblem_whenMissingContractOffer() {
        var message = jsonFactory.createObjectBuilder()
                .add(ID, MESSAGE_ID)
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_OFFER_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, CALLBACK_ADDRESS)
                .build();

        var result = transformer.transform(message, context);

        assertThat(result).isNull();
        verify(context, times(1)).reportProblem(any());
    }

    @Test
    void transform_shouldReportProblem_whenMissingContractOfferId() {
        var message = jsonFactory.createObjectBuilder()
                .add(ID, MESSAGE_ID)
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_OFFER_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, CALLBACK_ADDRESS)
                .add(DSPACE_PROPERTY_OFFER, jsonFactory.createObjectBuilder().build())
                .build();
        var policy = policy();

        when(context.transform(any(JsonObject.class), eq(Policy.class))).thenReturn(policy);

        var result = transformer.transform(message, context);

        assertThat(result).isNull();
        verify(context, times(1)).reportProblem(any());
    }

    @Test
    void transform_shouldReportProblem_whenPolicyTransformationFails() {
        var message = jsonFactory.createObjectBuilder()
                .add(ID, MESSAGE_ID)
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_OFFER_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, CALLBACK_ADDRESS)
                .add(DSPACE_PROPERTY_OFFER, jsonFactory.createObjectBuilder()
                        .add(ID, CONTRACT_OFFER_ID)
                        .build())
                .build();

        when(context.transform(any(JsonObject.class), eq(Policy.class))).thenReturn(null);

        var result = transformer.transform(message, context);

        assertThat(result).isNull();
        verify(context, times(1)).reportProblem(any());
    }

    private Policy policy() {
        return Policy.Builder.newInstance().target(ASSET_ID).build();
    }

}
