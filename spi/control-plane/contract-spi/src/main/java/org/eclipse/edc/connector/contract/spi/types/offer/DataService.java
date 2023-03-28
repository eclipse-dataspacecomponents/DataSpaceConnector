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

package org.eclipse.edc.connector.contract.spi.types.offer;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

public class DataService {
    
    private String id;
    private String terms; //"dct:terms": "ids:connector"
    private String endpointUrl;
    
    public String getId() {
        return id;
    }
    
    public String getTerms() {
        return terms;
    }
    
    public String getEndpointUrl() {
        return endpointUrl;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        var dataService = (DataService) o;
        return id.equals(dataService.getId()) && terms.equals(dataService.getTerms()) && endpointUrl.equals(dataService.getEndpointUrl());
    }
    
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private DataService dataService;
        
        private Builder() {
            dataService = new DataService();
        }
    
        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }
        
        public Builder id(String id) {
            dataService.id = id;
            return this;
        }
        
        public Builder terms(String terms) {
            dataService.terms = terms;
            return this;
        }
        
        public Builder endpointUrl(String endpointUrl) {
            dataService.endpointUrl = endpointUrl;
            return this;
        }
        
        public DataService build() {
            Objects.requireNonNull(dataService.id, "Id must not be null.");
            Objects.requireNonNull(dataService.terms, "Terms must not be null.");
            Objects.requireNonNull(dataService.endpointUrl, "EndpointUrl must not be null.");
            return dataService;
        }
    }
    
}
