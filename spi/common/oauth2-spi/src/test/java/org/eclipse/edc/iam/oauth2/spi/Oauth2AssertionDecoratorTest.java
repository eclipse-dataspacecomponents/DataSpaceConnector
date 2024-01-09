/*
 *  Copyright (c) 2020 - 2022 Amadeus
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

package org.eclipse.edc.iam.oauth2.spi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import static com.nimbusds.jwt.JWTClaimNames.AUDIENCE;
import static com.nimbusds.jwt.JWTClaimNames.EXPIRATION_TIME;
import static com.nimbusds.jwt.JWTClaimNames.ISSUED_AT;
import static com.nimbusds.jwt.JWTClaimNames.ISSUER;
import static com.nimbusds.jwt.JWTClaimNames.JWT_ID;
import static com.nimbusds.jwt.JWTClaimNames.SUBJECT;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

class Oauth2AssertionDecoratorTest {
    private static final long TOKEN_EXPIRATION = 500;
    private final Instant now = Instant.now();
    private String audience;
    private String clientId;
    private Oauth2AssertionDecorator decorator;

    @BeforeEach
    void setUp() {
        audience = "test-audience";
        clientId = UUID.randomUUID().toString();
        var clock = Clock.fixed(now, UTC);
        decorator = new Oauth2AssertionDecorator(audience, clientId, clock, TOKEN_EXPIRATION);
    }

    @Test
    void verifyDecorate() {
        var claims = new HashMap<String, Object>();
        var headers = new HashMap<String, Object>();
        decorator.decorate(claims, headers);

        assertThat(headers).isEmpty();
        assertThat(claims)
                .hasEntrySatisfying(AUDIENCE, o -> assertThat(o).asInstanceOf(list(String.class)).contains(audience))
                .hasFieldOrPropertyWithValue(ISSUER, clientId)
                .hasFieldOrPropertyWithValue(SUBJECT, clientId)
                .containsKey(JWT_ID)
                .hasEntrySatisfying(ISSUED_AT, issueDate -> assertThat((Date) issueDate).isEqualTo(now))
                .hasEntrySatisfying(EXPIRATION_TIME, expiration -> assertThat((Date) expiration).isEqualTo(now.plusSeconds(TOKEN_EXPIRATION)));
    }
}