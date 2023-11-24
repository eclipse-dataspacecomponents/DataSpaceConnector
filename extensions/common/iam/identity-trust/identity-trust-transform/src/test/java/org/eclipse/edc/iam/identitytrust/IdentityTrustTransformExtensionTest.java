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

package org.eclipse.edc.iam.identitytrust;

import org.eclipse.edc.iam.identitytrust.transform.to.JsonObjectToCredentialStatusTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JsonObjectToCredentialSubjectTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JsonObjectToIssuerTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JsonObjectToPresentationQueryTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JsonObjectToVerifiableCredentialTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JsonObjectToVerifiablePresentationTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JwtToVerifiableCredentialTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JwtToVerifiablePresentationTransformer;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class IdentityTrustTransformExtensionTest {

    private final TypeTransformerRegistry mockRegistry = mock();

    @BeforeEach
    void setup(ServiceExtensionContext context) {
        context.registerService(TypeTransformerRegistry.class, mockRegistry);
    }

    @Test
    void initialize_assertTransformerRegistrations(IdentityTrustTransformExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(mockRegistry).register(isA(JsonObjectToCredentialStatusTransformer.class));
        verify(mockRegistry).register(isA(JsonObjectToCredentialSubjectTransformer.class));
        verify(mockRegistry).register(isA(JsonObjectToIssuerTransformer.class));
        verify(mockRegistry).register(isA(JsonObjectToPresentationQueryTransformer.class));
        verify(mockRegistry).register(isA(JsonObjectToVerifiableCredentialTransformer.class));
        verify(mockRegistry).register(isA(JsonObjectToVerifiablePresentationTransformer.class));
        verify(mockRegistry).register(isA(JwtToVerifiableCredentialTransformer.class));
        verify(mockRegistry).register(isA(JwtToVerifiablePresentationTransformer.class));
    }
}