/*
 *  Copyright (c) 2021 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.dataspaceconnector.ids.api.multipart.dispatcher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import org.eclipse.dataspaceconnector.ids.api.multipart.dispatcher.sender.MultipartArtifactRequestSender;
import org.eclipse.dataspaceconnector.ids.api.multipart.dispatcher.sender.MultipartCatalogDescriptionRequestSender;
import org.eclipse.dataspaceconnector.ids.api.multipart.dispatcher.sender.MultipartContractAgreementSender;
import org.eclipse.dataspaceconnector.ids.api.multipart.dispatcher.sender.MultipartContractOfferSender;
import org.eclipse.dataspaceconnector.ids.api.multipart.dispatcher.sender.MultipartContractRejectionSender;
import org.eclipse.dataspaceconnector.ids.api.multipart.dispatcher.sender.MultipartDescriptionRequestSender;
import org.eclipse.dataspaceconnector.ids.spi.IdsId;
import org.eclipse.dataspaceconnector.ids.spi.IdsIdParser;
import org.eclipse.dataspaceconnector.ids.spi.IdsType;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformerRegistry;
import org.eclipse.dataspaceconnector.spi.EdcException;
import org.eclipse.dataspaceconnector.spi.EdcSetting;
import org.eclipse.dataspaceconnector.spi.iam.IdentityService;
import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.Set;

public class IdsMultipartDispatcherServiceExtension implements ServiceExtension {

    @EdcSetting
    public static final String EDC_IDS_ID = "edc.ids.id";
    public static final String DEFAULT_EDC_IDS_ID = "urn:connector:edc";

    private static final String NAME = "IDS Multipart Dispatcher API extension";

    private Monitor monitor;

    @Override
    public Set<String> requires() {
        return Set.of(IdentityService.FEATURE,
                "edc:ids:transform:v1",
                "dataspaceconnector:dispatcher");
    }

    @Override
    public Set<String> provides() {
        return Set.of("edc:ids:api:multipart:dispatcher:v1");
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();

        var connectorId = resolveConnectorId(context);
        var httpClient = context.getService(OkHttpClient.class);
        var identityService = context.getService(IdentityService.class);
        var vault = context.getService(Vault.class);

        // TODO ObjectMapper needs to be replaced by one capable to write proper IDS JSON-LD
        //      once https://github.com/eclipse-dataspaceconnector/DataSpaceConnector/issues/236 is done
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        // create & register sender and dispatcher
        var transformerRegistry = context.getService(TransformerRegistry.class);

        var multipartDispatcher = new IdsMultipartRemoteMessageDispatcher();
        multipartDispatcher.register(new MultipartArtifactRequestSender(connectorId, httpClient, objectMapper, monitor, vault, identityService, transformerRegistry));
        multipartDispatcher.register(new MultipartDescriptionRequestSender(connectorId, httpClient, objectMapper, monitor, identityService, transformerRegistry));
        multipartDispatcher.register(new MultipartContractOfferSender(connectorId, httpClient, objectMapper, monitor, identityService, transformerRegistry));
        multipartDispatcher.register(new MultipartContractAgreementSender(connectorId, httpClient, objectMapper, monitor, identityService, transformerRegistry));
        multipartDispatcher.register(new MultipartContractRejectionSender(connectorId, httpClient, objectMapper, monitor, identityService, transformerRegistry));
        multipartDispatcher.register(new MultipartCatalogDescriptionRequestSender(connectorId, httpClient, objectMapper, monitor, identityService, transformerRegistry));

        var registry = context.getService(RemoteMessageDispatcherRegistry.class);
        registry.register(multipartDispatcher);

        monitor.info(String.format("Initialized %s", NAME));
    }

    @Override
    public void start() {
        monitor.info(String.format("Started %s", NAME));
    }

    @Override
    public void shutdown() {
        monitor.info(String.format("Shutdown %s", NAME));
    }

    private String resolveConnectorId(@NotNull ServiceExtensionContext context) {
        Objects.requireNonNull(context);

        String value = context.getSetting(EDC_IDS_ID, null);

        if (value == null) {
            String message = "IDS Settings: No setting found for key '%s'. Using default value '%s'";
            monitor.warning(String.format(message, EDC_IDS_ID, DEFAULT_EDC_IDS_ID));
            value = DEFAULT_EDC_IDS_ID;
        }

        try {
            // Hint: use stringified uri to keep uri path and query
            IdsId idsId = IdsIdParser.parse(value);
            if (idsId != null && idsId.getType() == IdsType.CONNECTOR) {
                return idsId.getValue();
            }
        } catch (IllegalArgumentException e) {
            String message = "IDS Settings: Expected valid URN for setting '%s', but was %s'. Expected format: 'urn:connector:[id]'";
            throw new EdcException(String.format(message, EDC_IDS_ID, DEFAULT_EDC_IDS_ID));
        }

        return value;
    }

}
