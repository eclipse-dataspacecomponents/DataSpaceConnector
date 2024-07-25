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
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.transform.spi.ProblemBuilder;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.protocol.dsp.negotiation.transform.to.TestInput.getExpanded;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_EVENT_TYPE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_ACCEPTED;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonObjectToContractNegotiationEventMessageTransformerTest {

    private final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Map.of());
    private final TransformerContext context = mock();

    private final JsonObjectToContractNegotiationEventMessageTransformer transformer =
            new JsonObjectToContractNegotiationEventMessageTransformer();

    @BeforeEach
    void setUp() {
        when(context.problem()).thenReturn(new ProblemBuilder(context));
    }

    @Test
    void transform() {
        var message = jsonFactory.createObjectBuilder()
                .add(JsonLdKeywords.ID, "id1")
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .add(DSPACE_PROPERTY_EVENT_TYPE, DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_ACCEPTED)
                .build();

        var result = transformer.transform(getExpanded(message), context);

        assertThat(result).isNotNull();
        assertThat(result.getClass()).isEqualTo(ContractNegotiationEventMessage.class);
        assertThat(result.getProtocol()).isNotEmpty();
        assertThat(result.getConsumerPid()).isEqualTo("consumerPid");
        assertThat(result.getProviderPid()).isEqualTo("providerPid");
        assertThat(result.getType()).isEqualTo(ContractNegotiationEventMessage.Type.ACCEPTED);

        verify(context, never()).reportProblem(anyString());
    }

    @Test
    void verify_failTransformWhenProcessIdMissing() {
        var message = jsonFactory.createObjectBuilder()
                .add(JsonLdKeywords.ID, "id1")
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE)
                .add(DSPACE_PROPERTY_EVENT_TYPE, DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_ACCEPTED)
                .build();

        var result = transformer.transform(getExpanded(message), context);

        assertThat(result).isNull();

        verify(context, times(1)).reportProblem(contains(DSPACE_PROPERTY_CONSUMER_PID));
    }

    @Test
    void verify_failTransformWhenEventTypeMissing() {
        var message = jsonFactory.createObjectBuilder()
                .add(JsonLdKeywords.ID, "id1")
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .build();

        var result = transformer.transform(getExpanded(message), context);

        assertThat(result).isNull();

        verify(context, times(1)).reportProblem(contains(DSPACE_PROPERTY_EVENT_TYPE));
    }

    @Test
    void verify_failTransformWhenEventTypeInvalid() {
        var message = jsonFactory.createObjectBuilder()
                .add(JsonLdKeywords.ID, "id1")
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .add(DSPACE_PROPERTY_EVENT_TYPE, "InvalidType")
                .build();

        var result = transformer.transform(getExpanded(message), context);

        assertThat(result).isNull();

        verify(context, times(1)).reportProblem(contains(DSPACE_PROPERTY_EVENT_TYPE));
    }
}
