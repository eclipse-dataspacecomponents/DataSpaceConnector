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

package org.eclipse.edc.test.e2e;

import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures.noConstraintPolicy;
import static org.eclipse.edc.junit.testfixtures.TestUtils.getResourceFileContentAsString;

public abstract class TransferEndToEndTestBase {

    protected static final TransferEndToEndParticipant CONSUMER = TransferEndToEndParticipant.Builder.newInstance()
            .name("consumer")
            .id("urn:connector:consumer")
            .build();
    protected static final TransferEndToEndParticipant PROVIDER = TransferEndToEndParticipant.Builder.newInstance()
            .name("provider")
            .id("urn:connector:provider")
            .build();

    protected static String privateKey = getResourceFileContentAsString("certs/key.pem");
    protected static String publicKey = getResourceFileContentAsString("certs/cert.pem");

    protected static String noConstraintPolicyId;

    @BeforeAll
    static void createNoConstraintPolicy() {
        noConstraintPolicyId = PROVIDER.createPolicyDefinition(noConstraintPolicy());
    }

    @BeforeEach
    void storeKeys() {
        getDataplaneVault().storeSecret("private-key", privateKey);
        getDataplaneVault().storeSecret("public-key", publicKey);
    }

    protected abstract Vault getDataplaneVault();

    protected final Duration timeout = Duration.ofSeconds(60);

    protected void createResourcesOnProvider(String assetId, JsonObject contractPolicy, Map<String, Object> dataAddressProperties) {
        PROVIDER.createAsset(assetId, Map.of("description", "description"), dataAddressProperties);
        var contractPolicyId = PROVIDER.createPolicyDefinition(contractPolicy);
        PROVIDER.createContractDefinition(assetId, UUID.randomUUID().toString(), noConstraintPolicyId, contractPolicyId);
    }

    protected void createResourcesOnProvider(String assetId, Map<String, Object> dataAddressProperties) {
        PROVIDER.createAsset(assetId, Map.of("description", "description"), dataAddressProperties);
        PROVIDER.createContractDefinition(assetId, UUID.randomUUID().toString(), noConstraintPolicyId, noConstraintPolicyId);
    }

    protected void awaitTransferToBeInState(String transferProcessId, TransferProcessStates state) {
        await().atMost(timeout).until(
                () -> CONSUMER.getTransferProcessState(transferProcessId),
                it -> Objects.equals(it, state.name())
        );
    }

}
