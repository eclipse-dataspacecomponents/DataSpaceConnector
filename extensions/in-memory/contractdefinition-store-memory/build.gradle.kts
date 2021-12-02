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
 *       Daimler TSS GmbH - initial API and implementation
 *
 */

plugins {
    `java-library`
}

dependencies {
    api(project(":spi"))
}

publishing {
    publications {
        create<MavenPublication>("in-memory.contractdefinition-store") {
            artifactId = "in-memory.contractdefinition-store"
            from(components["java"])
        }
    }
}
