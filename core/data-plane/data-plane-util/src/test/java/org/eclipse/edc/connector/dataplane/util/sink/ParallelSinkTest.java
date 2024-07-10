/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - Initial implementation
 *
 */

package org.eclipse.edc.connector.dataplane.util.sink;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.InputStreamDataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamFailure;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParallelSinkTest {

    private final Duration timeout = Duration.of(500, MILLIS);
    private final String errorMessage = "test-errormessage";
    private final String dataFlowRequestId = randomUUID().toString();
    private final FakeParallelSink fakeSink = new FakeParallelSink.Builder().monitor(mock())
            .executorService(Executors.newFixedThreadPool(2))
            .requestId(dataFlowRequestId).build();

    @Test
    void transfer_succeeds() {
        var dataSource = dataSource();

        var future = fakeSink.transfer(dataSource);
        
        assertThat(future).succeedsWithin(timeout)
                .satisfies(transferResult -> assertThat(transferResult.succeeded()).isTrue());
        assertThat(fakeSink.parts).containsExactly(dataSource);
        assertThat(fakeSink.complete).isEqualTo(1);
    }

    @Test
    void transfer_whenCompleteFails_fails() {
        var dataSource = dataSource();
        fakeSink.completeResponse = StreamResult.error("General error");

        var future = fakeSink.transfer(dataSource);
        
        assertThat(future).succeedsWithin(timeout).isEqualTo(fakeSink.completeResponse);
    }

    @Test
    void transfer_whenExceptionOpeningPartStream_fails() {
        var dataSourceMock = mock(DataSource.class);

        when(dataSourceMock.openPartStream()).thenThrow(new RuntimeException(errorMessage));

        var future = fakeSink.transfer(dataSourceMock);
        
        assertThat(future).succeedsWithin(timeout)
                .satisfies(transferResult -> assertThat(transferResult.failed()).isTrue())
                .satisfies(transferResult -> assertThat(transferResult.getFailureDetail())
                        .contains("Error processing data transfer request").contains(dataFlowRequestId).contains(errorMessage));
        assertThat(fakeSink.complete).isEqualTo(0);
    }

    @Test
    void transfer_whenFailureDuringTransfer_fails() {
        var dataSource = dataSource();
        fakeSink.transferResultSupplier = () -> StreamResult.error(errorMessage);

        var future = fakeSink.transfer(dataSource);
        
        assertThat(future).succeedsWithin(timeout)
                .satisfies(transferResult -> assertThat(transferResult.failed()).isTrue())
                .satisfies(transferResult -> assertThat(transferResult.getFailure().getReason()).isEqualTo(StreamFailure.Reason.GENERAL_ERROR))
                .satisfies(transferResult -> assertThat(transferResult.getFailureMessages()).containsExactly(errorMessage));

        assertThat(fakeSink.parts).containsExactly(dataSource);
        assertThat(fakeSink.complete).isEqualTo(0);
    }

    @Test
    void transfer_whenExceptionDuringTransfer_fails() {
        var dataSource = dataSource();
        fakeSink.transferResultSupplier = () -> {
            throw new RuntimeException(errorMessage);
        };

        var future = fakeSink.transfer(dataSource);
        
        assertThat(future).succeedsWithin(timeout)
                .satisfies(transferResult -> assertThat(transferResult.failed()).isTrue())
                .satisfies(transferResult -> assertThat(transferResult.getFailure().getReason()).isEqualTo(StreamFailure.Reason.GENERAL_ERROR))
                .satisfies(transferResult -> assertThat(transferResult.getFailureDetail())
                        .contains("Error processing data transfer request").contains(dataFlowRequestId).contains(errorMessage));

        assertThat(fakeSink.parts).containsExactly(dataSource);
        assertThat(fakeSink.complete).isEqualTo(0);
    }

    @Test
    void shouldNotBlock_whenDataSourceIsIndefinite() {
        var infiniteStream = IntStream.iterate(0, i -> i + 1).mapToObj(i -> mock(DataSource.Part.class));
        var dataSource = mock(DataSource.class);
        when(dataSource.openPartStream()).thenReturn(StreamResult.success(infiniteStream));

        var future = fakeSink.transfer(dataSource);

        assertThat(future).isNotNull();
    }

    private InputStreamDataSource dataSource() {
        return new InputStreamDataSource(
                "test-datasource-name",
                new ByteArrayInputStream("test-content".getBytes()));
    }

    private static class FakeParallelSink extends ParallelSink {

        List<DataSource.Part> parts;
        Supplier<StreamResult<Object>> transferResultSupplier = StreamResult::success;
        private int complete;
        private StreamResult<Object> completeResponse = StreamResult.success();

        @Override
        protected StreamResult<Object> transferParts(List<DataSource.Part> parts) {
            this.parts = parts;
            return transferResultSupplier.get();
        }

        @Override
        protected StreamResult<Object> complete() {
            complete++;
            return completeResponse;
        }

        public static class Builder extends ParallelSink.Builder<Builder, FakeParallelSink> {

            protected Builder() {
                super(new FakeParallelSink());
            }

            @Override
            protected void validate() {

            }
        }
    }
}
