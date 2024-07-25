/*
 *  Copyright (c) 2024 ZF Friedrichshafen AG
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       ZF Friedrichshafen AG - initial implementation
 *
 */

package org.eclipse.edc.validator.jsonobject.validators;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.edc.validator.jsonobject.JsonLdPath;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;

import java.util.Optional;
import java.util.function.Function;

import static java.lang.String.format;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.validator.spi.Violation.violation;

/**
 * Verifies that if an array is present the key:value pairs are of type @id:string with optional constraint on min size.
 */
public class OptionalIdArray implements Validator<JsonObject> {
    private final JsonLdPath path;
    private final Integer min;

    public OptionalIdArray(JsonLdPath path) {
        this(path, null);
    }

    public OptionalIdArray(JsonLdPath path, Integer min) {
        this.path = path;
        this.min = min;
    }

    public static Function<JsonLdPath, Validator<JsonObject>> min(Integer min) {
        return path -> new OptionalIdArray(path, min);
    }

    @Override
    public ValidationResult validate(JsonObject input) {
        ValidationResult sizeResult;

        sizeResult = Optional.ofNullable(input.getJsonArray(path.last()))
                .map(this::validateMin)
                .orElse(ValidationResult.success());

        if (sizeResult.failed()) {
            return sizeResult;
        }

        return Optional.ofNullable(input.getJsonArray(path.last()))
                .map(this::validateType)
                .orElse(ValidationResult.success());
    }

    private ValidationResult validateMin(JsonArray array) {
        if (min == null || (array.size() >= min)) {
            return ValidationResult.success();
        }
        return ValidationResult.failure(violation(format("array '%s' should at least contains '%s' elements", path, min), path.toString()));
    }

    private ValidationResult validateType(JsonArray array) {
        for (var value : array) {
            try {
                var id = value.asJsonObject().getJsonString(ID);
                if (id == null || id.getString().isBlank()) {
                    return ValidationResult.failure(violation(format("contents of array '%s' should not be blank or empty", path), path.toString()));
                }
            } catch (ClassCastException e) {
                return ValidationResult.failure(violation(format("contents of array '%s' should be of type string", path), path.toString()));
            }
        }
        return ValidationResult.success();
    }

}
