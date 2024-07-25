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

package org.eclipse.edc.connector.core;

import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.keys.LocalPublicKeyServiceImpl;
import org.eclipse.edc.keys.spi.KeyParserRegistry;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.PublicKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.connector.core.LocalPublicKeyDefaultExtension.EDC_PUBLIC_KEYS_PREFIX;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class LocalPublicKeyDefaultExtensionTest {

    private final KeyParserRegistry keyParserRegistry = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(KeyParserRegistry.class, keyParserRegistry);
    }

    @Test
    void localPublicKeyService(LocalPublicKeyDefaultExtension extension) {
        assertThat(extension.localPublicKeyService()).isInstanceOf(LocalPublicKeyServiceImpl.class);
    }

    @Test
    void localPublicKeyService_withValueConfig(LocalPublicKeyDefaultExtension extension, ServiceExtensionContext context) {

        var keys = Map.of(
                "key1.id", "key1",
                "key1.value", "value");

        when(keyParserRegistry.parsePublic("value")).thenReturn(Result.success(mock(PublicKey.class)));
        when(context.getConfig(EDC_PUBLIC_KEYS_PREFIX)).thenReturn(ConfigFactory.fromMap(keys));
        var localPublicKeyService = extension.localPublicKeyService();
        extension.initialize(context);
        extension.prepare();

        assertThat(localPublicKeyService.resolveKey("key1")).isSucceeded();
    }

    @Test
    void localPublicKeyService_withPathConfig(LocalPublicKeyDefaultExtension extension, ServiceExtensionContext context) {
        var path = TestUtils.getResource("rsa_2048.pem");
        var value = TestUtils.getResourceFileContentAsString("rsa_2048.pem");
        var keys = Map.of(
                "key1.id", "key1",
                "key1.path", path.getPath());

        when(keyParserRegistry.parsePublic(value)).thenReturn(Result.success(mock(PublicKey.class)));
        when(context.getConfig(EDC_PUBLIC_KEYS_PREFIX)).thenReturn(ConfigFactory.fromMap(keys));
        var localPublicKeyService = extension.localPublicKeyService();
        extension.initialize(context);
        extension.prepare();

        assertThat(localPublicKeyService.resolveKey("key1")).isSucceeded();
    }

    @Test
    void localPublicKeyService_shouldRaiseException_withoutValueOrPath(LocalPublicKeyDefaultExtension extension, ServiceExtensionContext context) {
        var keys = Map.of(
                "key1.id", "key1");

        when(context.getConfig(EDC_PUBLIC_KEYS_PREFIX)).thenReturn(ConfigFactory.fromMap(keys));
        extension.initialize(context);

        assertThatThrownBy(extension::prepare).isInstanceOf(EdcException.class);
    }

}