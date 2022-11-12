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
    `java-library`
    id("io.swagger.core.v3.swagger-gradle-plugin")
}

val awaitility: String by project
val failsafeVersion: String by project
val httpMockServer: String by project
val jerseyVersion: String by project
val okHttpVersion: String by project
val restAssured: String by project
val rsApi: String by project


dependencies {
    api(project(":spi:control-plane:transfer-spi"))
    api(project(":spi:common:web-spi"))
    implementation(project(":extensions:common:api:api-core"))

    implementation("com.squareup.okhttp3:okhttp:${okHttpVersion}")
    implementation("dev.failsafe:failsafe:${failsafeVersion}")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:${rsApi}")

    testImplementation(project(":core:control-plane:control-plane-core"))
    testImplementation(project(":extensions:common:http"))
    testImplementation(project(":core:common:junit"))
    testImplementation("io.rest-assured:rest-assured:${restAssured}")
    testImplementation("org.awaitility:awaitility:${awaitility}")
    testImplementation("org.mock-server:mockserver-netty:${httpMockServer}:shaded")
}


publishing {
    publications {
        create<MavenPublication>("provision-oauth2") {
            artifactId = "provision-oauth2"
            from(components["java"])
        }
    }
}
