/*
 *  Copyright (c) 2021 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial API and Implementation
 *
 */
val infoModelVersion: String by project
val rsApi: String by project
val jerseyVersion: String by project

plugins {
    `java-library`
}

dependencies {
    api(project(":spi"))
    api(project(":data-protocols:ids:ids-spi"))
    api(project(":data-protocols:ids:ids-core"))
    api(project(":data-protocols:ids:ids-transform-v1"))
    api(project(":core:transfer"))

    implementation("jakarta.ws.rs:jakarta.ws.rs-api:${rsApi}")
    implementation("org.glassfish.jersey.media:jersey-media-multipart:${jerseyVersion}")

    testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.28.0")
    testImplementation("net.javacrumbs.json-unit:json-unit-json-path:2.28.0")
    testImplementation("net.javacrumbs.json-unit:json-unit:2.28.0")
    testImplementation(testFixtures(project(":launchers:junit")))
    testImplementation(project(":core:protocol:web"))

}

publishing {
    publications {
        create<MavenPublication>("ids-api-multipart-endpoint-v1") {
            artifactId = "ids-api-multipart-endpoint-v1"
            from(components["java"])
        }
    }
}
