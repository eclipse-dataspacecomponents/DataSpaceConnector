/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.http.dispatcher;

import okhttp3.MediaType;
import okio.Buffer;
import org.eclipse.edc.protocol.dsp.http.TestMessage;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspRequestBasePathProvider;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.RequestPathProvider;
import org.eclipse.edc.protocol.dsp.http.spi.serialization.JsonLdRemoteMessageSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostDspHttpRequestFactoryTest {

    private final RequestPathProvider<TestMessage> pathProvider = mock();
    private final JsonLdRemoteMessageSerializer serializer = mock();
    private final DspRequestBasePathProvider dspRequestBasePathProvider = mock();
    private final PostDspHttpRequestFactory<TestMessage> factory = new PostDspHttpRequestFactory<>(serializer, dspRequestBasePathProvider, pathProvider);

    @Test
    void shouldCreateProperHttpRequest() {
        when(serializer.serialize(any())).thenReturn("serializedMessage");
        when(dspRequestBasePathProvider.provideBasePath(any())).thenReturn("http://counter-party");
        when(pathProvider.providePath(any())).thenReturn("/message/request/path");

        var message = new TestMessage("protocol", "http://counter-party", "counterPartyId");
        var request = factory.createRequest(message);

        assertThat(request.url().url().toString()).isEqualTo("http://counter-party/message/request/path");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.header("Content-Type")).isEqualTo("application/json");
        assertThat(request.body()).isNotNull().satisfies(body -> {
            assertThat(body.contentType()).isNotNull().extracting(MediaType::toString).isEqualTo("application/json; charset=utf-8");
            try (var buffer = new Buffer()) {
                body.writeTo(buffer);
                assertThat(buffer.toString()).contains("serializedMessage");
            }

        });
    }


}
