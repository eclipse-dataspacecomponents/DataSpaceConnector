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

package org.eclipse.edc.connector.policy.monitor.manager;

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.services.spi.contractagreement.ContractAgreementService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntry;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorManager;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorStore;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContextImpl;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.policy.monitor.PolicyMonitorExtension.POLICY_MONITOR_SCOPE;
import static org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntryStates.COMPLETED;
import static org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntryStates.FAILED;
import static org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntryStates.STARTED;
import static org.eclipse.edc.spi.persistence.StateEntityStore.hasState;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyMonitorManagerImplTest {

    private final PolicyMonitorStore store = mock();
    private final ContractAgreementService contractAgreementService = mock();
    private final TransferProcessService transferProcessService = mock();
    private final PolicyEngine policyEngine = mock();
    private PolicyMonitorManager manager;

    @BeforeEach
    void setUp() {
        manager = PolicyMonitorManagerImpl.Builder.newInstance()
                .executorInstrumentation(ExecutorInstrumentation.noop())
                .monitor(mock())
                .clock(mock())
                .contractAgreementService(contractAgreementService)
                .policyEngine(policyEngine)
                .transferProcessService(transferProcessService)
                .store(store)
                .build();
    }

    @Test
    void startMonitoring() {
        manager.startMonitoring("transferProcessId", "contractId");

        var captor = ArgumentCaptor.forClass(PolicyMonitorEntry.class);
        verify(store).save(captor.capture());
        var entry = captor.getValue();
        assertThat(entry.getId()).isEqualTo("transferProcessId");
        assertThat(entry.getContractId()).isEqualTo("contractId");
        assertThat(entry.getState()).isEqualTo(STARTED.code());
    }

    @Test
    void started_shouldTerminateTransferAndTransitionToComplete_whenPolicyIsNotValid() {
        var entry = PolicyMonitorEntry.Builder.newInstance()
                .id("transferProcessId")
                .contractId("contractId")
                .state(STARTED.code())
                .build();
        var policy = Policy.Builder.newInstance().build();
        var contractAgreement = createContractAgreement(policy);
        when(store.nextNotLeased(anyInt(), stateIs(STARTED.code()))).thenReturn(List.of(entry)).thenReturn(emptyList());
        when(transferProcessService.findById(entry.getId()))
                .thenReturn(TransferProcess.Builder.newInstance().state(TransferProcessStates.STARTED.code()).build());
        when(contractAgreementService.findById(any())).thenReturn(contractAgreement);
        when(policyEngine.evaluate(any(), any(), isA(PolicyContext.class))).thenReturn(Result.failure("policy is not valid"));
        when(transferProcessService.terminate(any())).thenReturn(ServiceResult.success());

        manager.start();

        await().untilAsserted(() -> {
            verify(contractAgreementService).findById("contractId");
            var captor = ArgumentCaptor.forClass(PolicyContextImpl.class);
            verify(policyEngine).evaluate(eq(POLICY_MONITOR_SCOPE), same(policy), captor.capture());
            var policyContext = captor.getValue();
            assertThat(policyContext.getContextData(ContractAgreement.class)).isSameAs(contractAgreement);
            verify(transferProcessService).terminate(argThat(c -> c.getEntityId().equals("transferProcessId")));
            verify(store).save(argThat(it -> it.getState() == COMPLETED.code()));
        });
    }

    @Test
    void started_shouldNotTransitionToComplete_whenTransferProcessTerminationFails() {
        var entry = PolicyMonitorEntry.Builder.newInstance()
                .id("transferProcessId")
                .contractId("contractId")
                .state(STARTED.code())
                .build();
        var policy = Policy.Builder.newInstance().build();
        when(store.nextNotLeased(anyInt(), stateIs(STARTED.code()))).thenReturn(List.of(entry)).thenReturn(emptyList());
        when(transferProcessService.findById(entry.getId()))
                .thenReturn(TransferProcess.Builder.newInstance().state(TransferProcessStates.STARTED.code()).build());
        when(contractAgreementService.findById(any())).thenReturn(createContractAgreement(policy));
        when(policyEngine.evaluate(any(), any(), isA(PolicyContext.class))).thenReturn(Result.failure("policy is not valid"));
        when(transferProcessService.terminate(any())).thenReturn(ServiceResult.conflict("failure"));

        manager.start();

        await().untilAsserted(() -> {
            verify(store).save(argThat(it -> it.getState() == STARTED.code()));
        });
    }

    @Test
    void started_shouldDoNothing_whenPolicyIsValid() {
        var entry = PolicyMonitorEntry.Builder.newInstance()
                .id("transferProcessId")
                .contractId("contractId")
                .state(STARTED.code())
                .build();
        var policy = Policy.Builder.newInstance().build();

        var stateTimestamp = entry.getStateTimestamp();
        when(store.nextNotLeased(anyInt(), stateIs(STARTED.code()))).thenReturn(List.of(entry)).thenReturn(emptyList());
        when(transferProcessService.findById(entry.getId()))
                .thenReturn(TransferProcess.Builder.newInstance().state(TransferProcessStates.STARTED.code()).build());
        when(contractAgreementService.findById(any())).thenReturn(createContractAgreement(policy));
        when(policyEngine.evaluate(any(), any(), isA(PolicyContext.class))).thenReturn(Result.success());

        manager.start();

        await().untilAsserted(() -> {
            verify(transferProcessService, never()).terminate(any());
            verify(store).save(argThat(it -> it.getState() == STARTED.code() && stateTimestamp < it.getStateTimestamp()));
        });
    }

    @Test
    void started_shouldTransitionToCompleted_whenTransferProcessIsAlreadyCompletedOrTerminated() {
        var entry = PolicyMonitorEntry.Builder.newInstance()
                .id("transferProcessId")
                .contractId("contractId")
                .state(STARTED.code())
                .build();
        when(store.nextNotLeased(anyInt(), stateIs(STARTED.code()))).thenReturn(List.of(entry)).thenReturn(emptyList());
        when(transferProcessService.findById(entry.getId()))
                .thenReturn(TransferProcess.Builder.newInstance().state(TransferProcessStates.COMPLETED.code()).build());

        manager.start();

        await().untilAsserted(() -> {
            verifyNoInteractions(policyEngine, contractAgreementService);
            verify(store).save(argThat(it -> it.getState() == COMPLETED.code()));
        });
    }

    @Test
    void started_shouldTransitionToFailed_whenTransferProcessNotFound() {
        var entry = PolicyMonitorEntry.Builder.newInstance()
                .id("transferProcessId")
                .contractId("contractId")
                .state(STARTED.code())
                .build();
        when(store.nextNotLeased(anyInt(), stateIs(STARTED.code()))).thenReturn(List.of(entry)).thenReturn(emptyList());
        when(transferProcessService.findById(any())).thenReturn(null);

        manager.start();

        await().untilAsserted(() -> {
            verifyNoInteractions(policyEngine, contractAgreementService);
            verify(store).save(argThat(it -> it.getState() == FAILED.code()));
        });
    }

    @Test
    void started_shouldTransitionToFailed_whenContractAgreementNotFound() {
        var entry = PolicyMonitorEntry.Builder.newInstance()
                .id("transferProcessId")
                .contractId("contractId")
                .state(STARTED.code())
                .build();
        when(store.nextNotLeased(anyInt(), stateIs(STARTED.code()))).thenReturn(List.of(entry)).thenReturn(emptyList());
        when(contractAgreementService.findById(any())).thenReturn(null);

        manager.start();

        await().untilAsserted(() -> {
            verifyNoInteractions(policyEngine);
            verify(store).save(argThat(it -> it.getState() == FAILED.code()));
        });
    }

    private ContractAgreement createContractAgreement(Policy policy) {
        return ContractAgreement.Builder.newInstance()
                .providerId("providerId")
                .consumerId("consumerId")
                .assetId("assetIt")
                .policy(policy)
                .build();
    }

    private Criterion[] stateIs(int state) {
        return aryEq(new Criterion[]{ hasState(state) });
    }
}
