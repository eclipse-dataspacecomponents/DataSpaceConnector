/*
 *  Copyright (c) 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Fraunhofer Institute for Software and Systems Engineering - improvements, add policy engine
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.edc.connector.contract.validation;

import org.eclipse.edc.connector.contract.policy.PolicyEquality;
import org.eclipse.edc.connector.contract.spi.ContractId;
import org.eclipse.edc.connector.contract.spi.offer.ContractDefinitionResolver;
import org.eclipse.edc.connector.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.contract.spi.validation.ValidatedConsumerOffer;
import org.eclipse.edc.connector.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.spi.agent.ParticipantAgentService;
import org.eclipse.edc.spi.asset.AssetIndex;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.result.Result;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

import static java.lang.String.format;
import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

/**
 * Implementation of the {@link ContractValidationService}.
 */
public class ContractValidationServiceImpl implements ContractValidationService {

    private final String participantId;
    private final ParticipantAgentService agentService;
    private final ContractDefinitionResolver contractDefinitionResolver;
    private final AssetIndex assetIndex;
    private final PolicyDefinitionStore policyStore;
    private final PolicyEngine policyEngine;
    private final PolicyEquality policyEquality;
    private final Clock clock;

    public ContractValidationServiceImpl(String participantId,
                                         ParticipantAgentService agentService,
                                         ContractDefinitionResolver contractDefinitionResolver,
                                         AssetIndex assetIndex,
                                         PolicyDefinitionStore policyStore,
                                         PolicyEngine policyEngine,
                                         PolicyEquality policyEquality,
                                         Clock clock) {
        this.participantId = participantId;
        this.agentService = agentService;
        this.contractDefinitionResolver = contractDefinitionResolver;
        this.assetIndex = assetIndex;
        this.policyStore = policyStore;
        this.policyEngine = policyEngine;
        this.policyEquality = policyEquality;
        this.clock = clock;
    }

    @Override
    @NotNull
    public Result<ValidatedConsumerOffer> validateInitialOffer(ClaimToken token, ContractOffer offer) {
        if (isMandatoryAttributeMissing(offer)) {
            return failure("Mandatory attributes are missing.");
        }

        var contractId = ContractId.parse(offer.getId());
        if (!contractId.isValid()) {
            return failure("Invalid id: " + offer.getId());
        }

        var agent = agentService.createFor(token);

        var consumerIdentity = agent.getIdentity();
        if (consumerIdentity == null) {
            return failure("Invalid consumer identity");
        }

        var contractDefinition = contractDefinitionResolver.definitionFor(agent, contractId.definitionPart());
        if (contractDefinition == null) {
            return failure(
                    "The ContractDefinition with id %s either does not exist or the access to it is not granted.");
        }

        var targetAsset = assetIndex.findById(offer.getAsset().getId());
        if (targetAsset == null) {
            return failure("Invalid target: " + offer.getAsset().getId());
        }

        // if policy target is null, default to the asset, otherwise validate it
        var policyTarget = offer.getPolicy().getTarget() != null ? offer.getPolicy().getTarget() : targetAsset.getId();
        if (!targetAsset.getId().equals(policyTarget)) {
            return failure(format("Contract offer asset '%s' does not match policy target: %s", offer.getAsset().getId(), policyTarget));
        }

        var contractPolicyDef = policyStore.findById(contractDefinition.getContractPolicyId());
        if (contractPolicyDef == null) {
            return failure(format("Policy %s not found", contractDefinition.getContractPolicyId()));
        }

        var offerValidity = ChronoUnit.SECONDS.between(offer.getContractStart(), offer.getContractEnd());
        if (offerValidity != contractDefinition.getValidity()) {
            return failure(format("Offer validity %ss does not match contract definition validity %ss", offerValidity, contractDefinition.getValidity()));
        }

        var sanitizedPolicy = contractPolicyDef.getPolicy().withTarget(targetAsset.getId());

        if (!policyEquality.test(sanitizedPolicy, offer.getPolicy())) {
            return failure("Policy in the contract offer is not equal to the one in the contract definition");
        }

        var contractPolicyResult = policyEngine.evaluate(NEGOTIATION_SCOPE, sanitizedPolicy, agent);
        if (contractPolicyResult.failed()) {
            return failure(format("Policy %s not fulfilled", contractPolicyDef.getUid()));
        }

        var validatedOffer = ContractOffer.Builder.newInstance()
                .id(offer.getId())
                .asset(targetAsset)
                .providerId(participantId)
                .policy(sanitizedPolicy)
                .contractStart(offer.getContractStart())
                .contractEnd(offer.getContractEnd())
                .build();

        return success(new ValidatedConsumerOffer(consumerIdentity, validatedOffer));
    }

    @Override
    @NotNull
    public Result<ContractAgreement> validateAgreement(ClaimToken token, ContractAgreement agreement) {
        var contractId = ContractId.parse(agreement.getId());
        if (!contractId.isValid()) {
            return failure(format("The contract id %s does not follow the expected scheme", agreement.getId()));
        }

        if (!isStarted(agreement) || isExpired(agreement)) {
            return failure("The agreement has not started yet or it has expired");
        }

        var agent = agentService.createFor(token);
        var consumerIdentity = agent.getIdentity();
        if (consumerIdentity == null || !consumerIdentity.equals(agreement.getConsumerId())) {
            return failure("Invalid provider credentials");
        }

        // Create additional context information for policy engine to make agreement available in context
        var contextInformation = new HashMap<Class<?>, Object>();
        contextInformation.put(ContractAgreement.class, agreement);

        var policyResult = policyEngine.evaluate(TRANSFER_SCOPE, agreement.getPolicy(), agent, contextInformation);
        if (!policyResult.succeeded()) {
            return failure(format("Policy does not fulfill the agreement %s, policy evaluation %s", agreement.getId(), policyResult.getFailureDetail()));
        }
        return success(agreement);
    }

    @Override
    @NotNull
    public Result<Void> validateRequest(ClaimToken token, ContractNegotiation negotiation) {
        var agent = agentService.createFor(token);
        var counterPartyIdentity = agent.getIdentity();
        return counterPartyIdentity != null && counterPartyIdentity.equals(negotiation.getCounterPartyId()) ? success() : failure("Invalid counter-party identity");
    }

    @Override
    @NotNull
    public Result<Void> validateConfirmed(ClaimToken token, ContractAgreement agreement, ContractOffer latestOffer) {
        if (latestOffer == null) {
            return failure("No offer found");
        }

        var contractId = ContractId.parse(agreement.getId());
        if (!contractId.isValid()) {
            return failure(format("ContractId %s does not follow the expected schema.", agreement.getId()));
        }

        var agent = agentService.createFor(token);
        var providerIdentity = agent.getIdentity();
        if (providerIdentity == null || !providerIdentity.equals(agreement.getProviderId())) {
            return failure("Invalid provider credentials");
        }

        if (!policyEquality.test(agreement.getPolicy().withTarget(latestOffer.getAsset().getId()), latestOffer.getPolicy())) {
            return failure("Policy in the contract agreement is not equal to the one in the contract offer");
        }

        return success();
    }

    private boolean isExpired(ContractAgreement contractAgreement) {
        return contractAgreement.getContractEndDate() * 1000L < clock.millis();
    }

    private boolean isStarted(ContractAgreement contractAgreement) {
        return contractAgreement.getContractStartDate() * 1000L <= clock.millis();
    }

    private boolean isMandatoryAttributeMissing(ContractOffer offer) {
        return offer.getProviderId() == null;
    }
}
