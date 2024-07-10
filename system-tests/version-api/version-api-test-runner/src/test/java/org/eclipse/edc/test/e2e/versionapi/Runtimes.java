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

package org.eclipse.edc.test.e2e.versionapi;

import org.eclipse.edc.connector.dataplane.spi.manager.DataPlaneManager;
import org.eclipse.edc.iam.identitytrust.sts.spi.service.StsClientService;
import org.eclipse.edc.iam.identitytrust.sts.spi.service.StsClientTokenGeneratorService;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndInstance;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.Mockito.mock;

public interface Runtimes {

    static RuntimeExtension inMemoryRuntime() {
        var rt = new RuntimePerClassExtension(new EmbeddedRuntime("control-plane", inMemoryConfiguration(), ":system-tests:version-api:version-api-test-runtime"));
        rt.registerServiceMock(DataPlaneManager.class, mock());
        rt.registerServiceMock(StsClientService.class, mock());
        rt.registerServiceMock(StsClientTokenGeneratorService.class, mock());
        return rt;
    }


    @NotNull
    static HashMap<String, String> inMemoryConfiguration() {
        return new HashMap<>() {
            {
                put("web.http.path", "/");
                put("web.http.protocol.path", "/protocol");
                var dspPort = getFreePort();
                put("web.http.protocol.port", String.valueOf(dspPort));
                put("edc.dsp.callback.address", "http://localhost:" + dspPort + "/protocol");
                put("web.http.port", String.valueOf(getFreePort()));
                put("web.http.control.port", String.valueOf(getFreePort()));
                put("web.http.management.path", "/management");
                put("web.http.management.port", String.valueOf(8181));
            }
        };
    }

    @NotNull
    static HashMap<String, String> postgresqlConfiguration() {
        var config = new HashMap<String, String>() {
            {
                put("edc.datasource.default.url", PostgresqlEndToEndInstance.JDBC_URL_PREFIX + "runtime");
                put("edc.datasource.default.user", PostgresqlEndToEndInstance.USER);
                put("edc.datasource.default.password", PostgresqlEndToEndInstance.PASSWORD);
            }
        };

        config.putAll(inMemoryConfiguration());
        return config;
    }
}
