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

package org.eclipse.edc.keys.keyparsers;

import org.assertj.core.api.Assertions;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.stream.Stream;

import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.Mockito.mock;


/**
 * Tests various private key types (EC, RSA, EdDSA) provided as PEM files.
 * Please note that the test data used here was generated externally using {@code openssl}:
 * <ul>
 *     <li>{@code openssl ecparam -name secp384r1 -genkey -noout -out ec_p256.pem}</li> (and other curves, respectively)
 *     <li>{@code openssl genpkey -algorithm ed25519 -outform PEM -out ed25519.pem}</li>
 *     <li>{@code openssl genrsa -out rsa_2048.pem 2048}</li>
 * </ul>
 * Public Keys were extracted using their respective methods.
 * <p>
 * <strong>Other key types, curves, etc. have NOT been tested!</strong>
 */
class PemParserTest {
    private final PemParser parser = new PemParser(mock());

    @ParameterizedTest
    @ArgumentsSource(PemPrivateKeyProvider.class)
    void canHandle(String pem) {
        Assertions.assertThat(parser.canHandle(pem)).isTrue();
    }

    @ParameterizedTest
    @ArgumentsSource(PemPrivateKeyProvider.class)
    void parse_privateKey(String pem) {
        var result = parser.parse(pem);
        assertThat(result)
                .isSucceeded()
                .isNotNull()
                .isInstanceOf(PrivateKey.class);

    }


    @ParameterizedTest
    @ArgumentsSource(PemPublicKeyProvider.class)
    void parse_publicKey(String pem) {

        assertThat(parser.parse(pem))
                .isSucceeded()
                .isNotNull()
                .isInstanceOf(PublicKey.class);
    }


    @ParameterizedTest
    @ArgumentsSource(PemConvertiblePrivateKeyProvider.class)
    void parsePublic_withPrivateKey(String pem) {
        var result = parser.parsePublic(pem);
        assertThat(result)
                .isSucceeded()
                .isNotNull()
                .isInstanceOf(PublicKey.class);

    }

    @Test
    void parsePublic_withPrivateKey_whenEd25519_shouldFail() {
        var pem = TestUtils.getResourceFileContentAsString("ed25519.pem");
        var result = parser.parsePublic(pem);
        assertThat(result)
                .isFailed()
                .detail().isEqualTo("PEM-encoded structure did not contain a public key.");
    }


    @ParameterizedTest
    @ArgumentsSource(PemPublicKeyProvider.class)
    void parsePublic_withPublicKey(String pem) {

        assertThat(parser.parsePublic(pem))
                .isSucceeded()
                .isNotNull()
                .isInstanceOf(PublicKey.class);
    }

    private static class PemPrivateKeyProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(Named.named("RSA PrivateKey", TestUtils.getResourceFileContentAsString("rsa_2048.pem"))),
                    Arguments.of(Named.named("EC PrivateKey (P256)", TestUtils.getResourceFileContentAsString("ec_p256.pem"))),
                    Arguments.of(Named.named("EC PrivateKey (P384)", TestUtils.getResourceFileContentAsString("ec_p384.pem"))),
                    Arguments.of(Named.named("EC PrivateKey (P512)", TestUtils.getResourceFileContentAsString("ec_p512.pem"))),
                    Arguments.of(Named.named("Ed25519 PrivateKey", TestUtils.getResourceFileContentAsString("ed25519.pem")))
            );
        }
    }

    private static class PemConvertiblePrivateKeyProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(Named.named("RSA PrivateKey", TestUtils.getResourceFileContentAsString("rsa_2048.pem"))),
                    Arguments.of(Named.named("EC PrivateKey (P256)", TestUtils.getResourceFileContentAsString("ec_p256.pem"))),
                    Arguments.of(Named.named("EC PrivateKey (P384)", TestUtils.getResourceFileContentAsString("ec_p384.pem"))),
                    Arguments.of(Named.named("EC PrivateKey (P512)", TestUtils.getResourceFileContentAsString("ec_p512.pem")))
            );
        }
    }

    private static class PemPublicKeyProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(Named.named("RSA PublicKey", TestUtils.getResourceFileContentAsString("rsa_2048-pub.pem"))),
                    Arguments.of(Named.named("EC PublicKey (P256)", TestUtils.getResourceFileContentAsString("ec_p256-pub.pem"))),
                    Arguments.of(Named.named("EC Public (P384)", TestUtils.getResourceFileContentAsString("ec_p384-pub.pem"))),
                    Arguments.of(Named.named("EC Public (P512)", TestUtils.getResourceFileContentAsString("ec_p512-pub.pem"))),
                    Arguments.of(Named.named("Ed25519 PublicKey", TestUtils.getResourceFileContentAsString("ed25519-pub.pem")))
            );
        }
    }
}