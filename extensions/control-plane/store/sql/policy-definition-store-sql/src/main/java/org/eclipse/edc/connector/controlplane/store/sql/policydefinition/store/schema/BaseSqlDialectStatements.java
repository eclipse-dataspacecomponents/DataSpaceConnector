/*
 *  Copyright (c) 2022 Microsoft Corporation
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

package org.eclipse.edc.connector.controlplane.store.sql.policydefinition.store.schema;

import org.eclipse.edc.connector.controlplane.store.sql.policydefinition.store.schema.postgres.PolicyDefinitionMapping;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.translation.SqlOperatorTranslator;
import org.eclipse.edc.sql.translation.SqlQueryStatement;

public class BaseSqlDialectStatements implements SqlPolicyStoreStatements {

    protected final SqlOperatorTranslator operatorTranslator;

    public BaseSqlDialectStatements(SqlOperatorTranslator operatorTranslator) {
        this.operatorTranslator = operatorTranslator;
    }

    @Override
    public String getSelectTemplate() {
        return String.format("SELECT * FROM %s",
                getPolicyTable());
    }

    @Override
    public String getInsertTemplate() {
        return executeStatement()
                .column(getPolicyIdColumn())
                .jsonColumn(getPermissionsColumn())
                .jsonColumn(getProhibitionsColumn())
                .jsonColumn(getDutiesColumn())
                .jsonColumn(getProfilesColumn())
                .jsonColumn(getExtensiblePropertiesColumn())
                .column(getInheritsFromColumn())
                .column(getAssignerColumn())
                .column(getAssigneeColumn())
                .column(getTargetColumn())
                .column(getTypeColumn())
                .column(getCreatedAtColumn())
                .jsonColumn(getPrivatePropertiesColumn())
                .insertInto(getPolicyTable());
    }

    @Override
    public String getUpdateTemplate() {
        return executeStatement()
                .jsonColumn(getPermissionsColumn())
                .jsonColumn(getProhibitionsColumn())
                .jsonColumn(getDutiesColumn())
                .jsonColumn(getProfilesColumn())
                .jsonColumn(getExtensiblePropertiesColumn())
                .column(getInheritsFromColumn())
                .column(getAssignerColumn())
                .column(getAssigneeColumn())
                .column(getTargetColumn())
                .column(getTypeColumn())
                .jsonColumn(getPrivatePropertiesColumn())
                .update(getPolicyTable(), getPolicyIdColumn());

    }

    @Override
    public String getDeleteTemplate() {
        return executeStatement().delete(getPolicyTable(), getPolicyIdColumn());
    }

    @Override
    public SqlQueryStatement createQuery(QuerySpec querySpec) {
        return new SqlQueryStatement(getSelectTemplate(), querySpec, new PolicyDefinitionMapping(this), operatorTranslator);
    }
}
