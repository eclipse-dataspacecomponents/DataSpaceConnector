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

import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage.Type.ACCEPTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage.Type.FINALIZED;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_EVENT_TYPE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_ACCEPTED;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_FINALIZED;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;

/**
 * Creates a {@link ContractNegotiationEventMessage} from a {@link JsonObject}.
 */
public class JsonObjectToContractNegotiationEventMessageTransformer extends AbstractJsonLdTransformer<JsonObject, ContractNegotiationEventMessage> {

    public JsonObjectToContractNegotiationEventMessageTransformer() {
        super(JsonObject.class, ContractNegotiationEventMessage.class);
    }

    @Override
    public @Nullable ContractNegotiationEventMessage transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = ContractNegotiationEventMessage.Builder.newInstance();

        if (!transformMandatoryString(object.get(DSPACE_PROPERTY_CONSUMER_PID), builder::consumerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE)
                    .property(DSPACE_PROPERTY_CONSUMER_PID)
                    .report();
            return null;
        }
        if (!transformMandatoryString(object.get(DSPACE_PROPERTY_PROVIDER_PID), builder::providerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE)
                    .property(DSPACE_PROPERTY_PROVIDER_PID)
                    .report();
            return null;
        }

        var eventType = transformString(object.get(DSPACE_PROPERTY_EVENT_TYPE), context);
        if (DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_ACCEPTED.equals(eventType)) {
            builder.type(ACCEPTED);
        } else if (DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_FINALIZED.equals(eventType)) {
            builder.type(FINALIZED);
        } else {
            context.problem()
                    .unexpectedType()
                    .type(DSPACE_TYPE_CONTRACT_NEGOTIATION_EVENT_MESSAGE)
                    .property(DSPACE_PROPERTY_EVENT_TYPE)
                    .expected(DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_ACCEPTED)
                    .expected(DSPACE_VALUE_NEGOTIATION_EVENT_TYPE_FINALIZED)
                    .report();
            return null;
        }

        return builder.build();
    }

}
