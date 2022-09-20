/*
 *  Copyright (c) 2022 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - Initial Implementation
 *
 */

package org.eclipse.dataspaceconnector.ids.token.validation.rule;

import org.eclipse.dataspaceconnector.spi.iam.ClaimToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

class IdsValidationRuleTest {

    private IdsValidationRule rule;

    @BeforeEach
    public void setUp() {
        rule = new IdsValidationRule(false);
    }

    @Test
    void validation_succeeded() {
        Map<String, Object> additional = Map.of("issuerConnector", "issuerConnector");
        var token = ClaimToken.Builder.newInstance().build();

        var result = rule.checkRule(token, additional);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void validation_failed_emptyIssuerConnector() {
        Map<String, Object> additional = emptyMap();

        var token = ClaimToken.Builder.newInstance().build();
        var result = rule.checkRule(token, additional);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureMessages()).contains("Required issuerConnector is missing in message");
    }
}
