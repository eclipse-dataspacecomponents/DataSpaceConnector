/*
 *  Copyright (c) 2021 Daimler TSS GmbH
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

package org.eclipse.edc.sql.pool.commons;

import org.eclipse.edc.runtime.metamodel.annotation.Setting;

interface CommonsConnectionPoolConfigKeys {

    @Setting(required = false, value = "The maximum number of idling connections maintained by the pool")
    String POOL_MAX_IDLE_CONNECTIONS = "pool.maxIdleConnections";

    @Setting(required = false, value = "The maximum number of total connections maintained by the pool")
    String POOL_MAX_TOTAL_CONNECTIONS = "pool.maxTotalConnections";

    @Setting(required = false, value = "The minimum number of idling connections maintained by the pool")
    String POOL_MIN_IDLE_CONNECTIONS = "pool.minIdleConnections";

    @Setting(required = false, value = "Boolean flag that specifies whether connections obtained from the pool will be validated")
    String POOL_TEST_CONNECTION_ON_BORROW = "pool.testConnectionOnBorrow";

    @Setting(required = false, value = "Boolean flag that specifies whether established connections should be validated")
    String POOL_TEST_CONNECTION_ON_CREATE = "pool.testConnectionOnCreate";

    @Setting(required = false, value = "Boolean flag that determines if connection validation should occur after a connection has been returned to the connection pool")
    String POOL_TEST_CONNECTION_ON_RETURN = "pool.testConnectionOnReturn";

    @Setting(required = false, value = "Boolean flag to define whether idle connections will be validated.")
    String POOL_TEST_CONNECTION_WHILE_IDLE = "pool.testConnectionWhileIdle";

    @Setting(required = false, value = "Boolean flag to specify the SQL query that will be used to validate connections in the pool")
    String POOL_TEST_QUERY = "pool.testQuery";

    @Setting(required = true, value = "The JDBC URL of the DB that the connection pool connects to")
    String URL = "url";
}
