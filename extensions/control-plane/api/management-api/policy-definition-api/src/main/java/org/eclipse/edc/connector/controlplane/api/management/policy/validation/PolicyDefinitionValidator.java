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

package org.eclipse.edc.connector.controlplane.api.management.policy.validation;

import jakarta.json.JsonObject;
import org.eclipse.edc.validator.jsonobject.JsonLdPath;
import org.eclipse.edc.validator.jsonobject.JsonObjectValidator;
import org.eclipse.edc.validator.jsonobject.validators.MandatoryObject;
import org.eclipse.edc.validator.jsonobject.validators.MandatoryValue;
import org.eclipse.edc.validator.jsonobject.validators.OptionalIdArray;
import org.eclipse.edc.validator.jsonobject.validators.OptionalIdNotBlank;
import org.eclipse.edc.validator.jsonobject.validators.TypeIs;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;

import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_POLICY;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ACTION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_AND_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_AND_SEQUENCE_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_CONSEQUENCE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_DUTY_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_LEFT_OPERAND_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OBLIGATION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OPERATOR_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OR_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_SET;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROFILE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROHIBITION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_REMEDY_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_RIGHT_OPERAND_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_XONE_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.validator.spi.Violation.violation;

public class PolicyDefinitionValidator {
    public static Validator<JsonObject> instance() {
        return JsonObjectValidator.newValidator()
                .verifyId(OptionalIdNotBlank::new)
                .verify(EDC_POLICY_DEFINITION_POLICY, MandatoryObject::new)
                .verifyObject(EDC_POLICY_DEFINITION_POLICY, PolicyValidator::instance)
                .build();
    }

    private static class PolicyValidator {
        public static JsonObjectValidator.Builder instance(JsonObjectValidator.Builder builder) {
            return builder
                    .verify(path -> new TypeIs(path, ODRL_POLICY_TYPE_SET))
                    .verifyArrayItem(ODRL_PERMISSION_ATTRIBUTE, PermissionValidator::instance)
                    .verifyArrayItem(ODRL_OBLIGATION_ATTRIBUTE, DutyValidator::instance)
                    .verifyArrayItem(ODRL_PROHIBITION_ATTRIBUTE, ProhibitionValidator::instance)
                    .verify(ProfileValidator::new);
        }
    }

    private static class PermissionValidator {
        public static JsonObjectValidator.Builder instance(JsonObjectValidator.Builder builder) {

            return builder
                    .verify(ActionValidator::new)
                    .verifyArrayItem(ODRL_DUTY_ATTRIBUTE, DutyValidator::instance)
                    .verifyArrayItem(ODRL_CONSTRAINT_ATTRIBUTE, b -> b
                            .verify(ConstraintValidator::new));
        }

    }

    private static class DutyValidator {
        public static JsonObjectValidator.Builder instance(JsonObjectValidator.Builder builder) {

            return builder
                    .verify(ActionValidator::new)
                    .verifyArrayItem(ODRL_CONSEQUENCE_ATTRIBUTE, ConsequenceValidator::instance)
                    .verifyArrayItem(ODRL_CONSTRAINT_ATTRIBUTE, b -> b
                            .verify(ConstraintValidator::new));
        }

    }

    private static class ProhibitionValidator {
        public static JsonObjectValidator.Builder instance(JsonObjectValidator.Builder builder) {

            return builder
                    .verify(ActionValidator::new)
                    .verifyArrayItem(ODRL_REMEDY_ATTRIBUTE, DutyValidator::instance)
                    .verifyArrayItem(ODRL_CONSTRAINT_ATTRIBUTE, b -> b
                            .verify(ConstraintValidator::new));
        }

    }

    private record ProfileValidator(JsonLdPath path) implements Validator<JsonObject> {
        @Override
        public ValidationResult validate(JsonObject input) {
            return JsonObjectValidator.newValidator()
                    .verify(ODRL_PROFILE_ATTRIBUTE, OptionalIdArray.min(1))
                    .build()
                    .validate(input);
        }
    }

    private static class ConsequenceValidator {
        public static JsonObjectValidator.Builder instance(JsonObjectValidator.Builder builder) {

            return builder
                    .verify(ActionValidator::new)
                    .verifyArrayItem(ODRL_CONSTRAINT_ATTRIBUTE, b -> b
                            .verify(ConstraintValidator::new));
        }
    }

    private record ActionValidator(JsonLdPath path) implements Validator<JsonObject> {
        @Override
        public ValidationResult validate(JsonObject input) {
            return Optional.of(input.containsKey(ODRL_ACTION_ATTRIBUTE))
                    .filter(it -> input.get(ODRL_ACTION_ATTRIBUTE) != null)
                    .map(it -> ValidationResult.success())
                    .orElse(ValidationResult.failure(violation(format("%s is mandatory but missing or null", path.append(ODRL_ACTION_ATTRIBUTE)), ODRL_ACTION_ATTRIBUTE)));
        }
    }

    private record ConstraintValidator(JsonLdPath path) implements Validator<JsonObject> {

        private static final List<String> LOGICAL_CONSTRAINTS = List.of(
                ODRL_AND_CONSTRAINT_ATTRIBUTE,
                ODRL_XONE_CONSTRAINT_ATTRIBUTE,
                ODRL_OR_CONSTRAINT_ATTRIBUTE,
                ODRL_AND_SEQUENCE_CONSTRAINT_ATTRIBUTE
        );

        @Override
        public ValidationResult validate(JsonObject input) {
            var logicalOperands = input.keySet().stream().filter(LOGICAL_CONSTRAINTS::contains).toList();

            if (logicalOperands.size() > 1) {
                return ValidationResult.failure(
                        violation("Cannot define multiple operands in a Logical Constraint: %s".formatted(logicalOperands),
                                path.toString()));
            }

            if (logicalOperands.size() == 1) {
                return ValidationResult.success();
            }

            return JsonObjectValidator.newValidator()
                    .verify(ODRL_LEFT_OPERAND_ATTRIBUTE, MandatoryObject::new)
                    .verifyObject(ODRL_LEFT_OPERAND_ATTRIBUTE, b -> b.verifyId(OptionalIdNotBlank::new))
                    .verify(ODRL_OPERATOR_ATTRIBUTE, MandatoryObject::new)
                    .verifyObject(ODRL_OPERATOR_ATTRIBUTE, b -> b.verifyId(OptionalIdNotBlank::new))
                    .verify(ODRL_RIGHT_OPERAND_ATTRIBUTE, MandatoryValue::new)
                    .build()
                    .validate(input);
        }

    }

}
