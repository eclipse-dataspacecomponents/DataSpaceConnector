/*
 * Copyright (c) Microsoft Corporation.
 *  All rights reserved.
 *
 */

package com.microsoft.dagx.dataseed.policy;

import com.microsoft.dagx.policy.model.*;
import com.microsoft.dagx.spi.monitor.Monitor;
import com.microsoft.dagx.spi.policy.PolicyRegistry;
import com.microsoft.dagx.spi.system.ServiceExtension;
import com.microsoft.dagx.spi.system.ServiceExtensionContext;

import java.util.List;

import static com.microsoft.dagx.policy.model.Operator.IN;


public class PolicyDataSeederExtension implements ServiceExtension {

    public static final String USE_EU_POLICY = "use-eu";
    public static final String USE_US_OR_EU_POLICY = "use-us-eu";
    private Monitor monitor;
    private ServiceExtensionContext context;

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();
        this.context = context;

        monitor.info("Initialized Policy DataSeeder extension");
    }

    @Override
    public void start() {
        savePolicies();
        monitor.info("Started Policy DataSeeder extension");
    }

    @Override
    public void shutdown() {
        monitor.info("Shutdown Policy DataSeeder extension");
    }


    private void savePolicies() {
        PolicyRegistry policyRegistry = context.getService(PolicyRegistry.class);

        LiteralExpression spatialExpression = new LiteralExpression("ids:absoluteSpatialPosition");
        var euConstraint = AtomicConstraint.Builder.newInstance().leftExpression(spatialExpression).operator(IN).rightExpression(new LiteralExpression("eu")).build();
        var euUsePermission = Permission.Builder.newInstance().action(Action.Builder.newInstance().type("idsc:USE").build()).constraint(euConstraint).build();
        var euPolicy = Policy.Builder.newInstance().id(USE_EU_POLICY).permission(euUsePermission).build();
        policyRegistry.registerPolicy(euPolicy);

        var usConstraint = AtomicConstraint.Builder.newInstance().leftExpression(spatialExpression).operator(IN).rightExpression(new LiteralExpression("us")).build();
        var usOrEuConstrain = OrConstraint.Builder.newInstance().constraints(List.of(euConstraint, usConstraint)).build();
        var usOrEuPermission = Permission.Builder.newInstance().action(Action.Builder.newInstance().type("idsc:USE").build()).constraint(usOrEuConstrain).build();
        var usOrEuPolicy = Policy.Builder.newInstance().id(USE_US_OR_EU_POLICY).permission(usOrEuPermission).build();
        policyRegistry.registerPolicy(usOrEuPolicy);
    }

}
