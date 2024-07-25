/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
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

package org.eclipse.edc.connector.controlplane.defaults.storage.transferprocess;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.store.InMemoryStatefulEntityStore;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.UUID;
import java.util.stream.Stream;

import static org.eclipse.edc.spi.query.Criterion.criterion;

/**
 * An in-memory, threadsafe process store. This implementation is intended for testing purposes only.
 */
public class InMemoryTransferProcessStore extends InMemoryStatefulEntityStore<TransferProcess> implements TransferProcessStore {

    public InMemoryTransferProcessStore(Clock clock, CriterionOperatorRegistry criterionOperatorRegistry) {
        this(UUID.randomUUID().toString(), clock, criterionOperatorRegistry);
    }

    public InMemoryTransferProcessStore(String leaserId, Clock clock, CriterionOperatorRegistry criterionOperatorRegistry) {
        super(TransferProcess.class, leaserId, clock, criterionOperatorRegistry);
    }

    @Override
    public @Nullable TransferProcess findForCorrelationId(String correlationId) {
        var querySpec = QuerySpec.Builder.newInstance().filter(criterion("correlationId", "=", correlationId)).build();

        return super.findAll(querySpec).findFirst().orElse(null);
    }

    @Override
    public void delete(String id) {
        super.delete(id);
    }

    @Override
    public Stream<TransferProcess> findAll(QuerySpec querySpec) {
        return super.findAll(querySpec);
    }

}
