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

package org.eclipse.edc.protocol.dsp.transform.transformer.from;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.catalog.spi.DataService;
import org.eclipse.edc.protocol.dsp.transform.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.protocol.dsp.transform.transformer.JsonLdKeywords.ID;
import static org.eclipse.edc.protocol.dsp.transform.transformer.JsonLdKeywords.TYPE;
import static org.eclipse.edc.protocol.dsp.transform.transformer.PropertyAndTypeNames.DCAT_DATA_SERVICE_TYPE;
import static org.eclipse.edc.protocol.dsp.transform.transformer.PropertyAndTypeNames.DCT_ENDPOINT_URL_ATTRIBUTE;
import static org.eclipse.edc.protocol.dsp.transform.transformer.PropertyAndTypeNames.DCT_TERMS_ATTRIBUTE;

public class JsonObjectFromDataServiceTransformer extends AbstractJsonLdTransformer<DataService, JsonObject> {

    private final JsonBuilderFactory jsonFactory;

    public JsonObjectFromDataServiceTransformer(JsonBuilderFactory jsonFactory) {
        super(DataService.class, JsonObject.class);
        this.jsonFactory = jsonFactory;
    }

    @Override
    public @Nullable JsonObject transform(@NotNull DataService dataService, @NotNull TransformerContext context) {
        var objectBuilder = jsonFactory.createObjectBuilder();
        objectBuilder.add(ID, dataService.getId());
        objectBuilder.add(TYPE, DCAT_DATA_SERVICE_TYPE);

        objectBuilder.add(DCT_TERMS_ATTRIBUTE, dataService.getTerms());
        objectBuilder.add(DCT_ENDPOINT_URL_ATTRIBUTE, dataService.getEndpointUrl());

        return objectBuilder.build();
    }

}
