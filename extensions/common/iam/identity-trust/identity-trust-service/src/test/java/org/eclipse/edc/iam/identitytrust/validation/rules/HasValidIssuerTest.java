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

package org.eclipse.edc.iam.identitytrust.validation.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.eclipse.edc.identitytrust.TestFunctions.createCredentialBuilder;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;

class HasValidIssuerTest {

    @DisplayName("Issuer (string) is in the list of valid issuers")
    @Test
    void hasValidIssuer_string() {
        var vc = createCredentialBuilder()
                .issuer("did:web:issuer2")
                .build();
        assertThat(new HasValidIssuer(List.of("did:web:issuer1", "did:web:issuer2")).apply(vc)).isSucceeded();
    }

    @DisplayName("Issuer (object) is in the list of valid issuers")
    @Test
    void hasValidIssuer_object() {
        var vc = createCredentialBuilder()
                .issuer(Map.of("id", "did:web:issuer1", "name", "test issuer company"))
                .build();
        assertThat(new HasValidIssuer(List.of("did:web:issuer1", "did:web:issuer2")).apply(vc)).isSucceeded();
    }

    @DisplayName("Issuer (string) is not in the list of valid issuers")
    @Test
    void invalidIssuer_string() {
        var vc = createCredentialBuilder()
                .issuer("did:web:invalid")
                .build();
        assertThat(new HasValidIssuer(List.of("did:web:issuer1", "did:web:issuer2")).apply(vc)).isFailed()
                .detail().isEqualTo("Issuer 'did:web:invalid' is not in the list of allowed issuers");
    }

    @DisplayName("Issuer (object) is not in the list of valid issuers")
    @Test
    void invalidIssuer_object() {
        var vc = createCredentialBuilder()
                .issuer(Map.of("id", "did:web:invalid", "name", "test issuer company"))
                .build();
        assertThat(new HasValidIssuer(List.of("did:web:issuer1", "did:web:issuer2")).apply(vc)).isFailed()
                .detail().isEqualTo("Issuer 'did:web:invalid' is not in the list of allowed issuers");
    }

    @DisplayName("Issuer (object) does not have an 'id' property")
    @Test
    void issuerIsObject_noIdField() {
        var vc = createCredentialBuilder()
                .issuer(Map.of("name", "test issuer company"))
                .build();
        assertThat(new HasValidIssuer(List.of("did:web:issuer1", "did:web:issuer2")).apply(vc)).isFailed()
                .detail().isEqualTo("Issuer was an object, but did not contain an 'id' field");
    }

    @DisplayName("Issuer neither a string nor an object")
    @Test
    void issuerIsInvalidType() {
        var vc = createCredentialBuilder()
                .issuer(43L)
                .build();
        assertThat(new HasValidIssuer(List.of("did:web:issuer1", "did:web:issuer2")).apply(vc)).isFailed()
                .detail().isEqualTo("VC Issuer must either be a String or an Object but was class java.lang.Long.");
    }
}