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

import de.fraunhofer.iais.eis.PermissionBuilder;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import org.eclipse.dataspaceconnector.ids.spi.IdsId;
import org.eclipse.dataspaceconnector.ids.spi.IdsType;
import org.eclipse.dataspaceconnector.ids.spi.transform.IdsTypeTransformer;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformerContext;
import org.eclipse.dataspaceconnector.policy.model.Constraint;
import org.eclipse.dataspaceconnector.policy.model.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.Objects;

public class PermissionToPermissionTransformer implements IdsTypeTransformer<Permission, de.fraunhofer.iais.eis.Permission> {

    @Override
    public Class<Permission> getInputType() {
        return Permission.class;
    }

    @Override
    public Class<de.fraunhofer.iais.eis.Permission> getOutputType() {
        return de.fraunhofer.iais.eis.Permission.class;
    }

    @Override
    public @Nullable de.fraunhofer.iais.eis.Permission transform(Permission object, @NotNull TransformerContext context) {
        Objects.requireNonNull(context);
        if (object == null) {
            return null;
        }

        IdsId idsId = IdsId.Builder.newInstance().value(object.hashCode()).type(IdsType.PERMISSION).build();
        URI id = context.transform(idsId, URI.class);
        PermissionBuilder permissionBuilder = new PermissionBuilder(id);

        for (Constraint edcConstraint : object.getConstraints()) {
            de.fraunhofer.iais.eis.Constraint idsConstraint = context.transform(edcConstraint, de.fraunhofer.iais.eis.Constraint.class);
            permissionBuilder._constraint_(idsConstraint);
        }

        String target = object.getTarget();
        if (target != null) {
            permissionBuilder._target_(URI.create(target));
        }

        String assigner = object.getAssigner();
        if (assigner != null) {
            permissionBuilder._assigner_(Collections.singletonList(URI.create(assigner)));
        }

        String assignee = object.getAssignee();
        if (assignee != null) {
            permissionBuilder._assignee_(Collections.singletonList(URI.create(assignee)));
        }

        if (object.getAction() != null) {
            de.fraunhofer.iais.eis.Action action = context.transform(object.getAction(), de.fraunhofer.iais.eis.Action.class);
            permissionBuilder._action_(action);
        }

        if (object.getDuty() != null) {
            de.fraunhofer.iais.eis.Duty duty = context.transform(object.getDuty(), de.fraunhofer.iais.eis.Duty.class);
            permissionBuilder._preDuty_(duty);
        }

        de.fraunhofer.iais.eis.Permission permission;
        try {
            permission = permissionBuilder.build();
        } catch (ConstraintViolationException e) {
            context.reportProblem(String.format("Failed to build IDS permission: %s", e.getMessage()));
            permission = null;
        }

        return permission;
    }
}
