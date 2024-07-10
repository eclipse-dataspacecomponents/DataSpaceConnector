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
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - Initial implementation
 *
 */

package org.eclipse.edc.connector.dataplane.spi.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.dataplane.spi.pipeline.StreamFailure.Reason.NOT_FOUND;

public class StreamFailureTest {
    private static final String FAILURE_MESSAGE = "failure message";

    @Test
    void verify_with_failureDetail_message_starts_with_reason() {
        var failure = new StreamFailure(List.of(FAILURE_MESSAGE), NOT_FOUND);
        assertThat(failure.getFailureDetail()).startsWith(NOT_FOUND.toString());
    }

    @Test
    void verify_with_failureDetail_message_only_contains_reason_when_message_is_empty() {
        var failure = new StreamFailure(Collections.emptyList(), NOT_FOUND);
        assertThat(failure.getFailureDetail()).startsWith(NOT_FOUND.toString());
    }
}
