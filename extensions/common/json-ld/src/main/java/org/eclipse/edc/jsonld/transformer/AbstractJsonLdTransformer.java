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

package org.eclipse.edc.jsonld.transformer;

/**
 * Base JSON-LD transformer implementation.
 */
public abstract class AbstractJsonLdTransformer<INPUT, OUTPUT> implements JsonLdTransformer<INPUT, OUTPUT> {
    private final Class<INPUT> input;
    private final Class<OUTPUT> output;

    protected AbstractJsonLdTransformer(Class<INPUT> input, Class<OUTPUT> output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public Class<INPUT> getInputType() {
        return input;
    }

    @Override
    public Class<OUTPUT> getOutputType() {
        return output;
    }
}
