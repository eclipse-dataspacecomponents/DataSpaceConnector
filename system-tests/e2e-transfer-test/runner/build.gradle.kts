/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

plugins {
    java
}

dependencies {
    testImplementation(project(":spi:control-plane:transfer-spi"))
    testImplementation(project(":spi:data-plane:data-plane-spi"))
    testImplementation(project(":extensions:common:sql:sql-core"))
    testImplementation(project(":extensions:common:transaction:transaction-local"))

    testImplementation(project(":spi:common:web-spi"))
    testImplementation(project(":core:common:connector-core"))
    testImplementation(project(":core:common:junit"))
    testImplementation(testFixtures(project(":core:common:lib:http-lib")))
    testImplementation(testFixtures(project(":extensions:common:sql:sql-core")))
    testImplementation(testFixtures(project(":extensions:control-plane:api:management-api:management-api-test-fixtures")))
    testImplementation(project(":extensions:common:json-ld"))

    testImplementation(libs.postgres)
    testImplementation(libs.restAssured)
    testImplementation(libs.assertj)
    testImplementation(libs.awaitility)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.mockserver.netty)
    testImplementation(libs.mockserver.client)
    testImplementation(libs.kafkaClients)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.kafka)

    testCompileOnly(project(":system-tests:e2e-transfer-test:control-plane"))
    testCompileOnly(project(":system-tests:e2e-transfer-test:data-plane"))
}

edcBuild {
    publish.set(false)
}
