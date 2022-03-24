/*
 *  Copyright (c) 2020-2022 Microsoft Corporation
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

val mockitoVersion: String by project

plugins {
    `java-library`
}

dependencies {
    api(project(":extensions:data-plane-selector:selector-spi"))
    implementation(project(":common:util"))
}

publishing {
    publications {
        create<MavenPublication>("data-plane-selector-spi") {
            artifactId = "data-plane-selector-spi"
            from(components["java"])
        }
    }
}
