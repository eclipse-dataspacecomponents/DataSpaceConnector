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
package org.eclipse.dataspaceconnector.dataplane.spi.pipeline;

import org.eclipse.dataspaceconnector.dataplane.spi.result.TransferResult;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataFlowRequest;

import java.util.concurrent.CompletableFuture;

/**
 * Transfers data from a source to a sink.
 */
public interface PipelineService {

    /**
     * Transfers data associated with the request.
     */
    CompletableFuture<TransferResult> transfer(DataFlowRequest request);

    /**
     * Transfers data using the supplied data source.
     */
    CompletableFuture<TransferResult> transfer(DataSource source, DataFlowRequest request);

    /**
     * Transfers data using the supplied data sink.
     */
    CompletableFuture<TransferResult> transfer(DataSink sink, DataFlowRequest request);

    /**
     * Registers a factory for creating data sources.
     */
    void registerFactory(DataSourceFactory factory);

    /**
     * Registers a factory for creating data sinks.
     */
    void registerFactory(DataSinkFactory factory);
}
