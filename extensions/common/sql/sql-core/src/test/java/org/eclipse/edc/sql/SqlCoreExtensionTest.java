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

package org.eclipse.edc.sql;

import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ComponentTest
@ExtendWith(DependencyInjectionExtension.class)
class SqlCoreExtensionTest {
    private ServiceExtensionContext context;

    @BeforeEach
    void setup(ServiceExtensionContext context) {
        this.context = context;
        context.registerService(TransactionContext.class, new NoopTransactionContext());
    }

    @Test
    void initialize(SqlCoreExtension extension) {
        assertThatThrownBy(() -> extension.initialize(context)).isInstanceOf(EdcException.class)
                .hasMessage("The EDC SQL implementations cannot be used with a '%s'. Please provide a TransactionContext implementation.".formatted(NoopTransactionContext.class.getName()));
    }
}