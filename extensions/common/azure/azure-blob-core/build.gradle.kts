/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
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
    `java-test-fixtures`
}

dependencies {
    api(project(":spi:control-plane:control-plane-spi"))

    implementation(root.azure.storageblob)
    implementation(project(":core:common:util"))

    testFixturesApi(project(":extensions:common:azure:azure-blob-core"))
    testFixturesImplementation(project(":core:common:util"))

    testFixturesImplementation(root.junit.jupiter.api)
    testFixturesRuntimeOnly(root.junit.jupiter.engine)
}


