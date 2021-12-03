/*
 *  Copyright (c) 2021 Microsoft Corporation
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

package org.eclipse.dataspaceconnector.spi.types.domain.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TransferProcessTest {

    @Test
    void verifyDeserialization() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        TransferProcess process = TransferProcess.Builder.newInstance().id(UUID.randomUUID().toString()).build();
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, process);

        TransferProcess deserialized = mapper.readValue(writer.toString(), TransferProcess.class);

        assertEquals(process, deserialized);
    }

    @Test
    void verifyCopy() {
        TransferProcess process = TransferProcess.Builder.newInstance().id(UUID.randomUUID().toString()).type(TransferProcess.Type.PROVIDER).state(TransferProcessStates.COMPLETED.code()).stateCount(1).stateTimestamp(1).build();
        TransferProcess copy = process.copy();

        assertEquals(process.getState(), copy.getState());
        assertEquals(process.getType(), copy.getType());
        assertEquals(process.getStateCount(), copy.getStateCount());
        assertEquals(process.getStateTimestamp(), copy.getStateTimestamp());

        assertEquals(process, copy);
    }

    @Test
    void verifyConsumerTransitions() {
        TransferProcess process = TransferProcess.Builder.newInstance().id(UUID.randomUUID().toString()).type(TransferProcess.Type.CONSUMER).build();

        // test illegal transition
        assertThrows(IllegalStateException.class, () -> process.transitionProvisioning(ResourceManifest.Builder.newInstance().build()));
        process.transitionInitial();

        // test illegal transition
        assertThrows(IllegalStateException.class, process::transitionProvisioned);

        process.transitionProvisioning(ResourceManifest.Builder.newInstance().build());
        process.transitionProvisioned();

        process.transitionRequested();

        // test illegal transition
        assertThrows(IllegalStateException.class, process::transitionEnded);

        process.transitionRequestAck();
        process.transitionInProgress();
        process.transitionCompleted();

        process.transitionDeprovisioning();
        process.transitionDeprovisioned();
        process.transitionEnded();
    }

    @Test
    void verifyProviderTransitions() {
        TransferProcess process = TransferProcess.Builder.newInstance().id(UUID.randomUUID().toString()).type(TransferProcess.Type.PROVIDER).build();

        process.transitionInitial();

        process.transitionProvisioning(ResourceManifest.Builder.newInstance().build());
        process.transitionProvisioned();

        // no request or ack on provider
        process.transitionInProgress();
        process.transitionCompleted();

        process.transitionDeprovisioning();
        process.transitionDeprovisioned();
        process.transitionEnded();
    }

    @Test
    void verifyTransitionRollback() {
        TransferProcess process = TransferProcess.Builder.newInstance().id(UUID.randomUUID().toString()).build();
        process.transitionInitial();
        process.transitionProvisioning(ResourceManifest.Builder.newInstance().build());

        process.rollbackState(TransferProcessStates.INITIAL);

        assertEquals(TransferProcessStates.INITIAL.code(), process.getState());
        assertEquals(1, process.getStateCount());
    }

    @Test
    void verifyProvisioningComplete() {
        TransferProcess.Builder builder = TransferProcess.Builder.newInstance().id(UUID.randomUUID().toString());

        ResourceManifest manifest = ResourceManifest.Builder.newInstance().build();
        manifest.addDefinition(TestResourceDefinition.Builder.newInstance().id("r1").build());

        TransferProcess process = builder.resourceManifest(manifest).build();

        assertFalse(process.provisioningComplete());

        ProvisionedResourceSet resourceSet = ProvisionedResourceSet.Builder.newInstance().build();

        process =  process.toBuilder().provisionedResourceSet(resourceSet).build();

        assertFalse(process.provisioningComplete());

        resourceSet.addResource(TestProvisionedResource.Builder.newInstance().id("p1").resourceDefinitionId("r1").transferProcessId("123").build());

        assertTrue(process.provisioningComplete());

    }
}
