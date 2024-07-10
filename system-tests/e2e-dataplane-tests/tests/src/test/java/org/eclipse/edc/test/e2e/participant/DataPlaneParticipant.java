/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.test.e2e.participant;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.eclipse.edc.connector.controlplane.test.system.utils.Participant;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static java.io.File.separator;
import static org.eclipse.edc.util.io.Ports.getFreePort;

public class DataPlaneParticipant extends Participant {

    private final URI controlPlaneControl = URI.create("http://localhost:" + getFreePort() + "/control");
    private final URI dataPlaneDefault = URI.create("http://localhost:" + getFreePort());
    private final URI dataPlaneControl = URI.create("http://localhost:" + getFreePort() + "/control");
    private final URI dataPlanePublic = URI.create("http://localhost:" + getFreePort() + "/public");

    private DataPlaneParticipant() {
        super();
    }

    public Endpoint getDataPlaneControlEndpoint() {
        return new Endpoint(dataPlaneControl);
    }

    public Endpoint getDataPlanePublicEndpoint() {
        return new Endpoint(dataPlanePublic);
    }

    public Map<String, String> dataPlaneConfiguration() {
        return new HashMap<>() {
            {
                put("web.http.port", String.valueOf(dataPlaneDefault.getPort()));
                put("web.http.path", "/api");
                put("web.http.public.port", String.valueOf(dataPlanePublic.getPort()));
                put("web.http.public.path", "/public");
                put("web.http.control.port", String.valueOf(dataPlaneControl.getPort()));
                put("web.http.control.path", dataPlaneControl.getPath());
                put("edc.keystore", resourceAbsolutePath("certs/cert.pfx"));
                put("edc.keystore.password", "123456");
                put("edc.dataplane.token.validation.endpoint", controlPlaneControl + "/token");
                put("edc.dataplane.http.sink.partition.size", "1");
                put("edc.transfer.proxy.token.signer.privatekey.alias", "1");
                put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
            }
        };
    }

    @NotNull
    private String resourceAbsolutePath(String filename) {
        return System.getProperty("user.dir") + separator + "build" + separator + "resources" + separator + "test" + separator + filename;
    }

    public static final class Builder extends Participant.Builder<DataPlaneParticipant, Builder> {

        private Builder() {
            super(new DataPlaneParticipant());
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @Override
        public DataPlaneParticipant build() {
            super.managementEndpoint(new Endpoint(URI.create("http://localhost:" + getFreePort() + "/api/management")));
            super.protocolEndpoint(new Endpoint(URI.create("http://localhost:" + getFreePort() + "/protocol")));
            super.build();
            return participant;
        }
    }
}
