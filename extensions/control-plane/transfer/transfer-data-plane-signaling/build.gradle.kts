/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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
    api(project(":spi:common:core-spi"))
    api(project(":spi:control-plane:contract-spi"))
    api(project(":spi:control-plane:transfer-spi"))

    api(project(":spi:control-plane:transfer-data-plane-spi"))
    api(project(":spi:data-plane:data-plane-spi"))
    api(project(":extensions:data-plane:data-plane-signaling:data-plane-signaling-client"))
    api(project(":spi:data-plane-selector:data-plane-selector-spi"))
    
    testImplementation(project(":core:common:junit"))
}


