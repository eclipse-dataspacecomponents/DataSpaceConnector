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

package org.eclipse.edc.jsonld.spi.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static jakarta.json.JsonValue.ValueType.ARRAY;
import static jakarta.json.JsonValue.ValueType.FALSE;
import static jakarta.json.JsonValue.ValueType.NUMBER;
import static jakarta.json.JsonValue.ValueType.OBJECT;
import static jakarta.json.JsonValue.ValueType.STRING;
import static jakarta.json.JsonValue.ValueType.TRUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.KEYWORDS;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;

/**
 * Base JSON-LD transformer implementation.
 */
public abstract class AbstractJsonLdTransformer<INPUT, OUTPUT> implements JsonLdTransformer<INPUT, OUTPUT> {
    private final Class<INPUT> input;
    private final Class<OUTPUT> output;

    protected AbstractJsonLdTransformer(Class<INPUT> input, Class<OUTPUT> output) {
        this.input = input;
        this.output = output;
    }

    @NotNull
    protected static Consumer<JsonValue> doNothing() {
        return v -> {
        };
    }

    @Override
    public Class<INPUT> getInputType() {
        return input;
    }

    @Override
    public Class<OUTPUT> getOutputType() {
        return output;
    }

    /**
     * Extracts the {@link JsonObject} from the value. If the value is a {@link JsonObject}, it will be returned. If it is a {@link JsonArray}, the first entry will be
     * returned if it is a {@link JsonObject}, otherwise null. Note that if a JsonObject cannot be returned, a problem will be reported to the context.
     *
     * @param value        the value to extract the object from
     * @param context      the current transformation context
     * @param propertyName the property name to use for error reporting
     * @return the extracted object or null if one cannot be returned
     */
    protected JsonObject returnMandatoryJsonObject(@Nullable JsonValue value, TransformerContext context, String propertyName) {
        return returnJsonObject(value, context, propertyName, true);
    }

    /**
     * Extracts the {@link JsonObject} from the value. If the value is a {@link JsonObject}, it will be returned. If it is a {@link JsonArray}, the first entry will be
     * returned if it is a {@link JsonObject}, otherwise null. Note that if a JsonObject cannot be returned, a problem will be reported to the context.
     *
     * @param value        the value to extract the object from
     * @param context      the current transformation context
     * @param propertyName the property name to use for error reporting
     * @param mandatory    if false and the value is null, no error is reported
     * @return the extracted object or null if one cannot be returned
     */
    @Nullable
    protected JsonObject returnJsonObject(@Nullable JsonValue value, TransformerContext context, String propertyName, boolean mandatory) {
        if (value instanceof JsonArray) {
            return value.asJsonArray().stream().filter(Objects::nonNull)
                    .findFirst().map(entry -> {
                        if (entry instanceof JsonObject) {
                            return entry.asJsonObject();
                        } else {
                            context.problem()
                                    .unexpectedType()
                                    .type(ARRAY)
                                    .property(propertyName)
                                    .expected(OBJECT)
                                    .actual(entry.getValueType())
                                    .report();
                            return null;
                        }
                    })
                    .orElseGet(() -> {
                        context.reportProblem(format("Property '%s' contains an empty array", propertyName));
                        return null;
                    });

        } else if (value instanceof JsonObject) {
            return value.asJsonObject();
        } else if (value == null) {
            if (mandatory) {
                context.problem()
                        .nullProperty()
                        .property(propertyName)
                        .report();
            }
            return null;
        } else {
            context.problem()
                    .unexpectedType()
                    .property(propertyName)
                    .expected(OBJECT)
                    .expected(ARRAY)
                    .actual(value.toString())
                    .report();
            return null;
        }
    }

    /**
     * Transforms properties of a Java type. The properties are mapped to generic JSON values.
     *
     * @param properties the properties to map
     * @param builder    the builder on which to set the properties
     * @param mapper     the mapper for converting the properties
     * @param context    the transformer context
     */
    protected void transformProperties(Map<String, ?> properties, JsonObjectBuilder builder, ObjectMapper mapper, TransformerContext context) {
        if (properties == null) {
            return;
        }
        properties.forEach((k, v) -> {
            try {
                builder.add(k, mapper.convertValue(v, JsonValue.class));
            } catch (IllegalArgumentException e) {
                context.problem()
                        .invalidProperty()
                        .type(VALUE)
                        .property(k)
                        .value(v != null ? v.toString() : "null")
                        .error(e.getMessage())
                        .report();
            }
        });
    }

    protected void visitProperties(JsonObject object, BiConsumer<String, JsonValue> consumer) {
        object.entrySet().stream().filter(entry -> !KEYWORDS.contains(entry.getKey())).forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
    }

    protected void visitProperties(JsonObject object, Function<String, Consumer<JsonValue>> consumer) {
        object.entrySet().stream()
                .filter(entry -> !KEYWORDS.contains(entry.getKey()))
                .forEach(entry -> consumer.apply(entry.getKey()).accept(entry.getValue()));
    }

    /**
     * Visit all the elements of the value and apply the result function. If the input value
     * it's not an array, report an error.
     *
     * @param value          The input value
     * @param resultFunction The function to apply to each element
     * @param context        the transformer context
     */
    protected void visitArray(JsonValue value, Consumer<JsonValue> resultFunction, TransformerContext context) {
        if (value instanceof JsonArray) {
            visitArray(value.asJsonArray(), resultFunction);
        } else {
            context.problem()
                    .unexpectedType()
                    .actual(value != null ? value.getValueType() : null)
                    .expected(ARRAY)
                    .report();
        }
    }

    /**
     * Visit all the elements of the value and apply the result function.
     *
     * @param array          The input array
     * @param resultFunction The function to apply to each element
     */
    protected void visitArray(JsonArray array, Consumer<JsonValue> resultFunction) {
        array.forEach(resultFunction);
    }

    @Nullable
    protected Object transformGenericProperty(JsonValue value, TransformerContext context) {
        if (value instanceof JsonArray) {
            var jsonArray = (JsonArray) value;
            if (jsonArray.size() == 1) {
                // unwrap array
                return context.transform(jsonArray.get(0), Object.class);
            } else {
                return jsonArray.stream().map(prop -> context.transform(prop, Object.class)).collect(toList());
            }
        } else {
            return context.transform(value, Object.class);
        }
    }

    /**
     * Transforms a JsonValue to a string and applies the result function. If the value parameter is not of type JsonString, JsonObject or JsonArray,
     * a problem is reported to the context.
     *
     * @param value          the value to transform
     * @param resultFunction the function to apply to the transformation result
     * @param context        the transformer context
     */
    protected void transformString(JsonValue value, Consumer<String> resultFunction, TransformerContext context) {
        resultFunction.accept(transformString(value, context));
    }

    /**
     * Transforms a mandatory JsonValue to a string and applies the result function. If the value parameter is not of type JsonString, JsonObject or JsonArray,
     * a problem is reported to the context.
     *
     * @param value          the value to transform
     * @param resultFunction the function to apply to the transformation result
     * @param context        the transformer context
     * @return true if the string was present
     */
    protected boolean transformMandatoryString(JsonValue value, Consumer<String> resultFunction, TransformerContext context) {
        var result = transformString(value, context);
        if (result == null) {
            return false;
        }
        resultFunction.accept(result);
        return true;
    }

    /**
     * Transforms a JsonValue to a string and applies the result function. If the value parameter is not of type JsonString, JsonObject or JsonArray,
     * a problem is reported to the context.
     *
     * @param value   the value to transform
     * @param context the transformer context
     * @return the string result
     */
    @Nullable
    protected String transformString(JsonValue value, TransformerContext context) {
        if (value instanceof JsonString) {
            return ((JsonString) value).getString();
        } else if (value instanceof JsonObject) {
            var object = value.asJsonObject();
            return Stream.of(VALUE, ID).map(object::get)
                    .filter(Objects::nonNull)
                    .findFirst().map(it -> transformString(it, context))
                    .orElseGet(() -> {
                        // no need to report problem as it will have been donne above with call to transformString()
                        return null;
                    });
        } else if (value instanceof JsonArray) {
            return transformString(((JsonArray) value).get(0), context);
        } else if (value == null) {
            return null;
        } else {
            context.problem()
                    .unexpectedType()
                    .actual(value.getValueType())
                    .expected(OBJECT)
                    .expected(ARRAY)
                    .report();
            return null;
        }
    }

    /**
     * Transforms a JsonValue to int. If the value parameter is not of type JsonNumber, JsonObject or JsonArray, a problem is reported to the context.
     *
     * @param value   the value to transform
     * @param context the transformer context
     * @return the int value
     */
    protected int transformInt(JsonValue value, TransformerContext context) {
        if (value instanceof JsonNumber) {
            return ((JsonNumber) value).intValue();
        } else if (value instanceof JsonObject) {
            var jsonNumber = value.asJsonObject().getJsonNumber(VALUE);
            return transformInt(jsonNumber, context);
        } else if (value instanceof JsonArray) {
            return transformInt(value.asJsonArray().get(0), context);
        } else {
            var problem = context.problem().unexpectedType().expected(OBJECT).expected(ARRAY).expected(NUMBER);
            if (value != null) {
                problem.actual(value.getValueType());
            }
            problem.report();
            return 0;
        }
    }

    /**
     * Transforms a JsonValue to boolean. If the value parameter is not of type JsonObject or JsonArray, a problem is reported to the context.
     *
     * @param value   the value to transform
     * @param context the transformer context
     * @return the int value
     */
    protected boolean transformBoolean(JsonValue value, TransformerContext context) {
        if (value == null) {
            context.problem().unexpectedType().expected(OBJECT).expected(ARRAY).actual("null").report();
            return false;
        }
        if (value instanceof JsonObject) {
            return value.asJsonObject().getBoolean(VALUE);
        } else if (value instanceof JsonArray) {
            return transformBoolean(value.asJsonArray().get(0), context);
        } else if (TRUE == value.getValueType()) {
            return true;
        } else if (FALSE == value.getValueType()) {
            return false;
        } else {
            context.problem().unexpectedType()
                    .expected(OBJECT)
                    .expected(ARRAY)
                    .actual(value.getValueType())
                    .report();
            return false;
        }
    }

    /**
     * Transforms a JsonValue to the desired output type. The result can be a single instance or a list of that type, depending on whether the given value is a
     * JsonObject or a JsonArray. The result function is applied to every instance. If the value parameter is neither of type JsonObject nor JsonArray, a problem
     * is reported to the context.
     *
     * @param value          the value to transform
     * @param type           the desired result type
     * @param resultFunction the function to apply to the transformation result, if it is a single instance
     * @param context        the transformer context
     * @param <T>            the desired result type
     */
    protected <T> void transformArrayOrObject(JsonValue value, Class<T> type, Consumer<T> resultFunction, TransformerContext context) {
        if (value instanceof JsonArray) {
            var jsonArray = (JsonArray) value;
            jsonArray.stream().map(entry -> context.transform(entry, type)).forEach(resultFunction);
        } else if (value instanceof JsonObject) {
            var result = context.transform(value, type);
            resultFunction.accept(result);
        } else {
            var problem = context.problem().unexpectedType().expected(OBJECT).expected(ARRAY);
            if (value != null) {
                problem.actual(value.getValueType());
            }
            problem.report();
        }
    }

    /**
     * Transforms a JsonValue to a List. If the value parameter is not of type JsonArray, a problem is reported to the context.
     *
     * @param value   the value to transform
     * @param type    the desired result type
     * @param context the transformer context
     * @param <T>     the desired result type
     * @return the transformed list, null if the value type was not valid.
     */
    @Nullable
    protected <T> List<T> transformArray(JsonValue value, Class<T> type, TransformerContext context) {
        if (value instanceof JsonObject) {
            var transformed = context.transform(value.asJsonObject(), type);
            if (transformed == null) {
                context.problem().unexpectedType()
                        .type(OBJECT)
                        .actual(type)
                        .report();
                return emptyList();
            }
            return List.of(transformed);
        } else if (value instanceof JsonArray) {
            return value.asJsonArray().stream()
                    .map(entry -> context.transform(entry, type))
                    .collect(toList());
        } else {
            var problem = context.problem().unexpectedType().expected(OBJECT).expected(ARRAY);
            if (value != null) {
                problem.actual(value.getValueType());
            }
            problem.report();
            return null;
        }
    }

    /**
     * Transforms a JsonValue to the desired output type. If the value parameter is neither of type JsonObject nor JsonArray, a problem is reported to the context.
     * <p>
     * This method reports errors it encounters.
     *
     * @param value   the value to transform
     * @param type    the desired result type
     * @param context the transformer context
     * @param <T>     the desired result type
     * @return the transformed list
     */
    protected <T> T transformObject(JsonValue value, Class<T> type, TransformerContext context) {
        if (value instanceof JsonArray) {
            return value.asJsonArray().stream()
                    .map(entry -> context.transform(entry, type))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> {
                        context.problem().unexpectedType()
                                .type(ARRAY)
                                .expected(type)
                                .report();
                        return null;
                    });
        } else if (value instanceof JsonObject) {
            return context.transform(value, type);
        } else {
            var problem = context.problem().unexpectedType()
                    .type(type)
                    .expected(OBJECT)
                    .expected(ARRAY);
            if (value != null) {
                problem.actual(value.getValueType());
            }
            problem.report();
            return null;
        }
    }

    /**
     * Returns the {@code @id} of the JSON object.
     */
    protected String nodeId(JsonValue object) {
        if (object instanceof JsonArray) {
            return nodeId(object.asJsonArray().get(0));
        } else {
            var id = object.asJsonObject().get(ID);
            return id instanceof JsonString ? ((JsonString) id).getString() : null;
        }
    }

    /**
     * Returns the @value of the JSON object.
     */
    protected String nodeValue(JsonValue object) {
        if (object instanceof JsonArray) {
            return nodeValue(object.asJsonArray().get(0));
        } else {
            var value = object.asJsonObject().get(VALUE);
            return value instanceof JsonString ? ((JsonString) value).getString() : null;
        }
    }

    /**
     * Returns the {@code @value} of the JSON object.
     */
    protected String nodeValue(JsonValue value, TransformerContext context) {
        if (value instanceof JsonString) {
            var jsonString = (JsonString) value;
            return jsonString.getString();
        } else if (value instanceof JsonObject) {
            var object = (JsonObject) value;
            return object.getString(VALUE);
        } else if (value instanceof JsonArray) {
            var array = (JsonArray) value;
            return nodeValue(array.get(0), context);
        } else {
            context.problem()
                    .invalidProperty()
                    .property(VALUE)
                    .value(value != null ? value.toString() : null)
                    .report();
            return null;
        }
    }

    /**
     * Returns the {@code @type} of the JSON object. If more than one type is specified, this method will return the first.
     */
    protected String nodeType(JsonObject object, TransformerContext context) {
        var typeNode = object.get(TYPE);
        if (typeNode == null) {
            context.problem()
                    .missingProperty()
                    .property(TYPE)
                    .report();
            return null;
        }

        if (typeNode instanceof JsonString) {
            return ((JsonString) typeNode).getString();
        } else if (typeNode instanceof JsonArray) {
            var array = (JsonArray) typeNode;
            if (array.isEmpty()) {
                return null;
            }
            var typeValue = array.get(0); // a note can have more than one type, take the first
            if (!(typeValue instanceof JsonString)) {
                var problem = context.problem().unexpectedType().property(TYPE).expected(STRING);
                if (typeValue != null) {
                    problem.actual(typeValue.getValueType());
                }
                problem.report();
                return null;
            }
            return ((JsonString) typeValue).getString();
        }

        context.problem()
                .unexpectedType()
                .property(TYPE)
                .actual(typeNode.getValueType())
                .expected(STRING)
                .expected(ARRAY)
                .report();
        return null;
    }

    /**
     * Tries to return the instance given by a supplier (a builder's build method). If this fails due to validation errors, e.g. a required property is missing,
     * reports a problem to the context.
     *
     * @param builder the supplier
     * @param context the context
     * @return the instance or null
     */
    protected <T> T builderResult(Supplier<T> builder, TransformerContext context) {
        try {
            return builder.get();
        } catch (Exception e) {
            context.reportProblem(format("Failed to construct instance: %s", e.getMessage()));
            return null;
        }
    }
}
