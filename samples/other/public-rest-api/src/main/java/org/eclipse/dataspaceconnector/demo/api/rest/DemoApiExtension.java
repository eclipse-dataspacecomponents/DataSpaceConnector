package org.eclipse.dataspaceconnector.demo.api.rest;

import org.eclipse.dataspaceconnector.metadata.catalog.CatalogService;
import org.eclipse.dataspaceconnector.spi.protocol.web.WebService;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.transfer.TransferProcessManager;
import org.eclipse.dataspaceconnector.spi.transfer.store.TransferProcessStore;

import java.util.Set;


public class DemoApiExtension implements ServiceExtension {

    @Override
    public String name() {
        return "REST API";
    }

    @Override
    public Set<String> requires() {
        return Set.of("dataspaceconnector:transferprocessstore", "edc:catalog-service");
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var webService = context.getService(WebService.class);
        var monitor = context.getMonitor();

        var transferProcessManager = context.getService(TransferProcessManager.class);
        var processStore = context.getService(TransferProcessStore.class);
        var catalogService = context.getService(CatalogService.class);

        var controller = new DemoApiController(context.getConnectorId(), monitor, transferProcessManager, processStore, catalogService);
        webService.registerController(controller);
    }

}
