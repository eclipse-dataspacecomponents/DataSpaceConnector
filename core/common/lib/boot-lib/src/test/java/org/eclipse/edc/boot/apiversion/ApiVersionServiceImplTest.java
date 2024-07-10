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

package org.eclipse.edc.boot.apiversion;

import org.eclipse.edc.spi.system.apiversion.VersionRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionServiceImplTest {
    private final ApiVersionServiceImpl service = new ApiVersionServiceImpl();

    @Test
    void addRecord_whenNotExists() {
        service.addRecord("foo", new VersionRecord("1.0.0", "/v1/", Instant.now(), "stable"));
        assertThat(service.getRecords()).hasSize(1).containsKey("foo");
        assertThat(service.getRecords().get("foo")).hasSize(1);
    }

    @Test
    void addRecord_whenExists() {
        service.addRecord("foo", new VersionRecord("1.0.0", "/v1/", Instant.now(), "stable"));
        service.addRecord("foo", new VersionRecord("2.0.0", "/v2/", Instant.now(), "stable"));
        assertThat(service.getRecords()).hasSize(1).containsKey("foo");
        assertThat(service.getRecords().get("foo")).hasSize(2);
    }

    @Test
    void getRecords() {
        service.addRecord("foo1", new VersionRecord("1.0.0", "/v1/", Instant.now(), "stable"));
        service.addRecord("foo2", new VersionRecord("2.0.0", "/v2/", Instant.now(), "stable"));
        assertThat(service.getRecords()).hasSize(2);
    }
}