package org.eclipse.dataspaceconnector.samples.identity.did;

import org.eclipse.dataspaceconnector.iam.did.spi.store.DidStore;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;

import java.util.Set;

public class DidDocumentStoreExtension implements ServiceExtension {
    @Override
    public Set<String> provides() {
        return Set.of("edc:did-documentstore");
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var store = new InMemoryDidDocumentStore();
        context.registerService(DidStore.class, store);
    }
}
