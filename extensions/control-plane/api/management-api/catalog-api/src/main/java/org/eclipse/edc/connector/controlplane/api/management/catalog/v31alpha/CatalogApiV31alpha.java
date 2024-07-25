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

package org.eclipse.edc.connector.controlplane.api.management.catalog.v31alpha;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonObject;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.eclipse.edc.api.model.ApiCoreSchema;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequest.CATALOG_REQUEST_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;

@OpenAPIDefinition(info = @Info(version = "v3"))
@Tag(name = "Catalog V3")
public interface CatalogApiV31alpha {

    @Operation(
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = CatalogRequestSchema.class))),
            responses = { @ApiResponse(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CatalogSchema.class)
                    ),
                    description = "Gets contract offers (=catalog) of a single connector") }
    )
    void requestCatalogV31alpha(JsonObject request, @Suspended AsyncResponse response);

    @Operation(
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = DatasetRequestSchema.class))),
            responses = { @ApiResponse(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DatasetSchema.class)
                    ),
                    description = "Gets single dataset from a connector") }
    )
    void getDatasetV31alpha(JsonObject request, @Suspended AsyncResponse response);

    @Schema(name = "CatalogRequest", example = CatalogRequestSchema.CATALOG_REQUEST_EXAMPLE)
    record CatalogRequestSchema(
            @Schema(name = CONTEXT, requiredMode = REQUIRED)
            Object context,
            @Schema(name = TYPE, example = CATALOG_REQUEST_TYPE)
            String type,
            @Schema(requiredMode = REQUIRED)
            String counterPartyAddress,
            // Switch to required in the next API iteration
            @Schema(requiredMode = NOT_REQUIRED)
            String counterPartyId,
            @Schema(requiredMode = REQUIRED)
            String protocol,
            @Schema(requiredMode = NOT_REQUIRED)
            List<String> additionalScopes,
            ApiCoreSchema.QuerySpecSchema querySpec) {

        public static final String CATALOG_REQUEST_EXAMPLE = """
                {
                    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                    "@type": "CatalogRequest",
                    "counterPartyAddress": "http://provider-address",
                    "counterPartyId": "providerId",
                    "protocol": "dataspace-protocol-http",
                    "additionalScopes": [ "org.eclipse.edc.vc.type:SomeCredential:read", "org.eclipse.edc.vc.type:AnotherCredential:write" ],
                    "querySpec": {
                        "offset": 0,
                        "limit": 50,
                        "sortOrder": "DESC",
                        "sortField": "fieldName",
                        "filterExpression": []
                    }
                }
                """;
    }

    @Schema(name = "DatasetRequest", example = DatasetRequestSchema.DATASET_REQUEST_EXAMPLE)
    record DatasetRequestSchema(
            @Schema(name = TYPE, example = CATALOG_REQUEST_TYPE)
            String type,
            String counterPartyAddress,
            String counterPartyId,
            String protocol,
            ApiCoreSchema.QuerySpecSchema querySpec) {

        public static final String DATASET_REQUEST_EXAMPLE = """
                {
                    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                    "@type": "DatasetRequest",
                    "@id": "dataset-id",
                    "counterPartyAddress": "http://counter-party-address",
                    "counterPartyId": "counter-party-id",
                    "protocol": "dataspace-protocol-http"
                }
                """;
    }

    @Schema(name = "Catalog", description = "DCAT catalog", example = CatalogSchema.CATALOG_EXAMPLE)
    record CatalogSchema(
    ) {
        public static final String CATALOG_EXAMPLE = """
                {
                    "@id": "7df65569-8c59-4013-b1c0-fa14f6641bf2",
                    "@type": "dcat:Catalog",
                    "dcat:dataset": {
                        "@id": "bcca61be-e82e-4da6-bfec-9716a56cef35",
                        "@type": "dcat:Dataset",
                        "odrl:hasPolicy": {
                            "@id": "OGU0ZTMzMGMtODQ2ZS00ZWMxLThmOGQtNWQxNWM0NmI2NmY4:YmNjYTYxYmUtZTgyZS00ZGE2LWJmZWMtOTcxNmE1NmNlZjM1:NDY2ZTZhMmEtNjQ1Yy00ZGQ0LWFlZDktMjdjNGJkZTU4MDNj",
                            "@type": "odrl:Set",
                            "odrl:permission": {
                                "odrl:target": "bcca61be-e82e-4da6-bfec-9716a56cef35",
                                "odrl:action": {
                                    "odrl:type": "http://www.w3.org/ns/odrl/2/use"
                                },
                                "odrl:constraint": {
                                    "odrl:and": [
                                        {
                                            "odrl:leftOperand": "https://w3id.org/edc/v0.0.1/ns/inForceDate",
                                            "odrl:operator": {
                                                "@id": "odrl:gteq"
                                            },
                                            "odrl:rightOperand": "2023-07-07T07:19:58.585601395Z"
                                        },
                                        {
                                            "odrl:leftOperand": "https://w3id.org/edc/v0.0.1/ns/inForceDate",
                                            "odrl:operator": {
                                                "@id": "odrl:lteq"
                                            },
                                            "odrl:rightOperand": "2023-07-12T07:19:58.585601395Z"
                                        }
                                    ]
                                }
                            },
                            "odrl:prohibition": [],
                            "odrl:obligation": []
                        },
                        "dcat:distribution": [
                            {
                                "@type": "dcat:Distribution",
                                "dct:format": {
                                    "@id": "HttpData"
                                },
                                "dcat:accessService": "5e839777-d93e-4785-8972-1005f51cf367"
                            }
                        ],
                        "description": "description",
                        "id": "bcca61be-e82e-4da6-bfec-9716a56cef35"
                    },
                    "dcat:service": {
                        "@id": "5e839777-d93e-4785-8972-1005f51cf367",
                        "@type": "dcat:DataService",
                        "dct:terms": "connector",
                        "dct:endpointUrl": "http://localhost:16806/protocol"
                    },
                    "dspace:participantId": "urn:connector:provider",
                    "@context": {
                        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
                        "dct": "http://purl.org/dc/terms/",
                        "edc": "https://w3id.org/edc/v0.0.1/ns/",
                        "dcat": "http://www.w3.org/ns/dcat#",
                        "odrl": "http://www.w3.org/ns/odrl/2/",
                        "dspace": "https://w3id.org/dspace/v0.8/"
                    }
                }
                """;
    }

    @Schema(name = "Dataset", description = "DCAT dataset", example = DatasetSchema.DATASET_EXAMPLE)
    record DatasetSchema(
    ) {
        public static final String DATASET_EXAMPLE = """
                {
                    "@id": "bcca61be-e82e-4da6-bfec-9716a56cef35",
                    "@type": "dcat:Dataset",
                    "odrl:hasPolicy": {
                        "@id": "OGU0ZTMzMGMtODQ2ZS00ZWMxLThmOGQtNWQxNWM0NmI2NmY4:YmNjYTYxYmUtZTgyZS00ZGE2LWJmZWMtOTcxNmE1NmNlZjM1:NDY2ZTZhMmEtNjQ1Yy00ZGQ0LWFlZDktMjdjNGJkZTU4MDNj",
                        "@type": "odrl:Set",
                        "odrl:permission": {
                            "odrl:target": "bcca61be-e82e-4da6-bfec-9716a56cef35",
                            "odrl:action": {
                                "odrl:type": "http://www.w3.org/ns/odrl/2/use"
                            },
                            "odrl:constraint": {
                                "odrl:and": [
                                    {
                                        "odrl:leftOperand": "https://w3id.org/edc/v0.0.1/ns/inForceDate",
                                        "odrl:operator": {
                                            "@id": "odrl:gteq"
                                        },
                                        "odrl:rightOperand": "2023-07-07T07:19:58.585601395Z"
                                    },
                                    {
                                        "odrl:leftOperand": "https://w3id.org/edc/v0.0.1/ns/inForceDate",
                                        "odrl:operator": {
                                            "@id": "odrl:lteq"
                                        },
                                        "odrl:rightOperand": "2023-07-12T07:19:58.585601395Z"
                                    }
                                ]
                            }
                        },
                        "odrl:prohibition": [],
                        "odrl:obligation": [],
                        "odrl:target": "bcca61be-e82e-4da6-bfec-9716a56cef35"
                    },
                    "dcat:distribution": [
                        {
                            "@type": "dcat:Distribution",
                            "dct:format": {
                                "@id": "HttpData"
                            },
                            "dcat:accessService": "5e839777-d93e-4785-8972-1005f51cf367"
                        }
                    ],
                    "description": "description",
                    "id": "bcca61be-e82e-4da6-bfec-9716a56cef35",
                    "@context": {
                        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
                        "dct": "http://purl.org/dc/terms/",
                        "edc": "https://w3id.org/edc/v0.0.1/ns/",
                        "dcat": "http://www.w3.org/ns/dcat#",
                        "odrl": "http://www.w3.org/ns/odrl/2/",
                        "dspace": "https://w3id.org/dspace/v0.8/"
                    }
                }
                """;
    }
}
