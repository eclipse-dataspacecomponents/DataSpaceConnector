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
 *
 */

plugins {
    `java-library`
}

dependencies {
    implementation(project(":core:common:edr-store-core"))
    implementation(project(":core:common:token-core"))
    implementation(project(":core:control-plane:control-plane-core"))
    implementation(project(":data-protocols:dsp"))
    implementation(project(":extensions:common:http"))
    implementation(project(":extensions:common:api:control-api-configuration"))
    implementation(project(":extensions:common:api:management-api-configuration"))
    implementation(project(":extensions:common:iam:iam-mock"))

    implementation(project(":extensions:control-plane:api:control-plane-api"))
    implementation(project(":extensions:control-plane:api:management-api"))
    implementation(project(":extensions:control-plane:api:management-api:edr-cache-api"))
    implementation(project(":extensions:control-plane:callback:callback-event-dispatcher"))
    implementation(project(":extensions:control-plane:callback:callback-http-dispatcher"))
    implementation(project(":extensions:control-plane:edr:edr-store-receiver"))
    implementation(project(":extensions:control-plane:transfer:transfer-data-plane-signaling"))

    implementation(project(":core:data-plane-selector:data-plane-selector-core"))
    implementation(project(":extensions:data-plane-selector:data-plane-selector-api"))
    implementation(project(":extensions:data-plane-selector:data-plane-selector-control-api"))

    implementation(project(":extensions:control-plane:provision:provision-http"))
    implementation(project(":extensions:control-plane:transfer:transfer-pull-http-dynamic-receiver"))

    implementation(project(":core:policy-monitor:policy-monitor-core"))
}

edcBuild {
    publish.set(false)
}
