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

package org.eclipse.edc.verification.jwt;

import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.iam.did.crypto.JwtUtils;
import org.eclipse.edc.iam.did.spi.document.DidConstants;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.VerificationMethod;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.identitytrust.verification.JwtVerifier;
import org.eclipse.edc.spi.result.Result;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.util.Optional;

/**
 * Performs cryptographic (and some structural) verification of a self-issued ID token. To that end, the issuer of the token
 * ({@code iss} claim) is presumed to be a Decentralized Identifier (<a href="https://www.w3.org/TR/did-core/">DID</a>).
 * <p>
 * If the JWT contains in its header a {@code kid} field identifying the public key that was used for signing, the DID is
 * <strong>expected</strong> to have a <a href="https://www.w3.org/TR/did-core/#verification-methods">verificationMethod</a>
 * with that same ID. If no such verification method is found, {@link Result#failure(String)} is returned.
 * <p>
 * If no such {@code kid} header is present, then the <em>first</em> verification method is used.
 * <p>
 * Please note that <strong>no structural</strong> validation is done beyond the very basics (must have iss and aud claim).
 * This is done by the {@link SelfIssuedIdTokenVerifier}.
 */
public class SelfIssuedIdTokenVerifier implements JwtVerifier {
    private final DidPublicKeyResolver publicKeyResolver;

    public SelfIssuedIdTokenVerifier(DidPublicKeyResolver publicKeyResolver) {
        this.publicKeyResolver = publicKeyResolver;
    }

    @Override
    public Result<Void> verify(String serializedJwt, String audience) {

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(serializedJwt);
            var publicKeyWrapperResult = publicKeyResolver.resolvePublicKey(jwt.getJWTClaimsSet().getIssuer(), jwt.getHeader().getKeyID());
            if (publicKeyWrapperResult.failed()) {
                return publicKeyWrapperResult.mapTo();
            }

            var verified = JwtUtils.verify(jwt, publicKeyWrapperResult.getContent(), audience);
            if (verified.failed()) {
                return Result.failure("Token could not be verified: %s".formatted(verified.getFailureDetail()));
            }
            return Result.success();
        } catch (ParseException e) {
            return Result.failure("Error parsing JWT");
        }
    }

    private Optional<VerificationMethod> getVerificationMethod(DidDocument content, String kid) {
        return content.getVerificationMethod().stream().filter(vm -> vm.getId().equals(kid))
                .findFirst();
    }

    @NotNull
    private Optional<VerificationMethod> firstVerificationMethod(DidDocument did) {
        return did.getVerificationMethod().stream()
                .filter(vm -> DidConstants.ALLOWED_VERIFICATION_TYPES.contains(vm.getType()))
                .findFirst();
    }
}
