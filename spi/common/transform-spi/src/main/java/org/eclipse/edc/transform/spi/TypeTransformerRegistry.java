/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
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

package org.eclipse.edc.transform.spi;

import org.eclipse.edc.spi.result.Result;
import org.jetbrains.annotations.NotNull;

/**
 * Generic registry to hold {@link TypeTransformer} objects
 */
public interface TypeTransformerRegistry {

    /**
     * Registers a transformer.
     */
    void register(TypeTransformer<?, ?> transformer);

    /**
     * Return a type transformer registry specific to a context.
     *
     * @param context the context.
     * @return the type transformer registry context specific.
     */
    @NotNull TypeTransformerRegistry forContext(String context);

    /**
     * Returns a transformer that can transfor input to outputType
     * Throws an exception if the transformer cannot be found.
     *
     * @param <INPUT>    input type
     * @param <OUTPUT>   output type
     * @param input      the input object
     * @param outputType the output type
     * @return a transformer able to transform the input to the output
     */
    @NotNull <INPUT, OUTPUT> TypeTransformer<INPUT, OUTPUT> transformerFor(@NotNull INPUT input, @NotNull Class<OUTPUT> outputType);

    /**
     * Transforms the input and any contained types, returning its transformed representation or null if the operation cannot be completed.
     *
     * @param <INPUT>    the instance type
     * @param <OUTPUT>   the transformed input type
     * @param input      the instance to transform
     * @param outputType the transformed output type
     * @return the transform result
     */
    <INPUT, OUTPUT> Result<OUTPUT> transform(@NotNull INPUT input, @NotNull Class<OUTPUT> outputType);

}
