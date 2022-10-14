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
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 */

val okHttpVersion: String by project
val storageBlobVersion: String by project;
val failsafeVersion: String by project

plugins {
    `java-library`
}

dependencies {
    api(project(":spi:data-plane:data-plane-spi"))
    implementation(project(":core:common:util"))
    implementation(project(":core:data-plane:data-plane-common"))
    implementation(project(":extensions:common:aws:s3-core"))

    implementation("dev.failsafe:failsafe:${failsafeVersion}")

    testImplementation(project(":core:data-plane:data-plane-core"))
    testImplementation(testFixtures(project(":extensions:common:aws:aws-test")))
    testImplementation(project(":extensions:common:junit"))
}

publishing {
    publications {
        create<MavenPublication>("data-plane-s3") {
            artifactId = "data-plane-s3"
            from(components["java"])
        }
    }
}
