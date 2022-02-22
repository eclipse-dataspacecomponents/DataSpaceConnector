/*
 *  Copyright (c) 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */
package org.eclipse.dataspaceconnector.dataplane.http.pipeline;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.eclipse.dataspaceconnector.common.stream.PartitionIterator;
import org.eclipse.dataspaceconnector.dataplane.spi.pipeline.DataSink;
import org.eclipse.dataspaceconnector.dataplane.spi.pipeline.DataSource;
import org.eclipse.dataspaceconnector.dataplane.spi.result.TransferResult;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.result.AbstractResult;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.toList;
import static org.eclipse.dataspaceconnector.common.async.AsyncUtils.asyncAllOf;
import static org.eclipse.dataspaceconnector.spi.response.ResponseStatus.ERROR_RETRY;

/**
 * Writes data in a streaming fashion to an HTTP endpoint.
 */
public class HttpDataSink implements DataSink {
    private String authKey;
    private String authCode;
    private String endpoint;
    private String requestId;
    private int partitionSize = 5;
    private OkHttpClient httpClient;
    private ExecutorService executorService;
    private Monitor monitor;

    @Override
    public CompletableFuture<TransferResult> transfer(DataSource source) {
        try (var partStream = source.openPartStream()) {
            var partitioned = PartitionIterator.streamOf(partStream, partitionSize);
            var futures = partitioned.map(parts -> supplyAsync(() -> postData(parts), executorService)).collect(toList());
            return futures.stream()
                    .collect(asyncAllOf())
                    .thenApply(results -> {
                        return results.stream()
                                .filter(AbstractResult::failed)
                                .findFirst()
                                .map(r -> TransferResult.failure(ERROR_RETRY, String.join(",", r.getFailureMessages())))
                                .orElse(TransferResult.success());
                    })
                    .exceptionally(throwable -> TransferResult.failure(ERROR_RETRY, "Unhandled exception raised when transferring data: " + throwable.getMessage()));
        } catch (Exception e) {
            monitor.severe("Error processing data transfer request: " + requestId, e);
            return CompletableFuture.completedFuture(TransferResult.failure(ERROR_RETRY, "Error processing data transfer request"));
        }
    }

    /**
     * Retrieves the parts from the source endpoint using an HTTP GET.
     */
    private TransferResult postData(List<DataSource.Part> parts) {
        for (DataSource.Part part : parts) {
            var requestBody = new StreamingRequestBody(part);

            var requestBuilder = new Request.Builder();
            if (authKey != null) {
                requestBuilder.header(authKey, authCode);
            }

            var request = requestBuilder.url(endpoint + "/" + part.name()).post(requestBody).build();
            try (var response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    monitor.severe(format("Error received writing HTTP data %s to endpoint %s for request: %s", part.name(), endpoint, request));
                    return TransferResult.failure(ERROR_RETRY, "Error writing data");
                }
            } catch (Exception e) {
                monitor.severe(format("Error writing HTTP data %s to endpoint %s for request: %s", part.name(), endpoint, request), e);
                return TransferResult.failure(ERROR_RETRY, "Error writing data");
            }
        }
        return TransferResult.success();
    }

    private HttpDataSink() {
    }

    public static class Builder {
        private HttpDataSink sink;

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder endpoint(String endpoint) {
            sink.endpoint = endpoint;
            return this;
        }

        public Builder requestId(String requestId) {
            sink.requestId = requestId;
            return this;
        }

        public Builder partitionSize(int partitionSize) {
            sink.partitionSize = partitionSize;
            return this;
        }

        public Builder authKey(String authKey) {
            sink.authKey = authKey;
            return this;
        }

        public Builder authCode(String authCode) {
            sink.authCode = authCode;
            return this;
        }

        public Builder httpClient(OkHttpClient httpClient) {
            sink.httpClient = httpClient;
            return this;
        }

        public Builder executorService(ExecutorService executorService) {
            sink.executorService = executorService;
            return this;
        }

        public Builder monitor(Monitor monitor) {
            sink.monitor = monitor;
            return this;
        }

        public HttpDataSink build() {
            Objects.requireNonNull(sink.endpoint, "endpoint");
            Objects.requireNonNull(sink.requestId, "requestId");
            Objects.requireNonNull(sink.httpClient, "httpClient");
            Objects.requireNonNull(sink.executorService, "executorService");
            if (sink.authKey != null && sink.authCode == null) {
                throw new IllegalStateException("An authKey was set but authCode was null");
            }
            if (sink.authCode != null && sink.authKey == null) {
                throw new IllegalStateException("An authCode was set but authKey was null");
            }
            return sink;
        }

        private Builder() {
            sink = new HttpDataSink();
        }
    }
}
