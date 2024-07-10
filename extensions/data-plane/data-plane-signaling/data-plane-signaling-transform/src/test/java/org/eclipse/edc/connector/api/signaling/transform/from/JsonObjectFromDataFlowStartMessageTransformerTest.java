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

package org.eclipse.edc.connector.api.signaling.transform.from;

import jakarta.json.Json;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.util.JacksonJsonLd.createObjectMapper;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.DC_DATA_FLOW_START_MESSAGE_PROCESS_ID;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_AGREEMENT_ID;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_DATASET_ID;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_DESTINATION_CALLBACK_ADDRESS;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_DESTINATION_DATA_ADDRESS;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_FLOW_TYPE;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_PARTICIPANT_ID;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_SOURCE_DATA_ADDRESS;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_TRANSFER_TYPE_DESTINATION;
import static org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage.EDC_DATA_FLOW_START_MESSAGE_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonObjectFromDataFlowStartMessageTransformerTest {

    private final TransformerContext context = mock();
    private JsonObjectFromDataFlowStartMessageTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new JsonObjectFromDataFlowStartMessageTransformer(Json.createBuilderFactory(Map.of()), createObjectMapper());
        when(context.transform(isA(DataAddress.class), any())).thenReturn(Json.createObjectBuilder().build());
    }

    @Test
    void transform() {
        var message = DataFlowStartMessage.Builder.newInstance()
                .processId("processId")
                .assetId("assetId")
                .agreementId("agreementId")
                .participantId("participantId")
                .transferType(new TransferType("HttpData", FlowType.PUSH))
                .callbackAddress(URI.create("http://localhost"))
                .sourceDataAddress(DataAddress.Builder.newInstance().type("sourceType").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("destType").build())
                .build();

        var jsonObject = transformer.transform(message, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getJsonString(TYPE).getString()).isEqualTo(EDC_DATA_FLOW_START_MESSAGE_TYPE);
        assertThat(jsonObject.getJsonString(DC_DATA_FLOW_START_MESSAGE_PROCESS_ID).getString()).isEqualTo(message.getProcessId());
        assertThat(jsonObject.getJsonString(EDC_DATA_FLOW_START_MESSAGE_DATASET_ID).getString()).isEqualTo(message.getAssetId());
        assertThat(jsonObject.getJsonString(EDC_DATA_FLOW_START_MESSAGE_AGREEMENT_ID).getString()).isEqualTo(message.getAgreementId());
        assertThat(jsonObject.getJsonString(EDC_DATA_FLOW_START_MESSAGE_PARTICIPANT_ID).getString()).isEqualTo(message.getParticipantId());
        assertThat(jsonObject.getJsonString(EDC_DATA_FLOW_START_MESSAGE_DESTINATION_CALLBACK_ADDRESS).getString()).isEqualTo(message.getCallbackAddress().toString());
        assertThat(jsonObject.getJsonString(EDC_DATA_FLOW_START_MESSAGE_TRANSFER_TYPE_DESTINATION).getString()).isEqualTo("HttpData");
        assertThat(jsonObject.getJsonString(EDC_DATA_FLOW_START_MESSAGE_FLOW_TYPE).getString()).isEqualTo(message.getFlowType().toString());
        assertThat(jsonObject.get(EDC_DATA_FLOW_START_MESSAGE_DESTINATION_DATA_ADDRESS)).isNotNull();
        assertThat(jsonObject.get(EDC_DATA_FLOW_START_MESSAGE_SOURCE_DATA_ADDRESS)).isNotNull();
    }

    @Test
    void transform_whenTransferTypeIsNull() {
        var message = DataFlowStartMessage.Builder.newInstance()
                .processId("any")
                .sourceDataAddress(DataAddress.Builder.newInstance().type("any").build())
                .agreementId("agreementId")
                .participantId("participantId")
                .assetId("assetId")
                .callbackAddress(URI.create("http://localhost"))
                .transferType(null)
                .transferTypeDestination("DestinationType")
                .flowType(FlowType.PULL)
                .build();

        var jsonObject = transformer.transform(message, context);

        assertThat(jsonObject).isNotNull();
        assertThat(jsonObject.getString(EDC_DATA_FLOW_START_MESSAGE_TRANSFER_TYPE_DESTINATION)).isEqualTo("DestinationType");
        assertThat(jsonObject.getString(EDC_DATA_FLOW_START_MESSAGE_FLOW_TYPE)).isEqualTo("PULL");
    }

}
