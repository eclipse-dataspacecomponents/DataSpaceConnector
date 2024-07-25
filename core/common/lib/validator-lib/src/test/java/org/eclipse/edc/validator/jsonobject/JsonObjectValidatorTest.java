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

package org.eclipse.edc.validator.jsonobject;

import jakarta.json.JsonArrayBuilder;
import org.eclipse.edc.validator.jsonobject.validators.MandatoryArray;
import org.eclipse.edc.validator.jsonobject.validators.MandatoryObject;
import org.eclipse.edc.validator.jsonobject.validators.MandatoryValue;
import org.eclipse.edc.validator.jsonobject.validators.OptionalIdArray;
import org.eclipse.edc.validator.jsonobject.validators.OptionalIdNotBlank;
import org.eclipse.edc.validator.spi.ValidationFailure;
import org.eclipse.edc.validator.spi.Violation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;

class JsonObjectValidatorTest {

    @Test
    void shouldSucceed() {
        var input = createObjectBuilder();

        var result = JsonObjectValidator.newValidator().build().validate(input.build());

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldValidateId() {
        var input = createObjectBuilder().add(ID, " ");

        var result = JsonObjectValidator.newValidator()
                .verifyId(OptionalIdNotBlank::new)
                .build()
                .validate(input.build());

        assertThat(result).isFailed();
    }

    @Test
    void shouldValidateMandatoryObject_success() {
        var input = createObjectBuilder().add("mandatoryObject", createArrayBuilder().add(createObjectBuilder()));

        var result = JsonObjectValidator.newValidator()
                .verify("mandatoryObject", MandatoryObject::new)
                .build()
                .validate(input.build());

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldValidateMandatoryObject_failure() {
        var input = createObjectBuilder();

        var result = JsonObjectValidator.newValidator()
                .verify("mandatoryObject", MandatoryObject::new)
                .build()
                .validate(input.build());

        assertThat(result).isFailed();
    }

    @Test
    void shouldValidateNestedObject_whenMandatoryObject_success() {
        var input = createObjectBuilder()
                .add("mandatoryObject", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("subProperty", value("subValue")))
                );

        var result = JsonObjectValidator.newValidator()
                .verify("mandatoryObject", MandatoryObject::new)
                .verifyObject("mandatoryObject", v -> v
                        .verify("subProperty", MandatoryValue::new))
                .build()
                .validate(input.build());

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldValidateNestedObject_whenMandatoryObject_failure() {
        var input = createObjectBuilder()
                .add("mandatoryObject", createArrayBuilder()
                        .add(createObjectBuilder())
                );

        var result = JsonObjectValidator.newValidator()
                .verify("mandatoryObject", MandatoryObject::new)
                .verifyObject("mandatoryObject", v -> v
                        .verify("subProperty", MandatoryValue::new))
                .build()
                .validate(input.build());

        assertThat(result).isFailed().satisfies(failure -> {
            assertThat(failure.getViolations()).anySatisfy(violation -> {
                assertThat(violation.path()).contains("mandatoryObject/subProperty");
            });
        });
    }

    @Test
    void shouldValidateNestedObject_succeed_whenNestedObjectIsMissing() {
        var input = createObjectBuilder();

        var result = JsonObjectValidator.newValidator()
                .verifyObject("optionalProperty", v -> v
                        .verify("subProperty", MandatoryValue::new))
                .build()
                .validate(input.build());

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldValidateNestedArrayItem_success() {
        var input = createObjectBuilder()
                .add("arrayProperty", createArrayBuilder()
                        .add(createObjectBuilder().add("subProperty", value("value1")))
                        .add(createObjectBuilder().add("subProperty", value("value2")))
                );

        var result = JsonObjectValidator.newValidator()
                .verifyArrayItem("arrayProperty", v -> v
                        .verify("subProperty", MandatoryValue::new))
                .build()
                .validate(input.build());

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldValidateNestedArrayItem_failure() {
        var input = createObjectBuilder()
                .add("arrayProperty", createArrayBuilder()
                        .add(createObjectBuilder().add("subProperty", value("value1")))
                        .add(createObjectBuilder().add("subProperty", value(" ")))
                );

        var result = JsonObjectValidator.newValidator()
                .verifyArrayItem("arrayProperty", v -> v
                        .verify("subProperty", MandatoryValue::new))
                .build()
                .validate(input.build());

        assertThat(result).isFailed().satisfies(failure -> {
            assertThat(failure.getViolations()).anySatisfy(violation -> {
                assertThat(violation.path()).contains("arrayProperty/subProperty");
            });
        });
    }

    @Test
    void shouldValidateMandatoryArrayMinSize_failure() {
        var input = createObjectBuilder()
                .add("arrayProperty", createArrayBuilder()
                        .add(createObjectBuilder().add("subProperty", value("value1")))
                        .add(createObjectBuilder().add("subProperty", value(" ")))
                );

        var result = JsonObjectValidator.newValidator()
                .verify("arrayProperty", MandatoryArray.min(3))
                .build()
                .validate(input.build());

        assertThat(result).isFailed().satisfies(failure -> {
            assertThat(failure.getViolations()).anySatisfy(violation -> {
                assertThat(violation.path()).contains("arrayProperty");
            });
        });
    }

    @Test
    void shouldValidateMandatoryArray_failure() {
        var input = createObjectBuilder();

        var result = JsonObjectValidator.newValidator()
                .verify("arrayProperty", MandatoryArray::new)
                .build()
                .validate(input.build());

        assertThat(result).isFailed().satisfies(failure -> {
            assertThat(failure.getViolations()).anySatisfy(violation -> {
                assertThat(violation.path()).contains("arrayProperty");
            });
        });
    }

    @Test
    void shouldFail_whenInputIsNull() {
        var result = JsonObjectValidator.newValidator().build()
                .validate(null);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .hasSize(1)
                .first().satisfies(violation -> {
                    assertThat(violation.message()).contains("null");
                    assertThat(violation.path()).isEmpty();
                });
    }

    @Nested
    class TestOptionalIdArray {
        @Test
        void shouldFail_ValidateOptionalIdArrayMinSize() {
            var input = createObjectBuilder()
                    .add("arrayProperty", createArrayBuilder()
                            .add(createObjectBuilder().add(ID, "value1"))
                            .add(createObjectBuilder().add(ID, "value2"))
                    );

            var result = JsonObjectValidator.newValidator()
                    .verify("arrayProperty", OptionalIdArray.min(3))
                    .build()
                    .validate(input.build());

            assertThat(result).isFailed().satisfies(failure -> {
                assertThat(failure.getViolations()).anySatisfy(violation -> {
                    assertThat(violation.path()).contains("arrayProperty");
                });
            });
        }

        @Test
        void shouldSucceed_ValidateOptionalIdArrayNoValue() {
            var input = createObjectBuilder();

            var result = JsonObjectValidator.newValidator()
                    .verify("arrayProperty", OptionalIdArray::new)
                    .build()
                    .validate(input.build());

            assertThat(result).isSucceeded();
        }

        @Test
        void shouldFail_ValidateOptionalIdArrayWrongType() {
            var input = createObjectBuilder()
                    .add("arrayProperty", createArrayBuilder()
                            .add(createObjectBuilder().add("subProperty", createObjectBuilder()))
                    );

            var result = JsonObjectValidator.newValidator()
                    .verify("arrayProperty", OptionalIdArray::new)
                    .build()
                    .validate(input.build());

            assertThat(result).isFailed().satisfies(failure -> {
                assertThat(failure.getViolations()).anySatisfy(violation -> {
                    assertThat(violation.path()).contains("arrayProperty");
                });
            });
        }

        @Test
        void shouldSucceed_ValidateOptionalIdArray() {
            var input = createObjectBuilder()
                    .add("arrayProperty", createArrayBuilder()
                            .add(createObjectBuilder().add(ID, "value1"))
                            .add(createObjectBuilder().add(ID, "value2"))
                    );

            var result = JsonObjectValidator.newValidator()
                    .verify("arrayProperty", OptionalIdArray::new)
                    .build()
                    .validate(input.build());

            assertThat(result).isSucceeded();
        }
    }


    private JsonArrayBuilder value(String value) {
        return createArrayBuilder().add(createObjectBuilder().add(VALUE, value));
    }
}
