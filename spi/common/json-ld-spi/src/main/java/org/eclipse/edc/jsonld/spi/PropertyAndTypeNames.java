/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.edc.jsonld.spi;

import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Collection of DCAT, DCT and ODRL type and attribute names.
 */
public interface PropertyAndTypeNames {
    //DCAT
    String DCAT_CATALOG_TYPE = DCAT_SCHEMA + "Catalog";
    String DCAT_DATASET_TYPE = DCAT_SCHEMA + "Dataset";
    String DCAT_DISTRIBUTION_TYPE = DCAT_SCHEMA + "Distribution";
    String DCAT_DATA_SERVICE_TYPE = DCAT_SCHEMA + "DataService";
    String DCAT_DATA_SERVICE_ATTRIBUTE = DCAT_SCHEMA + "service";
    String DCAT_DATASET_ATTRIBUTE = DCAT_SCHEMA + "dataset";
    String DCAT_DISTRIBUTION_ATTRIBUTE = DCAT_SCHEMA + "distribution";
    String DCAT_ACCESS_SERVICE_ATTRIBUTE = DCAT_SCHEMA + "accessService";
    String DCAT_ENDPOINT_URL_ATTRIBUTE = DCAT_SCHEMA + "endpointUrl";
    String DCAT_ENDPOINT_DESCRIPTION_ATTRIBUTE = DCAT_SCHEMA + "endpointDescription";

    //EDC
    String EDC_CREATED_AT = EDC_NAMESPACE + "createdAt";

    //DCT
    @Deprecated(since = "0.5.1")
    String DEPRECATED_DCT_FORMAT_ATTRIBUTE = "https://purl.org/dc/terms/format";
    String DCT_FORMAT_ATTRIBUTE = DCT_SCHEMA + "format";
    @Deprecated(since = "0.6.2")
    String DCT_TERMS_ATTRIBUTE = DCT_SCHEMA + "terms";
    @Deprecated(since = "0.6.2")
    String DCT_ENDPOINT_URL_ATTRIBUTE = DCT_SCHEMA + "endpointUrl";

    String ODRL_POLICY_ATTRIBUTE = ODRL_SCHEMA + "hasPolicy";
    String ODRL_POLICY_TYPE_SET = ODRL_SCHEMA + "Set";
    String ODRL_POLICY_TYPE_OFFER = ODRL_SCHEMA + "Offer";
    String ODRL_POLICY_TYPE_AGREEMENT = ODRL_SCHEMA + "Agreement";
    String ODRL_CONSTRAINT_TYPE = ODRL_SCHEMA + "Constraint";
    String ODRL_LOGICAL_CONSTRAINT_TYPE = ODRL_SCHEMA + "LogicalConstraint";
    String ODRL_TARGET_ATTRIBUTE = ODRL_SCHEMA + "target";
    String ODRL_ASSIGNEE_ATTRIBUTE = ODRL_SCHEMA + "assignee";
    String ODRL_ASSIGNER_ATTRIBUTE = ODRL_SCHEMA + "assigner";
    String ODRL_PERMISSION_ATTRIBUTE = ODRL_SCHEMA + "permission";
    String ODRL_PROHIBITION_ATTRIBUTE = ODRL_SCHEMA + "prohibition";
    String ODRL_OBLIGATION_ATTRIBUTE = ODRL_SCHEMA + "obligation";
    String ODRL_ACTION_ATTRIBUTE = ODRL_SCHEMA + "action";
    @Deprecated(since = "0.7.0")
    String ODRL_ACTION_TYPE_ATTRIBUTE = ODRL_SCHEMA + "type";
    String ODRL_CONSEQUENCE_ATTRIBUTE = ODRL_SCHEMA + "consequence";
    String ODRL_REMEDY_ATTRIBUTE = ODRL_SCHEMA + "remedy";
    String ODRL_INCLUDED_IN_ATTRIBUTE = ODRL_SCHEMA + "includedIn";
    String ODRL_REFINEMENT_ATTRIBUTE = ODRL_SCHEMA + "refinement";
    String ODRL_CONSTRAINT_ATTRIBUTE = ODRL_SCHEMA + "constraint";
    String ODRL_LEFT_OPERAND_ATTRIBUTE = ODRL_SCHEMA + "leftOperand";
    String ODRL_OPERATOR_TYPE = ODRL_SCHEMA + "Operator";
    String ODRL_OPERATOR_ATTRIBUTE = ODRL_SCHEMA + "operator";
    String ODRL_RIGHT_OPERAND_ATTRIBUTE = ODRL_SCHEMA + "rightOperand";
    String ODRL_DUTY_ATTRIBUTE = ODRL_SCHEMA + "duty";
    String ODRL_AND_CONSTRAINT_ATTRIBUTE = ODRL_SCHEMA + "and";
    String ODRL_AND_SEQUENCE_CONSTRAINT_ATTRIBUTE = ODRL_SCHEMA + "andSequence";
    String ODRL_OR_CONSTRAINT_ATTRIBUTE = ODRL_SCHEMA + "or";
    String ODRL_XONE_CONSTRAINT_ATTRIBUTE = ODRL_SCHEMA + "xone";
    String ODRL_USE_ACTION_ATTRIBUTE = ODRL_SCHEMA + "use";
    String ODRL_PROFILE_ATTRIBUTE = ODRL_SCHEMA + "profile";

    String DSPACE_PROPERTY_PARTICIPANT_ID = DSPACE_SCHEMA + "participantId";
}
