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

package org.eclipse.dataspaceconnector.transfer.store.cosmos;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import net.jodah.failsafe.RetryPolicy;
import org.eclipse.dataspaceconnector.common.string.StringUtils;
import org.eclipse.dataspaceconnector.spi.EdcException;
import org.eclipse.dataspaceconnector.spi.EdcSetting;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.transfer.store.TransferProcessStore;
import org.eclipse.dataspaceconnector.transfer.store.cosmos.model.TransferProcessDocument;

import java.util.ArrayList;
import java.util.Set;

/**
 * Provides an in-memory implementation of the {@link org.eclipse.dataspaceconnector.spi.transfer.store.TransferProcessStore} for testing.
 */
public class CosmosTransferProcessStoreExtension implements ServiceExtension {
    /**
     * The setting for the CosmosDB account name
     */
    @EdcSetting
    private static final String COSMOS_ACCOUNTNAME_SETTING = "edc.cosmos.account.name";
    /**
     * The setting for the name of the database where TransferProcesses will be stored
     */
    @EdcSetting
    private static final String COSMOS_DBNAME_SETTING = "edc.cosmos.database.name";
    @EdcSetting
    private static final String COSMOS_PARTITION_KEY_SETTING = "edc.cosmos.partitionkey";
    private static final String DEFAULT_PARTITION_KEY = "dataspaceconnector";
    private static final String CONTAINER_NAME = "transferprocess";

    @Override
    public String name() {
        return "Cosmos Transfer Process Store";
    }

    @Override
    public Set<String> provides() {
        return Set.of("dataspaceconnector:transferprocessstore");
    }

    @Override
    public Set<String> requires() {
        return Set.of("dataspaceconnector:blobstoreapi", "edc:retry-policy");
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        // configure cosmos db
        var cosmosAccountName = context.getSetting(CosmosTransferProcessStoreExtension.COSMOS_ACCOUNTNAME_SETTING, null);
        if (StringUtils.isNullOrEmpty(cosmosAccountName)) {
            throw new EdcException("'" + CosmosTransferProcessStoreExtension.COSMOS_ACCOUNTNAME_SETTING + "' cannot be null or empty!");
        }
        var cosmosDbName = context.getSetting(CosmosTransferProcessStoreExtension.COSMOS_DBNAME_SETTING, null);
        if (StringUtils.isNullOrEmpty(cosmosDbName)) {
            throw new EdcException("'" + CosmosTransferProcessStoreExtension.COSMOS_DBNAME_SETTING + "' cannot be null or empty!");
        }

        // get cosmos db access key
        var vault = context.getService(Vault.class);
        var accountKey = vault.resolveSecret(cosmosAccountName);
        if (StringUtils.isNullOrEmpty(accountKey)) {
            throw new EdcException("No credentials found in vault for Cosmos DB '" + cosmosAccountName + "'");
        }

        // create cosmos db api client
        String host = "https://" + cosmosAccountName + ".documents.azure.com:443/";

        ArrayList<String> preferredRegions = new ArrayList<>();
        preferredRegions.add("West US");
        var client = new CosmosClientBuilder()
                .endpoint(host)
                .key(accountKey)
                .preferredRegions(preferredRegions)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .buildClient();


        var database = getDatabase(client, cosmosDbName);
        if (database.readAllContainers().stream().noneMatch(sp -> sp.getId().equals(CosmosTransferProcessStoreExtension.CONTAINER_NAME))) {
            throw new EdcException("A CosmosDB container named '" + CosmosTransferProcessStoreExtension.CONTAINER_NAME + "' was not found in account '" + cosmosAccountName + "'. Please create one, preferably using terraform.");
        }

        var container = database.getContainer(CosmosTransferProcessStoreExtension.CONTAINER_NAME);
        var partitionKey = context.getSetting(CosmosTransferProcessStoreExtension.COSMOS_PARTITION_KEY_SETTING, CosmosTransferProcessStoreExtension.DEFAULT_PARTITION_KEY);

        // get unique connector name
        var connectorId = context.getConnectorId();

        var retryPolicy = (RetryPolicy<Object>) context.getService(RetryPolicy.class);
        monitor.info("CosmosTransferProcessStore will use connector id '" + connectorId + "'");
        context.registerService(TransferProcessStore.class, new CosmosTransferProcessStore(container, context.getTypeManager(), partitionKey, connectorId, retryPolicy));

        context.getTypeManager().registerTypes(TransferProcessDocument.class);
    }

    private CosmosDatabase getDatabase(CosmosClient client, String databaseName) {
        CosmosDatabaseResponse databaseResponse = client.createDatabaseIfNotExists(databaseName);
        return client.getDatabase(databaseResponse.getProperties().getId());
    }


}

