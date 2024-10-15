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

package org.eclipse.edc.protocol.dsp.transferprocess.transform.type.to;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferTerminationMessage;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jakarta.json.JsonValue.ValueType.ARRAY;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CODE_IRI;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID_IRI;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID_IRI;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_REASON_IRI;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE_IRI;

public class JsonObjectToTransferTerminationMessageTransformer extends AbstractJsonLdTransformer<JsonObject, TransferTerminationMessage> {

    public JsonObjectToTransferTerminationMessageTransformer() {
        super(JsonObject.class, TransferTerminationMessage.class);
    }

    @Override
    public @Nullable TransferTerminationMessage transform(@NotNull JsonObject messageObject, @NotNull TransformerContext context) {
        var builder = TransferTerminationMessage.Builder.newInstance();

        if (!transformMandatoryString(messageObject.get(DSPACE_PROPERTY_CONSUMER_PID_IRI), builder::consumerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE_IRI)
                    .property(DSPACE_PROPERTY_CONSUMER_PID_IRI)
                    .report();
            return null;
        }

        if (!transformMandatoryString(messageObject.get(DSPACE_PROPERTY_PROVIDER_PID_IRI), builder::providerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE_IRI)
                    .property(DSPACE_PROPERTY_PROVIDER_PID_IRI)
                    .report();
            return null;
        }

        if (messageObject.containsKey(DSPACE_PROPERTY_CODE_IRI)) {
            transformString(messageObject.get(DSPACE_PROPERTY_CODE_IRI), builder::code, context);
        }

        var reasons = messageObject.get(DSPACE_PROPERTY_REASON_IRI);
        if (reasons != null) {  // optional property
            if (!(reasons instanceof JsonArray)) {
                context.problem()
                        .unexpectedType()
                        .type(DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE_IRI)
                        .property(DSPACE_PROPERTY_REASON_IRI)
                        .actual(reasons.getValueType())
                        .expected(ARRAY)
                        .report();
            } else {
                var array = (JsonArray) reasons;
                if (array.size() > 0) {
                    builder.reason(array.toString());
                }
            }
        }

        return builder.build();

    }
}
