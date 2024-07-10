/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
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

package org.eclipse.edc.connector.dataplane.selector.spi.instance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates.AVAILABLE;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates.REGISTERED;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates.UNAVAILABLE;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates.UNREGISTERED;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Representations of a data plane instance. Every DPF has an ID and a URL as well as a number, how often it was selected,
 * and a timestamp of its last selection time. In addition, there are extensible properties to hold specific properties.
 */
public class DataPlaneInstance extends StatefulEntity<DataPlaneInstance> {

    public static final String DATAPLANE_INSTANCE_TYPE = EDC_NAMESPACE + "DataPlaneInstance";
    @Deprecated(since = "0.6.3")
    public static final String TURN_COUNT = EDC_NAMESPACE + "turnCount";
    public static final String LAST_ACTIVE = EDC_NAMESPACE + "lastActive";
    public static final String URL = EDC_NAMESPACE + "url";
    public static final String PROPERTIES = EDC_NAMESPACE + "properties";
    public static final String ALLOWED_TRANSFER_TYPES = EDC_NAMESPACE + "allowedTransferTypes";
    public static final String ALLOWED_SOURCE_TYPES = EDC_NAMESPACE + "allowedSourceTypes";
    @Deprecated(since = "0.7.0")
    public static final String ALLOWED_DEST_TYPES = EDC_NAMESPACE + "allowedDestTypes";

    public static final String DATAPLANE_INSTANCE_STATE = EDC_NAMESPACE + "state";
    public static final String DATAPLANE_INSTANCE_STATE_TIMESTAMP = EDC_NAMESPACE + "stateTimestamp";


    private Map<String, Object> properties = new HashMap<>();
    private Set<String> allowedTransferTypes = new HashSet<>();
    private Set<String> allowedSourceTypes = new HashSet<>();
    @Deprecated(since = "0.7.0")
    private Set<String> allowedDestTypes = new HashSet<>();
    @Deprecated(since = "0.6.3")
    private int turnCount = 0;
    private long lastActive = Instant.now().toEpochMilli();
    private URL url;

    private DataPlaneInstance() {
    }

    @Override
    public DataPlaneInstance copy() {
        var builder = Builder.newInstance()
                .url(url)
                .lastActive(lastActive)
                .turnCount(turnCount)
                .allowedDestTypes(allowedDestTypes)
                .allowedSourceTypes(allowedSourceTypes)
                .allowedTransferType(allowedTransferTypes)
                .properties(properties);

        return copy(builder);
    }

    @Override
    public String stateAsString() {
        return DataPlaneInstanceStates.from(state).name();
    }

    /**
     * Determines whether this instance can handle a particular source and data address, by evaluating sourceAddress and
     * transferType against an internal list of allowed source and transfer types.
     *
     * @param sourceAddress the sourceAddress
     * @param transferType  the transferType
     * @return true if it can handle, false otherwise.
     */
    public boolean canHandle(DataAddress sourceAddress, @Nullable String transferType) {
        Objects.requireNonNull(sourceAddress, "source cannot be null!");
        Objects.requireNonNull(transferType, "transferType cannot be null!");
        return allowedSourceTypes.contains(sourceAddress.getType()) && allowedTransferTypes.contains(transferType);
    }

    public URL getUrl() {
        return url;
    }

    @Deprecated(since = "0.6.3")
    public int getTurnCount() {
        return turnCount;
    }

    public long getLastActive() {
        return lastActive;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public Set<String> getAllowedSourceTypes() {
        return Collections.unmodifiableSet(allowedSourceTypes);
    }

    @Deprecated(since = "0.7.0")
    public Set<String> getAllowedDestTypes() {
        return Collections.unmodifiableSet(allowedDestTypes);
    }

    public Set<String> getAllowedTransferTypes() {
        return Collections.unmodifiableSet(allowedTransferTypes);
    }

    public void transitionToRegistered() {
        transitionTo(REGISTERED.code());
    }

    public void transitionToAvailable() {
        transitionTo(AVAILABLE.code());
    }

    public void transitionToUnavailable() {
        transitionTo(UNAVAILABLE.code());
    }

    public void transitionToUnregistered() {
        transitionTo(UNREGISTERED.code());
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder extends StatefulEntity.Builder<DataPlaneInstance, Builder> {

        private Builder(DataPlaneInstance dataPlaneInstance) {
            super(dataPlaneInstance);
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder(new DataPlaneInstance());
        }

        @Deprecated(since = "0.6.3")
        public Builder turnCount(int turnCount) {
            entity.turnCount = turnCount;
            return this;
        }

        public Builder lastActive(long lastActive) {
            entity.lastActive = lastActive;
            return this;
        }

        public Builder allowedSourceType(String type) {
            entity.allowedSourceTypes.add(type);
            return this;
        }

        public Builder allowedDestType(String type) {
            entity.allowedDestTypes.add(type);
            return this;
        }

        public Builder allowedTransferType(String type) {
            entity.allowedTransferTypes.add(type);
            return this;
        }

        public Builder url(URL url) {
            entity.url = url;
            return this;
        }

        public Builder url(String url) {
            try {
                entity.url = new URL(url);
            } catch (MalformedURLException e) {
                throw new EdcException(e);
            }
            return this;
        }

        public Builder property(String key, Object value) {
            entity.properties.put(key, value);
            return this;
        }

        public Builder allowedDestTypes(Set<String> types) {
            entity.allowedDestTypes = types;
            return this;
        }

        public Builder allowedSourceTypes(Set<String> types) {
            if (types != null) {
                entity.allowedSourceTypes = types;
            }
            return this;
        }

        public Builder allowedTransferType(Set<String> types) {
            if (types != null) {
                entity.allowedTransferTypes = types;
            }
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            entity.properties = properties;
            return this;
        }

        @Override
        public Builder self() {
            return this;
        }

        public DataPlaneInstance build() {
            Objects.requireNonNull(entity.url, "DataPlaneInstance must have an URL");

            return super.build();
        }
    }
}
