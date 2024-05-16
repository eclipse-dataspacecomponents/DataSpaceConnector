/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.dataplane.selector.control.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.POST;
import org.eclipse.edc.api.model.ApiCoreSchema;

import java.net.URL;
import java.util.Set;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.eclipse.edc.connector.dataplane.selector.control.api.model.SelectionRequest.SELECTION_REQUEST_TYPE;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;

@OpenAPIDefinition
@Tag(name = "Dataplane Selector")
public interface DataplaneSelectorControlApi {

    @Operation(method = HttpMethod.POST,
            operationId = "registerDataplane",
            description = "Register new Dataplane",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = DataPlaneInstanceSchema.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dataplane successfully registered",
                            content = @Content(schema = @Schema(implementation = ApiCoreSchema.IdResponseSchema.class))),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "409", description = "Resource already exists",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            })
    @POST
    JsonObject registerDataplane(JsonObject request);

    @Operation(method = HttpMethod.DELETE,
            operationId = "unregisterDataplane",
            description = "Unregister existing Dataplane",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Dataplane successfully unregistered"),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "Resource not found",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            }
    )
    @POST
    void unregisterDataplane(String id);

    @Operation(method = "POST",
            operationId = "selectDataplane",
            description = "Finds the best fitting data plane instance for a particular query",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = SelectionRequestSchema.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The DataPlane instance that fits best for the given selection request",
                            content = @Content(schema = @Schema(implementation = DataPlaneInstanceSchema.class))),
                    @ApiResponse(responseCode = "204", description = "No suitable DataPlane instance was found"),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            })
    @POST
    JsonObject selectDataplane(JsonObject request);

    @Operation(method = "GET",
            operationId = "getAllDataPlaneInstances",
            description = "Returns a list of all currently registered data plane instances",
            responses = {
                    @ApiResponse(responseCode = "200", description = "A (potentially empty) list of currently registered data plane instances",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DataPlaneInstanceSchema.class))))
            }
    )
    @GET
    JsonArray getAllDataPlaneInstances();

    @Schema(example = DataPlaneInstanceSchema.DATAPLANE_INSTANCE_EXAMPLE)
    record DataPlaneInstanceSchema(
            @Schema(name = CONTEXT, requiredMode = REQUIRED)
            Object context,
            @Schema(name = TYPE, example = DATAPLANE_INSTANCE_TYPE)
            String type,
            @Schema(name = ID, requiredMode = REQUIRED)
            String id,
            @Schema(requiredMode = REQUIRED)
            URL url,
            @Schema(requiredMode = REQUIRED)
            Set<String> allowedSourceTypes,
            @Schema(requiredMode = REQUIRED)
            Set<String> allowedDestTypes) {
        public static final String DATAPLANE_INSTANCE_EXAMPLE = """
                {
                    "@context": {
                        "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
                    },
                    "@id": "your-dataplane-id",
                    "url": "http://somewhere.com:1234/api/v1",
                    "allowedSourceTypes": [
                        "source-type1",
                        "source-type2"
                    ],
                    "allowedDestTypes": ["your-dest-type"],
                    "allowedTransferTypes": ["transfer-type"]
                }
                """;
    }

    @Schema(example = SelectionRequestSchema.SELECTION_REQUEST_INPUT_EXAMPLE)
    record SelectionRequestSchema(
            @Schema(name = TYPE, example = SELECTION_REQUEST_TYPE)
            String type,
            String strategy,
            @Schema(requiredMode = REQUIRED)
            String transferType,
            @Schema(requiredMode = REQUIRED)
            ApiCoreSchema.DataAddressSchema source,
            ApiCoreSchema.DataAddressSchema destination
    ) {
        public static final String SELECTION_REQUEST_INPUT_EXAMPLE = """
                {
                    "@context": {
                        "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
                    },
                    "source": {
                        "@type": "https://w3id.org/edc/v0.0.1/ns/DataAddress",
                        "type": "test-src1"
                    },
                    "destination": {
                        "@type": "https://w3id.org/edc/v0.0.1/ns/DataAddress",
                        "type": "test-dst2"
                    },
                    "strategy": "you_custom_strategy",
                    "transferType": "you_custom_transfer_type"
                }
                """;
    }
}
