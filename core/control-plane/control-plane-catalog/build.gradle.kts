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
*       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - Initial API and Implementation
*
*/
plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":spi:control-plane:catalog-spi"))
    api(project(":spi:control-plane:contract-spi"))
    api(project(":spi:control-plane:transfer-spi"))
    api(project(":spi:control-plane:asset-spi"))

    implementation(project(":spi:common:data-address:data-address-http-data-spi"))
    testImplementation(project(":tests:junit-base"))

    testImplementation(project(":core:common:connector-core"))
    testImplementation(project(":core:control-plane:control-plane-core"))
    testImplementation(project(":core:common:lib:query-lib"))
}


