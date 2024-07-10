/*
 *  Copyright (c) 2020 - 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.api.control.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.eclipse.edc.api.auth.spi.AuthenticationRequestFilter;
import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationRegistry;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.SettingContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.spi.system.apiversion.VersionRecord;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.providers.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.WebServiceConfiguration;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;
import org.eclipse.edc.web.spi.configuration.context.ControlApiUrl;

import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Tells all the Control API controllers under which context alias they need to register their resources: either
 * `default` or `control`
 */
@Extension(value = ControlApiConfigurationExtension.NAME)
@Provides(ControlApiUrl.class)
public class ControlApiConfigurationExtension implements ServiceExtension {

    public static final String NAME = "Control API configuration";

    @Setting(value = "Configures endpoint for reaching the Control API. If it's missing it defaults to the hostname configuration.")
    public static final String CONTROL_API_ENDPOINT = "edc.control.endpoint";

    private static final String WEB_SERVICE_NAME = "Control API";

    @SettingContext("Control API context setting key")
    private static final String CONTROL_CONFIG_KEY = "web.http." + ApiContext.CONTROL;

    public static final WebServiceSettings SETTINGS = WebServiceSettings.Builder.newInstance()
            .apiConfigKey(CONTROL_CONFIG_KEY)
            .contextAlias(ApiContext.CONTROL)
            .defaultPath("/api/v1/control")
            .defaultPort(9191)
            .useDefaultContext(true)
            .name(WEB_SERVICE_NAME)
            .build();
    private static final String CONTROL_SCOPE = "CONTROL_API";
    private static final String API_VERSION_JSON_FILE = "control-api-version.json";

    @Inject
    private WebServer webServer;
    @Inject
    private WebServiceConfigurer configurator;
    @Inject
    private WebService webService;
    @Inject
    private Hostname hostname;
    @Inject
    private JsonLd jsonLd;
    @Inject
    private TypeManager typeManager;
    @Inject
    private ApiAuthenticationRegistry authenticationRegistry;

    @Inject
    private ApiVersionService apiVersionService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var config = context.getConfig(CONTROL_CONFIG_KEY);
        var controlApiConfiguration = configurator.configure(config, webServer, SETTINGS);
        var jsonLdMapper = typeManager.getMapper(JSON_LD);
        context.registerService(ControlApiUrl.class, controlApiUrl(context, controlApiConfiguration));

        jsonLd.registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, CONTROL_SCOPE);
        jsonLd.registerNamespace(DSPACE_PREFIX, DSPACE_SCHEMA, CONTROL_SCOPE);

        var authenticationRequestFilter = new AuthenticationRequestFilter(authenticationRegistry, "control-api");
        webService.registerResource(ApiContext.CONTROL, authenticationRequestFilter);
        webService.registerResource(ApiContext.CONTROL, new ObjectMapperProvider(jsonLdMapper));
        webService.registerResource(ApiContext.CONTROL, new JerseyJsonLdInterceptor(jsonLd, jsonLdMapper, CONTROL_SCOPE));

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
                    .forEach(vr -> apiVersionService.addRecord(ApiContext.CONTROL, vr));
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }

    private ControlApiUrl controlApiUrl(ServiceExtensionContext context, WebServiceConfiguration config) {
        var callbackAddress = context.getSetting(CONTROL_API_ENDPOINT, format("http://%s:%s%s", hostname.get(), config.getPort(), config.getPath()));
        try {
            var url = URI.create(callbackAddress);
            return () -> url;
        } catch (IllegalArgumentException e) {
            context.getMonitor().severe("Error creating control plane endpoint url", e);
            throw new EdcException(e);
        }
    }
}
