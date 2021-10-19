/*
 *  Copyright (c) 2021 Microsoft Corporation
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
package org.eclipse.dataspaceconnector.identity;

import com.nimbusds.jwt.SignedJWT;
import org.eclipse.dataspaceconnector.iam.did.crypto.credentials.VerifiableCredentialFactory;
import org.eclipse.dataspaceconnector.iam.did.crypto.key.KeyConverter;
import org.eclipse.dataspaceconnector.iam.did.spi.credentials.CredentialsVerifier;
import org.eclipse.dataspaceconnector.iam.did.spi.document.DidConstants;
import org.eclipse.dataspaceconnector.iam.did.spi.document.DidDocument;
import org.eclipse.dataspaceconnector.iam.did.spi.document.JwkPublicKey;
import org.eclipse.dataspaceconnector.iam.did.spi.document.Service;
import org.eclipse.dataspaceconnector.iam.did.spi.document.VerificationMethod;
import org.eclipse.dataspaceconnector.iam.did.spi.key.PublicKeyWrapper;
import org.eclipse.dataspaceconnector.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.dataspaceconnector.spi.iam.ClaimToken;
import org.eclipse.dataspaceconnector.spi.iam.IdentityService;
import org.eclipse.dataspaceconnector.spi.iam.TokenResult;
import org.eclipse.dataspaceconnector.spi.iam.VerificationResult;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DistributedIdentityService implements IdentityService {
    private final Supplier<SignedJWT> verifiableCredentialProvider;
    private final DidResolverRegistry resolverRegistry;
    private final CredentialsVerifier credentialsVerifier;
    private final Monitor monitor;

    public DistributedIdentityService(Supplier<SignedJWT> vcProvider, DidResolverRegistry resolverRegistry, CredentialsVerifier credentialsVerifier, Monitor monitor) {
        verifiableCredentialProvider = vcProvider;
        this.resolverRegistry = resolverRegistry;
        this.credentialsVerifier = credentialsVerifier;
        this.monitor = monitor;
    }

    @Override
    public TokenResult obtainClientCredentials(String scope) {

        var jwt = verifiableCredentialProvider.get();
        var token = jwt.serialize();
        var expiration = new Date().getTime() + TimeUnit.MINUTES.toMillis(10);

        return TokenResult.Builder.newInstance().token(token).expiresIn(expiration).build();
    }

    @Override
    public VerificationResult verifyJwtToken(String token, String audience) {
        try {
            var jwt = SignedJWT.parse(token);
            monitor.debug("Starting verification...");

            monitor.debug("Resolving other party's DID Document");
            var didResult = resolverRegistry.resolve(jwt.getJWTClaimsSet().getIssuer());
            if (didResult.invalid()) {
                return new VerificationResult("Unable to resolve DID: " + didResult.getInvalidMessage());
            }
            monitor.debug("Extracting public key");

            // this will return the _first_ public key entry
            Optional<VerificationMethod> publicKey = getPublicKey(didResult.getDidDocument());
            if (publicKey.isEmpty()) {
                return new VerificationResult("Public Key not found in DID Document!");
            }

            //convert the POJO into a usable PK-wrapper:
            JwkPublicKey publicKeyJwk = publicKey.get().getPublicKeyJwk();
            PublicKeyWrapper publicKeyWrapper = KeyConverter.toPublicKeyWrapper(publicKeyJwk, publicKey.get().getId());

            monitor.debug("Verifying JWT with public key...");
            if (!VerifiableCredentialFactory.verify(jwt, publicKeyWrapper)) {
                return new VerificationResult("Token could not be verified!");
            }
            monitor.debug("verification successful! Fetching data from IdentityHub");
            String hubUrl = getHubUrl(didResult.getDidDocument());
            var credentialsResult = credentialsVerifier.verifyCredentials(hubUrl, publicKeyWrapper);

            monitor.debug("Building ClaimToken");
            var tokenBuilder = ClaimToken.Builder.newInstance();
            var claimToken = tokenBuilder.claims(credentialsResult.getValidatedCredentials()).build();

            return new VerificationResult(claimToken);
        } catch (ParseException e) {
            monitor.info("Error parsing JWT", e);
            return new VerificationResult("Error parsing JWT");
        }
    }

    String getHubUrl(DidDocument did) {
        return did.getService().stream().filter(service -> service.getType().equals(DidConstants.HUB_URL)).map(Service::getServiceEndpoint).findFirst().orElseThrow();
    }

    @NotNull
    private Optional<VerificationMethod> getPublicKey(DidDocument did) {
        return did.getVerificationMethod().stream().filter(vm -> DidConstants.ALLOWED_VERIFICATION_TYPES.contains(vm.getType())).findFirst();
    }
}
