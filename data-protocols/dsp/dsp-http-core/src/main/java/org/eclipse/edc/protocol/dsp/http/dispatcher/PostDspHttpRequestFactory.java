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

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspHttpRequestFactory;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspRequestBasePathProvider;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.RequestPathProvider;
import org.eclipse.edc.protocol.dsp.http.spi.serialization.JsonLdRemoteMessageSerializer;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

/**
 * Creates a POST request through the Dataspace Protocol
 *
 * @param <M> the message class.
 */
public class PostDspHttpRequestFactory<M extends RemoteMessage> implements DspHttpRequestFactory<M> {

    public static final String APPLICATION_JSON = "application/json";
    private final RequestPathProvider<M> pathProvider;
    private final JsonLdRemoteMessageSerializer serializer;
    private final DspRequestBasePathProvider dspBasePathProvider;

    public PostDspHttpRequestFactory(JsonLdRemoteMessageSerializer serializer, DspRequestBasePathProvider dspBasePathProvider, RequestPathProvider<M> pathProvider) {
        this.serializer = serializer;
        this.dspBasePathProvider = dspBasePathProvider;
        this.pathProvider = pathProvider;
    }

    @Override
    public Request createRequest(M message) {
        var body = serializer.serialize(message);
        var requestBody = RequestBody.create(body, MediaType.get(APPLICATION_JSON));

        var url = HttpUrl.get(dspBasePathProvider.provideBasePath(message) + pathProvider.providePath(message));

        return new Request.Builder()
                .url(url)
                .header("Content-Type", APPLICATION_JSON)
                .post(requestBody)
                .build();
    }

}
