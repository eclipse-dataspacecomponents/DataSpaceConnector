/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Fraunhofer Institute for Software and Systems Engineering
 *
 */

package org.eclipse.dataspaceconnector.iam.oauth2.core.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.eclipse.dataspaceconnector.iam.oauth2.spi.JwtDecoratorRegistry;
import org.eclipse.dataspaceconnector.iam.oauth2.spi.ValidationRule;
import org.eclipse.dataspaceconnector.spi.EdcException;
import org.eclipse.dataspaceconnector.spi.iam.ClaimToken;
import org.eclipse.dataspaceconnector.spi.iam.IdentityService;
import org.eclipse.dataspaceconnector.spi.iam.TokenRepresentation;
import org.eclipse.dataspaceconnector.spi.result.Result;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implements the OAuth2 client credentials flow and bearer token validation.
 */
public class Oauth2ServiceImpl implements IdentityService {

    private static final String GRANT_TYPE = "client_credentials";
    private static final String ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private final Oauth2Configuration configuration;

    private final OkHttpClient httpClient;
    private final TypeManager typeManager;
    private final List<ValidationRule> validationRules;
    private final JWSSigner tokenSigner;
    private final JwtDecoratorRegistry jwtDecoratorRegistry;
    private final JWSHeader.Builder jwsHeaderBuilder;

    /**
     * Creates a new instance of the OAuth2 Service
     *
     * @param configuration             The configuration
     * @param tokenSigner               A {@link JWSSigner} instance.
     * @param client                    Http client
     * @param jwtDecoratorRegistry      Registry containing the decorator for build the JWT
     * @param typeManager               Type manager
     * @param additionalValidationRules An optional list of {@link ValidationRule} that are evaluated <em>after</em> the
     *                                  standard OAuth2 validation
     */
    public Oauth2ServiceImpl(Oauth2Configuration configuration, JWSSigner tokenSigner, OkHttpClient client, JwtDecoratorRegistry jwtDecoratorRegistry, TypeManager typeManager, ValidationRule... additionalValidationRules) {
        this.configuration = configuration;
        this.typeManager = typeManager;
        httpClient = client;
        this.jwtDecoratorRegistry = jwtDecoratorRegistry;
        this.tokenSigner = tokenSigner;

        List<ValidationRule> rules = new ArrayList<>();
        rules.add(new Oauth2ValidationRule()); //OAuth2 validation must ALWAYS be done
        rules.addAll(List.of(additionalValidationRules));
        validationRules = Collections.unmodifiableList(rules);

        if (tokenSigner instanceof ECDSASigner) {
            //supports ECDSA private key
            jwsHeaderBuilder = new JWSHeader.Builder(JWSAlgorithm.ES256);
        } else {
            //default: RSA private key
            jwsHeaderBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256);
        }
    }

    @Override
    public Result<TokenRepresentation> obtainClientCredentials(String scope) {
        String assertion = buildJwt();

        RequestBody requestBody = new FormBody.Builder()
                .add("client_assertion_type", ASSERTION_TYPE)
                .add("grant_type", GRANT_TYPE)
                .add("client_assertion", assertion)
                .add("scope", scope)
                .build();

        Request request = new Request.Builder().url(configuration.getTokenUrl()).addHeader("Content-Type", CONTENT_TYPE).post(requestBody).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                try (var body = response.body()) {
                    String message = body == null ? "<empty body>" : body.string();
                    return Result.failure(message);
                }
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return Result.failure("<empty token body>");
            }

            String responsePayload = responseBody.string();
            LinkedHashMap<String, Object> deserialized = typeManager.readValue(responsePayload, LinkedHashMap.class);
            String token = (String) deserialized.get("access_token");
            long expiresIn = ((Integer) deserialized.get("expires_in")).longValue();
            TokenRepresentation tokenRepresentation = TokenRepresentation.Builder.newInstance().token(token).expiresIn(expiresIn).build();
            return Result.success(tokenRepresentation);
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }

    @Override
    public Result<ClaimToken> verifyJwtToken(String token, String audience) {
        try {
            var signedJwt = SignedJWT.parse(token);

            String publicKeyId = signedJwt.getHeader().getKeyID();
            var verifier = createVerifier(signedJwt.getHeader(), publicKeyId);
            if (verifier == null) {
                return Result.failure("Failed to create verifier");
            }

            if (!signedJwt.verify(verifier)) {
                return Result.failure("Token verification not successful");
            }
            var claimsSet = signedJwt.getJWTClaimsSet();

            // now we get the results of all the single rules, lets collate them into one
            var errors = validationRules.stream()
                    .map(r -> r.checkRule(claimsSet, audience))
                    .filter(Result::failed)
                    .map(Result::getFailureMessages)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

            // return instantly if there are errors present
            if (!errors.isEmpty()) {
                return Result.failure(errors);
            }

            // build claim tokens
            var tokenBuilder = ClaimToken.Builder.newInstance();
            claimsSet.getClaims().forEach((k, v) -> {
                var claimValue = Objects.toString(v);
                if (claimValue == null) {
                    // only support strings
                    return;
                }
                tokenBuilder.claim(k, claimValue);
            });
            return Result.success(tokenBuilder.build());

        } catch (JOSEException e) {
            return Result.failure(e.getMessage());
        } catch (ParseException e) {
            return Result.failure("Token could not be decoded");
        }
    }

    @Nullable
    private JWSVerifier createVerifier(JWSHeader header, String publicKeyId) {
        RSAPublicKey publicKey = configuration.getIdentityProviderKeyResolver().resolveKey(publicKeyId);
        try {
            return new DefaultJWSVerifierFactory().createJWSVerifier(header, publicKey);
        } catch (JOSEException e) {
            return null;
        }
    }

    private String buildJwt() {
        try {
            var claimsSet = new JWTClaimsSet.Builder();
            jwtDecoratorRegistry.getAll().forEach(d -> d.decorate(jwsHeaderBuilder, claimsSet));
            var jwt = new SignedJWT(jwsHeaderBuilder.build(), claimsSet.build());
            jwt.sign(tokenSigner);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new EdcException(e);
        }
    }
}
