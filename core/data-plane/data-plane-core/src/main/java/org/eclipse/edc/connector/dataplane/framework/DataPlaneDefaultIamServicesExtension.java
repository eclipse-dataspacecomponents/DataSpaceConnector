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

package org.eclipse.edc.connector.dataplane.framework;

import org.eclipse.edc.connector.dataplane.framework.iam.DefaultDataPlaneAccessTokenServiceImpl;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessControlService;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessTokenService;
import org.eclipse.edc.connector.dataplane.spi.store.AccessTokenDataStore;
import org.eclipse.edc.keys.spi.LocalPublicKeyService;
import org.eclipse.edc.keys.spi.PrivateKeyResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.token.JwtGenerationService;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.jetbrains.annotations.NotNull;

import java.security.PrivateKey;
import java.util.function.Supplier;


@Extension(value = DataPlaneDefaultIamServicesExtension.NAME)
public class DataPlaneDefaultIamServicesExtension implements ServiceExtension {

    public static final String NAME = "Data Plane Framework Default IAM Services";

    @Setting(value = "Alias of private key used for signing tokens, retrieved from private key resolver")
    public static final String TOKEN_SIGNER_PRIVATE_KEY_ALIAS = "edc.transfer.proxy.token.signer.privatekey.alias";
    @Setting(value = "Alias of public key used for verifying the tokens, retrieved from the vault")
    public static final String TOKEN_VERIFIER_PUBLIC_KEY_ALIAS = "edc.transfer.proxy.token.verifier.publickey.alias";

    @Inject
    private AccessTokenDataStore accessTokenDataStore;
    @Inject
    private TokenValidationService tokenValidationService;
    @Inject
    private PrivateKeyResolver privateKeyResolver;
    @Inject
    private LocalPublicKeyService localPublicKeyService;

    @Override
    public String name() {
        return NAME;
    }

    @Provider(isDefault = true)
    public DataPlaneAccessControlService defaultAccessControlService(ServiceExtensionContext context) {
        context.getMonitor().debug("DataPlane Access Control: default implementation is used, will always return Result.success()");
        return (claimToken, address, requestData, additionalData) -> Result.success();
    }

    @Provider(isDefault = true)
    public DataPlaneAccessTokenService defaultAccessTokenService(ServiceExtensionContext context) {
        var tokenVerifierPublicKeyAlias = context.getConfig().getString(TOKEN_VERIFIER_PUBLIC_KEY_ALIAS);
        var tokenSignerPrivateKeyAlias = context.getConfig().getString(TOKEN_SIGNER_PRIVATE_KEY_ALIAS);
        var monitor = context.getMonitor().withPrefix("DataPlane IAM");
        return new DefaultDataPlaneAccessTokenServiceImpl(new JwtGenerationService(),
                accessTokenDataStore, monitor, getPrivateKeySupplier(tokenSignerPrivateKeyAlias, monitor),
                publicKeyIdSupplier(tokenVerifierPublicKeyAlias), tokenValidationService, localPublicKeyService);
    }

    private Supplier<String> publicKeyIdSupplier(String tokenVerifierPublicKeyAlias) {
        return () -> tokenVerifierPublicKeyAlias;
    }

    @NotNull
    private Supplier<PrivateKey> getPrivateKeySupplier(String tokenSignerPrivateKeyAlias, Monitor monitor) {
        return () -> privateKeyResolver.resolvePrivateKey(tokenSignerPrivateKeyAlias)
                .orElse(f -> {
                    monitor.warning("Cannot resolve private key: " + f.getFailureDetail());
                    return null;
                });
    }
}
