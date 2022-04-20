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
 *       Microsoft Corporation - initial API and implementation
 *
 */

plugins {
    `java-library`
    id("io.swagger.core.v3.swagger-gradle-plugin")
}

val okHttpVersion: String by project
val jodahFailsafeVersion: String by project
val rsApi: String by project
val restAssured: String by project


dependencies {
    api(project(":spi:transfer-spi"))
    api(project(":spi:web-spi"))
    implementation(project(":extensions:api:auth-spi"))
    implementation(project(":core:transfer")) // needs the AddProvisionedResourceCommand

    implementation("com.squareup.okhttp3:okhttp:${okHttpVersion}")
    implementation("net.jodah:failsafe:${jodahFailsafeVersion}")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:${rsApi}")

    testImplementation(project(":core:contract"))
    testImplementation(project(":extensions:dataloading"))
    testImplementation(project(":extensions:in-memory:assetindex-memory"))
    testImplementation(project(":extensions:in-memory:contractdefinition-store-memory"))
    testImplementation(project(":extensions:in-memory:negotiation-store-memory"))
    testImplementation(project(":extensions:in-memory:policy-store-memory"))
    testImplementation(project(":extensions:in-memory:transfer-store-memory"))
    testImplementation(testFixtures(project(":common:util")))
    testImplementation(testFixtures(project(":launchers:junit")))
    testImplementation("io.rest-assured:rest-assured:${restAssured}")

}


publishing {
    publications {
        create<MavenPublication>("http-provisioner") {
            artifactId = "http-provisioner"
            from(components["java"])
        }
    }
}
