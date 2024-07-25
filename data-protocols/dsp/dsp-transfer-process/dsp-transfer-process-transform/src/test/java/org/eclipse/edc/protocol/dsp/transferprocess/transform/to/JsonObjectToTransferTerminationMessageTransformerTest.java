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
import org.eclipse.edc.protocol.dsp.transferprocess.transform.type.to.JsonObjectToTransferTerminationMessageTransformer;
import org.eclipse.edc.transform.spi.ProblemBuilder;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CODE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_REASON;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonObjectToTransferTerminationMessageTransformerTest {

    private final TransformerContext context = mock();
    private final JsonObjectToTransferTerminationMessageTransformer transformer =
            new JsonObjectToTransferTerminationMessageTransformer();

    @Test
    void shouldTransform() {
        var reason = Json.createBuilderFactory(Map.of()).createObjectBuilder().add(DSPACE_SCHEMA + "foo", "bar");
        var reasonArray = Json.createBuilderFactory(Map.of()).createArrayBuilder().add(reason).build();

        var json = Json.createObjectBuilder()
                .add(TYPE, DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE)
                .add(DSPACE_PROPERTY_CONSUMER_PID, "consumerPid")
                .add(DSPACE_PROPERTY_PROVIDER_PID, "providerPid")
                .add(DSPACE_PROPERTY_CODE, "testCode")
                .add(DSPACE_PROPERTY_REASON, Json.createBuilderFactory(Map.of()).createArrayBuilder().add(reasonArray).build())
                .build();

        var result = transformer.transform(TestInput.getExpanded(json), context);

        assertThat(result).isNotNull();

        assertThat(result.getProviderPid()).isEqualTo("providerPid");
        assertThat(result.getConsumerPid()).isEqualTo("consumerPid");
        assertThat(result.getReason()).isEqualTo(format("[{\"%sfoo\":[{\"@value\":\"bar\"}]}]", DSPACE_SCHEMA));
        assertThat(result.getCode()).isEqualTo("testCode");

        verify(context, never()).reportProblem(anyString());
    }

    @Test
    void shouldReportError_whenMissingPids() {
        when(context.problem()).thenReturn(new ProblemBuilder(context));
        var reason = Json.createBuilderFactory(Map.of()).createObjectBuilder().add(DSPACE_SCHEMA + "foo", "bar");
        var reasonArray = Json.createBuilderFactory(Map.of()).createArrayBuilder().add(reason).build();

        var json = Json.createObjectBuilder()
                .add(TYPE, DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE)
                .add(DSPACE_PROPERTY_CODE, "testCode")
                .add(DSPACE_PROPERTY_REASON, Json.createBuilderFactory(Map.of()).createArrayBuilder().add(reasonArray).build())
                .build();

        var result = transformer.transform(TestInput.getExpanded(json), context);

        assertThat(result).isNull();
        verify(context).reportProblem(anyString());
    }
}
