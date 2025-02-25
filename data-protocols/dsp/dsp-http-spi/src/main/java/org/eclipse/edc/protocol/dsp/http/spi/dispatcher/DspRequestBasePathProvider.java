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

package org.eclipse.edc.protocol.dsp.http.spi.dispatcher;


import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

/**
 * Provide the http request base path given the outgoing message.
 */
@FunctionalInterface
public interface DspRequestBasePathProvider {
    /**
     * Return the path
     *
     * @param message the message.
     * @return the path.
     */
    String provideBasePath(RemoteMessage message);
}
