/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.edr.store.index.sql.schema.postgres;

import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.edr.store.index.sql.schema.EndpointDataReferenceEntryStatements;
import org.eclipse.edc.sql.translation.TranslationMapping;

/**
 * Maps fields of a {@link EndpointDataReferenceEntry} onto the
 * corresponding SQL schema (= column names)
 */
public class EndpointDataReferenceEntryMapping extends TranslationMapping {
    public EndpointDataReferenceEntryMapping(EndpointDataReferenceEntryStatements statements) {
        add("assetId", statements.getAssetIdColumn());
        add("agreementId", statements.getAgreementIdColumn());
        add("transferProcessId", statements.getTransferProcessIdColumn());
        add("providerId", statements.getProviderIdColumn());
        add("contractNegotiationId", statements.getContractNegotiationIdColumn());
        add("createdAt", statements.getCreatedAtColumn());
    }
}
