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

plugins {
    `java-library`
    id(libs.plugins.swagger.get().pluginId)
}

dependencies {
    api(project(":spi:common:core-spi"))
    api(project(":spi:data-plane-selector:data-plane-selector-spi"))
    implementation(project(":spi:common:transaction-spi"))
    implementation(project(":core:common:lib:transform-lib"))
    implementation(project(":core:common:lib:util-lib"))
    implementation(project(":core:common:lib:validator-lib"))
    implementation(project(":extensions:common:json-ld"))
    implementation(project(":extensions:common:api:api-core")) //for the exception mapper

    implementation(libs.jakarta.rsApi)

    testImplementation(project(":core:common:lib:json-ld-lib"))
    testImplementation(project(":core:common:connector-core"))
    testImplementation(project(":core:common:junit"))
    testImplementation(project(":core:data-plane-selector:data-plane-selector-core"))
    testImplementation(project(":extensions:common:api:management-api-configuration"))
    testImplementation(project(":extensions:common:http"))
    testImplementation(project(":extensions:data-plane:data-plane-signaling:data-plane-signaling-client"))

    testImplementation(libs.restAssured)
    testImplementation(testFixtures(project(":extensions:common:http:jersey-core")))

}

edcBuild {
    swagger {
        apiGroup.set("management-api")
    }
}



