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

package org.eclipse.edc.protocol.dsp.transferprocess.transformer.type.to;

import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonObjectToDataAddressTransformer extends AbstractJsonLdTransformer<JsonObject, DataAddress> {

    public JsonObjectToDataAddressTransformer() {
        super(JsonObject.class, DataAddress.class);
    }

    @Override
    public @Nullable DataAddress transform(@NotNull JsonObject jsonObject, @NotNull TransformerContext context) {
        var dataAddressBuilder = DataAddress.Builder.newInstance();

        jsonObject.forEach((k, v) -> dataAddressBuilder.property(k, jsonObject.getString(k)));

        return dataAddressBuilder.build();
    }
}
