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

package org.eclipse.dataspaceconnector.spi.types.domain.contract.agreement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.dataspaceconnector.policy.model.Policy;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * {@link ContractAgreement} to regulate data transfer between two parties.
 */
@JsonDeserialize(builder = ContractAgreement.Builder.class)
public class ContractAgreement {

    private final String id;
    private final URI providerAgentId; // TODO change to string again?
    private final URI consumerAgentId;
    private final ZonedDateTime contractSigningDate;
    private final ZonedDateTime contractStartDate;
    private final ZonedDateTime contractEndDate;
    private final Asset asset;
    private final Policy policy;

    private ContractAgreement(@NotNull String id,
                              @NotNull URI providerAgentId,
                              @NotNull URI consumerAgentId,
                              ZonedDateTime contractSigningDate,
                              ZonedDateTime contractStartDate,
                              ZonedDateTime contractEndDate,
                              @NotNull Asset asset,
                              @NotNull Policy policy) {
        this.id = Objects.requireNonNull(id);
        this.providerAgentId = Objects.requireNonNull(providerAgentId);
        this.consumerAgentId = Objects.requireNonNull(consumerAgentId);
        this.contractSigningDate = contractSigningDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.asset = Objects.requireNonNull(asset);
        this.policy = Objects.requireNonNull(policy);

        if (contractSigningDate == null) {
            throw new IllegalArgumentException("contract signing date must be set");
        }
        if (contractStartDate == null) {
            throw new IllegalArgumentException("contract start date must be set");
        }
        if (contractEndDate == null) {
            throw new IllegalArgumentException("contract end date must be set");
        }
    }

    /**
     * Unique identifier of the {@link ContractAgreement}.
     *
     * @return contract id
     */
    @NotNull
    public String getId() {
        return id;
    }

    /**
     * The id of the data providing agent.
     * Please note that id should be taken from the corresponding data ecosystem.
     * For example: In IDS the connector uses a URI from the IDS Information Model as ID. If the contract was
     * negotiated inside the IDS ecosystem, this URI should be used here.
     *
     * @return provider id
     */
    @NotNull
    public URI getProviderAgentId() {
        return providerAgentId;
    }

    /**
     * The id of the data consuming agent.
     * Please note that id should be taken from the corresponding contract ecosystem.
     * For example: In IDS the connector uses a URI from the IDS Information Model as ID. If the contract was
     * negotiated inside the IDS ecosystem, this URI should be used here.
     *
     * @return consumer id
     */
    @NotNull
    public URI getConsumerAgentId() {
        return consumerAgentId;
    }

    /**
     * The date when the {@link ContractAgreement} has been signed. <br>
     * Numeric value representing the number of seconds from
     * 1970-01-01T00:00:00Z UTC until the specified UTC date/time.
     *
     * @return contract signing date
     */
    public ZonedDateTime getContractSigningDate() {
        return contractSigningDate;
    }

    /**
     * The date from when the {@link ContractAgreement} is valid. <br>
     * Numeric value representing the number of seconds from
     * 1970-01-01T00:00:00Z UTC until the specified UTC date/time.
     *
     * @return contract start date
     */
    public ZonedDateTime getContractStartDate() {
        return contractStartDate;
    }

    /**
     * The date until the {@link ContractAgreement} remains valid. <br>
     * Numeric value representing the number of seconds from
     * 1970-01-01T00:00:00Z UTC until the specified UTC date/time.
     *
     * @return contract end date
     */
    public ZonedDateTime getContractEndDate() {
        return contractEndDate;
    }

    /**
     * The Asset that is covered by the {@link ContractAgreement}.
     *
     * @return asset
     */
    @NotNull
    public Asset getAsset() {
        return asset;
    }

    /**
     * A policy describing how the {@link Asset} of this contract may be used by the consumer.
     *
     * @return policy
     */
    @NotNull
    public Policy getPolicy() {
        return policy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, providerAgentId, consumerAgentId, contractSigningDate, contractStartDate, contractEndDate, asset, policy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContractAgreement that = (ContractAgreement) o;
        return contractSigningDate == that.contractSigningDate && contractStartDate == that.contractStartDate && contractEndDate == that.contractEndDate &&
                Objects.equals(id, that.id) && Objects.equals(providerAgentId, that.providerAgentId) && Objects.equals(consumerAgentId, that.consumerAgentId) &&
                Objects.equals(asset, that.asset) && Objects.equals(policy, that.policy);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String id;
        private URI providerAgentId;
        private URI consumerAgentId;
        private ZonedDateTime contractSigningDate;
        private ZonedDateTime contractStartDate;
        private ZonedDateTime contractEndDate;
        private Asset asset;
        private Policy policy;

        private Builder() {
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder providerAgentId(URI providerAgentId) {
            this.providerAgentId = providerAgentId;
            return this;
        }

        public Builder consumerAgentId(URI consumerAgentId) {
            this.consumerAgentId = consumerAgentId;
            return this;
        }

        public Builder contractSigningDate(ZonedDateTime contractSigningDate) {
            this.contractSigningDate = contractSigningDate;
            return this;
        }

        public Builder contractStartDate(ZonedDateTime contractStartDate) {
            this.contractStartDate = contractStartDate;
            return this;
        }

        public Builder contractEndDate(ZonedDateTime contractEndDate) {
            this.contractEndDate = contractEndDate;
            return this;
        }

        public Builder asset(Asset asset) {
            this.asset = asset;
            return this;
        }

        public Builder policy(Policy policy) {
            this.policy = policy;
            return this;
        }

        public ContractAgreement build() {
            return new ContractAgreement(id, providerAgentId, consumerAgentId, contractSigningDate, contractStartDate, contractEndDate, asset, policy);
        }

    }
}
