/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
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

package org.eclipse.edc.connector.api.management.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.json.Json;
import org.eclipse.edc.api.auth.spi.AuthenticationRequestFilter;
import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationRegistry;
import org.eclipse.edc.connector.api.management.configuration.transform.JsonObjectFromContractAgreementTransformer;
import org.eclipse.edc.connector.controlplane.transform.edc.from.JsonObjectFromAssetTransformer;
import org.eclipse.edc.connector.controlplane.transform.edc.to.JsonObjectToAssetTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.connector.controlplane.transform.odrl.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.SettingContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.agent.ParticipantIdMapper;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.spi.system.apiversion.VersionRecord;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromDataAddressTransformer;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToDataAddressTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.providers.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.WebServiceConfiguration;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;
import org.eclipse.edc.web.spi.configuration.context.ManagementApiUrl;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Configure 'management' api context.
 */
@Provides(ManagementApiUrl.class)
@Extension(ManagementApiConfigurationExtension.NAME)
public class ManagementApiConfigurationExtension implements ServiceExtension {

    public static final String API_VERSION_JSON_FILE = "management-api-version.json";
    public static final String NAME = "Management API configuration";
    public static final String WEB_SERVICE_NAME = "Management API";

    @SettingContext("Management API context setting key")
    private static final String MANAGEMENT_CONFIG_KEY = "web.http." + ApiContext.MANAGEMENT;

    public static final WebServiceSettings SETTINGS = WebServiceSettings.Builder.newInstance()
            .apiConfigKey(MANAGEMENT_CONFIG_KEY)
            .contextAlias(ApiContext.MANAGEMENT)
            .defaultPath("/api/v1/management")
            .defaultPort(8181)
            .useDefaultContext(true)
            .name(WEB_SERVICE_NAME)
            .build();
    private static final String MANAGEMENT_SCOPE = "MANAGEMENT_API";

    @Setting(value = "Configures endpoint for reaching the Management API.", defaultValue = "<hostname:management.port/management.path>")
    private static final String MANAGEMENT_API_ENDPOINT = "edc.management.endpoint";

    @Inject
    private WebService webService;
    @Inject
    private WebServer webServer;
    @Inject
    private ApiAuthenticationRegistry authenticationRegistry;
    @Inject
    private WebServiceConfigurer configurator;
    @Inject
    private TypeManager typeManager;
    @Inject
    private JsonLd jsonLd;
    @Inject
    private TypeTransformerRegistry transformerRegistry;
    @Inject
    private ParticipantIdMapper participantIdMapper;
    @Inject
    private Hostname hostname;

    @Inject
    private ApiVersionService apiVersionService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var config = context.getConfig(MANAGEMENT_CONFIG_KEY);
        var webServiceConfiguration = configurator.configure(config, webServer, SETTINGS);

        context.registerService(ManagementApiUrl.class, managementApiUrl(context, webServiceConfiguration));

        var authenticationFilter = new AuthenticationRequestFilter(authenticationRegistry, "management-api");
        webService.registerResource(ApiContext.MANAGEMENT, authenticationFilter);

        jsonLd.registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, MANAGEMENT_SCOPE);
        var jsonLdMapper = typeManager.getMapper(JSON_LD);
        webService.registerResource(ApiContext.MANAGEMENT, new ObjectMapperProvider(jsonLdMapper));
        webService.registerResource(ApiContext.MANAGEMENT, new JerseyJsonLdInterceptor(jsonLd, jsonLdMapper, MANAGEMENT_SCOPE));

        var managementApiTransformerRegistry = transformerRegistry.forContext("management-api");

        var factory = Json.createBuilderFactory(Map.of());
        managementApiTransformerRegistry.register(new JsonObjectFromContractAgreementTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromDataAddressTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromAssetTransformer(factory, jsonLdMapper));
        managementApiTransformerRegistry.register(new JsonObjectFromPolicyTransformer(factory, participantIdMapper));
        managementApiTransformerRegistry.register(new JsonObjectFromQuerySpecTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromCriterionTransformer(factory, jsonLdMapper));

        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper).forEach(managementApiTransformerRegistry::register);
        managementApiTransformerRegistry.register(new JsonObjectToDataAddressTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToCriterionTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToAssetTransformer());
        managementApiTransformerRegistry.register(new JsonValueToGenericTypeTransformer(jsonLdMapper));

        registerVersionInfo(getClass().getClassLoader());
    }

    private void registerVersionInfo(ClassLoader resourceClassLoader) {
        try (var versionContent = resourceClassLoader.getResourceAsStream(API_VERSION_JSON_FILE)) {
            if (versionContent == null) {
                throw new EdcException("Version file not found or not readable.");
            }
            Stream.of(typeManager.getMapper()
                            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                            .readValue(versionContent, VersionRecord[].class))
                    .forEach(vr -> apiVersionService.addRecord(ApiContext.MANAGEMENT, vr));
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }

    private ManagementApiUrl managementApiUrl(ServiceExtensionContext context, WebServiceConfiguration config) {
        var callbackAddress = context.getSetting(MANAGEMENT_API_ENDPOINT, format("http://%s:%s%s", hostname.get(), config.getPort(), config.getPath()));
        try {
            var url = URI.create(callbackAddress);
            return () -> url;
        } catch (IllegalArgumentException e) {
            context.getMonitor().severe("Error creating management plane endpoint url", e);
            throw new EdcException(e);
        }
    }
}
