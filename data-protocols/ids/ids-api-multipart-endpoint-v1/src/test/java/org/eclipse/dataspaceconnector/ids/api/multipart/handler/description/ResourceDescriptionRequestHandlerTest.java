package org.eclipse.dataspaceconnector.ids.api.multipart.handler.description;

import de.fraunhofer.iais.eis.DescriptionRequestMessage;
import de.fraunhofer.iais.eis.Resource;
import org.easymock.EasyMock;
import org.eclipse.dataspaceconnector.ids.spi.IdsType;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformResult;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformerRegistry;
import org.eclipse.dataspaceconnector.spi.asset.AssetIndex;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.eclipse.dataspaceconnector.ids.api.multipart.handler.description.DescriptionRequestHandlerMocks.mockAssetIndex;
import static org.eclipse.dataspaceconnector.ids.api.multipart.handler.description.DescriptionRequestHandlerMocks.mockDescriptionRequestMessage;
import static org.eclipse.dataspaceconnector.ids.api.multipart.handler.description.DescriptionRequestHandlerMocks.mockTransformerRegistry;

public class ResourceDescriptionRequestHandlerTest {

    private static final String CONNECTOR_ID = "urn:connector:edc";

    // subject
    private ResourceDescriptionRequestHandler resourceDescriptionRequestHandler;

    // mocks
    private Monitor monitor;
    private TransformerRegistry transformerRegistry;
    private DescriptionRequestMessage descriptionRequestMessage;
    private AssetIndex assetIndex;
    private Resource resource;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @BeforeEach
    public void setup() throws URISyntaxException {
        monitor = EasyMock.createMock(Monitor.class);
        resource = EasyMock.createMock(Resource.class);
        EasyMock.expect(resource.getId()).andReturn(new URI("urn:resource:hello"));
        EasyMock.replay(monitor, resource);

        assetIndex = mockAssetIndex();
        EasyMock.expect(assetIndex.findById(EasyMock.anyString())).andReturn(EasyMock.createMock(Asset.class));
        EasyMock.replay(assetIndex);

        transformerRegistry = mockTransformerRegistry(IdsType.RESOURCE);
        var resourceResult = (TransformResult) EasyMock.createMock(TransformResult.class);
        EasyMock.expect(resourceResult.getOutput()).andReturn(resource);
        EasyMock.expect(resourceResult.hasProblems()).andReturn(false);
        EasyMock.expect(transformerRegistry.transform(EasyMock.isA(Asset.class), EasyMock.eq(Resource.class))).andReturn(resourceResult);
        EasyMock.replay(transformerRegistry, resourceResult);

        descriptionRequestMessage = mockDescriptionRequestMessage(resource.getId());
        EasyMock.replay(descriptionRequestMessage);

        resourceDescriptionRequestHandler = new ResourceDescriptionRequestHandler(monitor, CONNECTOR_ID, assetIndex, transformerRegistry);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void testConstructorArgumentsNotNullable() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new ResourceDescriptionRequestHandler(null, CONNECTOR_ID, assetIndex, transformerRegistry));
        Assertions.assertThrows(NullPointerException.class,
                () -> new ResourceDescriptionRequestHandler(monitor, null, assetIndex, transformerRegistry));
        Assertions.assertThrows(NullPointerException.class,
                () -> new ResourceDescriptionRequestHandler(monitor, CONNECTOR_ID, null, transformerRegistry));
        Assertions.assertThrows(NullPointerException.class,
                () -> new ResourceDescriptionRequestHandler(monitor, CONNECTOR_ID, assetIndex, null));
    }

    @Test
    public void testSimpleSuccessPath() {
        var result = resourceDescriptionRequestHandler.handle(descriptionRequestMessage, null);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getHeader());
        Assertions.assertEquals(resource, result.getPayload());
    }
}
