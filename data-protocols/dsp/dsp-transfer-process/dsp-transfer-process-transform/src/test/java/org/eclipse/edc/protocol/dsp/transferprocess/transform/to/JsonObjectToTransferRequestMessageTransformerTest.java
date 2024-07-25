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

package org.eclipse.edc.protocol.dsp.transferprocess.transform.to;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.protocol.dsp.transferprocess.transform.type.to.JsonObjectToTransferRequestMessageTransformer;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.ProblemBuilder;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DEPRECATED_DCT_FORMAT_ATTRIBUTE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CALLBACK_ADDRESS;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_PROPERTY_CONTRACT_AGREEMENT_ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_PROPERTY_DATA_ADDRESS;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE;
import static org.eclipse.edc.protocol.dsp.transferprocess.transform.to.TestInput.getExpanded;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonObjectToTransferRequestMessageTransformerTest {

    private final String callbackAddress = "https://callback.de";
    private final String contractId = "TestContreactID";
    private final String destinationType = "dspace:s3+push";

    private final TransformerContext context = mock();

    private final JsonObjectToTransferRequestMessageTransformer transformer =
            new JsonObjectToTransferRequestMessageTransformer();

    @Test
    void jsonObjectToTransferRequestWithoutDataAddress() {
        var json = Json.createObjectBuilder()
                .add(TYPE, DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE)
                .add(DSPACE_PROPERTY_CONTRACT_AGREEMENT_ID, contractId)
                .add(DCT_FORMAT_ATTRIBUTE, destinationType)
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, callbackAddress)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "processId")
                .build();

        var result = transformer.transform(getExpanded(json), context);

        assertThat(result).isNotNull();
        assertThat(result.getContractId()).isEqualTo(contractId);
        assertThat(result.getTransferType()).isEqualTo(destinationType);
        assertThat(result.getDataDestination()).isNull();
        assertThat(result.getCallbackAddress()).isEqualTo(callbackAddress);
        assertThat(result.getConsumerPid()).isEqualTo("processId");

        verify(context, never()).reportProblem(anyString());
    }

    @Test
    void jsonObjectToTransferRequestWithDataAddress() {
        var dataDestination = DataAddress.Builder.newInstance().type("any").build();
        when(context.transform(any(), eq(DataAddress.class))).thenReturn(dataDestination);
        var json = Json.createObjectBuilder()
                .add(TYPE, DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE)
                .add(DSPACE_PROPERTY_CONTRACT_AGREEMENT_ID, contractId)
                .add(DCT_FORMAT_ATTRIBUTE, destinationType)
                .add(DSPACE_PROPERTY_DATA_ADDRESS, createDataAddress())
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, callbackAddress)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "processId")
                .build();

        var result = transformer.transform(getExpanded(json), context);

        assertThat(result).isNotNull();
        assertThat(result.getContractId()).isEqualTo(contractId);
        assertThat(result.getTransferType()).isEqualTo(destinationType);
        assertThat(result.getCallbackAddress()).isEqualTo(callbackAddress);
        assertThat(result.getDataDestination()).isSameAs(dataDestination);
        verify(context).transform(any(), eq(DataAddress.class));
        verify(context, never()).reportProblem(anyString());
    }

    @Deprecated(since = "0.5.1")
    @Test
    void jsonObjectToTransferRequestWithDataAddress_withDeprecatedDctNamespace() {
        var dataDestination = DataAddress.Builder.newInstance().type("any").build();
        when(context.transform(any(), eq(DataAddress.class))).thenReturn(dataDestination);
        var json = Json.createObjectBuilder()
                .add(TYPE, DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE)
                .add(DSPACE_PROPERTY_CONTRACT_AGREEMENT_ID, contractId)
                .add(DEPRECATED_DCT_FORMAT_ATTRIBUTE, destinationType)
                .add(DSPACE_PROPERTY_DATA_ADDRESS, createDataAddress())
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, callbackAddress)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "processId")
                .build();

        var result = transformer.transform(getExpanded(json), context);

        assertThat(result).isNotNull();
        assertThat(result.getTransferType()).isEqualTo(destinationType);
        verify(context, never()).reportProblem(anyString());
    }

    @Test
    void shouldReturnNullAndReportError_whenConsumerPidNotSet() {
        when(context.problem()).thenReturn(new ProblemBuilder(context));
        var json = Json.createObjectBuilder()
                .add(TYPE, DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE)
                .add(DSPACE_PROPERTY_CONTRACT_AGREEMENT_ID, contractId)
                .add(DCT_FORMAT_ATTRIBUTE, destinationType)
                .add(DSPACE_PROPERTY_DATA_ADDRESS, Json.createObjectBuilder().build())
                .add(DSPACE_PROPERTY_CALLBACK_ADDRESS, callbackAddress)
                .build();

        var result = transformer.transform(getExpanded(json), context);

        assertThat(result).isNull();
        verify(context).reportProblem(anyString());
    }

    private JsonObject createDataAddress() {
        return Json.createObjectBuilder()
                .add(EDC_NAMESPACE + "accessKeyId", "TESTID")
                .add(EDC_NAMESPACE + "region", "eu-central-1")
                .build();
    }
}
