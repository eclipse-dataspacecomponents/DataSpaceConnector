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

package org.eclipse.edc.connector.contract;

import org.eclipse.edc.connector.contract.observe.ContractNegotiationObservableImpl;
import org.eclipse.edc.connector.contract.offer.ContractDefinitionResolverImpl;
import org.eclipse.edc.connector.contract.policy.PolicyArchiveImpl;
import org.eclipse.edc.connector.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.contract.spi.offer.ContractDefinitionResolver;
import org.eclipse.edc.connector.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Contract Negotiation Default Services Extension
 */
@Extension(value = ContractNegotiationDefaultServicesExtension.NAME)
public class ContractNegotiationDefaultServicesExtension implements ServiceExtension {

    public static final String NAME = "Contract Negotiation Default Services";

    @Inject
    private ContractDefinitionStore contractDefinitionStore;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private PolicyDefinitionStore policyStore;

    @Inject
    private ContractNegotiationStore store;

    @Provider
    public ContractDefinitionResolver contractDefinitionResolver(ServiceExtensionContext context) {
        return new ContractDefinitionResolverImpl(context.getMonitor(), contractDefinitionStore, policyEngine, policyStore);
    }

    @Provider
    public ContractNegotiationObservable observable() {
        return new ContractNegotiationObservableImpl();
    }

    @Provider
    public PolicyArchive policyArchive() {
        return new PolicyArchiveImpl(store);
    }

    @Provider(isDefault = true)
    public ContractNegotiationPendingGuard pendingGuard() {
        return it -> false;
    }
}
