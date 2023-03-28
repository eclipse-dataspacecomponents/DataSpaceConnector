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

package org.eclipse.edc.protocol.dsp.controlplane.controller;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonObject;

@OpenAPIDefinition
@Tag(name = "Dataspace Protocol: Contract Negotiation")
public interface ContractNegotiationApi {
    /**
     * Provider-specific endpoint.
     *
     * @param id of contract negotiation.
     */
    @Operation(description = "Gets contract negotiation by id", operationId = "dspGetNegotiation")
    JsonObject getNegotiation(String id);

    /**
     * Provider-specific endpoint.
     *
     * @param body dspace:ContractRequestMessage sent by a consumer.
     */
    @Operation(description = "Starts contract negotiation", operationId = "dspInitiateNegotiation")
    JsonObject initiateNegotiation(JsonObject body);

    /**
     * Provider-specific endpoint.
     *
     * @param id of contract negotiation.
     * @param body dspace:ContractRequestMessage sent by a consumer.
     */
    @Operation(description = "Adds contract offer to contract negotiation", operationId = "dspConsumerOfferNegotiation")
    void consumerOffer(String id, JsonObject body);

    /**
     * Endpoint on provider and consumer side.
     *
     * @param id of contract negotiation.
     * @param body dspace:ContractNegotiationEventMessage sent by consumer or provider.
     */
    @Operation(description = "Accepts current offer or finalizes an agreement within a contract negotiation", operationId = "dspEventNegotiation")
    void createEvent(String id, JsonObject body);

    /**
     * Provider-specific endpoint.
     *
     * @param id of contract negotiation.
     * @param body dspace:ContractAgreementVerificationMessage sent by a consumer.
     */
    @Operation(description = "Verifies current agreement of contract negotiation", operationId = "dspVerifyAgreementNegotiation")
    void verifyAgreement(String id, JsonObject body);

    /**
     * Endpoint on provider and consumer side.
     *
     * @param id of contract negotiation.
     * @param body dspace:ContractNegotiationTerminationMessage sent by consumer or provider.
     */
    @Operation(description = "Terminates contract negotiation", operationId = "dspTerminateNegotiation")
    void terminateNegotiation(String id, JsonObject body);

    /**
     * Consumer-specific endpoint.
     *
     * @param id of contract negotiation.
     * @param body dspace:ContractOfferMessage sent by a provider.
     */
    @Operation(description = "Adds contract offer to contract negotiation", operationId = "dspProviderOfferNegotiation")
    void providerOffer(String id, JsonObject body);

    /**
     * Consumer-specific endpoint.
     *
     * @param id of contract negotiation.
     * @param body dspace:ContractAgreementMessage sent by a provider.
     */
    @Operation(description = "Creates an agreement within a contract negotiation", operationId = "dspCreateAgreementNegotiation")
    void createAgreement(String id, JsonObject body);
}
