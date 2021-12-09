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

import de.fraunhofer.iais.eis.BinaryOperator;
import de.fraunhofer.iais.eis.LeftOperand;
import de.fraunhofer.iais.eis.util.RdfResource;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformerContext;
import org.eclipse.dataspaceconnector.policy.model.AtomicConstraint;
import org.eclipse.dataspaceconnector.policy.model.Expression;
import org.eclipse.dataspaceconnector.policy.model.LiteralExpression;
import org.eclipse.dataspaceconnector.policy.model.Operator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IdsConstraintToConstraintTransformerTest {

    private static final URI CONSTRAINT_ID = URI.create("https://constraint.com");

    private IdsConstraintToConstraintTransformer transformer;

    private de.fraunhofer.iais.eis.Constraint idsConstraint;
    private TransformerContext context;

    @BeforeEach
    void setUp() {
        transformer = new IdsConstraintToConstraintTransformer();
        idsConstraint = new de.fraunhofer.iais.eis.ConstraintBuilder(CONSTRAINT_ID)
                ._leftOperand_(LeftOperand.PURPOSE)
                ._operator_(BinaryOperator.EQ)
                ._rightOperand_(new RdfResource("hello"))
                .build();
        context = mock(TransformerContext.class);
    }

    @Test
    void testThrowsNullPointerExceptionForAll() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            transformer.transform(null, null);
        });
    }

    @Test
    void testThrowsNullPointerExceptionForContext() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            transformer.transform(idsConstraint, null);
        });
    }

    @Test
    void testReturnsNull() {
        var result = transformer.transform(null, context);

        Assertions.assertNull(result);
    }

    @Test
    void testTransform() {
        // prepare
        var expectedLeftExpression = new LiteralExpression("left");
        var expectedRightExpression = new LiteralExpression("right");

        when(context.transform(any(LeftOperand.class), eq(Expression.class)))
                .thenReturn(expectedLeftExpression);
        when(context.transform(any(BinaryOperator.class), eq(Operator.class)))
                .thenReturn(Operator.EQ);
        when(context.transform(any(RdfResource.class), eq(Expression.class)))
                .thenReturn(expectedRightExpression);

        var result = (AtomicConstraint) transformer.transform(idsConstraint, context);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getLeftExpression());
        Assertions.assertEquals(result.getLeftExpression(), expectedLeftExpression);
        Assertions.assertNotNull(result.getOperator());
        Assertions.assertNotNull(result.getRightExpression());
        Assertions.assertEquals(result.getRightExpression(), expectedRightExpression);
    }

}
