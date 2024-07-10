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
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

plugins {
    `java-library`
}

dependencies {
    implementation(project(":core:common:token-core"))
    implementation(project(":core:control-plane:control-plane-core"))
    implementation(project(":data-protocols:dsp"))
    implementation(project(":extensions:common:http"))
    implementation(project(":extensions:common:iam:iam-mock"))
    implementation(project(":extensions:common:json-ld"))
    implementation(project(":extensions:control-plane:api:management-api"))
    implementation(project(":extensions:control-plane:api:management-api:secrets-api"))
    implementation(project(":extensions:common:api:version-api"))
    implementation(project(":extensions:data-plane:data-plane-control-api"))
    implementation(project(":extensions:common:api:api-observability"))
    implementation(project(":extensions:data-plane:data-plane-client"))
    implementation(project(":extensions:common:iam:identity-trust:identity-trust-sts:identity-trust-sts-api"))
    implementation(project(":core:data-plane-selector:data-plane-selector-core"))
    implementation(project(":extensions:common:api:control-api-configuration"))
}

edcBuild {
    publish.set(false)
}
