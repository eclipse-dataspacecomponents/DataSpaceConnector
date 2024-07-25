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

package org.eclipse.edc.util.io;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utilities for assigning ports.
 */
public final class Ports {
    public static final int MAX_TCP_PORT = 65_535;
    private static final Random RANDOM = new Random();
    private static final Set<Integer> ALREADY_RETURNED = new HashSet<>();

    /**
     * Gets a free port in the range 1024 - 65535 by trying them randomly.
     *
     * @return the lower bound.
     * @throws IllegalArgumentException if no free port is available
     */
    public static int getFreePort() {
        return getFreePort(1024);
    }

    /**
     * Gets a free port in the range lowerBound - 65535 by trying them randomly.
     *
     * @return the lower bound.
     * @throws IllegalArgumentException if no free port is available
     */
    public static int getFreePort(int lowerBound) {
        if (lowerBound <= 0 || lowerBound >= MAX_TCP_PORT) {
            throw new IllegalArgumentException("Lower bound must be > 0 and < " + MAX_TCP_PORT);
        }
        return getFreePort(lowerBound, MAX_TCP_PORT);
    }

    /**
     * Gets a free port in the range lowerBound - upperBound randomly. Will not return ports already returned.
     *
     * @return the first free port
     * @throws IllegalArgumentException if no free port is available or if the bounds are invalid.
     */
    public static int getFreePort(int lowerBound, int upperBound) {

        if (lowerBound <= 0 || lowerBound >= MAX_TCP_PORT || lowerBound >= upperBound) {
            throw new IllegalArgumentException("Lower bound must be > 0 and < " + MAX_TCP_PORT + " and be < upperBound");
        }
        if (upperBound > MAX_TCP_PORT) {
            throw new IllegalArgumentException("Upper bound must be < " + MAX_TCP_PORT);
        }

        do {
            var port = lowerBound + RANDOM.nextInt(upperBound - lowerBound);
            if (!ALREADY_RETURNED.contains(port)) {
                ALREADY_RETURNED.add(port);

                if (IS_PORT_AVAILABLE.test(port)) {
                    return port;
                }
            }
        } while (true);
    }

    private static final Predicate<Integer> CHECK_SERVER_SOCKET = port -> {
        try (var serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    };

    private static final Predicate<Integer> CHECK_DATAGRAM_SOCKET = port -> {
        try (var datagramSocket = new DatagramSocket(port)) {
            datagramSocket.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    };

    private static final Predicate<Integer> CHECK_SOCKET = port -> {
        try (var socket = new Socket("localhost", port)) {
            socket.setReuseAddress(true);
            return false;
        } catch (IOException ignored) {
            return true;
        }
    };

    private static final Predicate<Integer> IS_PORT_AVAILABLE = CHECK_SERVER_SOCKET.and(CHECK_DATAGRAM_SOCKET).and(CHECK_SOCKET);

    private Ports() {
    }

}
