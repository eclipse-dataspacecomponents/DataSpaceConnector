package org.eclipse.dataspaceconnector.transfer.core.transfer;

import org.eclipse.dataspaceconnector.spi.transfer.TransferProcessManager;
import org.eclipse.dataspaceconnector.spi.transfer.TransferResponse;
import org.eclipse.dataspaceconnector.spi.transfer.store.TransferProcessStore;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataRequest;

public class DelegatingTransferProcessManager implements TransferProcessManager {
    private final AsyncTransferProcessManager asyncManager;
    private final SyncTransferProcessManager syncManager;

    public DelegatingTransferProcessManager(AsyncTransferProcessManager asyncManager, SyncTransferProcessManager syncManager) {
        this.asyncManager = asyncManager;
        this.syncManager = syncManager;
    }

    @Override
    public TransferResponse initiateConsumerRequest(DataRequest dataRequest) {
        return dataRequest.isSync() ? syncManager.initiateConsumerRequest(dataRequest) : asyncManager.initiateConsumerRequest(dataRequest);
    }

    @Override
    public TransferResponse initiateProviderRequest(DataRequest dataRequest) {
        return dataRequest.isSync() ? syncManager.initiateProviderRequest(dataRequest) : asyncManager.initiateProviderRequest(dataRequest);
    }

    public void start(TransferProcessStore transferProcessStore) {
        asyncManager.start(transferProcessStore);
    }

    public void stop() {
        asyncManager.stop();
    }
}
