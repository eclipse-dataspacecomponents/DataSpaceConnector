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
    api(project(":spi:common:catalog-spi"))
    api(project(":spi:control-plane:contract-spi"))
    api(project(":spi:control-plane:transfer-spi"))

    testImplementation(project(":core:common:junit"))
    testImplementation(project(":core:control-plane:control-plane-core"))
}


