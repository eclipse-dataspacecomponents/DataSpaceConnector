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

package org.eclipse.edc.connector.controlplane.api.management.edr.v3;

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
import org.eclipse.edc.api.model.ApiCoreSchema;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;

@OpenAPIDefinition(info = @Info(version = "v3"))
@Tag(name = "EDR Cache V3")
public interface EdrCacheApiV3 {

    @Operation(description = "Request all Edr entries according to a particular query",
            requestBody = @RequestBody(
                    content = @Content(schema = @Schema(implementation = ApiCoreSchema.QuerySpecSchema.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The edr entries matching the query",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EndpointDataReferenceEntrySchema.class)))),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            })
    JsonArray requestEdrEntriesV3(JsonObject querySpecJson);

    @Operation(description = "Gets the EDR data address with the given transfer process ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The data address",
                            content = @Content(schema = @Schema(implementation = ApiCoreSchema.DataAddressSchema.class))),
                    @ApiResponse(responseCode = "400", description = "Request was malformed, e.g. id was null",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "An EDR data address with the given transfer process ID does not exist",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            }
    )
    JsonObject getEdrEntryDataAddressV3(String transferProcessId);

    @Operation(description = "Removes an EDR entry given the transfer process ID",
            responses = {
                    @ApiResponse(responseCode = "204", description = "EDR entry was deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Request was malformed, e.g. id was null",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "An EDR entry with the given ID does not exist",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            })
    void removeEdrEntryV3(String transferProcessId);

    @ArraySchema()
    @Schema(name = "EndpointDataReferenceEntry", example = EndpointDataReferenceEntrySchema.EDR_ENTRY_OUTPUT_EXAMPLE)
    record EndpointDataReferenceEntrySchema(
            @Schema(name = ID)
            String id,
            @Schema(name = TYPE, example = EndpointDataReferenceEntry.EDR_ENTRY_TYPE)
            String type
    ) {
        public static final String EDR_ENTRY_OUTPUT_EXAMPLE = """
                {
                    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                    "@id": "transfer-process-id",
                    "transferProcessId": "transfer-process-id",
                    "agreementId": "agreement-id",
                    "contractNegotiationId": "contract-negotiation-id",
                    "assetId": "asset-id",
                    "providerId": "provider-id",
                    "createdAt": 1688465655
                }
                """;
    }

}
