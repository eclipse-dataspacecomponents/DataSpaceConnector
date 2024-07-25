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

package org.eclipse.edc.connector.controlplane.api.management.catalog;

import org.eclipse.edc.connector.controlplane.api.management.catalog.v2.CatalogApiV2Controller;
import org.eclipse.edc.connector.controlplane.api.management.catalog.v3.CatalogApiV3Controller;
import org.eclipse.edc.connector.controlplane.api.management.catalog.v31alpha.CatalogApiV31AlphaController;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequest.CATALOG_REQUEST_TYPE;
import static org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequest.DATASET_REQUEST_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class CatalogApiExtensionTest {

    private final JsonObjectValidatorRegistry validatorRegistry = mock();
    private final WebService webService = mock(WebService.class);

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(JsonObjectValidatorRegistry.class, validatorRegistry);
        context.registerService(WebService.class, webService);
    }

    @Test
    void initiate_shouldRegisterValidators(CatalogApiExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(validatorRegistry).register(eq(CATALOG_REQUEST_TYPE), any());
        verify(validatorRegistry).register(eq(DATASET_REQUEST_TYPE), any());
    }

    @Test
    void initiate_shouldRegisterControllers(CatalogApiExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(webService).registerResource(any(), isA(CatalogApiV2Controller.class));
        verify(webService).registerResource(any(), isA(CatalogApiV3Controller.class));
        verify(webService).registerResource(any(), isA(CatalogApiV31AlphaController.class));
    }
}
