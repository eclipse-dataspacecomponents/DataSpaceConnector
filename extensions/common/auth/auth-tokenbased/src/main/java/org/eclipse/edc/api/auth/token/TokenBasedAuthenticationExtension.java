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
 *       Mercedes-Benz Tech Innovation GmbH - add README.md; authentication key can be retrieved from vault
 *       Fraunhofer Institute for Software and Systems Engineering - update monitor info
 *
 */

package org.eclipse.edc.api.auth.token;

import org.eclipse.edc.api.auth.spi.ApiAuthenticationProvider;
import org.eclipse.edc.api.auth.spi.AuthenticationService;
import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationProviderRegistry;
import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;

import java.util.Optional;
import java.util.UUID;

import static org.eclipse.edc.web.spi.configuration.WebServiceConfigurer.WEB_HTTP_PREFIX;

/**
 * Extension that registers an AuthenticationService that uses API Keys and register
 * an {@link ApiAuthenticationProvider} under the type called tokenbased
 */
@Extension(value = TokenBasedAuthenticationExtension.NAME)
public class TokenBasedAuthenticationExtension implements ServiceExtension {

    public static final String NAME = "Static token API Authentication";
    public static final String AUTH_KEY = "auth";

    public static final String CONFIG_ALIAS = WEB_HTTP_PREFIX + ".<context>." + AUTH_KEY + ".";
    @Setting(context = CONFIG_ALIAS, value = "The api key to use for the <context>")
    public static final String AUTH_API_KEY = "key";
    @Setting(context = CONFIG_ALIAS, value = "The vault api key alias to use for the <context>")
    public static final String AUTH_API_KEY_ALIAS = "key.alias";
    public static final String TOKENBASED_TYPE = "tokenbased";
    @Setting
    @Deprecated(since = "0.7.1")
    private static final String AUTH_SETTING_APIKEY = "edc.api.auth.key";
    @Setting
    @Deprecated(since = "0.7.1")
    private static final String AUTH_SETTING_APIKEY_ALIAS = "edc.api.auth.key.alias";
    @Inject
    private Vault vault;
    @Inject
    private ApiAuthenticationRegistry authenticationRegistry;

    @Inject
    private ApiAuthenticationProviderRegistry providerRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var apiKey = Optional.ofNullable(context.getSetting(AUTH_SETTING_APIKEY_ALIAS, null))
                .map(alias -> vault.resolveSecret(alias))
                .orElseGet(() -> context.getSetting(AUTH_SETTING_APIKEY, UUID.randomUUID().toString()));

        // only register as fallback, if no other has been registered
        if (!authenticationRegistry.hasService("management-api")) {
            authenticationRegistry.register("management-api", new TokenBasedAuthenticationService(apiKey));
        }

        providerRegistry.register(TOKENBASED_TYPE, this::tokenBasedProvider);
    }

    public Result<AuthenticationService> tokenBasedProvider(Config config) {
        var apiKey = Optional.ofNullable(config.getString(AUTH_API_KEY_ALIAS, null))
                .map(alias -> vault.resolveSecret(alias))
                .orElseGet(() -> config.getString(AUTH_API_KEY));

        return Result.success(new TokenBasedAuthenticationService(apiKey));
    }
}
