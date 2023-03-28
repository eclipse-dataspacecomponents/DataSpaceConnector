/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

plugins {
    `java-library`
}

dependencies {
    api(project(":data-protocols:dsp:dsp-core"))
    api(project(":data-protocols:dsp:dsp-spi:dsp-control-plane-spi"))
    api(project(":data-protocols:dsp:dsp-transform"))

    api(project(":spi:common:core-spi"))
    api(project(":spi:control-plane:contract-spi"))
    api(project(":spi:control-plane:control-plane-spi"))
    api(project(":extensions:common:http"))

    implementation("jakarta.json:jakarta.json-api:2.1.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jakarta-jsonp:2.14.2")

    implementation(libs.jakarta.rsApi)
}