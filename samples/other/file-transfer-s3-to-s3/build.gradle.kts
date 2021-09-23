/*
 *  Copyright (c) 2021 Siemens AG
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Siemens AG - initial implementation
 *
 */

val awsVersion: String by project
val rsApi: String by project

plugins {
    `java-library`
    id("application")
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

dependencies {
    api(project(":core:bootstrap"))
    api(project(":spi"))
    implementation(project(":extensions:aws:s3:provision"))
    implementation(project(":extensions:aws:s3:s3-schema"))


    implementation(platform("software.amazon.awssdk:bom:${awsVersion}"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:sts")
    implementation("software.amazon.awssdk:auth")
    implementation("software.amazon.awssdk:iam")

    implementation("jakarta.ws.rs:jakarta.ws.rs-api:${rsApi}")

    implementation(project(":core:protocol:web"))
    implementation(project(":core:transfer"))
    implementation(project(":data-protocols:ids"))
    implementation(project(":extensions:in-memory:policy-registry-memory"))
    implementation(project(":extensions:in-memory:metadata-memory"))
    implementation(project(":extensions:in-memory:transfer-store-memory"))
    implementation(project(":extensions:iam:iam-mock"))
    implementation(project(":data-protocols:ids:ids-policy-mock"))
    implementation(project(":extensions:filesystem:configuration-fs"))
}

application {
    @Suppress("DEPRECATION")
    mainClassName = "org.eclipse.dataspaceconnector.transfer.demo.DemoLauncher"
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    exclude("**/pom.properties", "**/pom.xm")
    mergeServiceFiles()
    archiveFileName.set("dataspaceconnector-transfer-demo.jar")
}