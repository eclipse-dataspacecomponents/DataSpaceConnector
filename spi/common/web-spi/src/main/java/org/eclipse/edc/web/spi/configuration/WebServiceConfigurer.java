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

package org.eclipse.edc.web.spi.configuration;


import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.web.spi.WebServer;

/**
 * Configure an API extension
 */
@ExtensionPoint
public interface WebServiceConfigurer {

    String WEB_HTTP_PREFIX = "web.http";

    /**
     * Build the configuration for an API
     *
     * @param config    The context configuration
     * @param webServer The WebServer
     * @param settings  WebService settings
     * @return The final webservice configuration
     */
    WebServiceConfiguration configure(Config config, WebServer webServer, WebServiceSettings settings);
}
