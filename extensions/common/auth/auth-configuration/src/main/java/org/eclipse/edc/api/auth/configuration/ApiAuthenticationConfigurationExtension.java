/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.api.auth.configuration;

import org.eclipse.edc.api.auth.spi.AuthenticationRequestFilter;
import org.eclipse.edc.api.auth.spi.AuthenticationService;
import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationProviderRegistry;
import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.web.spi.WebService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.eclipse.edc.api.auth.configuration.ApiAuthenticationConfigurationExtension.NAME;
import static org.eclipse.edc.web.spi.configuration.WebServiceConfigurer.WEB_HTTP_PREFIX;

@Extension(NAME)
public class ApiAuthenticationConfigurationExtension implements ServiceExtension {

    public static final String NAME = "Api Authentication Configuration Extension";

    public static final String AUTH_KEY = "auth";
    public static final String CONFIG_ALIAS = WEB_HTTP_PREFIX + ".<context>." + AUTH_KEY + ".";

    @Setting(context = CONFIG_ALIAS, value = "The type of the authentication provider.", required = true)
    public static final String TYPE_KEY = "type";

    @Setting(context = CONFIG_ALIAS, value = "The api context where to apply the authentication. Default to the web <context>")
    public static final String CONTEXT_KEY = "context";

    private Map<String, Config> authConfiguration = new HashMap<>();

    @Inject
    private ApiAuthenticationProviderRegistry providerRegistry;

    @Inject
    private ApiAuthenticationRegistry authenticationRegistry;

    @Inject
    private WebService webService;


    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        authConfiguration = context.getConfig(WEB_HTTP_PREFIX)
                .partition().filter(config -> config.getString(AUTH_KEY + "." + TYPE_KEY, null) != null)
                .map(cfg -> Map.entry(cfg.currentNode(), cfg.getConfig(AUTH_KEY)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void prepare() {
        for (var entry : authConfiguration.entrySet()) {
            var webContext = entry.getKey();
            var apiContext = Optional.ofNullable(entry.getValue().getString(CONTEXT_KEY, null)).orElse(webContext);
            var serviceResult = configureService(entry.getValue());
            if (serviceResult.failed()) {
                throw new EdcException("Failed to configure authentication for context %s: %s".formatted(entry.getKey(), serviceResult.getFailureDetail()));
            }
            authenticationRegistry.register(apiContext, serviceResult.getContent());
            var authenticationFilter = new AuthenticationRequestFilter(authenticationRegistry, apiContext);
            webService.registerResource(entry.getKey(), authenticationFilter);
        }
    }

    private Result<AuthenticationService> configureService(Config config) {
        var type = config.getString(TYPE_KEY);
        return Optional.ofNullable(providerRegistry.resolve(type))
                .map(provider -> provider.provide(config))
                .orElseGet(() -> Result.failure("Authentication provider for type %s not found".formatted(type)));
    }
}
