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

package org.eclipse.edc.test.e2e;

import jakarta.json.JsonObject;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.Map;
import java.util.UUID;

import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndInstance.createDatabase;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.stop.Stop.stopQuietly;


class TransferPushEndToEndTest {

    abstract static class Tests extends TransferEndToEndTestBase {

        private static ClientAndServer providerDataSource;
        private static ClientAndServer consumerDataDestination;

        @BeforeAll
        static void setUp() {
            providerDataSource = startClientAndServer(getFreePort());
            consumerDataDestination = startClientAndServer(getFreePort());
            consumerDataDestination.when(HttpRequest.request()).respond(HttpResponse.response());
        }

        @AfterAll
        static void afterAll() {
            stopQuietly(providerDataSource);
            stopQuietly(consumerDataDestination);
        }

        @Test
        void httpPushDataTransfer() {
            providerDataSource.when(HttpRequest.request()).respond(HttpResponse.response().withBody("data"));
            var assetId = UUID.randomUUID().toString();
            var dataAddressProperties = Map.<String, Object>of(
                    "name", "transfer-test",
                    "baseUrl", "http://localhost:" + providerDataSource.getPort() + "/source",
                    "type", "HttpData",
                    "proxyQueryParams", "true"
            );
            createResourcesOnProvider(assetId, dataAddressProperties);

            var transferProcessId = CONSUMER.requestAssetFrom(assetId, PROVIDER)
                    .withDestination(httpDataAddress("http://localhost:" + consumerDataDestination.getPort() + "/destination"))
                    .withTransferType("HttpData-PUSH").execute();

            awaitTransferToBeInState(transferProcessId, COMPLETED);

            providerDataSource.verify(HttpRequest.request("/source").withMethod("GET"));
            consumerDataDestination.verify(HttpRequest.request("/destination").withBody(BinaryBody.binary("data".getBytes())));
        }

        @Test
        void httpToHttp_oauth2Provisioning() {
            var oauth2server = startClientAndServer(getFreePort());
            oauth2server.when(HttpRequest.request()).respond(HttpResponse.response().withBody(json(Map.of("access_token", "token"))));
            providerDataSource.when(HttpRequest.request()).respond(HttpResponse.response().withBody("data"));
            getDataplaneVault().storeSecret("provision-oauth-secret", "supersecret");
            var assetId = UUID.randomUUID().toString();
            var sourceDataAddressProperties = Map.<String, Object>of(
                    "type", "HttpData",
                    "baseUrl", "http://localhost:" + providerDataSource.getPort() + "/source",
                    "oauth2:clientId", "clientId",
                    "oauth2:clientSecretKey", "provision-oauth-secret",
                    "oauth2:tokenUrl", "http://localhost:" + oauth2server.getPort() + "/token"
            );

            createResourcesOnProvider(assetId, sourceDataAddressProperties);

            var transferProcessId = CONSUMER.requestAssetFrom(assetId, PROVIDER)
                    .withDestination(httpDataAddress("http://localhost:" + consumerDataDestination.getPort() + "/destination"))
                    .withTransferType("HttpData-PUSH").execute();

            awaitTransferToBeInState(transferProcessId, COMPLETED);

            oauth2server.verify(HttpRequest.request("/token").withBody("grant_type=client_credentials&client_secret=supersecret&client_id=clientId"));
            providerDataSource.verify(HttpRequest.request("/source").withMethod("GET").withHeader("Authorization", "Bearer token"));
            consumerDataDestination.verify(HttpRequest.request("/destination").withBody(BinaryBody.binary("data".getBytes())));
            stopQuietly(oauth2server);
        }

        private JsonObject httpDataAddress(String baseUrl) {
            return createObjectBuilder()
                    .add(TYPE, EDC_NAMESPACE + "DataAddress")
                    .add(EDC_NAMESPACE + "type", "HttpData")
                    .add(EDC_NAMESPACE + "baseUrl", baseUrl)
                    .build();
        }
    }

    @Nested
    @EndToEndTest
    class InMemory extends Tests {

        @RegisterExtension
        static final RuntimeExtension CONSUMER_CONTROL_PLANE = new RuntimePerClassExtension(
                    Runtimes.IN_MEMORY_CONTROL_PLANE.create("consumer-control-plane", CONSUMER.controlPlaneConfiguration()));

        @RegisterExtension
        static final RuntimeExtension PROVIDER_CONTROL_PLANE = new RuntimePerClassExtension(
                    Runtimes.IN_MEMORY_CONTROL_PLANE.create("provider-control-plane", PROVIDER.controlPlaneConfiguration()));

        @RegisterExtension
        static final RuntimeExtension PROVIDER_DATA_PLANE = new RuntimePerClassExtension(
                    Runtimes.IN_MEMORY_DATA_PLANE.create("provider-data-plane", PROVIDER.dataPlaneConfiguration()));

        @Override
        protected Vault getDataplaneVault() {
            return PROVIDER_DATA_PLANE.getService(Vault.class);
        }
    }

    @Nested
    @EndToEndTest
    class EmbeddedDataPlane extends Tests {

        @RegisterExtension
        static final RuntimeExtension CONSUMER_CONTROL_PLANE = new RuntimePerClassExtension(
                    Runtimes.IN_MEMORY_CONTROL_PLANE.create("consumer-control-plane", CONSUMER.controlPlaneConfiguration()));

        @RegisterExtension
        static final RuntimeExtension PROVIDER_CONTROL_PLANE = new RuntimePerClassExtension(
                    Runtimes.IN_MEMORY_CONTROL_PLANE_EMBEDDED_DATA_PLANE.create("provider-control-plane", PROVIDER.controlPlaneEmbeddedDataPlaneConfiguration()));

        @Override
        protected Vault getDataplaneVault() {
            return PROVIDER_CONTROL_PLANE.getService(Vault.class);
        }
    }

    @Nested
    @PostgresqlIntegrationTest
    class Postgres extends Tests {

        @RegisterExtension
        static final BeforeAllCallback CREATE_DATABASES = context -> {
            createDatabase(CONSUMER.getName());
            createDatabase(PROVIDER.getName());
        };

        @RegisterExtension
        static final RuntimeExtension CONSUMER_CONTROL_PLANE = new RuntimePerClassExtension(
                    Runtimes.POSTGRES_CONTROL_PLANE.create("consumer-control-plane", CONSUMER.controlPlanePostgresConfiguration()));

        @RegisterExtension
        static final RuntimeExtension PROVIDER_CONTROL_PLANE = new RuntimePerClassExtension(
                    Runtimes.POSTGRES_CONTROL_PLANE.create("provider-control-plane", PROVIDER.controlPlanePostgresConfiguration()));

        @RegisterExtension
        static final RuntimeExtension PROVIDER_DATA_PLANE = new RuntimePerClassExtension(
                    Runtimes.POSTGRES_DATA_PLANE.create("provider-data-plane", PROVIDER.dataPlanePostgresConfiguration()));

        @Override
        protected Vault getDataplaneVault() {
            return PROVIDER_DATA_PLANE.getService(Vault.class);
        }
    }

}
