/*
 *  Copyright (c) 2022 Microsoft Corporation
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

package org.eclipse.edc.util.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortsTest {

    public static final int MAX_PORT = 65535;
    public static final int MIN_PORT = 1024;

    @Test
    void getFreePort() {
        assertThat(Ports.getFreePort()).isGreaterThan(MIN_PORT).isLessThan(MAX_PORT);
    }

    @Test
    void getFreePort_lowerBound() {
        assertThat(Ports.getFreePort(5000)).isGreaterThanOrEqualTo(5000).isLessThan(MAX_PORT);

        assertThatThrownBy(() -> Ports.getFreePort(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ports.getFreePort(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ports.getFreePort(65536)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFreePort_withUpperAndLowerBound() {
        assertThat(Ports.getFreePort(5000, 7000)).isGreaterThanOrEqualTo(5000).isLessThan(7000);

        assertThatThrownBy(() -> Ports.getFreePort(5000, 4999)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ports.getFreePort(5000, 5000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ports.getFreePort(5000, 65536)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ports.getFreePort(5000, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFreePortAvoidDuplicated() {
        var lowerBound = 6000;
        var portsToBeGenerated = 10;
        var ports = range(0, portsToBeGenerated)
                .mapToObj(i -> Ports.getFreePort(lowerBound, lowerBound + portsToBeGenerated))
                .distinct().toList();

        assertThat(ports.size()).isEqualTo(portsToBeGenerated);
    }

    @Test
    void getFreePort_whenOccupied() throws IOException {
        var port = Ports.getFreePort(MIN_PORT);

        try (var socket = new ServerSocket(port)) {
            assertThat(Ports.getFreePort(MIN_PORT)).describedAs("Next free port").isNotEqualTo(socket.getLocalPort());
        }
    }
}
