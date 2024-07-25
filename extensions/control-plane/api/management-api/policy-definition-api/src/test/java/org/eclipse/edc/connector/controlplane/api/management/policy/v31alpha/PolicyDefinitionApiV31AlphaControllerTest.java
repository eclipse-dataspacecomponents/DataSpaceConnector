/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.connector.controlplane.api.management.policy.v31alpha;

import io.restassured.specification.RequestSpecification;
import org.eclipse.edc.connector.controlplane.api.management.policy.BasePolicyDefinitionApiControllerTest;

import static io.restassured.RestAssured.given;

public class PolicyDefinitionApiV31AlphaControllerTest extends BasePolicyDefinitionApiControllerTest {

    @Override
    protected Object controller() {
        return new PolicyDefinitionApiV31AlphaController(monitor, transformerRegistry, service, validatorRegistry);
    }

    @Override
    protected RequestSpecification baseRequest() {
        return given()
                .baseUri("http://localhost:%d/v3.1alpha/policydefinitions".formatted(port))
                .port(port);
    }

}
