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

package org.eclipse.edc.connector.controlplane.catalog.spi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.query.QuerySpec;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

public class CatalogRequest {

    public static final String CATALOG_REQUEST_TYPE = EDC_NAMESPACE + "CatalogRequest";
    public static final String CATALOG_REQUEST_PROTOCOL = EDC_NAMESPACE + "protocol";
    public static final String CATALOG_REQUEST_COUNTER_PARTY_ADDRESS = EDC_NAMESPACE + "counterPartyAddress";
    public static final String CATALOG_REQUEST_COUNTER_PARTY_ID = EDC_NAMESPACE + "counterPartyId";
    public static final String CATALOG_REQUEST_QUERY_SPEC = EDC_NAMESPACE + "querySpec";
    public static final String CATALOG_REQUEST_ADDITIONAL_SCOPES = EDC_NAMESPACE + "additionalScopes";
    private List<String> additionalScopes = new ArrayList<>();
    private QuerySpec querySpec;
    private String counterPartyAddress;
    private String counterPartyId;
    private String protocol;

    private CatalogRequest() {
    }

    public List<String> getAdditionalScopes() {
        return additionalScopes;
    }

    public String getCounterPartyAddress() {
        return counterPartyAddress;
    }

    public String getCounterPartyId() {
        return counterPartyId;
    }

    public QuerySpec getQuerySpec() {
        return querySpec;
    }

    public String getProtocol() {
        return protocol;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private final CatalogRequest instance;

        private Builder() {
            instance = new CatalogRequest();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder querySpec(QuerySpec querySpec) {
            instance.querySpec = querySpec;
            return this;
        }

        public Builder counterPartyAddress(String counterPartyAddress) {
            instance.counterPartyAddress = counterPartyAddress;
            return this;
        }

        public Builder counterPartyId(String counterPartyId) {
            instance.counterPartyId = counterPartyId;
            return this;
        }

        public Builder protocol(String protocol) {
            instance.protocol = protocol;
            return this;
        }

        public Builder additionalScopes(List<String> additionalScopes) {
            instance.additionalScopes = additionalScopes;
            return this;
        }

        public Builder additionalScope(String additionalScope) {
            instance.additionalScopes.add(additionalScope);
            return this;
        }

        public CatalogRequest build() {
            return instance;
        }
    }
}
