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

package org.eclipse.edc.spi.result;

import java.util.List;

import static java.lang.String.format;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.ALREADY_EXISTS;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.NOT_FOUND;

/**
 * Specialized {@link Result} class to indicate the success or failure of an interaction with the persistence layer.
 *
 * @param <T> The type of content
 */
public class StoreResult<T> extends AbstractResult<T, StoreFailure> {

    private static final String ASSET_NOT_FOUND = "Asset/DataAddress with ID %s not found";
    private static final String ASSET_EXISTS = "Asset with ID %s already exists";

    protected StoreResult(T content, StoreFailure failure) {
        super(content, failure);
    }

    public static <T> StoreResult<T> success(T content) {
        return new StoreResult<>(content, null);
    }

    public static <T> StoreResult<T> alreadyExists(String assetId) {
        return new StoreResult<>(null, new StoreFailure(List.of(format(ASSET_EXISTS, assetId)), ALREADY_EXISTS));
    }

    public static <T> StoreResult<T> notFound(String assetId) {
        return new StoreResult<>(null, new StoreFailure(List.of(format(ASSET_NOT_FOUND, assetId)), NOT_FOUND));
    }


    public static <T> StoreResult<T> success() {
        return StoreResult.success(null);
    }

    public StoreFailure.Reason reason() {
        return getFailure().getReason();
    }
}
