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

package org.eclipse.edc.connector.controlplane.api.management.contractdefinition.v2;

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
import org.eclipse.edc.connector.controlplane.api.management.contractdefinition.BaseContractDefinitionApiController;
import org.eclipse.edc.connector.controlplane.services.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.api.ApiWarnings.deprecationWarning;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/v2/contractdefinitions")
public class ContractDefinitionApiV2Controller extends BaseContractDefinitionApiController implements ContractDefinitionApiV2 {
    public ContractDefinitionApiV2Controller(TypeTransformerRegistry transformerRegistry, ContractDefinitionService service, Monitor monitor, JsonObjectValidatorRegistry validatorRegistry) {
        super(transformerRegistry, service, monitor, validatorRegistry);
    }

    @POST
    @Path("/request")
    @Override
    public JsonArray queryContractDefinitionsV2(JsonObject querySpecJson) {
        monitor.warning(deprecationWarning("/v2", "/v3"));
        return queryContractDefinitions(querySpecJson);
    }

    @GET
    @Path("{id}")
    @Override
    public JsonObject getContractDefinitionV2(@PathParam("id") String id) {
        monitor.warning(deprecationWarning("/v2", "/v3"));
        return getContractDefinition(id);
    }

    @POST
    @Override
    public JsonObject createContractDefinitionV2(JsonObject createObject) {
        monitor.warning(deprecationWarning("/v2", "/v3"));
        return createContractDefinition(createObject);
    }

    @DELETE
    @Path("{id}")
    @Override
    public void deleteContractDefinitionV2(@PathParam("id") String id) {
        monitor.warning(deprecationWarning("/v2", "/v3"));
        deleteContractDefinition(id);
    }

    @PUT
    @Override
    public void updateContractDefinitionV2(JsonObject updateObject) {
        monitor.warning(deprecationWarning("/v2", "/v3"));
        updateContractDefinition(updateObject);
    }
}
