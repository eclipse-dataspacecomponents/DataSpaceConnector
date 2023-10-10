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

package org.eclipse.edc.iam.identitytrust.validation;

import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.identitytrust.validation.JwtValidator;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;

import java.text.ParseException;
import java.util.Objects;

import static java.time.Instant.now;
import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

/**
 * Default implementation for JWT validation in the context of IATP.
 */
public class JwtValidatorImpl implements JwtValidator {

    private static final long EPSILON = 60;

    @SuppressWarnings("checkstyle:WhitespaceAfter")
    @Override
    public Result<ClaimToken> validateToken(TokenRepresentation tokenRepresentation, String audience) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(tokenRepresentation.getToken());

            var claims = jwt.getJWTClaimsSet();
            var iss = claims.getIssuer();
            var aud = claims.getAudience();
            var jti = claims.getClaim("jti");
            var clientId = claims.getClaim("client_id");
            var sub = claims.getSubject();
            var exp = claims.getExpirationTime();
            var subJwk = claims.getClaim("sub_jwk");

            if (!Objects.equals(iss, sub)) {
                return failure("The iss and aud claims must be identical.");
            }
            if (subJwk != null) {
                return failure("The sub_jwk claim must not be present.");
            }
            if (!aud.contains(audience)) {
                return failure("aud claim expected to be %s but was %s".formatted(audience, aud));
            }
            if (!Objects.equals(clientId, iss)) {
                return failure("client_id must be equal to the issuer ID");
            }
            if (jti == null) {
                return failure("The jti claim is mandatory.");
            }
            if (exp == null) {
                return failure("The exp claim is mandatory.");
            }
            if (exp.toInstant().plusSeconds(EPSILON).isBefore(now())) {
                return failure("The token must not be expired.");
            }
            var bldr = ClaimToken.Builder.newInstance();
            jwt.getJWTClaimsSet().getClaims().forEach(bldr::claim);
            return success(bldr.build());
        } catch (ParseException e) {
            return failure("Error parsing JWT");
        }


    }
}
