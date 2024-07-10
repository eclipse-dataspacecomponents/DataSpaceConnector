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

import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.test.e2e.participant.DataPlaneParticipant;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractDataPlaneTest {

    protected static final DataPlaneParticipant DATAPLANE = DataPlaneParticipant.Builder.newInstance()
            .name("provider")
            .id("urn:connector:provider")
            .build();

    @RegisterExtension
    protected static RuntimeExtension runtime =
            new RuntimePerClassExtension(new EmbeddedRuntime(
                    "data-plane",
                    DATAPLANE.dataPlaneConfiguration(),
                    ":system-tests:e2e-dataplane-tests:runtimes:data-plane"
            ));

    protected void seedVault() {
        var vault = runtime.getService(Vault.class);

        var privateKeyContent = TestUtils.getResourceFileContentAsString("certs/key.pem");
        vault.storeSecret("1", privateKeyContent);

        var publicKey = TestUtils.getResourceFileContentAsString("certs/cert.pem");
        vault.storeSecret("public-key", publicKey);
    }
}
