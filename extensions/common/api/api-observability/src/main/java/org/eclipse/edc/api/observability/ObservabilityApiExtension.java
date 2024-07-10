/*
 *  Copyright (c) 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - Initial implementation
 *
 */

package org.eclipse.edc.api.observability;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.spi.system.apiversion.VersionRecord;
import org.eclipse.edc.spi.system.health.HealthCheckResult;
import org.eclipse.edc.spi.system.health.HealthCheckService;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebService;

import java.io.IOException;
import java.util.stream.Stream;

@Extension(value = ObservabilityApiExtension.NAME)
public class ObservabilityApiExtension implements ServiceExtension {

    public static final String NAME = "Observability API";
    public static final String OBSERVABILITY_CONTEXT = "observability";
    private static final String API_VERSION_JSON_FILE = "observability-api-version.json";
    private final HealthCheckResult result = HealthCheckResult.Builder.newInstance().component(NAME).build();

    @Inject
    private WebService webService;

    @Inject
    private HealthCheckService healthCheckService;
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
        webService.registerResource(new ObservabilityApiController(healthCheckService));

        healthCheckService.addReadinessProvider(() -> result);
        healthCheckService.addLivenessProvider(() -> result);
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
                    .forEach(vr -> apiVersionService.addRecord(OBSERVABILITY_CONTEXT, vr));
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }
}
