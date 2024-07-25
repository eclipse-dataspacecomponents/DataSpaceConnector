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

package org.eclipse.edc.connector.controlplane.api.management.policy.v31alpha;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import org.eclipse.edc.connector.controlplane.api.management.policy.BasePolicyDefinitionApiController;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/v3.1alpha/policydefinitions")
public class PolicyDefinitionApiV31AlphaController extends BasePolicyDefinitionApiController implements PolicyDefinitionApiV31Alpha {
    public PolicyDefinitionApiV31AlphaController(Monitor monitor, TypeTransformerRegistry transformerRegistry, PolicyDefinitionService service, JsonObjectValidatorRegistry validatorRegistry) {
        super(monitor, transformerRegistry, service, validatorRegistry);
    }

    @POST
    @Path("request")
    @Override
    public JsonArray queryPolicyDefinitionsV3(JsonObject querySpecJson) {
        return queryPolicyDefinitions(querySpecJson);
    }

    @GET
    @Path("{id}")
    @Override
    public JsonObject getPolicyDefinitionV3(@PathParam("id") String id) {
        return getPolicyDefinition(id);
    }

    @POST
    @Override
    public JsonObject createPolicyDefinitionV3(JsonObject request) {
        return createPolicyDefinition(request);
    }

    @DELETE
    @Path("{id}")
    @Override
    public void deletePolicyDefinitionV3(@PathParam("id") String id) {
        deletePolicyDefinition(id);
    }

    @PUT
    @Path("{id}")
    @Override
    public void updatePolicyDefinitionV3(@PathParam("id") String id, JsonObject input) {
        updatePolicyDefinition(id, input);
    }
}
