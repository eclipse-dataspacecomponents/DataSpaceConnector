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

package org.eclipse.edc.connector.controlplane.api.management.catalog;

import jakarta.json.JsonObject;
import jakarta.ws.rs.container.AsyncResponse;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequest;
import org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequest;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.ValidationFailureException;

import static org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequest.CATALOG_REQUEST_TYPE;
import static org.eclipse.edc.connector.controlplane.catalog.spi.DatasetRequest.DATASET_REQUEST_TYPE;


public abstract class BaseCatalogApiController {

    private final CatalogService service;
    private final TypeTransformerRegistry transformerRegistry;
    private final JsonObjectValidatorRegistry validatorRegistry;

    public BaseCatalogApiController(CatalogService service, TypeTransformerRegistry transformerRegistry,
                                    JsonObjectValidatorRegistry validatorRegistry) {
        this.service = service;
        this.transformerRegistry = transformerRegistry;
        this.validatorRegistry = validatorRegistry;
    }

    public void requestCatalog(JsonObject requestBody, AsyncResponse response) {
        validatorRegistry.validate(CATALOG_REQUEST_TYPE, requestBody).orElseThrow(ValidationFailureException::new);

        var request = transformerRegistry.transform(requestBody, CatalogRequest.class)
                .orElseThrow(InvalidRequestException::new);

        var scopes = request.getAdditionalScopes().toArray(new String[0]);
        service.requestCatalog(request.getCounterPartyId(), request.getCounterPartyAddress(), request.getProtocol(), request.getQuerySpec(), scopes)
                .whenComplete((result, throwable) -> {
                    try {
                        response.resume(toResponse(result, throwable));
                    } catch (Throwable mapped) {
                        response.resume(mapped);
                    }
                });
    }

    public void getDataset(JsonObject requestBody, AsyncResponse response) {
        validatorRegistry.validate(DATASET_REQUEST_TYPE, requestBody).orElseThrow(ValidationFailureException::new);

        var request = transformerRegistry.transform(requestBody, DatasetRequest.class)
                .orElseThrow(InvalidRequestException::new);

        service.requestDataset(request.getId(), request.getCounterPartyId(), request.getCounterPartyAddress(), request.getProtocol())
                .whenComplete((result, throwable) -> {
                    try {
                        response.resume(toResponse(result, throwable));
                    } catch (Throwable mapped) {
                        response.resume(mapped);
                    }
                });
    }

    private byte[] toResponse(StatusResult<byte[]> result, Throwable throwable) throws Throwable {
        if (throwable == null) {
            if (result.succeeded()) {
                return result.getContent();
            } else {
                throw new BadGatewayException(result.getFailureDetail());
            }
        } else {
            if (throwable instanceof EdcException || throwable.getCause() instanceof EdcException) {
                throw new BadGatewayException(throwable.getMessage());
            } else {
                throw throwable;
            }
        }
    }

}
