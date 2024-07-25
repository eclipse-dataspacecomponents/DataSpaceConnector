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

import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.statement.SqlStatements;
import org.eclipse.edc.sql.translation.SqlQueryStatement;

/**
 * Statement templates and SQL table+column names required for the PolicyStore.
 */
@ExtensionPoint
public interface SqlPolicyStoreStatements extends SqlStatements {

    /**
     * SELECT all statement for policies.
     */
    String getSelectTemplate();

    /**
     * INSERT statement for policy.
     */
    String getInsertTemplate();

    /**
     * UPDATE statement for policy.
     */
    String getUpdateTemplate();

    /**
     * DELETE statement for policies.
     */
    String getDeleteTemplate();


    default String getPolicyTable() {
        return "edc_policydefinitions";
    }

    default String getPolicyIdColumn() {
        return "policy_id";
    }

    default String getPermissionsColumn() {
        return "permissions";
    }

    default String getProhibitionsColumn() {
        return "prohibitions";
    }

    default String getDutiesColumn() {
        return "duties";
    }

    default String getProfilesColumn() {
        return "profiles";
    }

    default String getExtensiblePropertiesColumn() {
        return "extensible_properties";
    }

    default String getInheritsFromColumn() {
        return "inherits_from";
    }

    default String getAssignerColumn() {
        return "assigner";
    }

    default String getAssigneeColumn() {
        return "assignee";
    }

    default String getTargetColumn() {
        return "target";
    }

    default String getTypeColumn() {
        return "policy_type";
    }

    default String getCreatedAtColumn() {
        return "created_at";
    }

    default String getPrivatePropertiesColumn() {
        return "private_properties";
    }

    SqlQueryStatement createQuery(QuerySpec querySpec);
}
