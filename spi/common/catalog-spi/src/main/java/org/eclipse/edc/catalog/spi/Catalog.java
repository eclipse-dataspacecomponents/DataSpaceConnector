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

package org.eclipse.edc.catalog.spi;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.connector.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.contract.spi.types.offer.DataService;
import org.eclipse.edc.connector.contract.spi.types.offer.Dataset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;

/**
 * DTO representing catalog containing {@link ContractOffer}s.
 */
@JsonDeserialize(builder = Catalog.Builder.class)
public class Catalog {
    private String id;
    private List<ContractOffer> contractOffers;
    private List<Dataset> datasets = new ArrayList<>();
    private List<DataService> dataServices = new ArrayList<>();
    private Map<String, Object> properties = new HashMap<>();

    public String getId() {
        return id;
    }

    public List<ContractOffer> getContractOffers() {
        return contractOffers;
    }
    
    public List<Dataset> getDatasets() {
        return datasets;
    }
    
    public List<DataService> getDataServices() {
        return dataServices;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private Catalog catalog;
        
        private Builder() {
            catalog = new Catalog();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            catalog.id = id;
            return this;
        }

        public Builder contractOffers(List<ContractOffer> contractOffers) {
            catalog.contractOffers = contractOffers;
            return this;
        }
        
        public Builder datasets(List<Dataset> datasets) {
            catalog.datasets = datasets;
            return this;
        }
    
        public Builder dataset(Dataset dataset) {
            catalog.datasets.add(dataset);
            return this;
        }
        
        public Builder dataServices(List<DataService> dataServices) {
            catalog.dataServices = dataServices;
            return this;
        }
    
        public Builder dataService(DataService dataService) {
            catalog.dataServices.add(dataService);
            return this;
        }
        
        public Builder properties(Map<String, Object> properties) {
            catalog.properties = properties;
            return this;
        }
        
        public Builder property(String key, Object value) {
            catalog.properties.put(key, value);
            return this;
        }

        public Catalog build() {
            if (catalog.id == null) {
                catalog.id = randomUUID().toString();
            }
            if (catalog.contractOffers == null && catalog.datasets == null) {
                throw new NullPointerException("Either contractOffers or datasets required for catalog.");
            }
            
            return catalog;
        }

    }
}
