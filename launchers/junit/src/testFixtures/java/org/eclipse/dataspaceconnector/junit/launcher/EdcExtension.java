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

package org.eclipse.dataspaceconnector.junit.launcher;

import okhttp3.Interceptor;
import org.eclipse.dataspaceconnector.monitor.MonitorProvider;
import org.eclipse.dataspaceconnector.spi.EdcException;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.SystemExtension;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.system.DefaultServiceExtensionContext;
import org.eclipse.dataspaceconnector.system.ExtensionLoader;
import org.eclipse.dataspaceconnector.system.ServiceLocator;
import org.eclipse.dataspaceconnector.system.ServiceLocatorImpl;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.eclipse.dataspaceconnector.common.types.Cast.cast;

/**
 * A JUnit extension for running an embedded EDC runtime as part of a test fixture.
 * This extension attaches a EDC runtime to the {@link BeforeTestExecutionCallback} and {@link AfterTestExecutionCallback} lifecycle hooks. Parameter injection of runtime services is supported.
 */
public class EdcExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver {
    private final LinkedHashMap<Class<?>, Object> serviceMocks = new LinkedHashMap<>();
    private final LinkedHashMap<Class<? extends SystemExtension>, List<SystemExtension>> systemExtensions = new LinkedHashMap<>();
    private List<ServiceExtension> runningServiceExtensions;
    private DefaultServiceExtensionContext context;
    private Monitor monitor;

    /**
     * Registers a mock service with the runtime.
     *
     * @param mock the service mock
     */
    public <T> void registerServiceMock(Class<T> type, T mock) {
        serviceMocks.put(type, mock);
    }

    /**
     * Registers a service extension with the runtime.
     */
    public <T extends SystemExtension> void registerSystemExtension(Class<T> type, SystemExtension extension) {
        systemExtensions.computeIfAbsent(type, k -> new ArrayList<>()).add(extension);
    }

    public void registerInterceptor(Interceptor interceptor) {

    }

    @Override
    public void beforeTestExecution(ExtensionContext extensionContext) {
        var typeManager = new TypeManager();

        monitor = ExtensionLoader.loadMonitor();

        MonitorProvider.setInstance(monitor);

        context = new DefaultServiceExtensionContext(typeManager, monitor, new MultiSourceServiceLocator());
        context.initialize();

        serviceMocks.forEach((key, value) -> context.registerService(cast(key), value));

        try {
            if (!serviceMocks.containsKey(Vault.class)) {
                ExtensionLoader.loadVault(context);
            }

            runningServiceExtensions = context.loadServiceExtensions();

            ExtensionLoader.bootServiceExtensions(runningServiceExtensions, context);
        } catch (Exception e) {
            throw new EdcException(e);
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (runningServiceExtensions != null) {
            var iter = runningServiceExtensions.listIterator(runningServiceExtensions.size());
            while (iter.hasPrevious()) {
                ServiceExtension extension = iter.previous();
                extension.shutdown();
                monitor.info("Shutdown " + extension);
            }
        }

        // clear the systemExtensions map to prevent it from piling up between subsequent runs
        systemExtensions.clear();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        var type = parameterContext.getParameter().getParameterizedType();
        if (type.equals(EdcExtension.class)) {
            return true;
        } else if (type instanceof Class) {
            return context.hasService(cast(type));
        }
        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        var type = parameterContext.getParameter().getParameterizedType();
        if (type.equals(EdcExtension.class)) {
            return this;
        } else if (type instanceof Class) {
            return context.getService(cast(type));
        }
        return null;
    }

    /**
     * A service locator that allows additional extensions to be manually loaded by a test fixture. This locator return the union of registered extensions and extensions loaded
     * by the delegate.
     */
    private class MultiSourceServiceLocator implements ServiceLocator {
        private final ServiceLocator delegate = new ServiceLocatorImpl();

        @Override
        public <T> List<T> loadImplementors(Class<T> type, boolean required) {
            List<T> extensions = cast(systemExtensions.getOrDefault(type, new ArrayList<>()));
            extensions.addAll(delegate.loadImplementors(type, required));
            return extensions;
        }

        /**
         * This implementation will override singleton implementions found by the delegate.
         */
        @Override
        public <T> T loadSingletonImplementor(Class<T> type, boolean required) {
            List<SystemExtension> extensions = systemExtensions.get(type);
            if (extensions == null || extensions.isEmpty()) {
                return delegate.loadSingletonImplementor(type, required);
            } else if (extensions.size() > 1) {
                throw new EdcException("Multiple extensions were registered for type: " + type.getName());
            }
            return type.cast(extensions.get(0));
        }
    }

}
