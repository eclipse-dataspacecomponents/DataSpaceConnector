/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
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

package org.eclipse.edc.connector.dataplane.selector.spi;

import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates;
import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main interaction interface for an EDC runtime (=control plane) to communicate with the DPF selector.
 */
@ExtensionPoint
public interface DataPlaneSelectorService {

    String DEFAULT_STRATEGY = "random";

    /**
     * Returns all {@link DataPlaneInstance}s known in the system
     */
    ServiceResult<List<DataPlaneInstance>> getAll();

    /**
     * Select the {@link DataPlaneInstance} that can handle the source and the transferType using the passed strategy
     *
     * @param source            the source.
     * @param transferType      the transfer type.
     * @param selectionStrategy the selection strategy.
     * @return the DataPlaneInstance, null if not found.
     */
    ServiceResult<DataPlaneInstance> select(DataAddress source, String transferType, @Nullable String selectionStrategy);

    /**
     * Add a data plane instance
     */
    ServiceResult<Void> addInstance(DataPlaneInstance instance);

    /**
     * Delete a Data Plane instance.
     *
     * @param instanceId the instance id.
     * @return successful result if operation completed, failure otherwise.
     */
    ServiceResult<Void> delete(String instanceId);

    /**
     * Unregister a Data Plane instance. The state will transition to {@link DataPlaneInstanceStates#UNREGISTERED}.
     *
     * @param instanceId the instance id.
     * @return successful result if operation completed, failure otherwise.
     */
    ServiceResult<Void> unregister(String instanceId);

    /**
     * Find a Data Plane instance by id.
     *
     * @param id the id.
     * @return the {@link DataPlaneInstance} if operation is successful, failure otherwise.
     */
    ServiceResult<DataPlaneInstance> findById(String id);

    /**
     * Selects the {@link DataPlaneInstance} that can handle a source and destination {@link DataAddress} using the configured
     * strategy.
     *
     * @deprecated please use the one that passes the transferType
     */
    @Deprecated(since = "0.6.3")
    default DataPlaneInstance select(DataAddress source, DataAddress destination) {
        return select(source, destination, "random");
    }

    /**
     * Selects the {@link DataPlaneInstance} that can handle a source and destination {@link DataAddress} using the passed
     * strategy.
     *
     * @deprecated please use the one that passes the transferType
     */
    @Deprecated(since = "0.6.3")
    default DataPlaneInstance select(DataAddress source, DataAddress destination, String selectionStrategy) {
        return select(source, destination, selectionStrategy, null);
    }

    /**
     * Selects the {@link DataPlaneInstance} that can handle a source and destination {@link DataAddress} using the passed
     * strategy and the optional transferType.
     *
     * @deprecated please use {@link #select(DataAddress, String, String)}.
     */
    @Deprecated(since = "0.6.4")
    default DataPlaneInstance select(DataAddress source, DataAddress destination, @Nullable String selectionStrategy, @Nullable String transferType) {
        var selection = select(source, transferType, selectionStrategy);
        if (selection.succeeded()) {
            return selection.getContent();
        } else {
            return null;
        }
    }


}
