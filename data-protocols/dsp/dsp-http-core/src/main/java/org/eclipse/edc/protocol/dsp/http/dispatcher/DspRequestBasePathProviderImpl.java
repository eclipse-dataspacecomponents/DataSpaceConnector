/*
 *  Copyright (c) 2025 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.protocol.dsp.http.dispatcher;

import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolVersion;
import org.eclipse.edc.protocol.dsp.http.spi.DspProtocolParser;
import org.eclipse.edc.protocol.dsp.http.spi.dispatcher.DspRequestBasePathProvider;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

public class DspRequestBasePathProviderImpl implements DspRequestBasePathProvider {

    private final DspProtocolParser protocolParser;
    private final boolean wellKnownPath;

    public DspRequestBasePathProviderImpl(DspProtocolParser protocolParser, boolean wellKnownPath) {
        this.protocolParser = protocolParser;
        this.wellKnownPath = wellKnownPath;
    }

    @Override
    public String provideBasePath(RemoteMessage message) {
        var protocolPath = "";
        if (wellKnownPath) {
            protocolPath = protocolParser.parse(message.getProtocol())
                    .map(ProtocolVersion::path)
                    .map(this::removeTrailingSlash)
                    .orElseThrow(failure -> new EdcException(failure.getFailureDetail()));
        }
        return message.getCounterPartyAddress() + protocolPath;
    }

    private String removeTrailingSlash(String path) {
        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
