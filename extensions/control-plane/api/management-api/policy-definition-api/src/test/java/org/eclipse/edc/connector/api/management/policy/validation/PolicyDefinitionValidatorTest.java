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

package org.eclipse.edc.connector.api.management.policy.validation;

import jakarta.json.JsonObject;
import org.eclipse.edc.validator.spi.ValidationFailure;
import org.eclipse.edc.validator.spi.Validator;
import org.eclipse.edc.validator.spi.Violation;
import org.junit.jupiter.api.Test;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.eclipse.edc.connector.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_POLICY;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_SET;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;

class PolicyDefinitionValidatorTest {

    private final Validator<JsonObject> validator = PolicyDefinitionValidator.instance();

    @Test
    void shouldSucceed_whenObjectIsValid() {
        var policyDefinition = createObjectBuilder()
                .add(EDC_POLICY_DEFINITION_POLICY, createArrayBuilder()
                        .add(createObjectBuilder().add(TYPE, createArrayBuilder().add(ODRL_POLICY_TYPE_SET))))
                .build();

        var result = validator.validate(policyDefinition);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldFail_whenIdIsBlank() {
        var policyDefinition = createObjectBuilder()
                .add(ID, " ")
                .build();

        var result = validator.validate(policyDefinition);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .isNotEmpty()
                .filteredOn(it -> ID.equals(it.path()))
                .anySatisfy(violation -> assertThat(violation.message()).contains("blank"));
    }

    @Test
    void shouldFail_whenPolicyIsMissing() {
        var policyDefinition = createObjectBuilder().build();

        var result = validator.validate(policyDefinition);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .isNotEmpty()
                .filteredOn(it -> EDC_POLICY_DEFINITION_POLICY.equals(it.path()))
                .anySatisfy(violation -> assertThat(violation.message()).contains("mandatory"));
    }


    @Test
    void shouldFail_whenTypeIsMissing() {
        var policyDefinition = createObjectBuilder().build();

        var result = validator.validate(policyDefinition);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .isNotEmpty()
                .filteredOn(it -> EDC_POLICY_DEFINITION_POLICY.equals(it.path()))
                .anySatisfy(violation -> assertThat(violation.message()).contains("mandatory"));
    }

    @Test
    void shouldFail_whenTypeIsInvalid() {
        var policyDefinition = createObjectBuilder()
                .add(EDC_POLICY_DEFINITION_POLICY, createArrayBuilder()
                        .add(createObjectBuilder().add(TYPE, createArrayBuilder().add("InvalidType"))))
                .build();
        var result = validator.validate(policyDefinition);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .isNotEmpty()
                .filteredOn(it -> it.path().contains(EDC_POLICY_DEFINITION_POLICY))
                .anySatisfy(violation -> assertThat(violation.message()).contains("was expected to be"));
    }
}
