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

package org.eclipse.edc.junit.extensions;

import org.eclipse.edc.boot.system.runtime.BaseRuntime;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.SystemExtension;
import org.eclipse.edc.spi.system.configuration.Config;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.edc.boot.system.ExtensionLoader.loadMonitor;

/**
 * Embedded runtime that runs inside another runtime
 */
public class EmbeddedRuntime extends BaseRuntime {

    private static final Monitor MONITOR = loadMonitor();

    private final String name;
    private final Map<String, String> properties;
    private final LinkedHashMap<Class<?>, Object> serviceMocks = new LinkedHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final MultiSourceServiceLocator serviceLocator;
    private final URL[] classPathEntries;

    public EmbeddedRuntime(String name, Map<String, String> properties, String... additionalModules) {
        this(new MultiSourceServiceLocator(), name, properties, ClasspathReader.classpathFor(additionalModules));
    }

    public EmbeddedRuntime(String name, Map<String, String> properties, URL[] classpathEntries) {
        this(new MultiSourceServiceLocator(), name, properties, classpathEntries);
    }

    private EmbeddedRuntime(MultiSourceServiceLocator serviceLocator, String name, Map<String, String> properties, URL[] classPathEntries) {
        super(serviceLocator);
        this.serviceLocator = serviceLocator;
        this.name = name;
        this.properties = properties;
        this.classPathEntries = classPathEntries;
    }

    @Override
    public void boot(boolean addShutdownHook) {
        try {
            MONITOR.info("Starting runtime %s".formatted(name));

            // Temporarily inject system properties.
            var savedProperties = (Properties) System.getProperties().clone();
            properties.forEach(System::setProperty);

            var runtimeException = new AtomicReference<Exception>();
            var latch = new CountDownLatch(1);

            executorService.execute(() -> {
                try {
                    var classLoader = URLClassLoader.newInstance(classPathEntries);

                    Thread.currentThread().setContextClassLoader(classLoader);

                    super.boot(false);

                    latch.countDown();
                } catch (Exception e) {
                    runtimeException.set(e);
                    throw new EdcException(e);
                }
            });

            if (!latch.await(20, SECONDS)) {
                throw new EdcException("Failed to start EDC runtime", runtimeException.get());
            }

            MONITOR.info("Runtime %s started".formatted(name));
            // Restore system properties.
            System.setProperties(savedProperties);
        } catch (Exception e) {
            throw new EdcException(e);
        }

    }

    @Override
    public void shutdown() {
        serviceLocator.clearSystemExtensions();
        super.shutdown();
    }

    @Override
    protected @NotNull ServiceExtensionContext createContext(Monitor monitor, Config config) {
        return new TestServiceExtensionContext(monitor, config, serviceMocks);
    }

    @Override
    protected @NotNull Monitor createMonitor() {
        // disable logs when "quiet" log level is set
        if (System.getProperty("org.gradle.logging.level") != null) {
            return new Monitor() {
            };
        } else {
            return new ConsoleMonitor(name, ConsoleMonitor.Level.DEBUG, true);
        }
    }

    public <T extends SystemExtension> EmbeddedRuntime registerSystemExtension(Class<T> type, SystemExtension extension) {
        serviceLocator.registerSystemExtension(type, extension);
        return this;
    }

    public <T> EmbeddedRuntime registerServiceMock(Class<T> type, T mock) {
        serviceMocks.put(type, mock);
        return this;
    }

    public <T> T getService(Class<T> clazz) {
        return context.getService(clazz);
    }

    public ServiceExtensionContext getContext() {
        return context;
    }
}
