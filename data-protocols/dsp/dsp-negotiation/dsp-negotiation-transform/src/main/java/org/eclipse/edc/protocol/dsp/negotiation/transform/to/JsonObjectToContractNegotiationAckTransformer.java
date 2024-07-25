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

package org.eclipse.edc.protocol.dsp.negotiation.transform.to;

import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.contract.spi.types.protocol.ContractNegotiationAck;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_STATE;

/**
 * Create a {@link ContractNegotiationAck} from {@link JsonObject}
 */
public class JsonObjectToContractNegotiationAckTransformer extends AbstractJsonLdTransformer<JsonObject, ContractNegotiationAck> {

    public JsonObjectToContractNegotiationAckTransformer() {
        super(JsonObject.class, ContractNegotiationAck.class);
    }

    @Override
    public @Nullable ContractNegotiationAck transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var builder = ContractNegotiationAck.Builder.newInstance();

        transformMandatoryString(jsonObject.get(DSPACE_PROPERTY_CONSUMER_PID), builder::consumerPid, context);
        transformMandatoryString(jsonObject.get(DSPACE_PROPERTY_PROVIDER_PID), builder::providerPid, context);

        return builder
                .state(transformString(jsonObject.get(DSPACE_PROPERTY_STATE), context))
                .build();
    }
}
