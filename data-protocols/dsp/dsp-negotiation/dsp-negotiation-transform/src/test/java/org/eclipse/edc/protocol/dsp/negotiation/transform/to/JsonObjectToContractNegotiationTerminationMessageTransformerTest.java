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
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.protocol.dsp.negotiation.transform.to.TestInput.getExpanded;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_NEGOTIATION_TERMINATION_MESSAGE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CODE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_REASON;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JsonObjectToContractNegotiationTerminationMessageTransformerTest {

    public static final String CODE = "code1";
    private final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Map.of());
    private final TransformerContext context = mock();

    private final JsonObjectToContractNegotiationTerminationMessageTransformer transformer =
            new JsonObjectToContractNegotiationTerminationMessageTransformer();

    @Test
    void transform() {
        var reason = Json.createBuilderFactory(Map.of()).createObjectBuilder().add(DSPACE_SCHEMA + "foo", "bar");
        var reasonArray = Json.createBuilderFactory(Map.of()).createArrayBuilder().add(reason).build();

        var message = jsonFactory.createObjectBuilder()
                .add(JsonLdKeywords.ID, "id1")
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_NEGOTIATION_TERMINATION_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .add(DSPACE_PROPERTY_CODE, CODE)
                .add(DSPACE_PROPERTY_REASON, reasonArray)
                .build();

        var result = transformer.transform(getExpanded(message), context);

        assertThat(result).isNotNull();

        assertThat(result.getProtocol()).isNotNull();
        assertThat(result.getCounterPartyAddress()).isNull();
        assertThat(result.getConsumerPid()).isEqualTo("consumerPid");
        assertThat(result.getProviderPid()).isEqualTo("providerPid");
        assertThat(result.getCode()).isEqualTo(CODE);

        assertThat(result.getRejectionReason()).isEqualTo(format("[{\"%sfoo\":[{\"@value\":\"bar\"}]}]", DSPACE_SCHEMA));

        verify(context, never()).reportProblem(anyString());
    }

    @Test
    void verify_noCodeNodReason() {
        var message = jsonFactory.createObjectBuilder()
                .add(JsonLdKeywords.ID, "id1")
                .add(JsonLdKeywords.TYPE, DSPACE_TYPE_CONTRACT_NEGOTIATION_TERMINATION_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .build();

        var result = transformer.transform(getExpanded(message), context);

        assertThat(result).isNotNull();

        assertThat(result.getProtocol()).isNotNull();
        assertThat(result.getCounterPartyAddress()).isNull();
        assertThat(result.getCode()).isNull();
        assertThat(result.getRejectionReason()).isNull();

        verify(context, never()).reportProblem(anyString());
    }
}
