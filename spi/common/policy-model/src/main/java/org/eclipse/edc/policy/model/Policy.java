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

package org.eclipse.edc.policy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A collection of permissions, prohibitions, and obligations. Subtypes are defined by
 * {@link PolicyType}.
 * This is a value object. In order to have it identifiable and individually addressable, consider the use of PolicyDefinition.
 */
@JsonDeserialize(builder = Policy.Builder.class)
public class Policy {

    private final List<Permission> permissions = new ArrayList<>();
    private final List<Prohibition> prohibitions = new ArrayList<>();
    private final List<Duty> obligations = new ArrayList<>();
    private final List<String> profiles = new ArrayList<>();
    private final Map<String, Object> extensibleProperties = new HashMap<>();
    private String inheritsFrom;
    private String assigner;
    private String assignee;
    private String target;

    @JsonProperty("@type")
    private PolicyType type = PolicyType.SET;

    private Policy() {
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public List<Prohibition> getProhibitions() {
        return prohibitions;
    }

    public List<Duty> getObligations() {
        return obligations;
    }

    public List<String> getProfiles() {
        return profiles;
    }

    @Nullable
    public String getInheritsFrom() {
        return inheritsFrom;
    }

    public String getAssigner() {
        return assigner;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getTarget() {
        return target;
    }

    public PolicyType getType() {
        return type;
    }

    public Map<String, Object> getExtensibleProperties() {
        return extensibleProperties;
    }

    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitPolicy(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(permissions, prohibitions, obligations, extensibleProperties, inheritsFrom, assigner, assignee, target, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Policy policy = (Policy) o;
        return permissions.equals(policy.permissions) && prohibitions.equals(policy.prohibitions) && obligations.equals(policy.obligations) && profiles.equals(policy.profiles) && extensibleProperties.equals(policy.extensibleProperties) &&
                Objects.equals(inheritsFrom, policy.inheritsFrom) && Objects.equals(assigner, policy.assigner) && Objects.equals(assignee, policy.assignee) && Objects.equals(target, policy.target) && type == policy.type;
    }

    /**
     * Returns a copy of this policy with the specified target.
     *
     * @param target the target.
     * @return a copy with the specified target.
     */
    public Policy withTarget(String target) {
        return Builder.newInstance()
                .prohibitions(prohibitions)
                .permissions(permissions)
                .duties(obligations)
                .assigner(assigner)
                .assignee(assignee)
                .inheritsFrom(inheritsFrom)
                .type(type)
                .extensibleProperties(extensibleProperties)
                .target(target)
                .profiles(profiles)
                .build();
    }

    /**
     * A {@link Builder} initialized with the current policy.
     */
    public Builder toBuilder() {
        return Builder.newInstance()
                .prohibitions(prohibitions)
                .permissions(permissions)
                .duties(obligations)
                .assigner(assigner)
                .assignee(assignee)
                .inheritsFrom(inheritsFrom)
                .type(type)
                .extensibleProperties(extensibleProperties)
                .target(target)
                .profiles(profiles);
    }

    public interface Visitor<R> {
        R visitPolicy(Policy policy);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final Policy policy;

        private Builder() {
            policy = new Policy();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder prohibition(Prohibition prohibition) {
            policy.prohibitions.add(prohibition);
            return this;
        }

        public Builder prohibitions(List<Prohibition> prohibitions) {
            policy.prohibitions.addAll(prohibitions);
            return this;
        }

        public Builder permission(Permission permission) {
            policy.permissions.add(permission);
            return this;
        }

        public Builder permissions(List<Permission> permissions) {
            policy.permissions.addAll(permissions);
            return this;
        }


        public Builder profiles(List<String> profiles) {
            policy.profiles.addAll(profiles);
            return this;
        }

        public Builder duty(Duty duty) {
            policy.obligations.add(duty);
            return this;
        }

        @JsonProperty("obligations")
        public Builder duties(List<Duty> duties) {
            policy.obligations.addAll(duties);
            return this;
        }

        public Builder assigner(String assigner) {
            policy.assigner = assigner;
            return this;
        }

        public Builder assignee(String assignee) {
            policy.assignee = assignee;
            return this;
        }

        public Builder target(String target) {
            policy.target = target;
            return this;
        }

        public Builder inheritsFrom(String inheritsFrom) {
            policy.inheritsFrom = inheritsFrom;
            return this;
        }

        @JsonProperty("@type")
        public Builder type(PolicyType type) {
            policy.type = type;
            return this;
        }

        public Builder extensibleProperty(String key, Object value) {
            policy.extensibleProperties.put(key, value);
            return this;
        }

        public Builder extensibleProperties(Map<String, Object> properties) {
            policy.extensibleProperties.putAll(properties);
            return this;
        }

        public Policy build() {
            return policy;
        }
    }
}
