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
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_OFFER;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_REQUEST_MESSAGE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CALLBACK_ADDRESS;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;

/**
 * Creates a {@link ContractRequestMessage} from a {@link JsonObject}.
 */
public class JsonObjectToContractRequestMessageTransformer extends AbstractJsonLdTransformer<JsonObject, ContractRequestMessage> {

    public JsonObjectToContractRequestMessageTransformer() {
        super(JsonObject.class, ContractRequestMessage.class);
    }

    @Override
    public @Nullable ContractRequestMessage transform(@NotNull JsonObject requestObject, @NotNull TransformerContext context) {
        var builder = ContractRequestMessage.Builder.newInstance();

        if (!transformMandatoryString(requestObject.get(DSPACE_PROPERTY_CONSUMER_PID), builder::consumerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_REQUEST_MESSAGE)
                    .property(DSPACE_PROPERTY_CONSUMER_PID)
                    .report();
            return null;
        }

        var providerPid = requestObject.get(DSPACE_PROPERTY_PROVIDER_PID);
        if (providerPid != null) {
            builder.providerPid(transformString(providerPid, context));
        }

        var callback = requestObject.get(DSPACE_PROPERTY_CALLBACK_ADDRESS);
        if (callback != null) {
            builder.callbackAddress(transformString(callback, context));
        }

        var contractOffer = returnJsonObject(requestObject.get(DSPACE_PROPERTY_OFFER), context, DSPACE_PROPERTY_OFFER, false);
        if (contractOffer != null) {
            var policy = transformObject(contractOffer, Policy.class, context);
            if (policy == null) {
                context.problem()
                        .missingProperty()
                        .type(DSPACE_TYPE_CONTRACT_REQUEST_MESSAGE)
                        .property(DSPACE_PROPERTY_OFFER)
                        .report();
                return null;
            }
            var id = nodeId(contractOffer);
            if (id == null) {
                context.problem()
                        .missingProperty()
                        .type(DSPACE_PROPERTY_OFFER)
                        .property(ID)
                        .report();
                return null;
            }
            var offer = ContractOffer.Builder.newInstance().id(id).assetId(policy.getTarget()).policy(policy).build();
            builder.contractOffer(offer);
        } else if (context.hasProblems()) {
            return null;
        } else {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_REQUEST_MESSAGE)
                    .property(DSPACE_PROPERTY_OFFER)
                    .report();
            return null;
        }
        return builder.build();
    }

}


