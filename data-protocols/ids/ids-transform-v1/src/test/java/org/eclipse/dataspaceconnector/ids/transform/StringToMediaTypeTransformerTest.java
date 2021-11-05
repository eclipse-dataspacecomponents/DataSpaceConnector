/*
 *  Copyright (c) 2021 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial Implementation
 *
 */

package org.eclipse.dataspaceconnector.ids.transform;

import org.easymock.EasyMock;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StringToMediaTypeTransformerTest {
    private static final String STRING = "hello";

    // subject
    StringToMediaTypeTransformer stringToMediaTypeTransformer;

    // mocks
    private TransformerContext context;

    @BeforeEach
    public void setup() {
        stringToMediaTypeTransformer = new StringToMediaTypeTransformer();
        context = EasyMock.createMock(TransformerContext.class);
    }

    @Test
    void testThrowsNullPointerExceptionForAll() {
        EasyMock.replay(context);

        Assertions.assertThrows(NullPointerException.class, () -> {
            stringToMediaTypeTransformer.transform(null, null);
        });
    }

    @Test
    void testThrowsNullPointerExceptionForContext() {
        EasyMock.replay(context);

        Assertions.assertThrows(NullPointerException.class, () -> {
            stringToMediaTypeTransformer.transform(STRING, null);
        });
    }

    @Test
    void testReturnsNull() {
        EasyMock.replay(context);

        var result = stringToMediaTypeTransformer.transform(null, context);

        Assertions.assertNull(result);
    }

    @Test
    void testSuccessfulSimple() {
        // record
        EasyMock.replay(context);

        // invoke
        var result = stringToMediaTypeTransformer.transform(STRING, context);

        // verify
        Assertions.assertNotNull(result);
        Assertions.assertEquals(STRING, result.getFilenameExtension());
    }
}
