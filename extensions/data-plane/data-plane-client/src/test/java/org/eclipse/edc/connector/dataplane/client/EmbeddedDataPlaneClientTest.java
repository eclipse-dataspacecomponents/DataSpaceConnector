/*
 *  Copyright (c) 2022 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

package org.eclipse.edc.connector.dataplane.client;

import org.eclipse.edc.connector.dataplane.spi.client.DataPlaneClient;
import org.eclipse.edc.connector.dataplane.spi.manager.DataPlaneManager;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddedDataPlaneClientTest {

    private final DataPlaneManager dataPlaneManager = mock();
    private final DataPlaneClient client = new EmbeddedDataPlaneClient(dataPlaneManager);

    @Test
    void transfer_shouldSucceed_whenTransferInitiatedCorrectly() {
        var request = createDataFlowRequest();
        when(dataPlaneManager.validate(any())).thenReturn(Result.success(true));
        doNothing().when(dataPlaneManager).initiate(any());

        var result = client.transfer(request);

        verify(dataPlaneManager).validate(request);
        verify(dataPlaneManager).initiate(request);

        assertThat(result).isSucceeded();
    }

    @Test
    void transfer_shouldReturnFailedResult_whenValidationFailure() {
        var errorMsg = "error";
        var request = createDataFlowRequest();
        when(dataPlaneManager.validate(any())).thenReturn(Result.failure(errorMsg));
        doNothing().when(dataPlaneManager).initiate(any());

        var result = client.transfer(request);

        verify(dataPlaneManager).validate(request);
        verify(dataPlaneManager, never()).initiate(any());

        assertThat(result).isFailed().messages().hasSize(1).allSatisfy(s -> assertThat(s).contains(errorMsg));
    }

    @Test
    void terminate_shouldProxyCallToManager() {
        when(dataPlaneManager.terminate(any())).thenReturn(StatusResult.success());

        var result = client.terminate("dataFlowId");

        assertThat(result).isSucceeded();
        verify(dataPlaneManager).terminate("dataFlowId");
    }

    private static DataFlowRequest createDataFlowRequest() {
        return DataFlowRequest.Builder.newInstance()
                .trackable(true)
                .id("123")
                .processId("456")
                .sourceDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .build();
    }
}
