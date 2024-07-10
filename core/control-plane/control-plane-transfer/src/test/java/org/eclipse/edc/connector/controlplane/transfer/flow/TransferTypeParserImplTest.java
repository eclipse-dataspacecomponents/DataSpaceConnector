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

package org.eclipse.edc.connector.controlplane.transfer.flow;

import org.eclipse.edc.connector.controlplane.transfer.spi.flow.TransferTypeParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.types.domain.transfer.FlowType.PULL;
import static org.eclipse.edc.spi.types.domain.transfer.FlowType.PUSH;

class TransferTypeParserImplTest {

    private final TransferTypeParser parser = new TransferTypeParserImpl();

    @Test
    void shouldExtractPull() {
        var result = parser.parse("DestinationType-PULL");

        assertThat(result).isSucceeded().satisfies(type -> {
            assertThat(type.destinationType()).isEqualTo("DestinationType");
            assertThat(type.flowType()).isEqualTo(PULL);
        });
    }

    @Test
    void shouldExtractPush() {
        var result = parser.parse("DestinationType-PUSH");

        assertThat(result).isSucceeded().satisfies(type -> {
            assertThat(type.destinationType()).isEqualTo("DestinationType");
            assertThat(type.flowType()).isEqualTo(PUSH);
        });
    }

    @Test
    void shouldReturnFatalError_whenTypeIsUnknown() {
        var result = parser.parse("Any-NOT_KNOWN");

        assertThat(result).isFailed();
    }

    @Test
    void shouldReturnFatalError_whenFormatIsNotCorrect() {
        var result = parser.parse("not_correct");

        assertThat(result).isFailed();
    }

}
