/*
 *  Copyright (c) 2021 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.dataspaceconnector.spi.types.domain.contract.negotiation;

import org.eclipse.dataspaceconnector.spi.types.domain.contract.offer.ContractOffer;
import org.eclipse.dataspaceconnector.spi.types.domain.message.RemoteMessage;

import java.util.Objects;

/**
 * Object that wraps the contract offer and provides additional information about e.g. protocol
 * and recipient.
 */
public class ContractOfferRequest implements RemoteMessage {

    private ContractOfferType type = ContractOfferType.COUNTER_OFFER;
    private String protocol;
    private String connectorId;
    private String connectorAddress;
    private String correlationId;
    private ContractOffer contractOffer;

    @Override
    public String getProtocol() {
        return protocol;
    }

    public String getConnectorId() {
        return connectorId;
    }

    public String getConnectorAddress() {
        return connectorAddress;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public ContractOfferType getType() {
        return type;
    }

    public ContractOffer getContractOffer() {
        return contractOffer;
    }

    public static class Builder {
        private final ContractOfferRequest contractOfferRequest;

        private Builder() {
            this.contractOfferRequest = new ContractOfferRequest();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder protocol(String protocol) {
            this.contractOfferRequest.protocol = protocol;
            return this;
        }

        public Builder connectorId(String connectorId) {
            this.contractOfferRequest.connectorId = connectorId;
            return this;
        }

        public Builder connectorAddress(String connectorAddress) {
            this.contractOfferRequest.connectorAddress = connectorAddress;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.contractOfferRequest.correlationId = correlationId;
            return this;
        }

        public Builder contractOffer(ContractOffer contractOffer) {
            this.contractOfferRequest.contractOffer = contractOffer;
            return this;
        }

        public Builder type(ContractOfferType type) {
            this.contractRequest.type = type;
            return this;
        }

        public ContractRequest build() {
            Objects.requireNonNull(contractRequest.protocol, "protocol");
            Objects.requireNonNull(contractRequest.connectorId, "connectorId");
            Objects.requireNonNull(contractRequest.connectorAddress, "connectorAddress");
            Objects.requireNonNull(contractRequest.contractOffer, "contractOffer");
            return contractRequest;
        }
    }

    public enum ContractOfferType {
        INITIAL,
        COUNTER_OFFER
    }
}
