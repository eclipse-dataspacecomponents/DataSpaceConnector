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

package org.eclipse.edc.connector.dataplane.selector.api.schemas;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URL;
import java.util.Set;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;

@Schema(example = DataPlaneInstanceSchema.DATAPLANE_INSTANCE_EXAMPLE)
public record DataPlaneInstanceSchema(
        @Schema(name = CONTEXT, requiredMode = REQUIRED)
        Object context,
        @Schema(name = TYPE, example = DATAPLANE_INSTANCE_TYPE)
        String type,
        @Schema(name = ID)
        String id,
        @Schema(requiredMode = REQUIRED)
        URL url,
        @Schema(requiredMode = REQUIRED)
        Set<String> allowedSourceTypes,
        @Schema(requiredMode = REQUIRED)
        Set<String> allowedDestTypes,
        Integer turnCount,
        Long lastActive,
        String state,
        Long stateTimestamp) {
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
                "allowedTransferTypes": ["transfer-type"],
                "state": "AVAILABLE",
                "stateTimestamp": 1688465655
            }
            """;
}
