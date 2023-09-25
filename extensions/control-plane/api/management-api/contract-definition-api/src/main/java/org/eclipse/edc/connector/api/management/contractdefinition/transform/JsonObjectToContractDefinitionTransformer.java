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
 *       SAP SE - add private properties to contract definition
 *
 */

package org.eclipse.edc.connector.api.management.contractdefinition.transform;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition.*;


public class JsonObjectToContractDefinitionTransformer extends AbstractJsonLdTransformer<JsonObject, ContractDefinition> {

    public JsonObjectToContractDefinitionTransformer() {
        super(JsonObject.class, ContractDefinition.class);
    }

    @Override
    public @Nullable ContractDefinition transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = ContractDefinition.Builder.newInstance();
        builder.id(nodeId(object));
        visitProperties(object, (s, jsonValue) -> transformProperties(s, jsonValue, builder, context));
        return builderResult(builder::build, context);
    }

    private void transformProperties(String key, JsonValue jsonValue, ContractDefinition.Builder builder, TransformerContext context) {
        if (CONTRACT_DEFINITION_ACCESSPOLICY_ID.equals(key) ) {
            builder.accessPolicyId(transformString(jsonValue, context));
        } else if (CONTRACT_DEFINITION_CONTRACTPOLICY_ID.equals(key) ) {
            builder.contractPolicyId(transformString(jsonValue, context));
        } else if (CONTRACT_DEFINITION_ASSETS_SELECTOR.equals(key) ) {
            builder.assetsSelector(transformArray(jsonValue, Criterion.class, context));
        } else if (CONTRACT_DEFINITION_PRIVATE_PROPERTIES.equals(key) && jsonValue instanceof JsonArray) {
            var props = jsonValue.asJsonArray().getJsonObject(0);
            visitProperties(props, (k, val) -> transformProperties(k, val, builder, context));
        } else {
                builder.privateProperty(key, transformGenericProperty(jsonValue, context));
            }
        }
}
