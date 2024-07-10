/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *       Microsoft Corporation - Use IDS Webhook address for JWT audience claim
 *
 */

package org.eclipse.edc.iam.oauth2.daps;

import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.EdcExtension;
import org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.testfixtures.TestUtils.findBuildRoot;

@ExtendWith(EdcExtension.class)
@ComponentTest
class DapsIntegrationTest {

    public static final String CLIENT_CERTIFICATE_ALIAS = "1";
    public static final String CLIENT_PRIVATE_KEY_ALIAS = "2";
    private static final String AUDIENCE_IDS_CONNECTORS_ALL = "idsc:IDS_CONNECTORS_ALL";
    private static final String CLIENT_ID = "68:99:2E:D4:13:2D:FD:3A:66:6B:85:DE:FB:98:2E:2D:FD:E7:83:D7";
    private static final String CLIENT_KEYSTORE_PASSWORD = "1234";

    private final Path resourceFolder = findBuildRoot().toPath().resolve("extensions/common/iam/oauth2/oauth2-daps/src/test/resources");

    @Container
    private final GenericContainer<?> daps = new GenericContainer<>("ghcr.io/fraunhofer-aisec/omejdn-server:1.4.2")
            .withExposedPorts(4567)
            .withFileSystemBind(resourceFolder.resolve("config").toString(), "/opt/config")
            .withFileSystemBind(resourceFolder.resolve("keys").toString(), "/opt/keys");

    @Test
    void retrieveTokenAndValidate(IdentityService identityService) {
        var tokenParameters = TokenParameters.Builder.newInstance()
                .claims(JwtRegisteredClaimNames.SCOPE, "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL")
                .claims(JwtRegisteredClaimNames.AUDIENCE, "audience")
                .build();
        var tokenResult = identityService.obtainClientCredentials(tokenParameters);

        assertThat(tokenResult.succeeded()).withFailMessage(tokenResult::getFailureDetail).isTrue();

        var verificationContext = VerificationContext.Builder.newInstance()
                .policy(Policy.Builder.newInstance().build())
                .build();

        var verificationResult = identityService.verifyJwtToken(tokenResult.getContent(), verificationContext);

        assertThat(verificationResult.succeeded()).withFailMessage(verificationResult::getFailureDetail).isTrue();
    }

    @BeforeEach
    protected void before(EdcExtension extension) {
        System.setProperty("edc.keystore", "src/test/resources/keystore.p12");
        System.setProperty("edc.keystore.password", CLIENT_KEYSTORE_PASSWORD);

        var jwksPath = "/.well-known/jwks.json";
        daps.waitingFor(Wait.forHttp(jwksPath)).start();

        var dapsUrl = "http://%s:%s".formatted(daps.getHost(), daps.getFirstMappedPort());

        extension.setConfiguration(Map.of(
                "edc.oauth.token.url", dapsUrl + "/token",
                "edc.oauth.client.id", CLIENT_ID,
                "edc.oauth.provider.audience", AUDIENCE_IDS_CONNECTORS_ALL,
                "edc.oauth.endpoint.audience", AUDIENCE_IDS_CONNECTORS_ALL,
                "edc.oauth.provider.jwks.url", dapsUrl + jwksPath,
                "edc.oauth.certificate.alias", CLIENT_CERTIFICATE_ALIAS,
                "edc.oauth.private.key.alias", CLIENT_PRIVATE_KEY_ALIAS,
                "edc.iam.token.scope", "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL"
        ));
    }

}
