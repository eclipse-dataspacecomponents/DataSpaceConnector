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

package org.eclipse.edc.connector.api.sts.client.configuration;

import org.eclipse.edc.iam.identitytrust.sts.model.StsClient;
import org.eclipse.edc.iam.identitytrust.sts.store.StsClientStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;

import static java.lang.String.format;

@Extension(StsClientConfigurationExtension.NAME)
public class StsClientConfigurationExtension implements ServiceExtension {

    public static final String CONFIG_PREFIX = "edc.iam.sts.clients";
    public static final String CLIENT_ID = "client_id";
    public static final String ID = "id";
    public static final String CLIENT_NAME = "name";
    public static final String CLIENT_SECRET_ALIAS = "secret.alias";
    public static final String CLIENT_PRIVATE_KEY_ALIAS = "private-key.alias";

    public static final String NAME = "STS Client Configuration extension";

    @Inject
    private Monitor monitor;

    @Inject
    private StsClientStore clientStore;


    @Override
    public void initialize(ServiceExtensionContext context) {

        var config = context.getConfig(CONFIG_PREFIX);

        config.partition().forEach(this::configureClient);
    }

    @Override
    public String name() {
        return NAME;
    }

    private void configureClient(Config config) {
        var id = config.getString(ID);
        var name = config.getString(CLIENT_NAME);
        var clientId = config.getString(CLIENT_ID);
        var clientSecretAlias = config.getString(CLIENT_SECRET_ALIAS);
        var clientPrivateKeyAlias = config.getString(CLIENT_PRIVATE_KEY_ALIAS);

        var client = StsClient.Builder.newInstance()
                .id(id)
                .clientId(clientId)
                .secretAlias(clientSecretAlias)
                .privateKeyAlias(clientPrivateKeyAlias)
                .name(name)
                .build();

        monitor.debug(format("Configuring STS client with id:%s and name:%s", client.getId(), client.getName()));

        clientStore.create(client);
    }
}