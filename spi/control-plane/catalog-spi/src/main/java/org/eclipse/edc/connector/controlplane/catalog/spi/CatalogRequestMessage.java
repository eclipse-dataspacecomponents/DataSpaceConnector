/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - Initial implementation
 *
 */

package org.eclipse.edc.connector.controlplane.catalog.spi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A request for a participant's {@link Catalog}.
 */
@JsonDeserialize(builder = CatalogRequestMessage.Builder.class)
public class CatalogRequestMessage implements RemoteMessage {

    private final Policy policy;
    private List<String> additionalScopes = new ArrayList<>();
    private String protocol = "unknown";
    private String counterPartyAddress;
    private String counterPartyId;
    private QuerySpec querySpec;


    private CatalogRequestMessage() {
        // at this time, this is just a placeholder.
        policy = Policy.Builder.newInstance().build();
    }

    @NotNull
    @Override
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @NotNull
    @Override
    public String getCounterPartyAddress() {
        return counterPartyAddress;
    }

    @NotNull
    @Override
    public String getCounterPartyId() {
        return counterPartyId;
    }


    public QuerySpec getQuerySpec() {
        return querySpec;
    }

    /**
     * Returns the {@link Policy} associated with the Catalog Request. Currently, this is an empty policy and serves as placeholder.
     *
     * @return the stub {@link Policy}.
     */
    public Policy getPolicy() {
        return policy;
    }

    public List<String> getAdditionalScopes() {
        return additionalScopes;
    }

    public static class Builder {
        private final CatalogRequestMessage message;

        private Builder() {
            message = new CatalogRequestMessage();
        }

        @JsonCreator
        public static CatalogRequestMessage.Builder newInstance() {
            return new CatalogRequestMessage.Builder();
        }

        public CatalogRequestMessage.Builder protocol(String protocol) {
            this.message.protocol = protocol;
            return this;
        }

        public CatalogRequestMessage.Builder counterPartyAddress(String callbackAddress) {
            this.message.counterPartyAddress = callbackAddress;
            return this;
        }

        public CatalogRequestMessage.Builder counterPartyId(String counterPartyId) {
            this.message.counterPartyId = counterPartyId;
            return this;
        }

        public CatalogRequestMessage.Builder querySpec(QuerySpec querySpec) {
            this.message.querySpec = querySpec;
            return this;
        }

        public CatalogRequestMessage.Builder additionalScopes(String... additionalScopes) {
            this.message.additionalScopes = Arrays.asList(additionalScopes);
            return this;
        }

        public CatalogRequestMessage build() {
            Objects.requireNonNull(message.protocol, "protocol");

            if (message.querySpec == null) {
                message.querySpec = QuerySpec.none();
            }

            return message;
        }

    }
}
