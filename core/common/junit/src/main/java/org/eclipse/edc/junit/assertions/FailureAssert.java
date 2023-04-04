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

package org.eclipse.edc.junit.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.StringAssert;
import org.eclipse.edc.spi.result.Failure;

public class FailureAssert<F extends Failure, SELF extends FailureAssert<F, SELF>> extends AbstractAssert<SELF, F> {

    protected FailureAssert(F object, Class selfType) {
        super(object, selfType);
    }

    @SuppressWarnings("unchecked")
    public static <SELF extends FailureAssert<F, SELF>, F extends Failure> SELF assertThat(F failure) {
        return (SELF) new FailureAssert<F, SELF>(failure, FailureAssert.class);
    }

    public ListAssert<String> messages() {
        return ListAssert.assertThatList(actual.getMessages());
    }

    public StringAssert detail() {
        return new StringAssert(actual.getFailureDetail());
    }
}
