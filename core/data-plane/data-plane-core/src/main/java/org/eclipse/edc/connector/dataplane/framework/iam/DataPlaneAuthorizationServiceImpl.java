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

package org.eclipse.edc.connector.dataplane.framework.iam;

import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessControlService;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessTokenService;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.result.Result.success;

public class DataPlaneAuthorizationServiceImpl implements DataPlaneAuthorizationService {
    public static final String PROPERTY_AGREEMENT_ID = "agreement_id";
    public static final String PROPERTY_ASSET_ID = "asset_id";
    public static final String PROPERTY_PROCESS_ID = "process_id";
    public static final String PROPERTY_FLOW_TYPE = "flow_type";
    private static final String PROPERTY_PARTICIPANT_ID = "participant_id";
    private final DataPlaneAccessTokenService accessTokenService;
    private final PublicEndpointGeneratorService endpointGenerator;
    private final DataPlaneAccessControlService accessControlService;
    private final String ownParticipantId;
    private final Clock clock;

    public DataPlaneAuthorizationServiceImpl(DataPlaneAccessTokenService accessTokenService,
                                             PublicEndpointGeneratorService endpointGenerator,
                                             DataPlaneAccessControlService accessControlService,
                                             String ownParticipantId,
                                             Clock clock) {
        this.accessTokenService = accessTokenService;
        this.endpointGenerator = endpointGenerator;
        this.accessControlService = accessControlService;
        this.ownParticipantId = ownParticipantId;
        this.clock = clock;
    }

    @Override
    public Result<DataAddress> createEndpointDataReference(DataFlowStartMessage message) {
        return endpointGenerator.generateFor(message.getTransferType().destinationType(), message.getSourceDataAddress())
                .compose(endpoint -> {
                            var additionalProperties = new HashMap<String, Object>(message.getProperties());
                            additionalProperties.put(PROPERTY_AGREEMENT_ID, message.getAgreementId());
                            additionalProperties.put(PROPERTY_ASSET_ID, message.getAssetId());
                            additionalProperties.put(PROPERTY_PROCESS_ID, message.getProcessId());
                            additionalProperties.put(PROPERTY_FLOW_TYPE, message.getFlowType().toString());
                            additionalProperties.put(PROPERTY_PARTICIPANT_ID, message.getParticipantId());

                            return accessTokenService.obtainToken(createTokenParams(message), message.getSourceDataAddress(), additionalProperties)
                                    .compose(tokenRepresentation -> createDataAddress(tokenRepresentation, endpoint));
                        }
                );
    }

    @Override
    public Result<DataAddress> authorize(String token, Map<String, Object> requestData) {
        return accessTokenService.resolve(token)
                .compose(atd -> accessControlService.checkAccess(atd.claimToken(), atd.dataAddress(), requestData, atd.additionalProperties())
                    .map(u -> atd.dataAddress())
                );
    }

    @Override
    public Result<Void> revokeEndpointDataReference(String transferProcessId, String reason) {
        return accessTokenService.revoke(transferProcessId, reason);
    }

    private Result<DataAddress> createDataAddress(TokenRepresentation tokenRepresentation, Endpoint publicEndpoint) {
        var address = DataAddress.Builder.newInstance()
                .type(publicEndpoint.endpointType())
                .property(EDC_NAMESPACE + "endpoint", publicEndpoint.endpoint())
                .property(EDC_NAMESPACE + "endpointType", publicEndpoint.endpointType()) //this is duplicated in the type() field, but will make serialization easier
                .properties(tokenRepresentation.getAdditional()) // would contain the "authType = bearer" entry
                .property(EDC_NAMESPACE + "authorization", tokenRepresentation.getToken())
                .build();

        return success(address);
    }

    private TokenParameters createTokenParams(DataFlowStartMessage message) {
        return TokenParameters.Builder.newInstance()
                .claims(JwtRegisteredClaimNames.JWT_ID, UUID.randomUUID().toString())
                .claims(JwtRegisteredClaimNames.AUDIENCE, message.getParticipantId())
                .claims(JwtRegisteredClaimNames.ISSUER, ownParticipantId)
                .claims(JwtRegisteredClaimNames.SUBJECT, ownParticipantId)
                .claims(JwtRegisteredClaimNames.ISSUED_AT, clock.instant().toEpochMilli()) // todo: milli or second?
                .build();
    }

}
