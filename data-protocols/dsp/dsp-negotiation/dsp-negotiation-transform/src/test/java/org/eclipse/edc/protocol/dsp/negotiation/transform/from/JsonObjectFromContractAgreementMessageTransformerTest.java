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

package org.eclipse.edc.protocol.dsp.negotiation.transform.from;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.Prohibition;
import org.eclipse.edc.transform.spi.ProblemBuilder;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNEE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNER_ATTRIBUTE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_AGREEMENT;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_TIMESTAMP;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROCESS_ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonObjectFromContractAgreementMessageTransformerTest {

    private static final String PROVIDER_ID = "providerId";
    private static final String CONSUMER_ID = "consumerId";
    private static final String TIMESTAMP = "1970-01-01T00:00:00Z";
    private static final String DSP = "dsp";
    public static final String AGREEMENT_ID = UUID.randomUUID().toString();

    private final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Map.of());
    private final TransformerContext context = mock();

    private final JsonObjectFromContractAgreementMessageTransformer transformer =
            new JsonObjectFromContractAgreementMessageTransformer(jsonFactory);

    @BeforeEach
    void setUp() {
        when(context.problem()).thenReturn(new ProblemBuilder(context));
    }

    @Test
    void transform() {
        var message = ContractAgreementMessage.Builder.newInstance()
                .protocol(DSP)
                .processId("processId")
                .providerPid("providerPid")
                .consumerPid("consumerPid")
                .counterPartyAddress("https://example.com")
                .contractAgreement(ContractAgreement.Builder.newInstance()
                        .id(AGREEMENT_ID)
                        .providerId(PROVIDER_ID)
                        .consumerId(CONSUMER_ID)
                        .assetId("assetId")
                        .policy(policy()).build())
                .build();
        var policyObject = jsonFactory.createObjectBuilder()
                .add(ID, "contractOfferId")
                .build();

        when(context.transform(any(Policy.class), eq(JsonObject.class))).thenReturn(policyObject);

        var result = transformer.transform(message, context);

        assertThat(result).isNotNull();
        assertThat(result.getString(ID)).isNotNull();
        assertThat(result.getString(ID)).isNotEmpty();
        assertThat(result.getString(TYPE)).isEqualTo(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE);
        assertThat(result.getString(DSPACE_PROPERTY_PROVIDER_PID)).isEqualTo("providerPid");
        assertThat(result.getString(DSPACE_PROPERTY_CONSUMER_PID)).isEqualTo("consumerPid");
        assertThat(result.getString(DSPACE_PROPERTY_PROCESS_ID)).isEqualTo("processId");

        var jsonAgreement = result.getJsonObject(DSPACE_PROPERTY_AGREEMENT);
        assertThat(jsonAgreement).isNotNull();
        assertThat(jsonAgreement.getString(ID)).isEqualTo(AGREEMENT_ID);
        assertThat(jsonAgreement.getString(DSPACE_PROPERTY_TIMESTAMP)).isEqualTo(TIMESTAMP);
        assertThat(jsonAgreement.getString(ODRL_ASSIGNEE_ATTRIBUTE)).isEqualTo(CONSUMER_ID);
        assertThat(jsonAgreement.getString(ODRL_ASSIGNER_ATTRIBUTE)).isEqualTo(PROVIDER_ID);

        verify(context, never()).reportProblem(anyString());
    }

    @Deprecated(since = "0.5.1")
    @Test
    void shouldSetConsumerIdAndProviderIdForBackwardCompatibility() {
        var message = ContractAgreementMessage.Builder.newInstance()
                .protocol(DSP)
                .processId("processId")
                .providerPid("providerPid")
                .consumerPid("consumerPid")
                .counterPartyAddress("https://example.com")
                .contractAgreement(ContractAgreement.Builder.newInstance()
                        .id(AGREEMENT_ID)
                        .providerId(PROVIDER_ID)
                        .consumerId(CONSUMER_ID)
                        .assetId("assetId")
                        .policy(policy()).build())
                .build();
        var policyObject = jsonFactory.createObjectBuilder()
                .add(ID, "contractOfferId")
                .build();

        when(context.transform(any(Policy.class), eq(JsonObject.class))).thenReturn(policyObject);

        var result = transformer.transform(message, context);

        assertThat(result).isNotNull();
        var jsonAgreement = result.getJsonObject(DSPACE_PROPERTY_AGREEMENT);
        assertThat(jsonAgreement.getString(DSPACE_PROPERTY_CONSUMER_ID)).isEqualTo(CONSUMER_ID);
        assertThat(jsonAgreement.getString(DSPACE_PROPERTY_PROVIDER_ID)).isEqualTo(PROVIDER_ID);

        verify(context, never()).reportProblem(anyString());
    }

    @Test
    void transform_policyError() {
        var message = ContractAgreementMessage.Builder.newInstance()
                .protocol(DSP)
                .processId("processId")
                .providerPid("providerPid")
                .consumerPid("consumerPid")
                .counterPartyAddress("https://example.com")
                .contractAgreement(ContractAgreement.Builder.newInstance()
                        .id(AGREEMENT_ID)
                        .providerId(PROVIDER_ID)
                        .consumerId(CONSUMER_ID)
                        .assetId("assetId")
                        .policy(policy()).build())
                .build();

        when(context.transform(any(Policy.class), eq(JsonObject.class))).thenReturn(null);

        assertThat(transformer.transform(message, context)).isNull();

        verify(context, times(1)).reportProblem(anyString());
    }

    private Policy policy() {
        var action = Action.Builder.newInstance().type("use").build();
        var permission = Permission.Builder.newInstance().action(action).build();
        var prohibition = Prohibition.Builder.newInstance().action(action).build();
        var duty = Duty.Builder.newInstance().action(action).build();
        return Policy.Builder.newInstance()
                .permission(permission)
                .prohibition(prohibition)
                .duty(duty)
                .build();
    }
}
