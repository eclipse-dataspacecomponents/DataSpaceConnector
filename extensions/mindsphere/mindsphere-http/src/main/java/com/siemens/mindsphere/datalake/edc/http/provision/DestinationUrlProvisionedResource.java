/*
 *  Copyright (c) 2021, 2022 Siemens AG
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

package com.siemens.mindsphere.datalake.edc.http.provision;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.ProvisionedDataDestinationResource;

@JsonDeserialize(builder = DestinationUrlProvisionedResource.Builder.class)
@JsonTypeName("dataspaceconnector:destinationurlprovisionedresource")
public class DestinationUrlProvisionedResource extends ProvisionedDataDestinationResource {

    private DestinationUrlProvisionedResource() {
    }

    @JsonProperty
    private String url;

    @JsonProperty
    private String path;

    // @Override
    // public DataAddress createDataDestination() {
    // return DataAddress.Builder.newInstance()
    // .keyName(path)
    // .property(HttpSchema.URL, url)
    // .type(HttpSchema.TYPE)
    // .build();
    // }

    @JsonIgnore
    @Override
    public String getResourceName() {
        return path;
    }

    public String getUrl() {
        return url;
    }

    public String getPath() {
        return path;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder
            extends ProvisionedDataDestinationResource.Builder<DestinationUrlProvisionedResource, Builder> {

        private Builder() {
            super(new DestinationUrlProvisionedResource());
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder path(String path) {
            provisionedResource.path = path;
            return this;
        }

        public Builder url(String url) {
            provisionedResource.url = url;
            return this;
        }
    }
}
