/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
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

package org.eclipse.edc.connector.controlplane.catalog.spi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Models the Distribution class of the DCAT spec. A Distribution is defined as a specific
 * representation of a Dataset. A Distribution contains a reference to the DataService, and thereby
 * endpoint, via which the Distribution can be obtained.
 */
@JsonDeserialize(builder = Distribution.Builder.class)
public class Distribution {

    /**
     * Protocol/technology via which this Distribution is available.
     */
    private String format;

    /**
     * DataService that contains access information for this Distribution.
     */
    private DataService dataService;

    public String getFormat() {
        return format;
    }

    public DataService getDataService() {
        return dataService;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final Distribution distribution;

        private Builder() {
            distribution = new Distribution();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder format(String format) {
            distribution.format = format;
            return this;
        }

        public Builder dataService(DataService dataService) {
            distribution.dataService = dataService;
            return this;
        }

        public Distribution build() {
            Objects.requireNonNull(distribution.dataService, "DataService must not be null");

            return distribution;
        }
    }

}
