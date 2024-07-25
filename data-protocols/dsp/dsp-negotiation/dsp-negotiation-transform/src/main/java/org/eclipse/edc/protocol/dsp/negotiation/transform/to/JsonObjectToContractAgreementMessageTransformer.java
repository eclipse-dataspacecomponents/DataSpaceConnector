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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_AGREEMENT;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_ID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_PROPERTY_TIMESTAMP;
import static org.eclipse.edc.protocol.dsp.spi.type.DspNegotiationPropertyAndTypeNames.DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_CONSUMER_PID;
import static org.eclipse.edc.protocol.dsp.spi.type.DspPropertyAndTypeNames.DSPACE_PROPERTY_PROVIDER_PID;

/**
 * Creates a {@link ContractAgreementMessage} from a {@link JsonObject}.
 */
public class JsonObjectToContractAgreementMessageTransformer extends AbstractJsonLdTransformer<JsonObject, ContractAgreementMessage> {
    private static final Set<String> EXCLUDED_POLICY_KEYWORDS =
            Set.of(DSPACE_PROPERTY_CONSUMER_ID, DSPACE_PROPERTY_PROVIDER_ID, DSPACE_PROPERTY_TIMESTAMP);

    public JsonObjectToContractAgreementMessageTransformer() {
        super(JsonObject.class, ContractAgreementMessage.class);
    }

    @Override
    public @Nullable ContractAgreementMessage transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var messageBuilder = ContractAgreementMessage.Builder.newInstance();
        if (!transformMandatoryString(object.get(DSPACE_PROPERTY_CONSUMER_PID), messageBuilder::consumerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE)
                    .property(DSPACE_PROPERTY_CONSUMER_PID)
                    .report();
            return null;
        }
        if (!transformMandatoryString(object.get(DSPACE_PROPERTY_PROVIDER_PID), messageBuilder::providerPid, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE)
                    .property(DSPACE_PROPERTY_PROVIDER_PID)
                    .report();
            return null;
        }

        var jsonAgreement = returnMandatoryJsonObject(object.get(DSPACE_PROPERTY_AGREEMENT), context, DSPACE_PROPERTY_AGREEMENT);
        if (jsonAgreement == null) {
            return null;
        }

        var filteredJsonAgreement = filterAgreementProperties(jsonAgreement);

        var policy = context.transform(filteredJsonAgreement, Policy.class);
        if (policy == null) {
            context.problem()
                    .invalidProperty()
                    .type(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE)
                    .property(DSPACE_PROPERTY_AGREEMENT)
                    .report();
            return null;
        }

        var agreement = contractAgreement(jsonAgreement, policy, context);
        if (agreement == null) {
            // problem already reported
            return null;
        }

        messageBuilder.contractAgreement(agreement);

        return messageBuilder.build();
    }

    private JsonObject filterAgreementProperties(JsonObject jsonAgreement) {
        var copiedJsonAgreement = Json.createObjectBuilder();
        jsonAgreement.entrySet().stream()
                .filter(e -> !EXCLUDED_POLICY_KEYWORDS.contains(e.getKey()))
                .forEach(e -> copiedJsonAgreement.add(e.getKey(), e.getValue()));
        return copiedJsonAgreement.build();
    }

    @Nullable
    private ContractAgreement contractAgreement(JsonObject jsonAgreement, Policy policy, TransformerContext context) {
        var builder = ContractAgreement.Builder.newInstance();
        var agreementId = nodeId(jsonAgreement);
        if (agreementId == null) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE)
                    .property(ID)
                    .report();
            return null;
        }

        var assignee = policy.getAssignee();
        var assigner = policy.getAssigner();

        builder.id(agreementId)
                .consumerId(assignee)
                .providerId(assigner)
                .policy(policy)
                .assetId(policy.getTarget());

        if (assignee == null && !transformMandatoryString(jsonAgreement.get(DSPACE_PROPERTY_CONSUMER_ID), builder::consumerId, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE)
                    .property(DSPACE_PROPERTY_CONSUMER_ID)
                    .report();
            return null;
        }

        if (assigner == null && !transformMandatoryString(jsonAgreement.get(DSPACE_PROPERTY_PROVIDER_ID), builder::providerId, context)) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE)
                    .property(DSPACE_PROPERTY_PROVIDER_ID)
                    .report();
            return null;
        }

        var timestamp = transformString(jsonAgreement.get(DSPACE_PROPERTY_TIMESTAMP), context);
        if (timestamp == null) {
            context.problem()
                    .missingProperty()
                    .type(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE)
                    .property(DSPACE_PROPERTY_TIMESTAMP)
                    .report();
            return null;
        }
        try {
            builder.contractSigningDate(Instant.parse(timestamp).getEpochSecond());
        } catch (DateTimeParseException e) {
            context.problem()
                    .invalidProperty()
                    .type(DSPACE_TYPE_CONTRACT_AGREEMENT_MESSAGE)
                    .property(DSPACE_PROPERTY_TIMESTAMP)
                    .value(timestamp)
                    .error(e.getMessage())
                    .report();
            return null;
        }

        return builder.build();
    }
}
