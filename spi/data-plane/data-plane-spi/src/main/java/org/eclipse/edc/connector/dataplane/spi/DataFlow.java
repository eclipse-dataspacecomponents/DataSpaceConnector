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

package org.eclipse.edc.connector.dataplane.spi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.COMPLETED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.FAILED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.NOTIFIED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.RECEIVED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.STARTED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.SUSPENDED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.TERMINATED;

/**
 * Entity that represent a Data Plane Transfer Flow
 * The id matches with the TransferProcess id that originated the DataFlow
 */
public class DataFlow extends StatefulEntity<DataFlow> {

    public static final String TERMINATION_REASON = "terminationReason";
    private DataAddress source;
    private DataAddress destination;
    private URI callbackAddress;
    private Map<String, String> properties = new HashMap<>();

    private TransferType transferType;

    @Override
    public DataFlow copy() {
        var builder = Builder.newInstance()
                .source(source)
                .destination(destination)
                .callbackAddress(callbackAddress)
                .properties(properties)
                .transferType(getTransferType());

        return copy(builder);
    }

    @Override
    public String stateAsString() {
        return DataFlowStates.from(state).name();
    }

    public DataAddress getSource() {
        return source;
    }

    public DataAddress getDestination() {
        return destination;
    }

    public URI getCallbackAddress() {
        return callbackAddress;
    }

    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public TransferType getTransferType() {
        return transferType;
    }

    public DataFlowStartMessage toRequest() {
        return DataFlowStartMessage.Builder.newInstance()
                .id(getId())
                .sourceDataAddress(getSource())
                .destinationDataAddress(getDestination())
                .processId(getId())
                .callbackAddress(getCallbackAddress())
                .traceContext(traceContext)
                .properties(getProperties())
                .transferType(getTransferType())
                .build();
    }

    public void transitToCompleted() {
        transitionTo(COMPLETED.code());
    }

    public void transitToReceived() {
        transitionTo(RECEIVED.code());
    }

    public void transitToFailed(String message) {
        errorDetail = message;
        transitionTo(FAILED.code());
    }

    public void transitToNotified() {
        transitionTo(NOTIFIED.code());
    }

    public void transitToTerminated(@Nullable String reason) {
        transitionTo(TERMINATED.code());
        if (reason != null) {
            properties.put(TERMINATION_REASON, reason);
        }
    }

    public void transitionToStarted() {
        transitionTo(STARTED.code());
    }

    public void transitToSuspended() {
        transitionTo(SUSPENDED.code());
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder extends StatefulEntity.Builder<DataFlow, Builder> {

        private Builder(DataFlow process) {
            super(process);
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder(new DataFlow());
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public DataFlow build() {
            super.build();

            if (entity.id == null) {
                entity.id = UUID.randomUUID().toString();
            }

            return entity;
        }

        public Builder source(DataAddress source) {
            entity.source = source;
            return this;
        }

        public Builder destination(DataAddress destination) {
            entity.destination = destination;
            return this;
        }

        public Builder callbackAddress(URI callbackAddress) {
            entity.callbackAddress = callbackAddress;
            return this;
        }

        public Builder transferType(TransferType transferType) {
            entity.transferType = transferType;
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            entity.properties = new HashMap<>(properties);
            return this;
        }

    }
}
