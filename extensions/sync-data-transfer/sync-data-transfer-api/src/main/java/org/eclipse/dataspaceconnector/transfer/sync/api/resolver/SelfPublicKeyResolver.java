/*
 *  Copyright (c) 2022 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

package org.eclipse.dataspaceconnector.transfer.sync.api.resolver;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import org.eclipse.dataspaceconnector.spi.EdcException;
import org.eclipse.dataspaceconnector.spi.iam.PublicKeyResolver;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;

/**
 * Return the public key associated with current entity (self).
 */
public class SelfPublicKeyResolver implements PublicKeyResolver {

    private final PublicKey key;

    public SelfPublicKeyResolver(@NotNull Vault vault, @NotNull String publicKeyAlias) {
        var secret = vault.resolveSecret(publicKeyAlias);
        if (secret == null) {
            throw new EdcException("Failed to retrieve secret with id: " + publicKeyAlias);
        }
        try {
            ECKey jwk = (ECKey) JWK.parseFromPEMEncodedObjects(secret);
            key = jwk.toRSAKey().toPublicKey();
        } catch (JOSEException e) {
            throw new EdcException(e);
        }
    }

    @Override
    public @Nullable PublicKey resolveKey(String id) {
        return key;
    }
}
