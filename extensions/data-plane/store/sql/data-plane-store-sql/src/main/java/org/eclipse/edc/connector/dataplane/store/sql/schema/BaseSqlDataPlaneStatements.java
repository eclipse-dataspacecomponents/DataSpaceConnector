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

package org.eclipse.edc.connector.dataplane.store.sql.schema;

import org.eclipse.edc.connector.dataplane.store.sql.schema.postgres.DataPlaneMapping;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.translation.SqlOperatorTranslator;
import org.eclipse.edc.sql.translation.SqlQueryStatement;

import static java.lang.String.format;

public class BaseSqlDataPlaneStatements implements DataPlaneStatements {

    protected final SqlOperatorTranslator operatorTranslator;

    public BaseSqlDataPlaneStatements(SqlOperatorTranslator operatorTranslator) {
        this.operatorTranslator = operatorTranslator;
    }

    @Override
    public String getInsertTemplate() {
        return executeStatement()
                .column(getIdColumn())
                .column(getStateColumn())
                .column(getCreatedAtColumn())
                .column(getUpdatedAtColumn())
                .column(getStateCountColumn())
                .column(getStateTimestampColumn())
                .jsonColumn(getTraceContextColumn())
                .column(getErrorDetailColumn())
                .column(getCallbackAddressColumn())
                .jsonColumn(getSourceColumn())
                .jsonColumn(getDestinationColumn())
                .jsonColumn(getPropertiesColumn())
                .column(getFlowTypeColumn())
                .column(getTransferTypeDestinationColumn())
                .insertInto(getDataPlaneTable());
    }

    @Override
    public String getUpdateTemplate() {
        return executeStatement()
                .column(getStateColumn())
                .column(getUpdatedAtColumn())
                .column(getStateCountColumn())
                .column(getStateTimestampColumn())
                .jsonColumn(getTraceContextColumn())
                .column(getErrorDetailColumn())
                .column(getCallbackAddressColumn())
                .jsonColumn(getSourceColumn())
                .jsonColumn(getDestinationColumn())
                .jsonColumn(getPropertiesColumn())
                .column(getFlowTypeColumn())
                .column(getTransferTypeDestinationColumn())
                .update(getDataPlaneTable(), getIdColumn());
    }

    @Override
    public String getSelectTemplate() {
        return "SELECT * FROM %s".formatted(getDataPlaneTable());
    }

    @Override
    public SqlQueryStatement createQuery(QuerySpec querySpec) {
        return new SqlQueryStatement(getSelectTemplate(), querySpec, new DataPlaneMapping(this), operatorTranslator);
    }

    @Override
    public String getDeleteLeaseTemplate() {
        return executeStatement().delete(getLeaseTableName(), getLeaseIdColumn());
    }

    @Override
    public String getInsertLeaseTemplate() {
        return executeStatement()
                .column(getLeaseIdColumn())
                .column(getLeasedByColumn())
                .column(getLeasedAtColumn())
                .column(getLeaseDurationColumn())
                .insertInto(getLeaseTableName());
    }

    @Override
    public String getUpdateLeaseTemplate() {
        return executeStatement()
                .column(getLeaseIdColumn())
                .update(getDataPlaneTable(), getIdColumn());
    }

    @Override
    public String getFindLeaseByEntityTemplate() {
        return format("SELECT * FROM %s  WHERE %s = (SELECT lease_id FROM %s WHERE %s=? )",
                getLeaseTableName(), getLeaseIdColumn(), getDataPlaneTable(), getIdColumn());
    }
}
