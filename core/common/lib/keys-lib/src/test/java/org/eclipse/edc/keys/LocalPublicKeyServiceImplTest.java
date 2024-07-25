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

package org.eclipse.edc.keys;

import org.eclipse.edc.junit.assertions.AbstractResultAssert;
import org.eclipse.edc.keys.spi.KeyParserRegistry;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.PublicKey;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalPublicKeyServiceImplTest {

    private final Vault vault = mock();
    private final KeyParserRegistry keyParserRegistry = mock();
    private LocalPublicKeyServiceImpl localPublicKeyService;

    @BeforeEach
    void setup() {
        localPublicKeyService = new LocalPublicKeyServiceImpl(vault, keyParserRegistry);
    }

    @Test
    void resolve_withCache() {
        when(keyParserRegistry.parsePublic("value")).thenReturn(Result.success(mock(PublicKey.class)));
        localPublicKeyService.addRawKey("id", "value");
        AbstractResultAssert.assertThat(localPublicKeyService.resolveKey("id")).isSucceeded();
        Mockito.verifyNoInteractions(vault);
    }

    @Test
    void resolve_withVault() {
        when(vault.resolveSecret("id")).thenReturn("value");
        when(keyParserRegistry.parsePublic("value")).thenReturn(Result.success(mock(PublicKey.class)));
        AbstractResultAssert.assertThat(localPublicKeyService.resolveKey("id")).isSucceeded();

        verify(vault).resolveSecret("id");
    }

    @Test
    void resolve_notFound() {
        AbstractResultAssert.assertThat(localPublicKeyService.resolveKey("id")).isFailed();
        verify(vault).resolveSecret("id");
    }

    @Test
    void resolve_wrongKeyFormat() {
        when(vault.resolveSecret("id")).thenReturn("value");
        when(keyParserRegistry.parsePublic("value")).thenReturn(Result.failure("failure"));

        AbstractResultAssert.assertThat(localPublicKeyService.resolveKey("id")).isFailed();
        verify(vault).resolveSecret("id");
    }
}
