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

package org.eclipse.edc.protocol.dsp.negotiation.transform.v2024.from;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREEING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.OFFERED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.OFFERING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFIED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFYING;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.protocol.dsp.negotiation.transform.v2024.from.TestFunction2024.toIri;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_NEGOTIATION_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_STATE_ACCEPTED_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_STATE_AGREED_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_STATE_FINALIZED_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_STATE_OFFERED_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_STATE_REQUESTED_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_STATE_TERMINATED_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_STATE_VERIFIED_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_STATE_TERM;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JsonObjectFromContractNegotiationV2024TransformerTest {

    private final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Map.of());
    private final TransformerContext context = mock(TransformerContext.class);

    private JsonObjectFromContractNegotiationV2024Transformer transformer;


    @BeforeEach
    void setUp() {
        transformer = new JsonObjectFromContractNegotiationV2024Transformer(jsonFactory);
    }

    @Test
    void transform_consumer() {
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("consumerPid")
                .correlationId("providerPid")
                .counterPartyId("counterPartyId")
                .counterPartyAddress("counterPartyAddress")
                .protocol("protocol")
                .state(REQUESTED.code())
                .type(ContractNegotiation.Type.CONSUMER)
                .build();

        var result = transformer.transform(negotiation, context);

        assertThat(result).isNotNull();
        assertThat(result.getString(ID)).isEqualTo("consumerPid");
        assertThat(result.getString(TYPE)).isEqualTo(toIri(DSPACE_TYPE_CONTRACT_NEGOTIATION_TERM));
        assertThat(result.getJsonObject(toIri(DSPACE_PROPERTY_CONSUMER_PID_TERM)).getString(ID)).isEqualTo("consumerPid");
        assertThat(result.getJsonObject(toIri(DSPACE_PROPERTY_PROVIDER_PID_TERM)).getString(ID)).isEqualTo("providerPid");

        verify(context, never()).reportProblem(anyString());
    }

    @Test
    void transform_provider() {
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("providerPid")
                .correlationId("consumerPid")
                .counterPartyId("counterPartyId")
                .counterPartyAddress("counterPartyAddress")
                .protocol("protocol")
                .state(REQUESTED.code())
                .type(ContractNegotiation.Type.PROVIDER)
                .build();

        var result = transformer.transform(negotiation, context);

        assertThat(result).isNotNull();
        assertThat(result.getString(ID)).isEqualTo("providerPid");
        assertThat(result.getString(TYPE)).isEqualTo(toIri(DSPACE_TYPE_CONTRACT_NEGOTIATION_TERM));
        assertThat(result.getJsonObject(toIri(DSPACE_PROPERTY_CONSUMER_PID_TERM)).getString(ID)).isEqualTo("consumerPid");
        assertThat(result.getJsonObject(toIri(DSPACE_PROPERTY_PROVIDER_PID_TERM)).getString(ID)).isEqualTo("providerPid");

        verify(context, never()).reportProblem(anyString());
    }

    @ParameterizedTest
    @ArgumentsSource(Status.class)
    void transform_status(ContractNegotiationStates inputState, String expectedDspState) {
        var value = "example";
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id(value)
                .correlationId(value)
                .counterPartyId("counterPartyId")
                .counterPartyAddress("counterPartyAddress")
                .protocol("protocol")
                .state(inputState.code())
                .build();

        var result = transformer.transform(negotiation, context);

        assertThat(result).isNotNull();
        assertThat(result.getJsonObject(toIri(DSPACE_PROPERTY_STATE_TERM)).getString(ID)).isEqualTo(expectedDspState);

        verify(context, never()).reportProblem(anyString());
    }

    public static class Status implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.arguments(REQUESTING, toIri(DSPACE_VALUE_NEGOTIATION_STATE_REQUESTED_TERM)),
                    Arguments.arguments(REQUESTED, toIri(DSPACE_VALUE_NEGOTIATION_STATE_REQUESTED_TERM)),
                    Arguments.arguments(OFFERING, toIri(DSPACE_VALUE_NEGOTIATION_STATE_OFFERED_TERM)),
                    Arguments.arguments(OFFERED, toIri(DSPACE_VALUE_NEGOTIATION_STATE_OFFERED_TERM)),
                    Arguments.arguments(ACCEPTING, toIri(DSPACE_VALUE_NEGOTIATION_STATE_ACCEPTED_TERM)),
                    Arguments.arguments(ACCEPTED, toIri(DSPACE_VALUE_NEGOTIATION_STATE_ACCEPTED_TERM)),
                    Arguments.arguments(AGREEING, toIri(DSPACE_VALUE_NEGOTIATION_STATE_AGREED_TERM)),
                    Arguments.arguments(AGREED, toIri(DSPACE_VALUE_NEGOTIATION_STATE_AGREED_TERM)),
                    Arguments.arguments(VERIFYING, toIri(DSPACE_VALUE_NEGOTIATION_STATE_VERIFIED_TERM)),
                    Arguments.arguments(VERIFIED, toIri(DSPACE_VALUE_NEGOTIATION_STATE_VERIFIED_TERM)),
                    Arguments.arguments(FINALIZING, toIri(DSPACE_VALUE_NEGOTIATION_STATE_FINALIZED_TERM)),
                    Arguments.arguments(FINALIZED, toIri(DSPACE_VALUE_NEGOTIATION_STATE_FINALIZED_TERM)),
                    Arguments.arguments(TERMINATING, toIri(DSPACE_VALUE_NEGOTIATION_STATE_TERMINATED_TERM)),
                    Arguments.arguments(TERMINATED, toIri(DSPACE_VALUE_NEGOTIATION_STATE_TERMINATED_TERM))
            );
        }
    }
}
