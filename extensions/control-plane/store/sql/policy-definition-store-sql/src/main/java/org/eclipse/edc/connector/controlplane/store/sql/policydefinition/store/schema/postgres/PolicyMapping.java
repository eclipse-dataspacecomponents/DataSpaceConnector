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

package org.eclipse.edc.connector.controlplane.store.sql.policydefinition.store.schema.postgres;

import org.eclipse.edc.connector.controlplane.store.sql.policydefinition.store.schema.SqlPolicyStoreStatements;
import org.eclipse.edc.sql.translation.JsonFieldTranslator;
import org.eclipse.edc.sql.translation.TranslationMapping;

public class PolicyMapping extends TranslationMapping {
    public PolicyMapping(SqlPolicyStoreStatements statements) {
        add("permissions", new JsonFieldTranslator(PostgresDialectStatements.PERMISSIONS_ALIAS));
        add("prohibitions", new JsonFieldTranslator(PostgresDialectStatements.PROHIBITIONS_ALIAS));
        add("obligations", new JsonFieldTranslator(PostgresDialectStatements.OBLIGATIONS_ALIAS));
        add("profiles", new JsonFieldTranslator(PostgresDialectStatements.PROFILES_ALIES));
        add("extensibleProperties", new JsonFieldTranslator(PostgresDialectStatements.EXT_PROPERTIES_ALIAS));
        add("inheritsFrom", statements.getInheritsFromColumn());
        add("assigner", statements.getAssignerColumn());
        add("assignee", statements.getAssigneeColumn());
        add("target", statements.getTargetColumn());
        add("type", statements.getTypeColumn());
    }
}
