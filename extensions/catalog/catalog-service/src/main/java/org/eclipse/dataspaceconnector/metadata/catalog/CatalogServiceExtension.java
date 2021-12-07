package org.eclipse.dataspaceconnector.metadata.catalog;


import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;

import java.util.Set;

public class CatalogServiceExtension implements ServiceExtension {

    @Override
    public String name() {
        return "Catalog Service";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var dispatcherRegistry = context.getService(RemoteMessageDispatcherRegistry.class);
        var catalogService = new CatalogServiceImpl(dispatcherRegistry);

        context.registerService(CatalogService.class, catalogService);
    }

    @Override
    public Set<String> provides() {
        return Set.of("edc:catalog-service");
    }

    @Override
    public Set<String> requires() {
        return Set.of("dataspaceconnector:dispatcher");
    }
}
