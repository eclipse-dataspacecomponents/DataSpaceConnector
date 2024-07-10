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

package org.eclipse.edc.test.e2e;

import io.restassured.common.mapper.TypeRef;
import jakarta.json.Json;
import org.assertj.core.api.ThrowingConsumer;
import org.eclipse.edc.connector.controlplane.test.system.utils.Participant;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static java.io.File.separator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.boot.BootServicesExtension.PARTICIPANT_ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_PREFIX;
import static org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndInstance.defaultDatasourceConfiguration;
import static org.eclipse.edc.util.io.Ports.getFreePort;

public class TransferEndToEndParticipant extends Participant {

    private final URI controlPlaneDefault = URI.create("http://localhost:" + getFreePort());
    private final URI controlPlaneControl = URI.create("http://localhost:" + getFreePort() + "/control");
    private final URI dataPlaneDefault = URI.create("http://localhost:" + getFreePort());
    private final URI dataPlaneControl = URI.create("http://localhost:" + getFreePort() + "/control");
    private final URI dataPlanePublic = URI.create("http://localhost:" + getFreePort() + "/public");
    private final int httpProvisionerPort = getFreePort();

    protected TransferEndToEndParticipant() {
        super();
    }

    public int getHttpProvisionerPort() {
        return httpProvisionerPort;
    }

    /**
     * Register a data plane using with input transfer type using the data plane signaling API url
     *
     * @deprecated dataplane now can register itself.
     */
    @Deprecated(since = "0.6.3")
    public void registerDataPlane(Set<String> transferTypes) {
        registerDataPlane(dataPlaneControl + "/v1/dataflows", Set.of("HttpData", "HttpProvision", "Kafka"), Set.of("HttpData", "HttpProvision", "HttpProxy", "Kafka"), transferTypes);
    }

    /**
     * Register a data plane
     *
     * @param url           The url of the data plane
     * @param sources       The allowed source types
     * @param destinations  The allowed destination types
     * @param transferTypes The allowed transfer types
     * @deprecated dataplane now can register itself.
     */
    @Deprecated(since = "0.6.3")
    public void registerDataPlane(String url, Set<String> sources, Set<String> destinations, Set<String> transferTypes) {
        var jsonObject = Json.createObjectBuilder()
                .add(CONTEXT, createObjectBuilder().add(EDC_PREFIX, EDC_NAMESPACE))
                .add(ID, UUID.randomUUID().toString())
                .add(EDC_NAMESPACE + "url", url)
                .add(EDC_NAMESPACE + "allowedSourceTypes", createArrayBuilder(sources))
                .add(EDC_NAMESPACE + "allowedDestTypes", createArrayBuilder(destinations))
                .add(EDC_NAMESPACE + "allowedTransferTypes", createArrayBuilder(transferTypes))
                .add(EDC_NAMESPACE + "properties", createObjectBuilder().add("publicApiUrl", dataPlanePublic.toString()))
                .build();

        managementEndpoint.baseRequest()
                .contentType(JSON)
                .body(jsonObject.toString())
                .when()
                .post("/v2/dataplanes")
                .then()
                .statusCode(200);
    }

    public Map<String, String> controlPlaneConfiguration() {
        return new HashMap<>() {
            {
                put(PARTICIPANT_ID, id);
                put("web.http.port", String.valueOf(controlPlaneDefault.getPort()));
                put("web.http.path", "/api");
                put("web.http.protocol.port", String.valueOf(protocolEndpoint.getUrl().getPort()));
                put("web.http.protocol.path", protocolEndpoint.getUrl().getPath());
                put("web.http.management.port", String.valueOf(managementEndpoint.getUrl().getPort()));
                put("web.http.management.path", managementEndpoint.getUrl().getPath());
                put("web.http.control.port", String.valueOf(controlPlaneControl.getPort()));
                put("web.http.control.path", controlPlaneControl.getPath());
                put("edc.dsp.callback.address", protocolEndpoint.getUrl().toString());
                put("edc.keystore", resourceAbsolutePath("certs/cert.pfx"));
                put("edc.keystore.password", "123456");
                put("edc.transfer.proxy.endpoint", dataPlanePublic.toString());
                put("edc.transfer.send.retry.limit", "1");
                put("edc.transfer.send.retry.base-delay.ms", "100");
                put("edc.negotiation.consumer.send.retry.limit", "1");
                put("edc.negotiation.provider.send.retry.limit", "1");
                put("edc.negotiation.consumer.send.retry.base-delay.ms", "100");
                put("edc.negotiation.provider.send.retry.base-delay.ms", "100");

                put("edc.negotiation.consumer.state-machine.iteration-wait-millis", "50");
                put("edc.negotiation.provider.state-machine.iteration-wait-millis", "50");
                put("edc.transfer.state-machine.iteration-wait-millis", "50");

                put("provisioner.http.entries.default.provisioner.type", "provider");
                put("provisioner.http.entries.default.endpoint", "http://localhost:%d/provision".formatted(httpProvisionerPort));
                put("provisioner.http.entries.default.data.address.type", "HttpProvision");
            }
        };
    }

    public Map<String, String> controlPlanePostgresConfiguration() {
        var baseConfiguration = controlPlaneConfiguration();
        baseConfiguration.putAll(defaultDatasourceConfiguration(getName()));
        return baseConfiguration;
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
                put("edc.dataplane.api.public.baseurl", dataPlanePublic + "/v2/");
                put("edc.dataplane.token.validation.endpoint", controlPlaneControl + "/token");
                put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                put("edc.dataplane.http.sink.partition.size", "1");
                put("edc.dataplane.state-machine.iteration-wait-millis", "50");
                put("edc.dpf.selector.url", controlPlaneControl + "/v1/dataplanes");
            }
        };
    }

    public Map<String, String> controlPlaneEmbeddedDataPlaneConfiguration() {
        var cfg = dataPlaneConfiguration();
        cfg.putAll(controlPlaneConfiguration());
        return cfg;
    }

    public Map<String, String> dataPlanePostgresConfiguration() {
        var baseConfiguration = dataPlaneConfiguration();
        baseConfiguration.putAll(defaultDatasourceConfiguration(getName()));
        return baseConfiguration;
    }

    /**
     * Get the EDR from the EDR cache by transfer process id.
     *
     * @param transferProcessId The transfer process id
     * @return The cached {@link DataAddress}
     */
    public DataAddress getEdr(String transferProcessId) {
        var dataAddressRaw = managementEndpoint.baseRequest()
                .contentType(JSON)
                .when()
                .get("/v3/edrs/{id}/dataaddress", transferProcessId)
                .then()
                .log().ifError()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(new TypeRef<Map<String, Object>>() {
                });


        var builder = DataAddress.Builder.newInstance();
        dataAddressRaw.forEach(builder::property);
        return builder.build();

    }

    /**
     * Pull data from provider using EDR.
     *
     * @param edr         endpoint data reference
     * @param queryParams query parameters
     * @param bodyAssertion assertion to be verified on the body
     */
    public void pullData(DataAddress edr, Map<String, String> queryParams, ThrowingConsumer<String> bodyAssertion) {
        var data = given()
                .baseUri(edr.getStringProperty("endpoint"))
                .header("Authorization", edr.getStringProperty("authorization"))
                .queryParams(queryParams)
                .when()
                .get()
                .then()
                .log().ifError()
                .statusCode(200)
                .extract().body().asString();

        assertThat(data).satisfies(bodyAssertion);
    }

    @NotNull
    private String resourceAbsolutePath(String filename) {
        return System.getProperty("user.dir") + separator + "build" + separator + "resources" + separator + "test" + separator + filename;
    }

    public static class Builder extends Participant.Builder<TransferEndToEndParticipant, Builder> {

        protected Builder() {
            super(new TransferEndToEndParticipant());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @Override
        public TransferEndToEndParticipant build() {
            super.managementEndpoint(new Endpoint(URI.create("http://localhost:" + getFreePort() + "/api/management")));
            super.protocolEndpoint(new Endpoint(URI.create("http://localhost:" + getFreePort() + "/protocol")));
            super.build();
            return participant;
        }
    }
}
