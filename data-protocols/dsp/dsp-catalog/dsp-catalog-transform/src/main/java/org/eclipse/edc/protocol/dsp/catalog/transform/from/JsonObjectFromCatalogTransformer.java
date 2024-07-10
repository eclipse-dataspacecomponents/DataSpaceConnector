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

package org.eclipse.edc.protocol.dsp.catalog.transform.from;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.spi.agent.ParticipantIdMapper;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jakarta.json.stream.JsonCollectors.toJsonArray;
import static java.util.Optional.ofNullable;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_CATALOG_TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DATASET_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DATA_SERVICE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DSPACE_PROPERTY_PARTICIPANT_ID;

/**
 * Converts from a {@link Catalog} to a DCAT catalog as a {@link JsonObject} in JSON-LD expanded form.
 */
public class JsonObjectFromCatalogTransformer extends AbstractJsonLdTransformer<Catalog, JsonObject> {
    private final JsonBuilderFactory jsonFactory;
    private final ObjectMapper mapper;
    private final ParticipantIdMapper participantIdMapper;

    public JsonObjectFromCatalogTransformer(JsonBuilderFactory jsonFactory, ObjectMapper mapper, ParticipantIdMapper participantIdMapper) {
        super(Catalog.class, JsonObject.class);
        this.jsonFactory = jsonFactory;
        this.mapper = mapper;
        this.participantIdMapper = participantIdMapper;
    }

    @Override
    public @Nullable JsonObject transform(@NotNull Catalog catalog, @NotNull TransformerContext context) {
        var datasets = catalog.getDatasets().stream()
                .map(offer -> context.transform(offer, JsonObject.class))
                .collect(toJsonArray());

        var dataServices = catalog.getDataServices().stream()
                .map(service -> context.transform(service, JsonObject.class))
                .collect(toJsonArray());

        var distributions = catalog.getDistributions().stream()
                .map(distro -> context.transform(distro, JsonObject.class))
                .collect(toJsonArray());

        var objectBuilder = jsonFactory.createObjectBuilder()
                .add(ID, catalog.getId())
                .add(TYPE, DCAT_CATALOG_TYPE)
                .add(DCAT_DATASET_ATTRIBUTE, datasets)
                .add(DCAT_DISTRIBUTION_ATTRIBUTE, distributions)
                .add(DCAT_DATA_SERVICE_ATTRIBUTE, dataServices);

        ofNullable(catalog.getParticipantId()).ifPresent(pid -> objectBuilder.add(DSPACE_PROPERTY_PARTICIPANT_ID, participantIdMapper.toIri(pid)));

        transformProperties(catalog.getProperties(), objectBuilder, mapper, context);

        return objectBuilder.build();
    }
}
