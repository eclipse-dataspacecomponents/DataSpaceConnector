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

package org.eclipse.edc.keys.keyparsers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.UUID;
import java.util.stream.Stream;

import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.Mockito.mock;

class JwkParserTest {

    private final JwkParser parser = new JwkParser(new ObjectMapper(), mock());

    @Test
    void canHandle() throws JOSEException {
        var jwk = new ECKeyGenerator(Curve.P_256)
                .keyID(UUID.randomUUID().toString())
                .generate();
        Assertions.assertThat(parser.canHandle(jwk.toJSONString())).isTrue();
    }

    @ParameterizedTest
    @ArgumentsSource(KeyProvider.class)
    void parse_privateKey(JWK jwk) {
        var result = parser.parse(jwk.toJSONString());
        assertThat(result).isSucceeded().isInstanceOf(PrivateKey.class);
    }

    @ParameterizedTest
    @ArgumentsSource(KeyProvider.class)
    void parse_publicKey(JWK jwk) {
        var publickey = jwk.toPublicJWK();
        var result = parser.parse(publickey.toJSONString());
        assertThat(result).isSucceeded().isInstanceOf(PublicKey.class);
    }


    @ParameterizedTest
    @ArgumentsSource(KeyProvider.class)
    void parsePublic_withPublicKey(JWK jwk) {
        var publickey = jwk.toPublicJWK();
        var result = parser.parsePublic(publickey.toJSONString());
        assertThat(result).isSucceeded().isInstanceOf(PublicKey.class);
    }


    @ParameterizedTest
    @ArgumentsSource(KeyProvider.class)
    void parsePublic_withPrivateKey(JWK jwk) {
        var result = parser.parsePublic(jwk.toJSONString());
        assertThat(result).isSucceeded().isInstanceOf(PublicKey.class);
    }

    private static class KeyProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(Named.named("EC-P256", KeyFunctions.createEc(Curve.P_256))),
                    Arguments.of(Named.named("EC-P384", KeyFunctions.createEc(Curve.P_384))),
                    Arguments.of(Named.named("EC-P521", KeyFunctions.createEc(Curve.P_521))),
                    Arguments.of(Named.named("EC-secp256k1", KeyFunctions.createEc(Curve.SECP256K1))),
                    Arguments.of(Named.named("OKP-Ed25519", KeyFunctions.createOkp(Curve.Ed25519))),
                    Arguments.of(Named.named("OKP-X25519", KeyFunctions.createOkp(Curve.X25519))),
                    Arguments.of(Named.named("RSA-2048", KeyFunctions.createRsa(2048))),
                    Arguments.of(Named.named("RSA-4096", KeyFunctions.createRsa(4096)))
            );
        }


    }
}