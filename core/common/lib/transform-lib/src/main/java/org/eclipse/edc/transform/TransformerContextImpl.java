/*
 *  Copyright (c) 2022 - 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.transform;

import org.eclipse.edc.transform.spi.ProblemBuilder;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class TransformerContextImpl implements TransformerContext {
    private final List<String> problems = new ArrayList<>();
    private final TypeTransformerRegistry registry;
    private final Map<Class<?>, Map<String, AtomicReference<?>>> data = new HashMap<>();

    public TransformerContextImpl(TypeTransformerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    @Override
    public List<String> getProblems() {
        return problems;
    }

    @Override
    public void reportProblem(String problem) {
        problems.add(problem);
    }

    @Override
    public ProblemBuilder problem() {
        return new ProblemBuilder(this);
    }

    @Override
    public <INPUT, OUTPUT> @Nullable OUTPUT transform(INPUT object, Class<OUTPUT> outputType) {
        if (object == null) {
            return null;
        }

        return registry.transformerFor(object, outputType)
                .transform(object, this);
    }

    @Override
    public void setData(Class<?> type, String key, Object value) {
        data.computeIfAbsent(type, t -> new HashMap<>()).put(key, new AtomicReference<>(value));
    }

    @Override
    public Object consumeData(Class<?> type, String key) {
        return Optional.of(type)
                .map(data::get)
                .map(typeMap -> typeMap.get(key))
                .map(reference -> reference.getAndSet(null))
                .orElse(null);
    }

}
