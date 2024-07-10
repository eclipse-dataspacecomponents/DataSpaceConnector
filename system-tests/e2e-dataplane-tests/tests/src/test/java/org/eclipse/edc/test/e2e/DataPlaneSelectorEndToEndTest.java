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

import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.strategy.SelectionStrategy;
import org.eclipse.edc.connector.dataplane.selector.spi.strategy.SelectionStrategyRegistry;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerMethodExtension;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.test.e2e.DataPlaneSelectorEndToEndTest.SelectFirst.SELECT_FIRST;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.Mockito.mock;

@EndToEndTest
public class DataPlaneSelectorEndToEndTest {

    @RegisterExtension
    private final RuntimeExtension controlPlane = new RuntimePerMethodExtension(new EmbeddedRuntime(
            "control-plane",
            Map.of(
                    "web.http.control.port", String.valueOf(getFreePort()),
                    "web.http.control.path", "/control",
                    "edc.dataplane.client.selector.strategy", SELECT_FIRST,
                    "edc.data.plane.selector.state-machine.check.period", "1",
                    "edc.core.retry.retries.max", "0"
            ),
            ":core:control-plane:control-plane-core",
            ":core:data-plane-selector:data-plane-selector-core",
            ":extensions:control-plane:transfer:transfer-data-plane-signaling",
            ":extensions:common:iam:iam-mock",
            ":extensions:common:http",
            ":extensions:common:api:control-api-configuration"
    )).registerServiceMock(ProtocolWebhook.class, mock());

    private final int dataPlaneControlPort = getFreePort();

    @RegisterExtension
    private final RuntimeExtension dataPlane = new RuntimePerMethodExtension(new EmbeddedRuntime(
            "data-plane",
            Map.of(
                    "web.http.port", String.valueOf(getFreePort()),
                    "web.http.path", "/api",
                    "web.http.control.port", String.valueOf(dataPlaneControlPort),
                    "web.http.control.path", "/control",
                    "edc.transfer.proxy.token.verifier.publickey.alias", "alias",
                    "edc.transfer.proxy.token.signer.privatekey.alias", "alias"
            ),
            ":core:data-plane:data-plane-core",
            ":extensions:data-plane:data-plane-http",
            ":extensions:common:api:control-api-configuration",
            ":extensions:control-plane:api:control-plane-api-client",
            ":extensions:data-plane:data-plane-signaling:data-plane-signaling-api",
            ":extensions:common:http"
    ));

    @Test
    void shouldNotSelectUnavailableDataPlanes() {
        var policy = Policy.Builder.newInstance()
                .assignee("any")
                .build();
        var transferProcess = TransferProcess.Builder.newInstance()
                .contractId("any")
                .assetId("any")
                .contentDataAddress(createHttpDataAddress())
                .dataDestination(createHttpDataAddress())
                .transferType("HttpData-PUSH").build();

        controlPlane.getService(SelectionStrategyRegistry.class).add(new SelectFirst());

        var selectorService = controlPlane.getService(DataPlaneSelectorService.class);
        selectorService.addInstance(createDataPlaneInstance("not-available", "http://localhost:" + getFreePort()));
        selectorService.addInstance(createDataPlaneInstance("available", "http://localhost:" + dataPlaneControlPort + "/control/v1/dataflows"));

        await().atMost(30, SECONDS).untilAsserted(() -> {
            var start = controlPlane.getService(DataFlowManager.class).start(transferProcess, policy);
            assertThat(start).isSucceeded();
        });
    }

    private DataAddress createHttpDataAddress() {
        return DataAddress.Builder.newInstance()
                .type("HttpData")
                .property(EDC_NAMESPACE + "baseUrl", "http://localhost/any")
                .build();
    }

    private DataPlaneInstance createDataPlaneInstance(String id, String url) {
        return DataPlaneInstance.Builder.newInstance().id(id).url(url)
                .allowedTransferType("HttpData-PUSH").allowedSourceType("HttpData").build();
    }

    /**
     * Permit to choose the first data plane, needed to verify the expected behavior.
     */
    static class SelectFirst implements SelectionStrategy {

        static final String SELECT_FIRST = "first";

        @Override
        public DataPlaneInstance apply(List<DataPlaneInstance> instances) {
            return instances.stream().findFirst().orElse(null);
        }

        @Override
        public String getName() {
            return SELECT_FIRST;
        }
    }
}
