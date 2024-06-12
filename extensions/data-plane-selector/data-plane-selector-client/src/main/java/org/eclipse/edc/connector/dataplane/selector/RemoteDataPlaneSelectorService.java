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

package org.eclipse.edc.connector.dataplane.selector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.api.auth.spi.ControlClientAuthenticationProvider;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static jakarta.json.Json.createObjectBuilder;
import static java.lang.String.format;
import static okhttp3.internal.Util.EMPTY_REQUEST;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_PREFIX;

public class RemoteDataPlaneSelectorService implements DataPlaneSelectorService {

    public static final MediaType TYPE_JSON = MediaType.parse("application/json");
    private static final String SELECT_PATH = "/select";
    private final EdcHttpClient httpClient;
    private final String url;
    private final ObjectMapper mapper;
    private final TypeTransformerRegistry typeTransformerRegistry;
    private final String selectionStrategy;
    private final ControlClientAuthenticationProvider authenticationProvider;

    public RemoteDataPlaneSelectorService(EdcHttpClient httpClient, String url, ObjectMapper mapper,
                                          TypeTransformerRegistry typeTransformerRegistry, String selectionStrategy,
                                          ControlClientAuthenticationProvider authenticationProvider) {
        this.httpClient = httpClient;
        this.url = url;
        this.mapper = mapper;
        this.typeTransformerRegistry = typeTransformerRegistry;
        this.selectionStrategy = selectionStrategy;
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    public ServiceResult<List<DataPlaneInstance>> getAll() {
        var requestBuilder = new Request.Builder().get().url(url);

        return request(requestBuilder)
                .compose(this::toJsonArray)
                .map(it -> it.stream()
                        .map(j -> typeTransformerRegistry.transform(j, DataPlaneInstance.class))
                        .filter(Result::succeeded)
                        .map(Result::getContent)
                        .toList()
                );
    }

    @Override
    public ServiceResult<DataPlaneInstance> select(DataAddress source, String transferType, @Nullable String selectionStrategy) {
        var srcAddress = typeTransformerRegistry.transform(source, JsonObject.class).orElseThrow(f -> new EdcException(f.getFailureDetail()));
        var jsonObject = Json.createObjectBuilder()
                .add(CONTEXT, createObjectBuilder().add(EDC_PREFIX, EDC_NAMESPACE))
                .add(TYPE, EDC_NAMESPACE + "SelectionRequest")
                .add(EDC_NAMESPACE + "source", srcAddress)
                .add(EDC_NAMESPACE + "strategy", Optional.ofNullable(selectionStrategy).orElse(this.selectionStrategy))
                .add(EDC_NAMESPACE + "transferType", transferType)
                .build();

        var body = RequestBody.create(jsonObject.toString(), TYPE_JSON);

        var requestBuilder = new Request.Builder().post(body).url(url + SELECT_PATH);

        return request(requestBuilder).compose(this::toJsonObject)
                .map(it -> typeTransformerRegistry.transform(it, DataPlaneInstance.class))
                .compose(ServiceResult::from);
    }

    @Override
    public ServiceResult<Void> addInstance(DataPlaneInstance instance) {
        var transform = typeTransformerRegistry.transform(instance, JsonObject.class);
        if (transform.failed()) {
            return ServiceResult.badRequest(transform.getFailureDetail());
        }

        var requestBody = Json.createObjectBuilder(transform.getContent())
                .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .build();
        var body = RequestBody.create(requestBody.toString(), TYPE_JSON);

        var requestBuilder = new Request.Builder().post(body).url(url);

        return request(requestBuilder).mapEmpty();
    }

    @Override
    public ServiceResult<Void> delete(String instanceId) {
        var requestBuilder = new Request.Builder().delete().url(url + "/" + instanceId);

        return request(requestBuilder).mapEmpty();
    }

    @Override
    public ServiceResult<Void> unregister(String instanceId) {
        var requestBuilder = new Request.Builder().put(EMPTY_REQUEST).url("%s/%s/unregister".formatted(url, instanceId));

        return request(requestBuilder).mapEmpty();
    }

    @Override
    public ServiceResult<DataPlaneInstance> findById(String id) {
        var requestBuilder = new Request.Builder().get().url(url + "/" + id);

        return request(requestBuilder).compose(this::toJsonObject)
                .map(it -> typeTransformerRegistry.transform(it, DataPlaneInstance.class).getContent());
    }

    private <R> ServiceResult<String> request(Request.Builder requestBuilder) {
        authenticationProvider.authenticationHeaders().forEach(requestBuilder::header);
        try (
                var response = httpClient.execute(requestBuilder.build());
                var responseBody = response.body();
        ) {
            var bodyAsString = responseBody == null ? null : responseBody.string();
            if (response.isSuccessful()) {
                return ServiceResult.success(bodyAsString);

            } else {
                return switch (response.code()) {
                    case 400 -> ServiceResult.badRequest("Remote API returned HTTP 400. " + bodyAsString);
                    case 401, 403 -> ServiceResult.unauthorized("Unauthorized. " + bodyAsString);
                    case 404 -> ServiceResult.notFound("Remote API returned HTTP 404." + bodyAsString);
                    case 409 -> ServiceResult.conflict("Remote API returned HTTP 409." + bodyAsString);
                    default -> ServiceResult.unexpected(format("An unknown error happened, HTTP Status = %d. Body %s", response.code(), bodyAsString));
                };
            }
        } catch (IOException exception) {
            return ServiceResult.unexpected("Unexpected IOException. " + exception.getMessage());
        }
    }

    private ServiceResult<JsonObject> toJsonObject(String it) {
        try {
            return ServiceResult.success(mapper.readValue(it, JsonObject.class));
        } catch (JsonProcessingException e) {
            return ServiceResult.unexpected("Cannot deserialize response body as JsonObject");
        }
    }

    private ServiceResult<JsonArray> toJsonArray(String it) {
        try {
            return ServiceResult.success(mapper.readValue(it, JsonArray.class));
        } catch (JsonProcessingException e) {
            return ServiceResult.unexpected("Cannot deserialize response body as JsonObject");
        }
    }


}
