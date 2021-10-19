package org.eclipse.dataspaceconnector.samples.identity.registrationservice.api;

import org.eclipse.dataspaceconnector.iam.did.spi.store.DidStore;
import org.eclipse.dataspaceconnector.spi.iam.RegistrationService;
import org.eclipse.dataspaceconnector.spi.protocol.web.WebService;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;

import java.util.Set;

public class RegistrationServiceApiExtension implements ServiceExtension {

    @Override
    public Set<String> requires() {
        return Set.of("edc:did-documentstore");
    }


    @Override
    public void initialize(ServiceExtensionContext context) {

        var didDocumentStore = context.getService(DidStore.class);

        // create the registration service, which offers a REST API
        var regSrv = new RegistrationServiceController(context.getMonitor(), didDocumentStore);
        context.registerService(RegistrationService.class, regSrv);

        // register the service as REST controller
        var webService = context.getService(WebService.class);
        webService.registerController(regSrv);

        context.getMonitor().info("Registration Service REST API initialized");
    }
}
