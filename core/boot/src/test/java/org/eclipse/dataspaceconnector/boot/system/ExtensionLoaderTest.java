/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
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

package org.eclipse.dataspaceconnector.boot.system;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.eclipse.dataspaceconnector.spi.monitor.ConsoleMonitor;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.monitor.MultiplexingMonitor;
import org.eclipse.dataspaceconnector.spi.security.CertificateResolver;
import org.eclipse.dataspaceconnector.spi.security.PrivateKeyResolver;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.MonitorExtension;
import org.eclipse.dataspaceconnector.spi.system.VaultExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionLoaderTest {

    @BeforeAll
    public static void setup() {
        // mock default open telemetry
        GlobalOpenTelemetry.set(mock(OpenTelemetry.class));
    }

    @Test
    void loadMonitor_whenSingleMonitorExtension() {
        var mockedMonitor = mock(Monitor.class);
        var exts = new ArrayList<MonitorExtension>();
        exts.add(() -> mockedMonitor);

        var monitor = ExtensionLoader.loadMonitor(exts);

        assertEquals(mockedMonitor, monitor);
    }

    @Test
    void loadMonitor_whenMultipleMonitorExtensions() {
        var exts = new ArrayList<MonitorExtension>();
        exts.add(() -> mock(Monitor.class));
        exts.add(ConsoleMonitor::new);

        var monitor = ExtensionLoader.loadMonitor(exts);

        assertTrue(monitor instanceof MultiplexingMonitor);
    }

    @Test
    void loadMonitor_whenNoMonitorExtension() {
        var monitor = ExtensionLoader.loadMonitor(new ArrayList<>());

        assertTrue(monitor instanceof ConsoleMonitor);
    }

    @Test
    void selectOpenTelemetryImpl_whenNoOpenTelemetry() {
        var openTelemetry = ExtensionLoader.selectOpenTelemetryImpl(emptyList());

        assertThat(openTelemetry).isEqualTo(GlobalOpenTelemetry.get());
    }

    @Test
    void selectOpenTelemetryImpl_whenSingleOpenTelemetry() {
        var customOpenTelemetry = mock(OpenTelemetry.class);

        var openTelemetry = ExtensionLoader.selectOpenTelemetryImpl(List.of(customOpenTelemetry));

        assertThat(openTelemetry).isSameAs(customOpenTelemetry);
    }

    @Test
    void selectOpenTelemetryImpl_whenSeveralOpenTelemetry() {
        var customOpenTelemetry1 = mock(OpenTelemetry.class);
        var customOpenTelemetry2 = mock(OpenTelemetry.class);

        Exception thrown = assertThrows(IllegalStateException.class,
                () -> ExtensionLoader.selectOpenTelemetryImpl(List.of(customOpenTelemetry1, customOpenTelemetry2)));
        assertEquals(thrown.getMessage(), "Found 2 OpenTelemetry implementations. Please provide only one OpenTelemetry service provider.");
    }

    @Test
    void loadVault_whenNotRegistered() {
        DefaultServiceExtensionContext contextMock = mock(DefaultServiceExtensionContext.class);

        when(contextMock.getMonitor()).thenReturn(mock(Monitor.class));
        when(contextMock.loadSingletonExtension(VaultExtension.class, false)).thenReturn(null);

        ExtensionLoader.loadVault(contextMock);

        verify(contextMock).registerService(eq(Vault.class), isA(Vault.class));
        verify(contextMock).registerService(eq(PrivateKeyResolver.class), any());
        verify(contextMock).registerService(eq(CertificateResolver.class), any());
        verify(contextMock, atLeastOnce()).getMonitor();
        verify(contextMock).loadSingletonExtension(VaultExtension.class, false);
    }

    @Test
    void loadVault() {
        DefaultServiceExtensionContext contextMock = mock(DefaultServiceExtensionContext.class);
        Vault vaultMock = mock(Vault.class);
        PrivateKeyResolver resolverMock = mock(PrivateKeyResolver.class);
        CertificateResolver certResolverMock = mock(CertificateResolver.class);
        when(contextMock.getMonitor()).thenReturn(mock(Monitor.class));
        when(contextMock.loadSingletonExtension(VaultExtension.class, false)).thenReturn(new VaultExtension() {

            @Override
            public Vault getVault() {
                return vaultMock;
            }

            @Override
            public PrivateKeyResolver getPrivateKeyResolver() {
                return resolverMock;
            }

            @Override
            public CertificateResolver getCertificateResolver() {
                return certResolverMock;
            }
        });

        ExtensionLoader.loadVault(contextMock);

        verify(contextMock, times(1)).registerService(Vault.class, vaultMock);
        verify(contextMock, atLeastOnce()).getMonitor();
        verify(contextMock).loadSingletonExtension(VaultExtension.class, false);
    }
}
