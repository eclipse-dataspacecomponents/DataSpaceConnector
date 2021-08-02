/*
 * Copyright (c) Microsoft Corporation.
 *  All rights reserved.
 *
 */

package com.microsoft.dagx.events.azure;

import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherAsyncClient;
import com.microsoft.dagx.spi.metadata.MetadataListener;
import com.microsoft.dagx.spi.monitor.Monitor;
import com.microsoft.dagx.spi.transfer.TransferProcessListener;
import com.microsoft.dagx.spi.types.domain.transfer.TransferProcess;
import com.microsoft.dagx.spi.types.domain.transfer.TransferProcessStates;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;

class AzureEventGridPublisher implements TransferProcessListener, MetadataListener {

    private final Monitor monitor;
    private final EventGridPublisherAsyncClient<EventGridEvent> client;
    private final String eventTypeTransferprocess = "dagx/transfer/transferprocess";
    private final String eventTypeMetadata = "dagx/metadata/store";
    private final String connectorId;

    public AzureEventGridPublisher(String connectorId, Monitor monitor, EventGridPublisherAsyncClient<EventGridEvent> client) {
        this.connectorId = connectorId;
        this.monitor = monitor;
        this.client = client;
    }

    @Override
    public void created(TransferProcess process) {
        var dto = createTransferProcessDto(process);
        if (process.getType() == TransferProcess.Type.CLIENT) {
            sendEvent("createdClient", eventTypeTransferprocess, dto).subscribe(new LoggingSubscriber<>("Transfer process created"));
        } else {
            sendEvent("createdProvider", eventTypeTransferprocess, dto).subscribe(new LoggingSubscriber<>("Transfer process created"));
        }
    }

    @Override
    public void completed(TransferProcess process) {
        sendEvent("completed", eventTypeTransferprocess, createTransferProcessDto(process)).subscribe(new LoggingSubscriber<>("Transfer process completed"));
    }


    @Override
    public void deprovisioned(TransferProcess process) {
        sendEvent("deprovisioned", eventTypeTransferprocess, createTransferProcessDto(process)).subscribe(new LoggingSubscriber<>("Transfer process resources deprovisioned"));

    }

    @Override
    public void ended(TransferProcess process) {
        sendEvent("ended", eventTypeTransferprocess, createTransferProcessDto(process)).subscribe(new LoggingSubscriber<>("Transfer process ended"));

    }

    @Override
    public void error(TransferProcess process) {
        sendEvent("error", eventTypeTransferprocess, createTransferProcessDto(process)).subscribe(new LoggingSubscriber<>("Transfer process errored!"));

    }

    @Override
    public void querySubmitted() {
        sendEvent("queryReceived", eventTypeMetadata, new EventDto(connectorId)).subscribe(new LoggingSubscriber<>("query submitted"));
    }

    @Override
    public void searchInitiated() {
        sendEvent("searchInitiated", eventTypeMetadata, new EventDto(connectorId)).subscribe(new LoggingSubscriber<>("search initiated"));
    }

    @Override
    public void metadataItemAdded() {
        sendEvent("itemAdded", eventTypeMetadata, new EventDto(connectorId)).subscribe(new LoggingSubscriber<>("AzureEventGrid: metadata item added"));
    }

    @Override
    public void metadataItemUpdated() {
        sendEvent("itemUpdated", eventTypeMetadata, new EventDto(connectorId)).subscribe(new LoggingSubscriber<>("metadata item updated"));
    }

    private Mono<Void> sendEvent(String what, String where, Object payload) {
        final BinaryData data = BinaryData.fromObject(payload);
        var evt = new EventGridEvent(what, where, data, "0.1");
        return client.sendEvent(evt);
    }

    @NotNull
    private TransferProcessDto createTransferProcessDto(TransferProcess process) {
        return TransferProcessDto.Builder.newInstance()
                .connector(connectorId)
                .state(TransferProcessStates.from(process.getState()))
                .requestId(process.getDataRequest().getId())
                .type(process.getType())
                .build();
    }

    private class LoggingSubscriber<T> extends BaseSubscriber<T> {

        private final String message;

        LoggingSubscriber(String message) {
            this.message = message;
        }

        @Override
        protected void hookOnComplete() {
            monitor.info("AzureEventGrid: " + message);
        }

        @Override
        protected void hookOnError(@NotNull Throwable throwable) {
            monitor.severe("Error during event publishing", throwable);
        }
    }
}
