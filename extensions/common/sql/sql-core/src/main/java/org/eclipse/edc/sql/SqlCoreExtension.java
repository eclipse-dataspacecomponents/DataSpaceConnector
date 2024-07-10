/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.sql;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;

import static java.lang.Integer.parseInt;

@Extension(value = SqlCoreExtension.NAME)
public class SqlCoreExtension implements ServiceExtension {

    public static final String NAME = "SQL Core";

    public static final String DEFAULT_EDC_SQL_FETCH_SIZE = "5000";
    @Setting(value = "Fetch size value used in SQL queries", defaultValue = DEFAULT_EDC_SQL_FETCH_SIZE)
    public static final String EDC_SQL_FETCH_SIZE = "edc.sql.fetch.size";

    @Inject
    private TransactionContext transactionContext;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        if (transactionContext instanceof NoopTransactionContext) {
            throw new EdcException("The EDC SQL implementations cannot be used with a '%s'. Please provide a TransactionContext implementation.".formatted(NoopTransactionContext.class.getName()));
        }
    }

    @Provider
    public QueryExecutor sqlQueryExecutor(ServiceExtensionContext context) {
        var fetchSize = context.getSetting(EDC_SQL_FETCH_SIZE, parseInt(DEFAULT_EDC_SQL_FETCH_SIZE));
        var configuration = new SqlQueryExecutorConfiguration(fetchSize);
        return new SqlQueryExecutor(configuration);
    }

    @Provider(isDefault = true)
    public ConnectionFactory connectionFactory() {
        return new DriverManagerConnectionFactory();
    }
}
