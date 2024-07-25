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
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractOfferMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_OFFER;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_OFFER_MESSAGE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CALLBACK_ADDRESS;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;

/**
 * Creates a {@link ContractOfferMessage} from a {@link JsonObject}.
 */
public class JsonObjectToContractOfferMessageTransformer extends AbstractJsonLdTransformer<JsonObject, ContractOfferMessage> {

    public JsonObjectToContractOfferMessageTransformer() {
        super(JsonObject.class, ContractOfferMessage.class);
    }

    @Override
    public @Nullable ContractOfferMessage transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var builder = ContractOfferMessage.Builder.newInstance();
        if (!transformMandatoryString(jsonObject.get(DSPACE_PROPERTY_PROVIDER_PID), builder::providerPid, context)) {
            reportMissingProperty(DSPACE_TYPE_CONTRACT_OFFER_MESSAGE, DSPACE_PROPERTY_PROVIDER_PID, context);
            return null;
        }

        var consumerPid = jsonObject.get(DSPACE_PROPERTY_CONSUMER_PID);
        if (consumerPid != null) {
            builder.consumerPid(transformString(consumerPid, context));
        }

        var callback = jsonObject.get(DSPACE_PROPERTY_CALLBACK_ADDRESS);
        if (callback != null) {
            builder.callbackAddress(transformString(callback, context));
        }

        var contractOffer = returnJsonObject(jsonObject.get(DSPACE_PROPERTY_OFFER), context, DSPACE_PROPERTY_OFFER, false);
        if (contractOffer != null) {
            context.setData(Policy.class, TYPE, PolicyType.OFFER);
            var policy = transformObject(contractOffer, Policy.class, context);
            if (policy == null) {
                reportMissingProperty(DSPACE_TYPE_CONTRACT_OFFER_MESSAGE, DSPACE_PROPERTY_OFFER, context);
                return null;
            }
            var id = nodeId(contractOffer);
            if (id == null) {
                reportMissingProperty(DSPACE_PROPERTY_OFFER, ID, context);
                return null;
            }
            var offer = ContractOffer.Builder.newInstance()
                    .id(id)
                    .assetId(policy.getTarget())
                    .policy(policy)
                    .build();
            builder.contractOffer(offer);
        } else {
            reportMissingProperty(DSPACE_TYPE_CONTRACT_OFFER_MESSAGE, DSPACE_PROPERTY_OFFER, context);
            return null;
        }

        return builder.build();
    }

    private void reportMissingProperty(String type, String property, TransformerContext context) {
        context.problem()
                .missingProperty()
                .type(type)
                .property(property)
                .report();
    }
}
