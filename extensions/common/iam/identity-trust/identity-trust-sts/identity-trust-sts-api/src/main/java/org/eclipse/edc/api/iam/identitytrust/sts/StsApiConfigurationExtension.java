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

package org.eclipse.edc.api.iam.identitytrust.sts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.SettingContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.spi.system.apiversion.VersionRecord;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;

import java.io.IOException;
import java.util.stream.Stream;

@Extension(value = StsApiConfigurationExtension.NAME)
public class StsApiConfigurationExtension implements ServiceExtension {

    public static final String NAME = "Secure Token Service API configuration";
    private static final String WEB_SERVICE_NAME = "STS API";
    private static final int DEFAULT_STS_API_PORT = 9292;
    private static final String DEFAULT_STS_API_CONTEXT_PATH = "/api/v1/sts";

    @SettingContext("Sts API context setting key")
    private static final String STS_CONFIG_KEY = "web.http." + ApiContext.STS;

    public static final WebServiceSettings SETTINGS = WebServiceSettings.Builder.newInstance()
            .apiConfigKey(STS_CONFIG_KEY)
            .contextAlias(ApiContext.STS)
            .defaultPath(DEFAULT_STS_API_CONTEXT_PATH)
            .defaultPort(DEFAULT_STS_API_PORT)
            .useDefaultContext(false)
            .name(WEB_SERVICE_NAME)
            .build();
    private static final String API_VERSION_JSON_FILE = "sts-api-version.json";

    @Inject
    private WebServer webServer;
    @Inject
    private WebServiceConfigurer configurator;
    @Inject
    private TypeManager typeManager;
    @Inject
    private ApiVersionService apiVersionService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var config = context.getConfig(STS_CONFIG_KEY);
        configurator.configure(config, webServer, SETTINGS);
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
                    .forEach(vr -> apiVersionService.addRecord(ApiContext.STS, vr));
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }
}
