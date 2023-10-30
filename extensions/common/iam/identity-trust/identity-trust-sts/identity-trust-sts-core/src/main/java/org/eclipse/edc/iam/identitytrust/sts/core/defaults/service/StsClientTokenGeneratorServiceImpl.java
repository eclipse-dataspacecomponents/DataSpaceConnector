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

package org.eclipse.edc.iam.identitytrust.sts.core.defaults.service;

import org.eclipse.edc.iam.identitytrust.sts.embedded.EmbeddedSecureTokenService;
import org.eclipse.edc.iam.identitytrust.sts.model.StsClient;
import org.eclipse.edc.iam.identitytrust.sts.model.StsClientTokenAdditionalParams;
import org.eclipse.edc.iam.identitytrust.sts.service.StsClientTokenGeneratorService;
import org.eclipse.edc.iam.identitytrust.sts.service.StsTokenGenerationProvider;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.ServiceResult;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.eclipse.edc.identitytrust.SelfIssuedTokenConstants.ACCESS_TOKEN;
import static org.eclipse.edc.identitytrust.SelfIssuedTokenConstants.BEARER_ACCESS_ALIAS;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.AUDIENCE;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.CLIENT_ID;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.ISSUER;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.SUBJECT;

public class StsClientTokenGeneratorServiceImpl implements StsClientTokenGeneratorService {

    private static final Map<String, Function<StsClientTokenAdditionalParams, String>> CLAIM_MAPPERS = Map.of(
            ACCESS_TOKEN, StsClientTokenAdditionalParams::getAccessToken,
            BEARER_ACCESS_ALIAS, StsClientTokenAdditionalParams::getBearerAccessAlias);

    private final long tokenExpiration;
    private final StsTokenGenerationProvider tokenGenerationProvider;
    private final Clock clock;

    public StsClientTokenGeneratorServiceImpl(StsTokenGenerationProvider tokenGenerationProvider, Clock clock, long tokenExpiration) {
        this.tokenGenerationProvider = tokenGenerationProvider;
        this.clock = clock;
        this.tokenExpiration = tokenExpiration;
    }

    @Override
    public ServiceResult<TokenRepresentation> tokenFor(StsClient client, StsClientTokenAdditionalParams additionalParams) {
        var embeddedTokenGenerator = new EmbeddedSecureTokenService(tokenGenerationProvider.tokenGeneratorFor(client), clock, tokenExpiration);

        var initialClaims = Map.of(
                ISSUER, client.getId(),
                SUBJECT, client.getId(),
                AUDIENCE, additionalParams.getAudience(),
                CLIENT_ID, client.getClientId());

        var claims = CLAIM_MAPPERS.entrySet().stream()
                .filter(entry -> entry.getValue().apply(additionalParams) != null)
                .reduce(initialClaims, (accumulator, entity) ->
                        Optional.ofNullable(entity.getValue().apply(additionalParams))
                                .map(enrichClaimsWith(accumulator, entity.getKey()))
                                .orElse(accumulator), (a, b) -> b);

        var tokenResult = embeddedTokenGenerator.createToken(claims, additionalParams.getBearerAccessScope())
                .map(this::enrichWithExpiration);

        if (tokenResult.failed()) {
            return ServiceResult.badRequest(tokenResult.getFailureDetail());
        }
        return ServiceResult.success(tokenResult.getContent());
    }

    private TokenRepresentation enrichWithExpiration(TokenRepresentation tokenRepresentation) {
        return TokenRepresentation.Builder.newInstance()
                .token(tokenRepresentation.getToken())
                .additional(tokenRepresentation.getAdditional())
                .expiresIn(tokenExpiration)
                .build();
    }

    private Function<String, Map<String, String>> enrichClaimsWith(Map<String, String> claims, String claim) {
        return (claimValue) -> {
            var newClaims = new HashMap<>(claims);
            newClaims.put(claim, claimValue);
            return Collections.unmodifiableMap(newClaims);
        };
    }

}
