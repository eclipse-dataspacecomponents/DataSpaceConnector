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
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.edc.web.jetty;

import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class WebServiceConfigurerImplTest {

    private static final String CONFIG = "web.http.test";
    private static final String PATH = "/api";
    private static final String ALIAS = "test";
    private static final int PORT = 8080;
    private final WebServer server = mock();

    private final WebServiceConfigurer configurator = new WebServiceConfigurerImpl(mock());

    @Test
    void verifyConfigure_whenDefaultConfig() {
        var config = ConfigFactory.fromMap(new HashMap<>());

        var settings = WebServiceSettings.Builder.newInstance()
                .apiConfigKey(CONFIG)
                .contextAlias(ALIAS)
                .defaultPath(PATH)
                .defaultPort(PORT)
                .useDefaultContext(false)
                .name("TEST")
                .build();

        var actualConfig = configurator.configure(config, server, settings);

        verify(server, times(1))
                .addPortMapping(ALIAS, PORT, PATH);

        assertThat(actualConfig.getPort()).isEqualTo(PORT);
        assertThat(actualConfig.getPath()).isEqualTo(PATH);

    }

    @Test
    void verifyConfigure_whenDefaultAlias() {

        var config = ConfigFactory.fromMap(new HashMap<>());
        var defValue = "default";
        when(server.getDefaultContextName()).thenReturn(defValue);

        var settings = WebServiceSettings.Builder.newInstance()
                .apiConfigKey(CONFIG)
                .contextAlias(ALIAS)
                .defaultPath(PATH)
                .defaultPort(PORT)
                .useDefaultContext(true)
                .name("TEST")
                .build();

        var actualConfig = configurator.configure(config, server, settings);

        verifyNoInteractions(server);
        assertThat(actualConfig.getPort()).isEqualTo(PORT);
        assertThat(actualConfig.getPath()).isEqualTo(PATH);

    }

    @Test
    void verifyConfigure_whenExternalConfig() {

        var path = "/api/custom";
        var port = 8765;
        var apiConfig = new HashMap<String, String>();
        apiConfig.put("port", String.valueOf(port));
        apiConfig.put("path", path);

        var config = ConfigFactory.fromMap(apiConfig);

        var settings = WebServiceSettings.Builder.newInstance()
                .apiConfigKey(CONFIG)
                .contextAlias(ALIAS)
                .defaultPath(PATH)
                .defaultPort(PORT)
                .useDefaultContext(false)
                .name("TEST")
                .build();

        var actualConfig = configurator.configure(config, server, settings);

        verifyNoInteractions(server);
        assertThat(actualConfig.getPort()).isEqualTo(port);
        assertThat(actualConfig.getPath()).isEqualTo(path);
    }

}
