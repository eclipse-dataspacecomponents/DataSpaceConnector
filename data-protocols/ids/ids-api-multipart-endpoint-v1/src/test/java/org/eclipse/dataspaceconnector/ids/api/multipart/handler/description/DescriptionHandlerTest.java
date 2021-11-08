/*
 *  Copyright (c) 2021 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial API and Implementation
 *
 */

package org.eclipse.dataspaceconnector.ids.api.multipart.handler.description;

import de.fraunhofer.iais.eis.DescriptionRequestMessage;
import de.fraunhofer.iais.eis.Message;
import org.easymock.EasyMock;
import org.eclipse.dataspaceconnector.ids.api.multipart.handler.DescriptionHandler;
import org.eclipse.dataspaceconnector.ids.api.multipart.message.MultipartRequest;
import org.eclipse.dataspaceconnector.ids.api.multipart.message.MultipartResponse;
import org.eclipse.dataspaceconnector.ids.spi.IdsId;
import org.eclipse.dataspaceconnector.ids.spi.IdsType;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformResult;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformerRegistry;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DescriptionHandlerTest {

    private static final String CONNECTOR_ID = "urn:connector:edc";

    // subject
    private DescriptionHandler descriptionHandler;

    // mocks
    private Monitor monitor;
    private TransformerRegistry transformerRegistry;
    private ArtifactDescriptionRequestHandler artifactDescriptionRequestHandler;
    private DataCatalogDescriptionRequestHandler dataCatalogDescriptionRequestHandler;
    private RepresentationDescriptionRequestHandler representationDescriptionRequestHandler;
    private ResourceDescriptionRequestHandler resourceDescriptionRequestHandler;
    private ConnectorDescriptionRequestHandler connectorDescriptionRequestHandler;

    @BeforeEach
    void setUp() {
        monitor = EasyMock.mock(Monitor.class);
        transformerRegistry = EasyMock.mock(TransformerRegistry.class);
        artifactDescriptionRequestHandler = EasyMock.mock(ArtifactDescriptionRequestHandler.class);
        dataCatalogDescriptionRequestHandler = EasyMock.mock(DataCatalogDescriptionRequestHandler.class);
        representationDescriptionRequestHandler = EasyMock.mock(RepresentationDescriptionRequestHandler.class);
        resourceDescriptionRequestHandler = EasyMock.mock(ResourceDescriptionRequestHandler.class);
        connectorDescriptionRequestHandler = EasyMock.mock(ConnectorDescriptionRequestHandler.class);

        descriptionHandler = new DescriptionHandler(
                monitor,
                CONNECTOR_ID,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler);
    }

    @Test
    void testCanHandleNullThrowsNullPointerException() {
        EasyMock.replay(
                monitor,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler);

        assertThrows(NullPointerException.class, () -> {
            descriptionHandler.canHandle(null);
        });
    }

    @Test
    void testCanHandleMultipartHeaderOfTypeDescriptionRequestMessageReturnsTrue() {
        DescriptionRequestMessage message = EasyMock.mock(DescriptionRequestMessage.class);

        EasyMock.replay(
                monitor,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler,
                message);

        MultipartRequest multipartRequest = MultipartRequest.Builder.newInstance()
                .header(message)
                .build();

        var result = descriptionHandler.canHandle(multipartRequest);

        assertThat(result).isTrue();
    }

    @Test
    void testHandleRequestOfTypeDescriptionRequestMessage() {
        // prepare
        DescriptionRequestMessage requestHeader = EasyMock.mock(DescriptionRequestMessage.class);

        Message responseHeader = EasyMock.mock(Message.class);
        MultipartResponse response = MultipartResponse.Builder.newInstance()
                .header(responseHeader)
                .build();

        EasyMock.expect(requestHeader.getRequestedElement()).andReturn(null);

        MultipartRequest multipartRequest = MultipartRequest.Builder.newInstance()
                .header(requestHeader)
                .build();

        EasyMock.expect(connectorDescriptionRequestHandler.handle(requestHeader, multipartRequest.getPayload())).andReturn(response);

        // record
        EasyMock.replay(
                monitor,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler,
                requestHeader,
                responseHeader);

        // invoke
        var result = descriptionHandler.handleRequest(multipartRequest);

        // verify
        assertThat(result).isNotNull();
        assertThat(result).extracting(MultipartResponse::getHeader).isEqualTo(responseHeader);
    }

    @Test
    void testHandleRequestOfTypeDescriptionRequestMessageForArtifact() {
        // prepare
        DescriptionRequestMessage requestHeader = EasyMock.mock(DescriptionRequestMessage.class);

        Message responseHeader = EasyMock.mock(Message.class);
        MultipartResponse response = MultipartResponse.Builder.newInstance()
                .header(responseHeader)
                .build();

        URI uri = URI.create("urn:artifact:1");
        EasyMock.expect(requestHeader.getRequestedElement()).andReturn(uri);

        EasyMock.expect(transformerRegistry.transform(uri, IdsId.class))
                .andReturn(new TransformResult<>(IdsId.Builder.newInstance().value("1").type(IdsType.ARTIFACT).build()));

        EasyMock.expect(artifactDescriptionRequestHandler.handle(EasyMock.eq(requestHeader), EasyMock.anyObject())).andReturn(response);

        MultipartRequest multipartRequest = MultipartRequest.Builder.newInstance()
                .header(requestHeader)
                .build();

        // record
        EasyMock.replay(
                monitor,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler,
                requestHeader,
                responseHeader);

        // invoke
        var result = descriptionHandler.handleRequest(multipartRequest);

        // verify
        assertThat(result).isNotNull();
        assertThat(result).extracting(MultipartResponse::getHeader).isEqualTo(responseHeader);
    }

    @Test
    void testHandleRequestOfTypeDescriptionRequestMessageForCatalog() {
        // prepare
        DescriptionRequestMessage requestHeader = EasyMock.mock(DescriptionRequestMessage.class);

        Message responseHeader = EasyMock.mock(Message.class);
        MultipartResponse response = MultipartResponse.Builder.newInstance()
                .header(responseHeader)
                .build();

        URI uri = URI.create("urn:catalog:1");
        EasyMock.expect(requestHeader.getRequestedElement()).andReturn(uri);

        EasyMock.expect(transformerRegistry.transform(uri, IdsId.class))
                .andReturn(new TransformResult<>(IdsId.Builder.newInstance().value("1").type(IdsType.CATALOG).build()));

        EasyMock.expect(dataCatalogDescriptionRequestHandler.handle(EasyMock.eq(requestHeader), EasyMock.anyObject())).andReturn(response);

        MultipartRequest multipartRequest = MultipartRequest.Builder.newInstance()
                .header(requestHeader)
                .build();

        // record
        EasyMock.replay(
                monitor,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler,
                requestHeader,
                responseHeader);

        // invoke
        var result = descriptionHandler.handleRequest(multipartRequest);

        // verify
        assertThat(result).isNotNull();
        assertThat(result).extracting(MultipartResponse::getHeader).isEqualTo(responseHeader);
    }

    @Test
    void testHandleRequestOfTypeDescriptionRequestMessageForRepresentation() {
        // prepare
        DescriptionRequestMessage requestHeader = EasyMock.mock(DescriptionRequestMessage.class);

        Message responseHeader = EasyMock.mock(Message.class);
        MultipartResponse response = MultipartResponse.Builder.newInstance()
                .header(responseHeader)
                .build();

        URI uri = URI.create("urn:representation:1");
        EasyMock.expect(requestHeader.getRequestedElement()).andReturn(uri);

        EasyMock.expect(transformerRegistry.transform(uri, IdsId.class))
                .andReturn(new TransformResult<>(IdsId.Builder.newInstance().value("1").type(IdsType.REPRESENTATION).build()));

        EasyMock.expect(representationDescriptionRequestHandler.handle(EasyMock.eq(requestHeader), EasyMock.anyObject())).andReturn(response);

        MultipartRequest multipartRequest = MultipartRequest.Builder.newInstance()
                .header(requestHeader)
                .build();

        // record
        EasyMock.replay(
                monitor,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler,
                requestHeader,
                responseHeader);

        // invoke
        var result = descriptionHandler.handleRequest(multipartRequest);

        // verify
        assertThat(result).isNotNull();
        assertThat(result).extracting(MultipartResponse::getHeader).isEqualTo(responseHeader);
    }

    @Test
    void testHandleRequestOfTypeDescriptionRequestMessageForResource() {
        // prepare
        DescriptionRequestMessage requestHeader = EasyMock.mock(DescriptionRequestMessage.class);

        Message responseHeader = EasyMock.mock(Message.class);
        MultipartResponse response = MultipartResponse.Builder.newInstance()
                .header(responseHeader)
                .build();

        URI uri = URI.create("urn:resource:1");
        EasyMock.expect(requestHeader.getRequestedElement()).andReturn(uri);

        EasyMock.expect(transformerRegistry.transform(uri, IdsId.class))
                .andReturn(new TransformResult<>(IdsId.Builder.newInstance().value("1").type(IdsType.RESOURCE).build()));

        EasyMock.expect(resourceDescriptionRequestHandler.handle(EasyMock.eq(requestHeader), EasyMock.anyObject())).andReturn(response);

        MultipartRequest multipartRequest = MultipartRequest.Builder.newInstance()
                .header(requestHeader)
                .build();

        // record
        EasyMock.replay(
                monitor,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler,
                requestHeader,
                responseHeader);

        // invoke
        var result = descriptionHandler.handleRequest(multipartRequest);

        // verify
        assertThat(result).isNotNull();
        assertThat(result).extracting(MultipartResponse::getHeader).isEqualTo(responseHeader);
    }

    @AfterEach
    void tearDown() {
        EasyMock.verify(
                monitor,
                transformerRegistry,
                artifactDescriptionRequestHandler,
                dataCatalogDescriptionRequestHandler,
                representationDescriptionRequestHandler,
                resourceDescriptionRequestHandler,
                connectorDescriptionRequestHandler
        );
    }
}