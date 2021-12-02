/*
 *  Copyright (c) 2021 Microsoft Corporation
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

package org.eclipse.dataspaceconnector.contractdefinition.store.memory;

import org.eclipse.dataspaceconnector.spi.contract.offer.store.ContractDefinitionStore;
import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default store implementation used when no extension is configured in a runtime. {@link ContractDefinition}s are stored ephemerally in memory.
 */
public class InMemoryContractDefinitionStore implements ContractDefinitionStore {
    private final Map<String, ContractDefinition> cache = new ConcurrentHashMap<>();

    @Override
    public @NotNull Collection<ContractDefinition> findAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public void save(Collection<ContractDefinition> definitions) {
        definitions.forEach(d -> cache.put(d.getId(), d));
    }

    @Override
    public void save(ContractDefinition definition) {
        cache.put(definition.getId(), definition);
    }

    @Override
    public void update(ContractDefinition definition) {
        save(definition);
    }

    @Override
    public void delete(String id) {
        cache.remove(id);
    }

    @Override
    public void reload() {
        // no-op
    }
}
