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

package org.eclipse.edc.spi.dataaddress;

import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;

public interface HttpDataAddressSchema {

    /**
     * DataAddress type
     */
    String HTTP_DATA_TYPE = "HttpData";

    /**
     * Base url
     */
    String BASE_URL = EDC_NAMESPACE + "baseUrl";

}
