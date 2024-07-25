/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.controlplane.services.spi.asset;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;

import java.util.List;

public interface AssetService {

    /**
     * Returns an asset by its id
     *
     * @param assetId id of the asset
     * @return the asset, null if it's not found
     */
    Asset findById(String assetId);

    /**
     * Search Assets
     *
     * @param query the query
     * @return the collection of assets that matches the query
     */
    ServiceResult<List<Asset>> search(QuerySpec query);

    /**
     * Create an asset
     *
     * @param asset       the asset
     * @return successful result if the asset is created correctly, failure otherwise
     */
    ServiceResult<Asset> create(Asset asset);

    /**
     * Delete an asset
     *
     * @param assetId the id of the asset to be deleted
     * @return successful result if the asset is deleted correctly, failure otherwise
     */
    ServiceResult<Asset> delete(String assetId);

    /**
     * Updates an asset. If the asset does not yet exist, {@link ServiceResult#notFound(String)} will be returned.
     *
     * @param asset The content of the Asset. Note that {@link Asset#getId()} will be ignored, rather the separately supplied ID is used
     * @return successful if updated, a failure otherwise.
     */
    ServiceResult<Asset> update(Asset asset);

}
