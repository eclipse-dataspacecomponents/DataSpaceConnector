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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.types.domain.Polymorphic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;

/**
 * Models the Dataset class of the DCAT spec. A Dataset is defined as a collection of data
 * available for access or download. The data described by a Dataset may be offered under
 * different policies and may be available via different distributions.
 */
@JsonDeserialize(builder = Dataset.Builder.class)
@JsonTypeName("dataspaceconnector:dataset")
public class Dataset implements Polymorphic {

    /**
     * Policies under which this Dataset is available.
     */
    protected final Map<String, Policy> offers = new HashMap<>();
    protected String id;
    /**
     * Representations of this Dataset.
     */
    protected List<Distribution> distributions = new ArrayList<>();

    /**
     * Properties for describing the Dataset.
     */
    protected Map<String, Object> properties = new HashMap<>();

    public String getId() {
        return id;
    }

    public Map<String, Policy> getOffers() {
        return offers;
    }

    public List<Distribution> getDistributions() {
        return distributions;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    @JsonIgnore
    public Object getProperty(String key) {
        return properties.get(key);
    }

    public boolean hasOffers() {
        return !offers.isEmpty();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder<T extends Dataset, B extends Builder<T, B>> {
        protected final T dataset;

        protected Builder(T dataset) {
            this.dataset = dataset;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @JsonCreator
        public static Builder newInstance() {
            return new Builder(new Dataset());
        }

        public B id(String id) {
            dataset.id = id;
            return self();
        }

        public B offer(String offerId, Policy policy) {
            dataset.offers.put(offerId, policy);
            return self();
        }

        public B offers(Map<String, Policy> offers) {
            dataset.offers.putAll(offers);
            return self();
        }

        public B distribution(Distribution distribution) {
            if (dataset.distributions == null) {
                dataset.distributions = new ArrayList<>();
            }
            dataset.distributions.add(distribution);
            return self();
        }

        public B distributions(List<Distribution> distributions) {
            if (dataset.distributions == null) {
                dataset.distributions = new ArrayList<>();
            }
            dataset.distributions.addAll(distributions);
            return self();
        }

        public B properties(Map<String, Object> properties) {
            dataset.properties = properties;
            return self();
        }

        public B property(String key, Object value) {
            dataset.properties.put(key, value);
            return self();
        }

        public T build() {
            if (dataset.id == null) {
                dataset.id = randomUUID().toString();
            }

            return dataset;
        }

        public B self() {
            return (B) this;
        }
    }

}
