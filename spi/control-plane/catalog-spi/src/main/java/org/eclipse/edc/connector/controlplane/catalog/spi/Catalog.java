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
 *       Fraunhofer Institute for Software and Systems Engineering - add datasets
 *
 */

package org.eclipse.edc.connector.controlplane.catalog.spi;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Catalog
 */
@JsonDeserialize(builder = Catalog.Builder.class)
@JsonTypeName("dataspaceconnector:catalog")
public class Catalog extends Dataset {
    protected final List<Dataset> datasets = new ArrayList<>();
    protected List<DataService> dataServices = new ArrayList<>();
    protected String participantId;

    public List<Dataset> getDatasets() {
        return datasets;
    }

    public List<DataService> getDataServices() {
        return dataServices;
    }

    public String getParticipantId() {
        return participantId;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder extends Dataset.Builder<Catalog, Catalog.Builder> {

        private Builder() {
            super(new Catalog());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder datasets(List<Dataset> datasets) {
            dataset.datasets.addAll(datasets);
            return this;
        }

        public Builder dataset(Dataset dataset) {
            this.dataset.datasets.add(dataset);
            return this;
        }

        public Builder dataServices(List<DataService> dataServices) {
            this.dataset.dataServices = dataServices;
            return this;
        }

        public Builder dataService(DataService dataService) {
            if (this.dataset.dataServices == null) {
                this.dataset.dataServices = new ArrayList<>();
            }
            this.dataset.dataServices.add(dataService);
            return this;
        }

        public Builder participantId(String participantId) {
            this.dataset.participantId = participantId;
            return this;
        }
    }
}
