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

import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DEPRECATED_DCT_FORMAT_ATTRIBUTE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CALLBACK_ADDRESS;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_PROPERTY_CONTRACT_AGREEMENT_ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_PROPERTY_DATA_ADDRESS;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE;

public class JsonObjectToTransferRequestMessageTransformer extends AbstractJsonLdTransformer<JsonObject, TransferRequestMessage> {

    public JsonObjectToTransferRequestMessageTransformer() {
        super(JsonObject.class, TransferRequestMessage.class);
    }

    @Override
    public @Nullable TransferRequestMessage transform(@NotNull JsonObject messageObject, @NotNull TransformerContext context) {
        var builder = TransferRequestMessage.Builder.newInstance();

        if (!transformMandatoryString(messageObject.get(DSPACE_PROPERTY_CONSUMER_PID), builder::consumerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE)
                    .property(DSPACE_PROPERTY_CONSUMER_PID)
                    .report();
            return null;
        }

        visitProperties(messageObject, k -> switch (k) {
            case DSPACE_PROPERTY_CONTRACT_AGREEMENT_ID -> v -> builder.contractId(transformString(v, context));
            case DSPACE_PROPERTY_CALLBACK_ADDRESS -> v -> builder.callbackAddress(transformString(v, context));
            case DSPACE_PROPERTY_DATA_ADDRESS ->
                    v -> builder.dataDestination(transformObject(v, DataAddress.class, context));
            case DCT_FORMAT_ATTRIBUTE, DEPRECATED_DCT_FORMAT_ATTRIBUTE ->
                    v -> builder.transferType(transformString(v, context));
            default -> doNothing();
        });

        return builder.build();
    }

}
