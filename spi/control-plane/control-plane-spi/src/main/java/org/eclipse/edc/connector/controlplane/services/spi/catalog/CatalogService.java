/*
 *  Copyright (c) 2020 - 2022 Bayerische Motoren Werke Aktiengesellschaft
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft - initial API and implementation
 *
 */

package org.eclipse.edc.connector.controlplane.services.spi.catalog;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.StatusResult;

import java.util.concurrent.CompletableFuture;

public interface CatalogService {

    /**
     * Return the catalog of the passed provider url.
     *
     * @param counterPartyAddress the url of the provider.
     * @param protocol            the protocol id string.
     * @param querySpec           the {@link QuerySpec} object.
     * @param additionalScopes    optional list of additional scope values that are intended for use with the IAM subsystem
     * @return the provider's catalog
     */
    CompletableFuture<StatusResult<byte[]>> requestCatalog(String counterPartyId, String counterPartyAddress, String protocol, QuerySpec querySpec, String... additionalScopes);

    /**
     * Return the dataset
     *
     * @param id                  the dataset id.
     * @param counterPartyAddress the url of the provider.
     * @param protocol            the protocol.
     * @return the provider dataset.
     */
    CompletableFuture<StatusResult<byte[]>> requestDataset(String id, String counterPartyId, String counterPartyAddress, String protocol);
}
