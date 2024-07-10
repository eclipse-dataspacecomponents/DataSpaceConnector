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

package org.eclipse.edc.api.auth.delegated;

import org.eclipse.edc.api.auth.spi.ApiAuthenticationProvider;
import org.eclipse.edc.api.auth.spi.AuthenticationService;
import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationProviderRegistry;
import org.eclipse.edc.api.auth.spi.registry.ApiAuthenticationRegistry;
import org.eclipse.edc.keys.spi.KeyParserRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.token.rules.ExpirationIssuedAtValidationRule;
import org.eclipse.edc.token.rules.NotBeforeValidationRule;
import org.eclipse.edc.token.spi.TokenValidationRulesRegistry;
import org.eclipse.edc.token.spi.TokenValidationService;

import java.time.Clock;

import static com.nimbusds.jose.jwk.source.JWKSourceBuilder.DEFAULT_CACHE_TIME_TO_LIVE;
import static org.eclipse.edc.api.auth.delegated.DelegatedAuthenticationService.MANAGEMENT_API_CONTEXT;
import static org.eclipse.edc.web.spi.configuration.WebServiceConfigurer.WEB_HTTP_PREFIX;

/**
 * Extension that registers an AuthenticationService that delegates authentication and authorization to a third-party IdP
 * and register an {@link ApiAuthenticationProvider} under the type called delegated
 */
@Extension(value = DelegatedAuthenticationExtension.NAME)
public class DelegatedAuthenticationExtension implements ServiceExtension {

    public static final int DEFAULT_VALIDATION_TOLERANCE = 5_000;
    public static final String NAME = "Delegating Authentication Service Extension";
    @Setting(value = "Duration (in ms) that the internal key cache is valid", type = "Long", defaultValue = "" + DEFAULT_CACHE_TIME_TO_LIVE)
    @Deprecated(since = "0.7.1")
    public static final String AUTH_SETTING_CACHE_VALIDITY_MS = "edc.api.auth.dac.cache.validity";
    @Deprecated(since = "0.7.1")
    @Setting(value = "URL where the third-party IdP's public key(s) can be resolved")
    public static final String AUTH_SETTING_KEY_URL = "edc.api.auth.dac.key.url";
    public static final String AUTH_KEY = "auth";
    public static final String CONFIG_ALIAS = WEB_HTTP_PREFIX + ".<context>." + AUTH_KEY + ".";
    @Setting(context = CONFIG_ALIAS, value = "URL where the third-party IdP's public key(s) can be resolved for the configured <context>")
    public static final String AUTH_KEY_URL = "dac.key.url";
    @Setting(context = CONFIG_ALIAS, value = "Duration (in ms) that the internal key cache is valid for the configured <context>", type = "Long", defaultValue = "" + DEFAULT_CACHE_TIME_TO_LIVE)
    public static final String AUTH_CACHE_VALIDITY_MS = "dac.cache.validity";
    public static final String DELEGATED_TYPE = "delegated";
    @Setting(value = "Default token validation time tolerance (in ms), e.g. for nbf or exp claims", defaultValue = "" + DEFAULT_VALIDATION_TOLERANCE)
    private static final String AUTH_SETTING_VALIDATION_TOLERANCE_MS = "edc.api.auth.dac.validation.tolerance";
    @Inject
    private ApiAuthenticationRegistry authenticationRegistry;
    @Inject
    private ApiAuthenticationProviderRegistry providerRegistry;
    @Inject
    private TokenValidationRulesRegistry tokenValidationRulesRegistry;
    @Inject
    private KeyParserRegistry keyParserRegistry;
    @Inject
    private TokenValidationService tokenValidationService;
    @Inject
    private Clock clock;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor().withPrefix("Delegated API Authentication");

        var keyUrl = context.getConfig().getString(AUTH_SETTING_KEY_URL, null);
        if (keyUrl == null) {
            monitor.warning("The '%s' setting was not provided, so the DelegatedAuthenticationService will NOT be registered. In this case, the TokenBasedAuthenticationService usually acts as fallback.".formatted(AUTH_SETTING_KEY_URL));
            return;
        }
        var cacheValidityMs = context.getConfig().getLong(AUTH_SETTING_CACHE_VALIDITY_MS, DEFAULT_CACHE_TIME_TO_LIVE);
        var tolerance = context.getConfig().getInteger(AUTH_SETTING_VALIDATION_TOLERANCE_MS, DEFAULT_VALIDATION_TOLERANCE);

        //todo: currently, only JWKS urls are supported
        var resolver = JwksPublicKeyResolver.create(keyParserRegistry, keyUrl, monitor, cacheValidityMs);

        tokenValidationRulesRegistry.addRule(MANAGEMENT_API_CONTEXT, new NotBeforeValidationRule(clock, tolerance, true));
        tokenValidationRulesRegistry.addRule(MANAGEMENT_API_CONTEXT, new ExpirationIssuedAtValidationRule(clock, tolerance, true));

        // always register - this would potentially overwrite other services
        authenticationRegistry.register("management-api", new DelegatedAuthenticationService(resolver, monitor, tokenValidationService, tokenValidationRulesRegistry));

        providerRegistry.register(DELEGATED_TYPE, (cfg) -> delegatedProvider(monitor, cfg));
    }

    public Result<AuthenticationService> delegatedProvider(Monitor monitor, Config config) {
        var keyUrl = config.getString(AUTH_KEY_URL);
        var cacheValidityMs = config.getLong(AUTH_CACHE_VALIDITY_MS, DEFAULT_CACHE_TIME_TO_LIVE);
        var resolver = JwksPublicKeyResolver.create(keyParserRegistry, keyUrl, monitor, cacheValidityMs);

        return Result.success(new DelegatedAuthenticationService(resolver, monitor, tokenValidationService, tokenValidationRulesRegistry));
    }
}
