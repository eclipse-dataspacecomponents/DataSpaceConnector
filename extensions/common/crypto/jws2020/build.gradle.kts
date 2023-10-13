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
plugins {
    `java-library`
}

dependencies {
    implementation(project(":spi:common:jwt-spi"))
    implementation(project(":spi:common:json-ld-spi"))
    implementation(project(":extensions:common:json-ld"))
    implementation(project(":core:common:util"))
    implementation(project(":extensions:common:crypto:crypto-core"))
    implementation(libs.nimbus.jwt)
    // used for the Ed25519 Verifier in conjunction with OctetKeyPairs (OKP)
    runtimeOnly(libs.tink)
    implementation(libs.jakartaJson)

    api(libs.iron.vc) {
        exclude("com.github.multiformats")
    }

    testImplementation(testFixtures(project(":core:common:junit")))
}
