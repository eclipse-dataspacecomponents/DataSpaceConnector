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

package org.eclipse.edc.spi.types.domain.transfer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.telemetry.TraceCarrier;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.Polymorphic;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * A request to transfer data from a source to destination.
 */
@JsonTypeName("dataspaceconnector:dataflowrequest")
@JsonDeserialize(builder = DataFlowStartMessage.Builder.class)
public class DataFlowStartMessage implements Polymorphic, TraceCarrier {

    public static final String DC_DATA_FLOW_START_MESSAGE_PROCESS_ID = EDC_NAMESPACE + "processId";
    public static final String EDC_DATA_FLOW_START_MESSAGE_SIMPLE_TYPE = "DataFlowStartMessage";
    public static final String EDC_DATA_FLOW_START_MESSAGE_TYPE = EDC_NAMESPACE + EDC_DATA_FLOW_START_MESSAGE_SIMPLE_TYPE;
    public static final String EDC_DATA_FLOW_START_MESSAGE_FLOW_TYPE = EDC_NAMESPACE + "flowType";
    public static final String EDC_DATA_FLOW_START_MESSAGE_TRANSFER_TYPE_DESTINATION = EDC_NAMESPACE + "transferTypeDestination";
    public static final String EDC_DATA_FLOW_START_MESSAGE_DATASET_ID = EDC_NAMESPACE + "datasetId";
    public static final String EDC_DATA_FLOW_START_MESSAGE_PARTICIPANT_ID = EDC_NAMESPACE + "participantId";
    public static final String EDC_DATA_FLOW_START_MESSAGE_AGREEMENT_ID = EDC_NAMESPACE + "agreementId";
    public static final String EDC_DATA_FLOW_START_MESSAGE_SOURCE_DATA_ADDRESS = EDC_NAMESPACE + "sourceDataAddress";
    public static final String EDC_DATA_FLOW_START_MESSAGE_DESTINATION_DATA_ADDRESS = EDC_NAMESPACE + "destinationDataAddress";
    public static final String EDC_DATA_FLOW_START_MESSAGE_DESTINATION_CALLBACK_ADDRESS = EDC_NAMESPACE + "callbackAddress";
    public static final String EDC_DATA_FLOW_START_MESSAGE_PROPERTIES = EDC_NAMESPACE + "properties";

    private String id;
    private String processId;

    private String assetId;
    private String participantId;
    private String agreementId;

    private DataAddress sourceDataAddress;
    private DataAddress destinationDataAddress;
    private URI callbackAddress;

    private Map<String, String> properties = new HashMap<>();
    private Map<String, String> traceContext = Map.of(); // TODO: should this stay in the DataFlow class?
    private TransferType transferType;

    private DataFlowStartMessage() {
    }

    /**
     * The unique request id. Request ids are provided by the originating consumer and must be unique.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the process id this request is associated with.
     */
    public String getProcessId() {
        return processId;
    }

    /**
     * The source address of the data.
     */
    public DataAddress getSourceDataAddress() {
        return sourceDataAddress;
    }

    /**
     * The target address the data is to be sent to.
     */
    public DataAddress getDestinationDataAddress() {
        return destinationDataAddress;
    }

    /**
     * The {@link FlowType} for the request
     */
    public FlowType getFlowType() {
        return getTransferType().flowType();
    }

    /**
     * The agreement id associated to the request
     */
    public String getAgreementId() {
        return agreementId;
    }

    /**
     * The asset id associated to the request
     */
    public String getAssetId() {
        return assetId;
    }

    /**
     * The transfer type associated to the request.
     *
     * @return the transfer type.
     */
    public TransferType getTransferType() {
        return transferType;
    }

    /**
     * The participant id associated to the request
     */
    public String getParticipantId() {
        return participantId;
    }

    /**
     * Custom properties that are passed to the provider connector.
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Trace context for this request
     */
    @Override
    public Map<String, String> getTraceContext() {
        return traceContext;
    }

    /**
     * Callback address for this request once it has completed
     */
    public URI getCallbackAddress() { // TODO: this could be a URI
        return callbackAddress;
    }

    /**
     * A builder initialized with the current DataFlowRequest
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final DataFlowStartMessage request;
        private String transferTypeDestination;
        private FlowType flowType;

        private Builder() {
            this(new DataFlowStartMessage());
        }

        private Builder(DataFlowStartMessage request) {
            this.request = request;
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            request.id = id;
            return this;
        }

        public Builder processId(String id) {
            request.processId = id;
            return this;
        }

        public Builder destinationType(String type) {
            if (request.destinationDataAddress == null) {
                request.destinationDataAddress = DataAddress.Builder.newInstance().type(type).build();
            } else {
                request.destinationDataAddress.setType(type);
            }
            return this;
        }

        public Builder sourceDataAddress(DataAddress source) {
            request.sourceDataAddress = source;
            return this;
        }

        public Builder destinationDataAddress(DataAddress destination) {
            request.destinationDataAddress = destination;
            return this;
        }

        public Builder transferType(TransferType transferType) {
            request.transferType = transferType;
            return this;
        }

        public Builder transferTypeDestination(String transferTypeDestination) {
            this.transferTypeDestination = transferTypeDestination;
            return this;
        }

        public Builder flowType(FlowType flowType) {
            this.flowType = flowType;
            return this;
        }

        public Builder agreementId(String agreementId) {
            request.agreementId = agreementId;
            return this;
        }

        public Builder participantId(String participantId) {
            request.participantId = participantId;
            return this;
        }

        public Builder assetId(String assetId) {
            request.assetId = assetId;
            return this;
        }

        public Builder properties(Map<String, String> value) {
            request.properties = value == null ? null : Map.copyOf(value);
            return this;
        }

        public Builder property(String key, String value) {
            request.properties.put(key, value);
            return this;
        }

        public Builder traceContext(Map<String, String> value) {
            request.traceContext = value;
            return this;
        }

        public Builder callbackAddress(URI callbackAddress) {
            request.callbackAddress = callbackAddress;
            return this;
        }

        public DataFlowStartMessage build() {
            if (request.id == null) {
                request.id = UUID.randomUUID().toString();
            }
            if (request.traceContext == null) {
                request.traceContext = new HashMap<>();
            }
            Objects.requireNonNull(request.processId, "processId");
            Objects.requireNonNull(request.sourceDataAddress, "sourceDataAddress");
            if (request.transferType == null) {
                request.transferType = new TransferType(this.transferTypeDestination, this.flowType);
            }
            return request;
        }

    }
}
