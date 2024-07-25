/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.edc.connector.controlplane.policy.spi.testfixtures;

import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.policy.model.Prohibition;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class TestFunctions {
    public static PolicyDefinition createPolicy(String id) {
        return createPolicy(id, null);
    }

    public static PolicyDefinition createPolicy(String id, String target) {
        var policy = createPolicyBuilder(id).target(target).build();

        return PolicyDefinition.Builder.newInstance()
                .createdAt(Clock.systemUTC().millis())
                .id(id)
                .policy(policy)
                .build();
    }

    public static PolicyDefinition createPolicy(String id, String target, List<String> profiles) {
        var policy = createPolicyBuilder(id).profiles(profiles).target(target).build();

        return PolicyDefinition.Builder.newInstance()
                .createdAt(Clock.systemUTC().millis())
                .id(id)
                .policy(policy)
                .build();
    }

    public static PolicyDefinition createPolicy(String id, String target, Map<String, Object> privateProperties) {
        var policy = createPolicyBuilder(id).target(target).build();

        return PolicyDefinition.Builder.newInstance()
                .createdAt(Clock.systemUTC().millis())
                .id(id)
                .policy(policy)
                .privateProperties(privateProperties)
                .build();
    }

    public static List<PolicyDefinition> createPolicies(int count) {
        return IntStream.range(0, count).mapToObj(i -> createPolicy("policyDef" + i)).collect(Collectors.toList());
    }

    public static Policy.Builder createPolicyBuilder(String id) {
        var permission = Permission.Builder.newInstance()
                .build();

        var prohibition = createProhibitionBuilder(id)
                .build();

        var duty = Duty.Builder.newInstance()
                .build();

        return Policy.Builder.newInstance()
                .permission(permission)
                .prohibition(prohibition)
                .duties(List.of(duty))
                .inheritsFrom("sampleInheritsFrom")
                .assigner("sampleAssigner")
                .assignee("sampleAssignee")
                .target("sampleTarget")
                .type(PolicyType.SET);
    }

    public static Prohibition.Builder createProhibitionBuilder(String id) {
        return Prohibition.Builder.newInstance();
    }

    public static Duty.Builder createDutyBuilder(String id) {
        return Duty.Builder.newInstance();
    }

    public static Permission.Builder createPermissionBuilder(String id) {
        return Permission.Builder.newInstance();
    }

    public static Action createAction(String type) {
        return Action.Builder.newInstance().constraint(AtomicConstraint.Builder.newInstance()
                        .leftExpression(new LiteralExpression("foo"))
                        .operator(Operator.EQ)
                        .rightExpression(new LiteralExpression("bar"))
                        .build())
                .type(type)
                .build();
    }

}
