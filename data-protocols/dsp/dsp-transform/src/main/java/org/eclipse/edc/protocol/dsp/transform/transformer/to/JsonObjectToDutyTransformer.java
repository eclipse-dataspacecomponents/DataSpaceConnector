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

package org.eclipse.edc.protocol.dsp.transform.transformer.to;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.protocol.dsp.transform.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.protocol.dsp.transform.transformer.Namespaces.ODRL_SCHEMA;

public class JsonObjectToDutyTransformer extends AbstractJsonLdTransformer<JsonObject, Duty> {
    
    private static final String ODRL_ACTION_PROPERTY = ODRL_SCHEMA + "action";
    private static final String ODRL_CONSTRAINT_PROPERTY = ODRL_SCHEMA + "constraint";
    private static final String ODRL_CONSEQUENCE_PROPERTY = ODRL_SCHEMA + "consequence";
    
    public JsonObjectToDutyTransformer() {
        super(JsonObject.class, Duty.class);
    }
    
    @Override
    public @Nullable Duty transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = Duty.Builder.newInstance();
        visitProperties(object, (key, value) -> transformProperties(key, value, builder, context));
        return builder.build();
    }
    
    private void transformProperties(String key, JsonValue value, Duty.Builder builder, TransformerContext context) {
        if (ODRL_ACTION_PROPERTY.equals(key)) {
            transformArrayOrObject(value, Action.class, builder::action, context);
        } else if (ODRL_CONSTRAINT_PROPERTY.equals(key)) {
            transformArrayOrObject(value, Constraint.class, builder::constraint, context);
        } else if (ODRL_CONSEQUENCE_PROPERTY.equals(key)) {
            transformArrayOrObject(value, Duty.class, builder::consequence, context);
        }
    }
}
