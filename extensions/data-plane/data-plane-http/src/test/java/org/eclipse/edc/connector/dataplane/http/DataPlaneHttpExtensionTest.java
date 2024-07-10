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
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.connector.dataplane.http;

import org.eclipse.edc.connector.controlplane.api.client.spi.transferprocess.TransferProcessApiClient;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.http.spi.HttpRequestParamsProvider;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;

import java.util.Map;
import java.util.UUID;

import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.Mockito.mock;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

@ComponentTest
public class DataPlaneHttpExtensionTest {

    private static ClientAndServer sourceServer;
    private static ClientAndServer destinationServer;
    private static final int SOURCE_PORT = getFreePort();
    private static final int DESTINATION_PORT = getFreePort();

    @RegisterExtension
    private static final RuntimeExtension RUNTIME = new RuntimePerClassExtension()
            .setConfiguration(Map.of(
                    "edc.transfer.proxy.token.verifier.publickey.alias", "alias",
                    "edc.transfer.proxy.token.signer.privatekey.alias", "alias"
            ))
            .registerServiceMock(TransferProcessApiClient.class, mock());

    @BeforeAll
    public static void setUp() {
        sourceServer = startClientAndServer(SOURCE_PORT);
        destinationServer = startClientAndServer(DESTINATION_PORT);
    }

    @AfterAll
    public static void tearDown() {
        stopQuietly(sourceServer);
        stopQuietly(destinationServer);
    }

    @Test
    void transferSourceToDestination(PipelineService pipelineService) {
        var source = HttpDataAddress.Builder.newInstance()
                .baseUrl("http://localhost:" + SOURCE_PORT)
                .build();
        var destination = HttpDataAddress.Builder.newInstance()
                .baseUrl("http://localhost:" + DESTINATION_PORT)
                .build();
        sourceServer.when(request()).respond(HttpResponse.response().withStatusCode(200));
        destinationServer.when(request()).respond(HttpResponse.response().withStatusCode(200));

        var request = DataFlowStartMessage.Builder.newInstance()
                .processId(UUID.randomUUID().toString())
                .sourceDataAddress(source)
                .destinationDataAddress(destination)
                .traceContext(emptyMap())
                .build();

        var future = pipelineService.transfer(request);

        assertThat(future).succeedsWithin(10, SECONDS)
                .matches(StreamResult::succeeded);
        sourceServer.verify(request().withMethod("GET"));
        destinationServer.verify(request().withMethod("POST"));
    }

    @Test
    void transferSourceToDestinationAddHeaders(PipelineService pipelineService, HttpRequestParamsProvider paramsProvider) {
        paramsProvider.registerSourceDecorator((request, address, builder) -> builder.header("customSourceHeader", "customValue"));
        paramsProvider.registerSinkDecorator((request, address, builder) -> builder.header("customSinkHeader", "customValue"));
        var source = HttpDataAddress.Builder.newInstance()
                .baseUrl("http://localhost:" + SOURCE_PORT)
                .build();
        var destination = HttpDataAddress.Builder.newInstance()
                .baseUrl("http://localhost:" + DESTINATION_PORT)
                .build();
        sourceServer.when(request()).respond(HttpResponse.response().withStatusCode(200));
        destinationServer.when(request()).respond(HttpResponse.response().withStatusCode(200));

        var request = DataFlowStartMessage.Builder.newInstance()
                .processId(UUID.randomUUID().toString())
                .sourceDataAddress(source)
                .destinationDataAddress(destination)
                .traceContext(emptyMap())
                .build();

        var future = pipelineService.transfer(request);

        assertThat(future).succeedsWithin(10, SECONDS)
                .matches(StreamResult::succeeded);
        sourceServer.verify(request().withMethod("GET").withHeader("customSourceHeader", "customValue"));
        destinationServer.verify(request().withMethod("POST").withHeader("customSinkHeader", "customValue"));
    }
}
