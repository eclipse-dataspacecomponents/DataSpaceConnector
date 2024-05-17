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

package org.eclipse.edc.connector.dataplane.registration;

import org.eclipse.edc.connector.controlplane.transfer.spi.callback.ControlApiUrl;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class DataplaneSelfRegistrationExtensionTest {

    private final DataPlaneSelectorService dataPlaneSelectorService = mock();
    private final ControlApiUrl controlApiUrl = mock();
    private final PipelineService pipelineService = mock();
    private final PublicEndpointGeneratorService publicEndpointGeneratorService = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(DataPlaneSelectorService.class, dataPlaneSelectorService);
        context.registerService(ControlApiUrl.class, controlApiUrl);
        context.registerService(PipelineService.class, pipelineService);
        context.registerService(PublicEndpointGeneratorService.class, publicEndpointGeneratorService);
    }

    @Test
    void shouldRegisterInstanceAtStartup(DataplaneSelfRegistrationExtension extension, ServiceExtensionContext context) throws MalformedURLException {
        when(context.getRuntimeId()).thenReturn("runtimeId");
        when(controlApiUrl.get()).thenReturn(URI.create("http://control/api/url"));
        when(pipelineService.supportedSinkTypes()).thenReturn(Set.of("sinkType", "anotherSinkType"));
        when(pipelineService.supportedSourceTypes()).thenReturn(Set.of("sourceType", "anotherSourceType"));
        when(publicEndpointGeneratorService.supportedDestinationTypes()).thenReturn(Set.of("pullDestType", "anotherPullDestType"));
        when(dataPlaneSelectorService.addInstance(any())).thenReturn(ServiceResult.success());

        extension.initialize(context);
        extension.start();

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(dataPlaneSelectorService).addInstance(captor.capture());
        var dataPlaneInstance = captor.getValue();
        assertThat(dataPlaneInstance.getId()).isEqualTo("runtimeId");
        assertThat(dataPlaneInstance.getUrl()).isEqualTo(new URL("http://control/api/url/v1/dataflows"));
        assertThat(dataPlaneInstance.getAllowedSourceTypes()).containsExactlyInAnyOrder("sourceType", "anotherSourceType");
        assertThat(dataPlaneInstance.getAllowedDestTypes()).containsExactlyInAnyOrder("sinkType", "anotherSinkType");
        assertThat(dataPlaneInstance.getAllowedTransferTypes())
                .containsExactlyInAnyOrder("pullDestType-PULL", "anotherPullDestType-PULL", "sinkType-PUSH", "anotherSinkType-PUSH");
    }

    @Test
    void shouldNotStart_whenRegistrationFails(DataplaneSelfRegistrationExtension extension, ServiceExtensionContext context) {
        when(controlApiUrl.get()).thenReturn(URI.create("http://control/api/url"));
        when(dataPlaneSelectorService.addInstance(any())).thenReturn(ServiceResult.conflict("cannot register"));

        extension.initialize(context);

        assertThatThrownBy(extension::start).isInstanceOf(EdcException.class);
    }

    @Test
    void shouldUnregisterInstanceAtStartup(DataplaneSelfRegistrationExtension extension, ServiceExtensionContext context) {
        when(context.getRuntimeId()).thenReturn("runtimeId");
        when(dataPlaneSelectorService.delete(any())).thenReturn(ServiceResult.success());
        extension.initialize(context);

        extension.shutdown();

        verify(dataPlaneSelectorService).delete("runtimeId");
    }
}
