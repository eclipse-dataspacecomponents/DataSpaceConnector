/*
 *  Copyright (c) 2022 ZF Friedrichshafen AG
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       ZF Friedrichshafen AG - Initial API and Implementation
 *
 */

package org.eclipse.dataspaceconnector.sql.policy.store;

import org.eclipse.dataspaceconnector.spi.EdcSetting;

/**
 * Defines configuration keys used by the SqlPolicyStoreServiceExtension.
 */
public interface ConfigurationKeys {

    /**
     * Name of the datasource to use for accessing policies.
     */
    @EdcSetting(required = true)
    String DATASOURCE_SETTING_NAME = "edc.datasource.policy.name";
}
