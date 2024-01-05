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

package org.eclipse.edc.iam.did.resolution;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKParameterNames;
import org.eclipse.edc.iam.did.spi.document.VerificationMethod;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.AbstractPublicKeyResolver;
import org.eclipse.edc.spi.security.KeyParserRegistry;
import org.eclipse.edc.spi.system.configuration.Config;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.regex.Pattern;

import static org.eclipse.edc.iam.did.spi.document.DidConstants.ALLOWED_VERIFICATION_TYPES;

public class DidPublicKeyResolverImpl extends AbstractPublicKeyResolver {
    /**
     * this regex pattern matches both DIDs and DIDs with a fragment (e.g. key-ID).
     * Group 1 ("did")      = the did:method:identifier portion
     * Group 2 ("fragment") = the #fragment portion
     */
    private static final Pattern PATTERN_DID_WITH_OPTIONAL_FRAGMENT = Pattern.compile("(?<did>did:.*:[^#]*)(?<fragment>#.*)?");
    private static final String GROUP_DID = "did";
    private static final String GROUP_FRAGMENT = "fragment";
    private final DidResolverRegistry resolverRegistry;

    public DidPublicKeyResolverImpl(KeyParserRegistry registry, DidResolverRegistry resolverRegistry, Config config, Monitor monitor) {
        super(registry, config, monitor);
        this.resolverRegistry = resolverRegistry;
    }

    @Override
    protected Result<String> resolveInternal(String id) {
        var matcher = PATTERN_DID_WITH_OPTIONAL_FRAGMENT.matcher(id);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("The given ID must conform to 'did:method:identifier[:fragment]' but did not"); //todo: use Result?
        }

        var did = matcher.group(GROUP_DID);
        String key = null;
        if (matcher.groupCount() > 1) {
            key = matcher.group(GROUP_FRAGMENT);
        }
        return resolveDidPublicKey(did, key);
    }

    private Result<String> resolveDidPublicKey(String didUrl, @Nullable String keyId) {
        var didResult = resolverRegistry.resolve(didUrl);
        if (didResult.failed()) {
            return didResult.mapTo();
        }
        var didDocument = didResult.getContent();
        if (didDocument.getVerificationMethod() == null || didDocument.getVerificationMethod().isEmpty()) {
            return Result.failure("DID does not contain a public key");
        }

        var verificationMethods = didDocument.getVerificationMethod().stream()
                .filter(vm -> ALLOWED_VERIFICATION_TYPES.contains(vm.getType()))
                .toList();

        // if there are more than 1 verification methods with the same ID
        if (verificationMethods.stream().map(VerificationMethod::getId).distinct().count() != verificationMethods.size()) {
            return Result.failure("Every verification method must have a unique ID");
        }
        Result<VerificationMethod> verificationMethod;

        if (keyId == null) { // only valid if exactly 1 verification method
            if (verificationMethods.size() > 1) {
                return Result.failure("The key ID ('kid') is mandatory if DID contains >1 verification methods.");
            }
            verificationMethod = Result.from(verificationMethods.stream().findFirst());
        } else { // look up VerificationMEthods by key ID
            verificationMethod = verificationMethods.stream().filter(vm -> vm.getId().equals(keyId))
                    .findFirst()
                    .map(Result::success)
                    .orElseGet(() -> Result.failure("No verification method found with key ID '%s'".formatted(keyId)));
        }
        return verificationMethod.compose(vm -> {
            var key = vm.getPublicKeyJwk();
            key.put(JWKParameterNames.KEY_ID, vm.getId());
            try {
                return Result.success(JWK.parse(key).toJSONString());
            } catch (ParseException e) {
                return Result.failure("Error parsing DID Verification Method: " + e);
            }

        });
    }
}
