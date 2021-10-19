package org.eclipse.dataspaceconnector.iam.did;

import okhttp3.OkHttpClient;
import org.eclipse.dataspaceconnector.iam.did.hub.IdentityHubClientImpl;
import org.eclipse.dataspaceconnector.iam.did.hub.IdentityHubController;
import org.eclipse.dataspaceconnector.iam.did.hub.IdentityHubImpl;
import org.eclipse.dataspaceconnector.iam.did.resolver.DefaultDidPublicKeyResolver;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.IdentityHub;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.IdentityHubClient;
import org.eclipse.dataspaceconnector.iam.did.spi.hub.IdentityHubStore;
import org.eclipse.dataspaceconnector.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.dataspaceconnector.iam.did.spi.resolution.DidResolver;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.protocol.web.WebService;
import org.eclipse.dataspaceconnector.spi.security.PrivateKeyResolver;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.niceMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.strictMock;
import static org.easymock.EasyMock.verify;

class IdentityDidCoreExtensionTest {

    private IdentityDidCoreExtension extension;
    private ServiceExtensionContext contextMock;

    @BeforeEach
    void setUp() {
        extension = new IdentityDidCoreExtension();
        contextMock = strictMock(ServiceExtensionContext.class);
    }


    @Test
    void verifyCorrectInitialization_noPkResolverPresent() {
        expect(contextMock.getService(IdentityHubStore.class)).andReturn(niceMock(IdentityHubStore.class));
        expect(contextMock.getTypeManager()).andReturn(new TypeManager());
        expect(contextMock.getService(DidPublicKeyResolver.class, true)).andReturn(null);
        // no pk resolver, we need to expect registering one
        expect(contextMock.getService(DidResolver.class)).andReturn(niceMock(DidResolver.class));
        contextMock.registerService(eq(DidPublicKeyResolver.class), isA(DefaultDidPublicKeyResolver.class));
        expectLastCall();

        expect(contextMock.getService(PrivateKeyResolver.class)).andReturn(niceMock(PrivateKeyResolver.class));
        expect(contextMock.getConnectorId()).andReturn("test-connector");


        contextMock.registerService(eq(IdentityHub.class), isA(IdentityHubImpl.class));
        expectLastCall();

        WebService webserviceMock = strictMock(WebService.class);
        expect(contextMock.getService(WebService.class)).andReturn(webserviceMock);
        webserviceMock.registerController(isA(IdentityHubController.class));
        expectLastCall();

        expect(contextMock.getService(OkHttpClient.class)).andReturn(niceMock(OkHttpClient.class));
        contextMock.registerService(eq(IdentityHubClient.class), isA(IdentityHubClientImpl.class));
        expectLastCall();

        expect(contextMock.getMonitor()).andReturn(new Monitor() {
        });

        replay(contextMock);
        replay(webserviceMock);
        extension.initialize(contextMock);

        verify(webserviceMock);
        verify(contextMock);
    }

    @Test
    void verifyCorrectInitialization_withPkResolverPresent() {
        expect(contextMock.getService(IdentityHubStore.class)).andReturn(niceMock(IdentityHubStore.class));
        expect(contextMock.getTypeManager()).andReturn(new TypeManager());
        expect(contextMock.getService(DidPublicKeyResolver.class, true)).andReturn(niceMock(DidPublicKeyResolver.class));
        // a pk resolver is present, we don't need to expect registering one

        expect(contextMock.getService(PrivateKeyResolver.class)).andReturn(niceMock(PrivateKeyResolver.class));
        expect(contextMock.getConnectorId()).andReturn("test-connector");


        contextMock.registerService(eq(IdentityHub.class), isA(IdentityHubImpl.class));
        expectLastCall();

        WebService webserviceMock = strictMock(WebService.class);
        expect(contextMock.getService(WebService.class)).andReturn(webserviceMock);
        webserviceMock.registerController(isA(IdentityHubController.class));
        expectLastCall();

        expect(contextMock.getService(OkHttpClient.class)).andReturn(niceMock(OkHttpClient.class));
        contextMock.registerService(eq(IdentityHubClient.class), isA(IdentityHubClientImpl.class));
        expectLastCall();

        expect(contextMock.getMonitor()).andReturn(new Monitor() {
        });

        replay(contextMock);
        replay(webserviceMock);
        extension.initialize(contextMock);

        verify(webserviceMock);
        verify(contextMock);
    }
}
