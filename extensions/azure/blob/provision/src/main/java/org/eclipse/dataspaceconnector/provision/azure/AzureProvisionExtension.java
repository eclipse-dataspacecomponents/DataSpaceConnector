/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
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

package org.eclipse.dataspaceconnector.provision.azure;

import net.jodah.failsafe.RetryPolicy;
import org.eclipse.dataspaceconnector.common.azure.BlobStoreApi;
import org.eclipse.dataspaceconnector.common.azure.BlobStoreApiImpl;
import org.eclipse.dataspaceconnector.provision.azure.blob.ObjectContainerProvisionedResource;
import org.eclipse.dataspaceconnector.provision.azure.blob.ObjectContainerStatusChecker;
import org.eclipse.dataspaceconnector.provision.azure.blob.ObjectStorageDefinitionConsumerGenerator;
import org.eclipse.dataspaceconnector.provision.azure.blob.ObjectStorageProvisioner;
import org.eclipse.dataspaceconnector.provision.azure.blob.ObjectStorageResourceDefinition;
import org.eclipse.dataspaceconnector.schema.azure.AzureBlobStoreSchema;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.transfer.provision.ProvisionManager;
import org.eclipse.dataspaceconnector.spi.transfer.provision.ResourceManifestGenerator;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.StatusCheckerRegistry;

import java.util.Set;

/**
 * Provides data transfer {@link org.eclipse.dataspaceconnector.spi.transfer.provision.Provisioner}s backed by Azure services.
 */
public class AzureProvisionExtension implements ServiceExtension {

    @Override
    public String name() {
        return "Azure Provision";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {

        var monitor = context.getMonitor();
        var provisionManager = context.getService(ProvisionManager.class);

        context.registerService(BlobStoreApi.class, new BlobStoreApiImpl(context.getService(Vault.class)));

        @SuppressWarnings("unchecked") var retryPolicy = (RetryPolicy<Object>) context.getService(RetryPolicy.class);
        BlobStoreApi blobStoreApi = context.getService(BlobStoreApi.class);
        provisionManager.register(new ObjectStorageProvisioner(retryPolicy, monitor, blobStoreApi));

        // register the generator
        var manifestGenerator = context.getService(ResourceManifestGenerator.class);
        manifestGenerator.registerConsumerGenerator(new ObjectStorageDefinitionConsumerGenerator());

        var statusCheckerReg = context.getService(StatusCheckerRegistry.class);
        statusCheckerReg.register(AzureBlobStoreSchema.TYPE, new ObjectContainerStatusChecker(blobStoreApi, retryPolicy));

        registerTypes(context.getTypeManager());
    }

    @Override
    public Set<String> requires() {
        return Set.of("edc:retry-policy");
    }

    @Override
    public Set<String> provides() {
        return Set.of("dataspaceconnector:blobstoreapi");
    }

    private void registerTypes(TypeManager typeManager) {
        typeManager.registerTypes(ObjectContainerProvisionedResource.class, ObjectStorageResourceDefinition.class, AzureSasToken.class);
    }

}
