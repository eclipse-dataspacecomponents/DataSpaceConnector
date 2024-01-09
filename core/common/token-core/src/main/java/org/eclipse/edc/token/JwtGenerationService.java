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
 *       Microsoft Corporation - Simplified token representation
 *
 */

package org.eclipse.edc.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.jwt.spi.JwsSignerVerifierFactory;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenDecorator;
import org.eclipse.edc.token.spi.TokenGenerationService;
import org.jetbrains.annotations.NotNull;

import java.security.PrivateKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

public class JwtGenerationService implements TokenGenerationService {
    private final JwsSignerVerifierFactory factory;


    public JwtGenerationService() {
        this.factory = new JwsSignerVerifierFactory();
    }

    @Override
    public Result<TokenRepresentation> generate(Supplier<PrivateKey> privateKeySupplier, @NotNull TokenDecorator... decorators) {

        var privateKey = privateKeySupplier.get();

        var tokenSigner = factory.createSignerFor(privateKey);
        var jwsAlgorithm = factory.getRecommendedAlgorithm(tokenSigner);

        var allDecorators = new ArrayList<>(Arrays.asList(decorators));
        allDecorators.add(new BaseDecorator(jwsAlgorithm));

        var header = createHeader(allDecorators);
        var claims = createClaimsSet(allDecorators);

        var token = new SignedJWT(header, claims);
        try {
            token.sign(tokenSigner);
        } catch (JOSEException e) {
            return Result.failure("Failed to sign token: " + e.getMessage());
        }
        return Result.success(TokenRepresentation.Builder.newInstance().token(token.serialize()).build());
    }

    private JWSHeader createHeader(@NotNull List<TokenDecorator> decorators) {
        var map = decorators.stream()
                .map(TokenDecorator::headers)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        try {
            return JWSHeader.parse(map);
        } catch (ParseException e) {
            throw new EdcException("Error parsing JWSHeader, this should never happens since the algorithm is always valid", e);
        }
    }

    private JWTClaimsSet createClaimsSet(@NotNull List<TokenDecorator> decorators) {
        var builder = new JWTClaimsSet.Builder();

        decorators.stream()
                .map(TokenDecorator::claims)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .forEach(claim -> builder.claim(claim.getKey(), claim.getValue()));

        return builder.build();
    }


    /**
     * Base JwtDecorator that provides the algorithm header value
     */
    private record BaseDecorator(JWSAlgorithm jwsAlgorithm) implements TokenDecorator {

        @Override
        public Map<String, Object> claims() {
            return emptyMap();
        }

        @Override
        public Map<String, Object> headers() {
            var map = new HashMap<String, Object>();
            map.put("alg", jwsAlgorithm.getName());
            return map;
        }
    }
}
