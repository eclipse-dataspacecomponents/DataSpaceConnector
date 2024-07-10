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
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *       Mercedes-Benz Tech Innovation GmbH - publish public api context into dedicated swagger hub page
 */

plugins {
    `java-library`
}

dependencies {
    implementation(project(":core:data-plane:data-plane-core"))
    implementation(project(":extensions:common:api:control-api-configuration"))
    implementation(project(":extensions:control-plane:api:control-plane-api-client"))
    implementation(project(":extensions:data-plane:data-plane-self-registration"))
    implementation(project(":extensions:data-plane:data-plane-http"))
    implementation(project(":extensions:data-plane:data-plane-kafka"))
    implementation(project(":extensions:data-plane:data-plane-http-oauth2"))
    implementation(project(":extensions:data-plane:data-plane-control-api"))
    implementation(project(":extensions:data-plane:data-plane-public-api-v2"))
    implementation(project(":extensions:data-plane:data-plane-signaling:data-plane-signaling-api"))
}

edcBuild {
    publish.set(false)
}
