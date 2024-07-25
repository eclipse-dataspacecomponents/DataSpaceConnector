/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.edc.connector.controlplane.api.management.catalog;

import org.eclipse.edc.connector.controlplane.api.management.catalog.transform.JsonObjectToCatalogRequestTransformer;
import org.eclipse.edc.connector.controlplane.api.management.catalog.transform.JsonObjectToDatasetRequestTransformer;
import org.eclipse.edc.connector.controlplane.api.management.catalog.v2.CatalogApiV2Controller;
import org.eclipse.edc.connector.controlplane.api.management.catalog.v3.CatalogApiV3Controller;
import org.eclipse.edc.connector.controlplane.api.management.catalog.v31alpha.CatalogApiV31AlphaController;
import org.eclipse.edc.connector.controlplane.api.management.catalog.validation.CatalogRequestValidator;
import org.eclipse.edc.connector.controlplane.api.management.catalog.validation.DatasetRequestValidator;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import static org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequest.CATALOG_REQUEST_TYPE;
import static org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequest.DATASET_REQUEST_TYPE;

@Extension(value = CatalogApiExtension.NAME)
public class CatalogApiExtension implements ServiceExtension {

    public static final String NAME = "Management API: Catalog";

    @Inject
    private WebService webService;

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Inject
    private CatalogService service;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        transformerRegistry.register(new JsonObjectToCatalogRequestTransformer());
        transformerRegistry.register(new JsonObjectToDatasetRequestTransformer());

        var managementApiTransformerRegistry = transformerRegistry.forContext("management-api");
        webService.registerResource(ApiContext.MANAGEMENT, new CatalogApiV2Controller(service, managementApiTransformerRegistry, validatorRegistry, context.getMonitor()));
        webService.registerResource(ApiContext.MANAGEMENT, new CatalogApiV3Controller(service, managementApiTransformerRegistry, validatorRegistry));
        webService.registerResource(ApiContext.MANAGEMENT, new CatalogApiV31AlphaController(service, managementApiTransformerRegistry, validatorRegistry));

        validatorRegistry.register(CATALOG_REQUEST_TYPE, CatalogRequestValidator.instance(criterionOperatorRegistry));
        validatorRegistry.register(DATASET_REQUEST_TYPE, DatasetRequestValidator.instance());
    }
}
