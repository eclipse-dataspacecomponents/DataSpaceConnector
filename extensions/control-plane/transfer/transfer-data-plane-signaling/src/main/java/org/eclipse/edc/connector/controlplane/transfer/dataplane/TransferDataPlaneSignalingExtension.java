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

package org.eclipse.edc.connector.controlplane.transfer.dataplane;

import org.eclipse.edc.connector.controlplane.transfer.dataplane.flow.DataPlaneSignalingFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowPropertiesProvider;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.TransferTypeParser;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.configuration.context.ControlApiUrl;

import java.util.Map;

import static org.eclipse.edc.connector.controlplane.transfer.dataplane.TransferDataPlaneSignalingExtension.NAME;

@Extension(NAME)
public class TransferDataPlaneSignalingExtension implements ServiceExtension {

    protected static final String NAME = "Transfer Data Plane Signaling Extension";

    private static final String DEFAULT_DATAPLANE_SELECTOR_STRATEGY = "random";

    @Setting(value = "Defines strategy for Data Plane instance selection in case Data Plane is not embedded in current runtime", defaultValue = DEFAULT_DATAPLANE_SELECTOR_STRATEGY)
    private static final String DPF_SELECTOR_STRATEGY = "edc.dataplane.client.selector.strategy";

    @Inject
    private DataFlowManager dataFlowManager;

    @Inject(required = false)
    private ControlApiUrl callbackUrl;

    @Inject
    private DataPlaneSelectorService selectorService;

    @Inject
    private DataPlaneClientFactory clientFactory;

    @Inject(required = false)
    private DataFlowPropertiesProvider propertiesProvider;

    @Inject
    private TransferTypeParser transferTypeParser;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var selectionStrategy = context.getSetting(DPF_SELECTOR_STRATEGY, DEFAULT_DATAPLANE_SELECTOR_STRATEGY);
        var controller = new DataPlaneSignalingFlowController(callbackUrl, selectorService, getPropertiesProvider(),
                clientFactory, selectionStrategy, transferTypeParser);
        dataFlowManager.register(controller);
    }

    private DataFlowPropertiesProvider getPropertiesProvider() {
        return propertiesProvider == null ? (tp, p) -> StatusResult.success(Map.of()) : propertiesProvider;
    }

}
