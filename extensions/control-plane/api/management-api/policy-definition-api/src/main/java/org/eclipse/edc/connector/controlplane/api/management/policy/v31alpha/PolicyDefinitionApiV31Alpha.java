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

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.management.schema.ManagementApiSchema;
import org.eclipse.edc.api.model.ApiCoreSchema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;

@OpenAPIDefinition(info = @Info(version = "v3.1-alpha"))
@Tag(name = "Policy Definition v3.1-alpha")
public interface PolicyDefinitionApiV31Alpha {

    @Operation(description = "Returns all policy definitions according to a query",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = ApiCoreSchema.QuerySpecSchema.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The policy definitions matching the query",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PolicyDefinitionOutputSchema.class)))),
                    @ApiResponse(responseCode = "400", description = "Request was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))) }
    )
    JsonArray queryPolicyDefinitionsV3(JsonObject querySpecJson);

    @Operation(description = "Gets a policy definition with the given ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The  policy definition",
                            content = @Content(schema = @Schema(implementation = PolicyDefinitionOutputSchema.class))),
                    @ApiResponse(responseCode = "400", description = "Request was malformed, e.g. id was null",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "An  policy definition with the given ID does not exist",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            }
    )
    JsonObject getPolicyDefinitionV3(String id);

    @Operation(description = "Creates a new policy definition",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = PolicyDefinitionInputSchema.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "policy definition was created successfully. Returns the Policy Definition Id and created timestamp",
                            content = @Content(schema = @Schema(implementation = ApiCoreSchema.IdResponseSchema.class))),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "409", description = "Could not create policy definition, because a contract definition with that ID already exists",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))) }
    )
    JsonObject createPolicyDefinitionV3(JsonObject policyDefinition);

    @Operation(description = "Removes a policy definition with the given ID if possible. Deleting a policy definition is " +
            "only possible if that policy definition is not yet referenced by a contract definition, in which case an error is returned. " +
            "DANGER ZONE: Note that deleting policy definitions can have unexpected results, do this at your own risk!",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Policy definition was deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Request was malformed, e.g. id was null",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "An policy definition with the given ID does not exist",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "409", description = "The policy definition cannot be deleted, because it is referenced by a contract definition",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            }
    )
    void deletePolicyDefinitionV3(String id);

    @Operation(description = "Updates an existing Policy, If the Policy is not found, an error is reported",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = PolicyDefinitionInputSchema.class))),
            responses = {
                    @ApiResponse(responseCode = "204", description = "policy definition was updated successfully."),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "policy definition could not be updated, because it does not exists",
                            content = @Content(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))
            }
    )
    void updatePolicyDefinitionV3(String id, JsonObject policyDefinition);

    @Schema(name = "PolicyDefinitionInput", example = PolicyDefinitionInputSchema.POLICY_DEFINITION_INPUT_EXAMPLE)
    record PolicyDefinitionInputSchema(
            @Schema(name = CONTEXT, requiredMode = REQUIRED)
            Object context,
            @Schema(name = ID)
            String id,
            @Schema(name = TYPE, example = EDC_POLICY_DEFINITION_TYPE)
            String type,
            @Schema(requiredMode = REQUIRED)
            ManagementApiSchema.PolicySchema policy) {

        // policy example took from https://w3c.github.io/odrl/bp/
        public static final String POLICY_DEFINITION_INPUT_EXAMPLE = """
                {
                    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                    "@id": "definition-id",
                    "policy": {
                        "@context": "http://www.w3.org/ns/odrl.jsonld",
                        "@type": "Set",
                        "uid": "http://example.com/policy:1010",
                        "profile": "http://example.com/odrl:profile:02",
                        "permission": [{
                            "target": "http://example.com/asset:9898.movie",
                            "action": "display",
                            "constraint": [{
                                "leftOperand": "spatial",
                                "operator": "eq",
                                "rightOperand":  "https://www.wikidata.org/wiki/Q183",
                                "comment": "i.e Germany"
                            }]
                        }]
                    }
                }
                """;
    }

    @Schema(name = "PolicyDefinitionOutput", example = PolicyDefinitionOutputSchema.POLICY_DEFINITION_OUTPUT_EXAMPLE)
    record PolicyDefinitionOutputSchema(
            @Schema(name = ID)
            String id,
            @Schema(name = TYPE, example = EDC_POLICY_DEFINITION_TYPE)
            String type,
            ManagementApiSchema.PolicySchema policy) {

        // policy example took from https://w3c.github.io/odrl/bp/
        public static final String POLICY_DEFINITION_OUTPUT_EXAMPLE = """
                {
                    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                    "@id": "definition-id",
                    "policy": {
                        "@context": "http://www.w3.org/ns/odrl.jsonld",
                        "@type": "Set",
                        "uid": "http://example.com/policy:1010",
                        "permission": [{
                            "target": "http://example.com/asset:9898.movie",
                            "action": "display",
                            "constraint": [{
                                "leftOperand": "spatial",
                                "operator": "eq",
                                "rightOperand":  "https://www.wikidata.org/wiki/Q183",
                                "comment": "i.e Germany"
                            }]
                        }]
                    },
                    "createdAt": 1688465655
                }
                """;
    }

}
