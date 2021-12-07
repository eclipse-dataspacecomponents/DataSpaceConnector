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

package org.eclipse.dataspaceconnector.configuration.fs;

import org.eclipse.dataspaceconnector.spi.EdcException;
import org.eclipse.dataspaceconnector.spi.EdcSetting;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.system.ConfigurationExtension;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.String.format;
import static org.eclipse.dataspaceconnector.common.configuration.ConfigurationFunctions.propOrEnv;

/**
 * Sources configuration values from a properties file.
 */
public class FsConfigurationExtension implements ConfigurationExtension {

    @EdcSetting
    private static final String CONFIG_LOCATION = propOrEnv("edc.fs.config", "dataspaceconnector-configuration.properties");

    private final Map<String, String> propertyCache = new HashMap<>();
    private Path configFile;

    /**
     * Default ctor - required for extension loading
     */
    public FsConfigurationExtension() {
    }

    /**
     * Testing ctor
     */
    FsConfigurationExtension(Path configFile) {
        this.configFile = configFile;
    }

    @Override
    public String name() {
        return "FS Configuration";
    }

    @Override
    public void initialize(Monitor monitor) {
        var configPath = configFile != null ? configFile : Paths.get(FsConfigurationExtension.CONFIG_LOCATION);
        if (!Files.exists(configPath)) {
            monitor.info(format("Configuration file does not exist: %s. Ignoring.", FsConfigurationExtension.CONFIG_LOCATION));
            return;
        }

        try (InputStream is = Files.newInputStream(configPath)) {
            var properties = new Properties();
            properties.load(is);
            for (String name : properties.stringPropertyNames()) {
                propertyCache.put(name, properties.getProperty(name));
            }
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }

    @Override
    public @Nullable String getSetting(String key) {
        return propertyCache.get(key);
    }
}
