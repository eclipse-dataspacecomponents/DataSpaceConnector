package com.microsoft.dagx.transfer.demo.protocols.stream;

import com.microsoft.dagx.spi.transfer.flow.DataFlowController;
import com.microsoft.dagx.spi.transfer.flow.DataFlowInitiateResponse;
import com.microsoft.dagx.spi.types.domain.transfer.DataRequest;
import com.microsoft.dagx.transfer.demo.protocols.spi.DemoProtocols;
import com.microsoft.dagx.transfer.demo.protocols.spi.stream.StreamPublisherRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Implements push-style streaming. The client runtime provisions a topic which the provider runtime publishes to.
 */
public class PushStreamFlowController implements DataFlowController {
    private StreamPublisherRegistry publisherRegistry;

    public PushStreamFlowController(StreamPublisherRegistry registry) {
        this.publisherRegistry = registry;
    }

    @Override
    public boolean canHandle(DataRequest dataRequest) {
        return DemoProtocols.PUSH_STREAM_WS.equals(dataRequest.getDestinationType()) ||  DemoProtocols.PUSH_STREAM_HTTP.equals(dataRequest.getDestinationType()) ;
    }

    @Override
    public @NotNull DataFlowInitiateResponse initiateFlow(DataRequest dataRequest) {
        publisherRegistry.notifyPublisher(dataRequest);
        return DataFlowInitiateResponse.OK;
    }

}
