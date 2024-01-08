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

package org.eclipse.edc.connector.contract.spi.types.negotiation;

import org.eclipse.edc.connector.contract.spi.types.protocol.ContractRemoteMessage;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.types.domain.offer.ContractOffer;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Object that wraps the contract offer and provides additional information about e.g. protocol and recipient.
 * Sent by the consumer.
 */
public class ContractRequestMessage extends ContractRemoteMessage {

    private Type type = Type.COUNTER_OFFER;
    private String callbackAddress;

    private ContractOffer contractOffer;
    private String contractOfferId;
    private String dataset;

    public Type getType() {
        return type;
    }

    @Nullable
    public ContractOffer getContractOffer() {
        return contractOffer;
    }

    @Nullable
    public String getContractOfferId() {
        return contractOfferId;
    }

    public String getDataset() {
        return dataset;
    }

    public String getCallbackAddress() {
        return callbackAddress;
    }

    @Override
    public Policy getPolicy() {
        return contractOffer.getPolicy();
    }

    public enum Type {
        INITIAL,
        COUNTER_OFFER
    }

    public static class Builder extends ContractRemoteMessage.Builder<ContractRequestMessage, Builder> {

        private Builder() {
            super(new ContractRequestMessage());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder callbackAddress(String callbackAddress) {
            message.callbackAddress = callbackAddress;
            return this;
        }

        public Builder contractOffer(ContractOffer contractOffer) {
            message.contractOffer = contractOffer;
            return this;
        }

        public Builder contractOfferId(String id) {
            message.contractOfferId = id;
            return this;
        }

        public Builder type(Type type) {
            message.type = type;
            return this;
        }

        public Builder dataset(String dataset) {
            message.dataset = dataset;
            return this;
        }

        public ContractRequestMessage build() {
            if (message.contractOfferId == null) {
                requireNonNull(message.contractOffer, "contractOffer");
            } else {
                requireNonNull(message.contractOfferId, "contractOfferId");
            }
            return super.build();
        }
    }
}
