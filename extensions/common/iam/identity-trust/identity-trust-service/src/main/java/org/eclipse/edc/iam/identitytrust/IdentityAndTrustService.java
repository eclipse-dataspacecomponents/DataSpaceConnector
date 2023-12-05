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

import com.nimbusds.jwt.SignedJWT;
import org.eclipse.edc.iam.identitytrust.validation.rules.HasValidIssuer;
import org.eclipse.edc.iam.identitytrust.validation.rules.HasValidSubjectIds;
import org.eclipse.edc.iam.identitytrust.validation.rules.IsNotExpired;
import org.eclipse.edc.iam.identitytrust.validation.rules.IsRevoked;
import org.eclipse.edc.identitytrust.AudienceResolver;
import org.eclipse.edc.identitytrust.CredentialServiceClient;
import org.eclipse.edc.identitytrust.CredentialServiceUrlResolver;
import org.eclipse.edc.identitytrust.SecureTokenService;
import org.eclipse.edc.identitytrust.TrustedIssuerRegistry;
import org.eclipse.edc.identitytrust.model.CredentialSubject;
import org.eclipse.edc.identitytrust.model.Issuer;
import org.eclipse.edc.identitytrust.model.VerifiableCredential;
import org.eclipse.edc.identitytrust.validation.CredentialValidationRule;
import org.eclipse.edc.identitytrust.validation.JwtValidator;
import org.eclipse.edc.identitytrust.verification.JwtVerifier;
import org.eclipse.edc.identitytrust.verification.PresentationVerifier;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.util.string.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.nimbusds.jwt.JWTClaimNames.AUDIENCE;
import static com.nimbusds.jwt.JWTClaimNames.EXPIRATION_TIME;
import static com.nimbusds.jwt.JWTClaimNames.ISSUED_AT;
import static com.nimbusds.jwt.JWTClaimNames.ISSUER;
import static com.nimbusds.jwt.JWTClaimNames.SUBJECT;
import static org.eclipse.edc.identitytrust.SelfIssuedTokenConstants.PRESENTATION_ACCESS_TOKEN_CLAIM;
import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

/**
 * Implements an {@link IdentityService}, that:
 * <ul>
 *     <li>Obtains an SI token from a SecureTokenService</li>
 *     <li>Establishes proof-of-original possession, by extracting the PRESENTATION_ACCESS_TOKEN_CLAIM, and re-packaging it into a new SI token</li>
 *     <li>Performs a presentation request against a CredentialService</li>
 *     <li>Validates and verifies the VerifiablePresentation</li>
 * </ul>
 * This service is intended to be used together with the Identity And Trust Protocols.
 * Details about the scope string can be found <a href="https://github.com/eclipse-tractusx/identity-trust/blob/main/specifications/M1/verifiable.presentation.protocol.md#31-access-scopes">here</a>
 */
public class IdentityAndTrustService implements IdentityService {
    private static final String SCOPE_STRING_REGEX = "(.+):(.+):(read|write|\\*)";
    private final SecureTokenService secureTokenService;
    private final String myOwnDid;
    private final String participantId;
    private final PresentationVerifier presentationVerifier;
    private final CredentialServiceClient credentialServiceClient;
    private final JwtValidator jwtValidator;
    private final JwtVerifier jwtVerifier;
    private final TrustedIssuerRegistry trustedIssuerRegistry;
    private final Clock clock;
    private final CredentialServiceUrlResolver credentialServiceUrlResolver;
    private final AudienceResolver audienceMapper;

    /**
     * Constructs a new instance of the {@link IdentityAndTrustService}.
     *
     * @param secureTokenService Instance of an STS, which can create SI tokens
     * @param myOwnDid           The DID which belongs to "this connector"
     */
    public IdentityAndTrustService(SecureTokenService secureTokenService, String myOwnDid, String participantId,
                                   PresentationVerifier presentationVerifier, CredentialServiceClient credentialServiceClient,
                                   JwtValidator jwtValidator, JwtVerifier jwtVerifier, TrustedIssuerRegistry trustedIssuerRegistry, Clock clock, CredentialServiceUrlResolver csUrlResolver, AudienceResolver audienceMapper) {
        this.secureTokenService = secureTokenService;
        this.myOwnDid = myOwnDid;
        this.participantId = participantId;
        this.presentationVerifier = presentationVerifier;
        this.credentialServiceClient = credentialServiceClient;
        this.jwtValidator = jwtValidator;
        this.jwtVerifier = jwtVerifier;
        this.trustedIssuerRegistry = trustedIssuerRegistry;
        this.clock = clock;
        this.credentialServiceUrlResolver = csUrlResolver;
        this.audienceMapper = audienceMapper;
    }

    @Override
    public Result<TokenRepresentation> obtainClientCredentials(TokenParameters parameters) {
        var newAud = audienceMapper.resolve(parameters.getAudience());
        parameters = TokenParameters.Builder.newInstance()
                .audience(newAud)
                .scope(parameters.getScope())
                .additional(parameters.getAdditional())
                .build();

        var scope = parameters.getScope();
        var scopeValidationResult = validateScope(scope);

        if (scopeValidationResult.failed()) {
            return failure(scopeValidationResult.getFailureMessages());
        }

        // create claims for the STS
        var claims = new HashMap<String, String>();
        parameters.getAdditional().forEach((k, v) -> claims.replace(k, v.toString()));

        claims.putAll(Map.of(
                "iss", myOwnDid,
                "sub", myOwnDid,
                "aud", parameters.getAudience(),
                "client_id", participantId));

        return secureTokenService.createToken(claims, scope);
    }

    @Override
    public Result<ClaimToken> verifyJwtToken(TokenRepresentation tokenRepresentation, VerificationContext context) {

        // verify and validate incoming SI Token
        var claimTokenResult = jwtVerifier.verify(tokenRepresentation.getToken(), participantId)
                .compose(v -> jwtValidator.validateToken(tokenRepresentation, participantId)) // audience must be set to my own participant ID
                .compose(Result::success);

        if (claimTokenResult.failed()) {
            return claimTokenResult.mapTo();
        }

        // create our own SI token, to request the VPs
        var claimToken = claimTokenResult.getContent();
        var accessToken = claimToken.getStringClaim(PRESENTATION_ACCESS_TOKEN_CLAIM);
        var issuer = claimToken.getStringClaim(ISSUER);
        var intendedAudience = claimToken.getStringClaim("client_id");

        /* TODO: DEMO the scopes should be extracted elsewhere. replace this section!!############################*/
        var scopes = new ArrayList<String>();
        try {
            var scope = SignedJWT.parse(accessToken).getJWTClaimsSet().getStringClaim("scope");
            scopes.add(scope);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        /* TODO: END DEMO ########################################################################################*/

        var siTokenClaims = Map.of(PRESENTATION_ACCESS_TOKEN_CLAIM, accessToken,
                ISSUED_AT, Instant.now().toString(),
                AUDIENCE, intendedAudience,
                ISSUER, myOwnDid,
                SUBJECT, myOwnDid,
                EXPIRATION_TIME, Instant.now().plus(5, ChronoUnit.MINUTES).toString());
        var siToken = secureTokenService.createToken(siTokenClaims, null);
        if (siToken.failed()) {
            return siToken.mapTo();
        }
        var siTokenString = siToken.getContent().getToken();

        // get CS Url, execute VP request
        var vpResponse = credentialServiceUrlResolver.resolve(issuer)
                .compose(url -> credentialServiceClient.requestPresentation(url, siTokenString, scopes));

        if (vpResponse.failed()) {
            return vpResponse.mapTo();
        }

        var presentations = vpResponse.getContent();
        var result = presentations.stream().map(verifiablePresentation -> {
            var credentials = verifiablePresentation.presentation().getCredentials();
            // verify, that the VP and all VPs are cryptographically OK
            return presentationVerifier.verifyPresentation(verifiablePresentation)
                    .compose(u -> validateVerifiableCredentials(credentials, issuer));
        }).reduce(Result.success(), Result::merge);
        //todo: at this point we have established what the other participant's DID is, and that it's authentic
        // so we need to make sure that `iss == sub == DID`
        return result.compose(u -> extractClaimToken(presentations.stream().map(p -> p.presentation().getCredentials().stream())
                .reduce(Stream.empty(), Stream::concat)
                .toList(), intendedAudience));
    }

    @NotNull
    private Result<Void> validateVerifiableCredentials(List<VerifiableCredential> credentials, String issuer) {
        // in addition, verify that all VCs are valid
        var filters = new ArrayList<>(List.of(
                new IsNotExpired(clock),
                new HasValidSubjectIds(issuer),
                new IsRevoked(null),
                new HasValidIssuer(getTrustedIssuerIds())));

        filters.addAll(getAdditionalValidations());
        var results = credentials.stream().map(c -> filters.stream().reduce(t -> Result.success(), CredentialValidationRule::and).apply(c)).reduce(Result::merge);
        return results.orElseGet(() -> failure("Could not determine the status of the VC validation"));
    }


    @NotNull
    private Result<ClaimToken> extractClaimToken(List<VerifiableCredential> credentials, String issuer) {
        if (credentials.isEmpty()) {
            return failure("No VerifiableCredentials were found on VP");
        }
        var b = ClaimToken.Builder.newInstance();
        credentials.stream().flatMap(vc -> vc.getCredentialSubject().stream())
                .map(CredentialSubject::getClaims)
                .forEach(claimSet -> claimSet.forEach(b::claim));

        b.claim("client_id", issuer);
        return success(b.build());
    }

    private Collection<? extends CredentialValidationRule> getAdditionalValidations() {
        return List.of();
    }

    private List<String> getTrustedIssuerIds() {
        return trustedIssuerRegistry.getTrustedIssuers().stream().map(Issuer::id).toList();
    }

    private Result<Void> validateScope(String scope) {
        if (StringUtils.isNullOrBlank(scope)) {
            return failure("Scope string invalid: input string was null or empty");
        }
        return scope.matches(SCOPE_STRING_REGEX) ?
                success() :
                failure("Scope string invalid: '%s' does not match regex %s".formatted(scope, SCOPE_STRING_REGEX));
    }
}
