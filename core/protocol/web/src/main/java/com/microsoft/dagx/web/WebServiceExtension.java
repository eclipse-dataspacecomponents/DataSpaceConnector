/*
 * Copyright (c) Microsoft Corporation.
 * All rights reserved.
 */

package com.microsoft.dagx.web;

import com.microsoft.dagx.spi.monitor.Monitor;
import com.microsoft.dagx.spi.protocol.web.WebService;
import com.microsoft.dagx.spi.system.ServiceExtension;
import com.microsoft.dagx.spi.system.ServiceExtensionContext;
import com.microsoft.dagx.spi.types.TypeManager;
import com.microsoft.dagx.web.rest.JerseyRestService;
import com.microsoft.dagx.web.transport.JettyService;

import static com.microsoft.dagx.spi.system.ServiceExtension.LoadPhase.PRIMORDIAL;

/**
 * Provides HTTP transport and REST binding services.
 *
 * TODO create keystore to support HTTPS
 */
public class WebServiceExtension implements ServiceExtension {
    private Monitor monitor;
    private JettyService jettyService;
    private JerseyRestService jerseyRestService;

    @Override
    public LoadPhase phase() {
        return PRIMORDIAL;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();
        TypeManager typeManager = context.getTypeManager();

        jettyService = new JettyService(context::getSetting, monitor);
        context.registerService(JettyService.class, jettyService);

        jerseyRestService = new JerseyRestService(jettyService, typeManager, monitor);

        context.registerService(WebService.class, jerseyRestService);

        monitor.info("Initialized Web extension");
    }

    @Override
    public void start() {
        jerseyRestService.start();
        jettyService.start();
        monitor.info("Started Web extension");
    }

    @Override
    public void shutdown() {
        if (jettyService != null) {
            jettyService.shutdown();
        }
        monitor.info("Shutdown Web extension");
    }


}
