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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_PROPERTY_DATA_ADDRESS;
import static org.eclipse.edc.protocol.dsp.spi.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_START_MESSAGE;

public class JsonObjectToTransferStartMessageTransformer extends AbstractJsonLdTransformer<JsonObject, TransferStartMessage> {

    public JsonObjectToTransferStartMessageTransformer() {
        super(JsonObject.class, TransferStartMessage.class);
    }

    @Override
    public @Nullable TransferStartMessage transform(@NotNull JsonObject messageObject, @NotNull TransformerContext context) {
        var builder = TransferStartMessage.Builder.newInstance();

        if (!transformMandatoryString(messageObject.get(DSPACE_PROPERTY_CONSUMER_PID), builder::consumerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_TRANSFER_START_MESSAGE)
                    .property(DSPACE_PROPERTY_CONSUMER_PID)
                    .report();
            return null;
        }

        if (!transformMandatoryString(messageObject.get(DSPACE_PROPERTY_PROVIDER_PID), builder::providerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_TRANSFER_START_MESSAGE)
                    .property(DSPACE_PROPERTY_PROVIDER_PID)
                    .report();
            return null;
        }

        var dataAddressObject = returnJsonObject(messageObject.get(DSPACE_PROPERTY_DATA_ADDRESS), context, DSPACE_PROPERTY_DATA_ADDRESS, false);
        if (dataAddressObject != null) {
            builder.dataAddress(context.transform(dataAddressObject, DataAddress.class));
        }

        return builder.build();
    }

}
