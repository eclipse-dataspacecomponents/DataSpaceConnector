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

package org.eclipse.edc.connector.dataplane.azure.datafactory;

import org.eclipse.edc.azure.blob.AzureBlobStoreSchema;
import org.eclipse.edc.ms.dataverse.MicrosoftDataverseSchema;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowRequest;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

import static java.lang.String.format;
import static org.eclipse.edc.azure.blob.validator.AzureStorageValidator.validateAccountName;
import static org.eclipse.edc.azure.blob.validator.AzureStorageValidator.validateBlobName;
import static org.eclipse.edc.azure.blob.validator.AzureStorageValidator.validateContainerName;
import static org.eclipse.edc.azure.blob.validator.AzureStorageValidator.validateKeyName;
import static org.eclipse.edc.ms.dataverse.validator.MicrosoftDataverseValidator.validateEntityName;
import static org.eclipse.edc.ms.dataverse.validator.MicrosoftDataverseValidator.validateServiceUri;
import static org.eclipse.edc.ms.dataverse.validator.MicrosoftDataverseValidator.validateServicePrincipalId;

/**
 * Validator for {@link AzureDataFactoryTransferService}.
 */
class AzureDataFactoryTransferRequestValidator {

    /**
     * Returns true if this service can transfer the request.
     */
    boolean canHandle(DataFlowRequest request) {
        DataFactoryPipelineType pipelineType = DataFactoryPipelineType.fromRequest(request);
        return  pipelineType != null;
    }

    /**
     * Returns true if the request is valid.
     */
    @NotNull Result<Boolean> validate(DataFlowRequest request) {
        try {
            validateSource(request.getSourceDataAddress());
            validateDestination(request.getDestinationDataAddress());
        } catch (IllegalArgumentException e) {
            return Result.failure(e.getMessage());
        }
        return Result.success(true);
    }

    private void validateSource(DataAddress source) {
        switch (source.getType()) {
            case AzureBlobStoreSchema.TYPE:
                var properties = new HashMap<>(source.getProperties());
                validateBlobName(properties.remove(AzureBlobStoreSchema.BLOB_NAME));
                validateBlobProperties(properties);
                break;
            default:
        }
    }

    private void validateDestination(DataAddress destination) {
        var properties = new HashMap<>(destination.getProperties());
        switch (destination.getType()) {
            case MicrosoftDataverseSchema.TYPE:
                validateDataverseProperties(properties);
                break;
            case AzureBlobStoreSchema.TYPE:
                validateBlobProperties(properties);
            default:
        }
    }

    private void validateBlobProperties(HashMap<String, String> properties) {
        validateAccountName(properties.remove(AzureBlobStoreSchema.ACCOUNT_NAME));
        validateContainerName(properties.remove(AzureBlobStoreSchema.CONTAINER_NAME));
        validateKeyName(properties.remove(DataAddress.KEY_NAME));
        properties.keySet().stream().filter(k -> !DataAddress.TYPE.equals(k)).findFirst().ifPresent(k -> {
            throw new IllegalArgumentException(format("Unexpected property %s", k));
        });
    }

    private void validateDataverseProperties(HashMap<String, String> properties) {
        validateEntityName(properties.remove(MicrosoftDataverseSchema.ENTITY_NAME));
        validateServiceUri(properties.remove(MicrosoftDataverseSchema.SERVICE_URI));
        validateServicePrincipalId(properties.remove(MicrosoftDataverseSchema.SERVICE_PRINCIPAL_ID));
        validateKeyName(properties.remove(DataAddress.KEY_NAME));
        properties.keySet().stream().filter(k -> !DataAddress.TYPE.equals(k)).findFirst().ifPresent(k -> {
            throw new IllegalArgumentException(format("Unexpected property %s", k));
        });
    }
}
