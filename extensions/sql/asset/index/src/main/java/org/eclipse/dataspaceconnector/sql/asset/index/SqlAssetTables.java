/*
 *  Copyright (c) 2022 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial API and Implementation
 *
 */

package org.eclipse.dataspaceconnector.sql.asset.index;

public interface SqlAssetTables {
    String getAssetTable();

    String getAssetColumnId();

    String getDataAddressTable();

    String getDataAddressColumnProperties();

    String getAssetPropertyTable();

    String getAssetPropertyColumnName();

    String getAssetPropertyColumnValue();

    String getAssetPropertyColumnType();
}
