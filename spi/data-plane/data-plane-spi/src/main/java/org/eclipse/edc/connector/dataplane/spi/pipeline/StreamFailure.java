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

package org.eclipse.edc.connector.dataplane.spi.pipeline;

import org.eclipse.edc.spi.result.Failure;

import java.util.List;

/**
 * A failure opening a stream.
 */
public class StreamFailure extends Failure {
    private final Reason reason;

    public StreamFailure(List<String> messages, Reason reason) {
        super(messages);
        this.reason = reason;
    }

    @Override
    public String getFailureDetail() {
        var str = super.getFailureDetail();
        return (str != null && !str.isEmpty()) ? (reason + ": " + str) : (reason + "");
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        NOT_FOUND, NOT_AUTHORIZED, GENERAL_ERROR
    }
}
