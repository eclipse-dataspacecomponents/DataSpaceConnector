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

package org.eclipse.edc.connector.controlplane.transform.odrl.to;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.policy.model.Prohibition;
import org.eclipse.edc.spi.agent.ParticipantIdMapper;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNEE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNER_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OBLIGATION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_AGREEMENT;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_OFFER;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_SET;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROFILE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROHIBITION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_TARGET_ATTRIBUTE;

/**
 * Converts from an ODRL policy as a {@link JsonObject} in JSON-LD expanded form to a {@link Policy}.
 */
public class JsonObjectToPolicyTransformer extends AbstractJsonLdTransformer<JsonObject, Policy> {

    private final ParticipantIdMapper participantIdMapper;

    public JsonObjectToPolicyTransformer(ParticipantIdMapper participantIdMapper) {
        super(JsonObject.class, Policy.class);
        this.participantIdMapper = participantIdMapper;
    }

    @Override
    public @Nullable Policy transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = Policy.Builder.newInstance();

        var policyType = Optional.ofNullable(context.consumeData(Policy.class, TYPE))
                .map(PolicyType.class::cast)
                .orElseGet(() -> {
                    var tp = object.getJsonArray(TYPE).stream().findFirst()
                            .map(JsonString.class::cast)
                            .map(JsonString::getString)
                            .orElse(ODRL_POLICY_TYPE_SET);

                    return switch (tp) {
                        case ODRL_POLICY_TYPE_SET -> PolicyType.SET;
                        case ODRL_POLICY_TYPE_OFFER -> PolicyType.OFFER;
                        case ODRL_POLICY_TYPE_AGREEMENT -> PolicyType.CONTRACT;
                        default -> null;
                    };
                });

        if (policyType == null) {
            context.problem()
                    .invalidProperty()
                    .property(TYPE)
                    .value(null)
                    .error("Invalid type for ODRL policy, should be one of [%s, %s, %s]".formatted(ODRL_POLICY_TYPE_SET, ODRL_POLICY_TYPE_OFFER, ODRL_POLICY_TYPE_AGREEMENT))
                    .report();
            return null;
        }

        builder.type(policyType);

        visitProperties(object, key -> switch (key) {
            case ODRL_PERMISSION_ATTRIBUTE -> v -> builder.permissions(transformArray(v, Permission.class, context));
            case ODRL_PROHIBITION_ATTRIBUTE -> v -> builder.prohibitions(transformArray(v, Prohibition.class, context));
            case ODRL_OBLIGATION_ATTRIBUTE -> v -> builder.duties(transformArray(v, Duty.class, context));
            case ODRL_TARGET_ATTRIBUTE -> v -> builder.target(transformString(v, context));
            case ODRL_ASSIGNER_ATTRIBUTE -> v -> builder.assigner(participantIdMapper.fromIri(transformString(v, context)));
            case ODRL_ASSIGNEE_ATTRIBUTE -> v -> builder.assignee(participantIdMapper.fromIri(transformString(v, context)));
            case ODRL_PROFILE_ATTRIBUTE -> v -> builder.profiles(transformProfile(v));
            default -> v -> builder.extensibleProperty(key, transformGenericProperty(v, context));
        });

        return builderResult(builder::build, context);
    }

    private List<String> transformProfile(JsonValue value) {
        return value.asJsonArray().stream()
                .map(this::nodeId)
                .filter(Objects::nonNull)
                .collect(toList());
    }
}
