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
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.spi.security.Vault;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

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
    protected final Duration timeout = Duration.ofSeconds(60);

    protected static void seedVault(RuntimeExtension runtime) {
        var vault = runtime.getService(Vault.class);

        var privateKeyContent = getResourceFileContentAsString("certs/key.pem");
        vault.storeSecret("1", privateKeyContent);

        var publicKey = getResourceFileContentAsString("certs/cert.pem");
        vault.storeSecret("public-key", publicKey);

        vault.storeSecret("provision-oauth-secret", "supersecret");
    }

    protected void createResourcesOnProvider(String assetId, JsonObject contractPolicy, Map<String, Object> dataAddressProperties) {
        PROVIDER.createAsset(assetId, Map.of("description", "description"), dataAddressProperties);
        var accessPolicyId = PROVIDER.createPolicyDefinition(noConstraintPolicy());
        var contractPolicyId = PROVIDER.createPolicyDefinition(contractPolicy);
        PROVIDER.createContractDefinition(assetId, UUID.randomUUID().toString(), accessPolicyId, contractPolicyId);
    }

}
