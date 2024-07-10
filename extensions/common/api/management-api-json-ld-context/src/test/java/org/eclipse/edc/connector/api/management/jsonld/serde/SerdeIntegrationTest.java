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

package org.eclipse.edc.connector.api.management.jsonld.serde;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import org.eclipse.edc.connector.controlplane.api.management.contractnegotiation.model.NegotiationState;
import org.eclipse.edc.connector.controlplane.api.management.transferprocess.model.SuspendTransfer;
import org.eclipse.edc.connector.controlplane.api.management.transferprocess.model.TerminateTransfer;
import org.eclipse.edc.connector.controlplane.api.management.transferprocess.model.TransferState;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.contract.spi.types.command.TerminateNegotiationCommand;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.connector.spi.service.SecretService;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.secret.Secret;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.MANAGEMENT_API_CONTEXT;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.MANAGEMENT_API_SCOPE;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.assetObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.contractDefinitionObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.contractRequestObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.createContractAgreement;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.createContractNegotiation;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.createEdrEntry;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.createTransferProcess;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.inForceDatePermission;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.policyDefinitionObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.querySpecObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.secretObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.suspendTransferObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.terminateNegotiationObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.terminateTransferObject;
import static org.eclipse.edc.connector.api.management.jsonld.serde.TestFunctions.transferRequestObject;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.Mockito.mock;

@ComponentTest
public class SerdeIntegrationTest {

    @RegisterExtension
    private static final RuntimeExtension RUNTIME;

    static {
        RUNTIME = new RuntimePerClassExtension();
        RUNTIME.registerServiceMock(ContractDefinitionService.class, mock());
        RUNTIME.registerServiceMock(ContractNegotiationService.class, mock());
        RUNTIME.registerServiceMock(PolicyDefinitionService.class, mock());
        RUNTIME.registerServiceMock(TransferProcessService.class, mock());
        RUNTIME.registerServiceMock(SecretService.class, mock());
        RUNTIME.registerServiceMock(EndpointDataReferenceStore.class, mock());
        RUNTIME.setConfiguration(Map.of(
                "web.http.port", String.valueOf(getFreePort()),
                "web.http.path", "/api",
                "web.http.management.port", String.valueOf(getFreePort()),
                "web.http.management.path", "/api",
                "edc.jsonld.vocab.disable", "true"
        ));
    }

    @Test
    void ser_ContractAgreement() {
        var agreement = createContractAgreement("test-id");
        var compactResult = serialize(agreement);

        assertThat(compactResult).isNotNull();
        assertThat(compactResult.getString(ID)).isEqualTo(agreement.getId());
        assertThat(compactResult.getString(TYPE)).isEqualTo("ContractAgreement");
        assertThat(compactResult.getString("providerId")).isEqualTo(agreement.getProviderId());
        assertThat(compactResult.getString("consumerId")).isEqualTo(agreement.getConsumerId());
        assertThat(compactResult.getString("assetId")).isEqualTo(agreement.getAssetId());
        assertThat(compactResult.getJsonObject("policy")).isNotNull();
        assertThat(compactResult.getJsonNumber("contractSigningDate")).isNotNull();

    }

    @Test
    void ser_ContractNegotiation() {
        var negotiation = createContractNegotiation();
        var compactResult = serialize(negotiation);

        assertThat(compactResult).isNotNull();
        assertThat(compactResult.getString(ID)).isEqualTo("test-id");
        assertThat(compactResult.getString("state")).isEqualTo(REQUESTED.name());
        assertThat(compactResult.getString("counterPartyId")).isEqualTo(negotiation.getCounterPartyId());
        assertThat(compactResult.getString("counterPartyAddress")).isEqualTo(negotiation.getCounterPartyAddress());
        assertThat(compactResult.getString("contractAgreementId")).isEqualTo(negotiation.getContractAgreement().getId());
        assertThat(compactResult.getString("errorDetail")).isEqualTo(negotiation.getErrorDetail());
        assertThat(compactResult.getString("type")).isEqualTo(negotiation.getType().toString());
        assertThat(compactResult.getString("protocol")).isEqualTo(negotiation.getProtocol());
        assertThat(compactResult.getJsonArray("callbackAddresses")).isNotEmpty().first().satisfies(callback -> {
            var firstCallback = negotiation.getCallbackAddresses().get(0);
            var cb = callback.asJsonObject();
            assertThat(cb.getString("uri")).isEqualTo(firstCallback.getUri());
            assertThat(cb.getBoolean("transactional")).isEqualTo(firstCallback.isTransactional());

            var events = cb.getJsonArray("events").stream().map(event -> ((JsonString) event).getString()).collect(Collectors.toSet());
            assertThat(events).containsAll(firstCallback.getEvents());
        });
        assertThat(compactResult.getJsonNumber("createdAt").longValue()).isEqualTo(1234);

    }

    @Test
    void ser_TransferProcess() {
        var transferProcess = createTransferProcess();
        var compactResult = serialize(transferProcess);

        assertThat(compactResult).isNotNull();
        assertThat(compactResult.getString(ID)).isEqualTo(transferProcess.getId());
        assertThat(compactResult.getString(TYPE)).isEqualTo("TransferProcess");
        assertThat(compactResult.getString("correlationId")).isEqualTo(transferProcess.getCorrelationId());
        assertThat(compactResult.getString("state")).isEqualTo(transferProcess.stateAsString());
        assertThat(compactResult.getJsonNumber("stateTimestamp").longValue()).isEqualTo(transferProcess.getStateTimestamp());
        assertThat(compactResult.getString("assetId")).isEqualTo(transferProcess.getAssetId());
        assertThat(compactResult.getString("contractId")).isEqualTo(transferProcess.getContractId());
        assertThat(compactResult.getString("type")).isEqualTo(transferProcess.getType().toString());
        assertThat(compactResult.getJsonObject("dataDestination")).isNotNull();
        assertThat(compactResult.getJsonArray("callbackAddresses")).hasSize(transferProcess.getCallbackAddresses().size());
        assertThat(compactResult.getString("errorDetail")).isEqualTo(transferProcess.getErrorDetail());
    }

    @Test
    void ser_EndpointDataReferenceEntry() {
        var transferProcess = createEdrEntry();
        var compactResult = serialize(transferProcess);

        assertThat(compactResult).isNotNull();
        assertThat(compactResult.getString(ID)).isEqualTo(transferProcess.getId());
        assertThat(compactResult.getString(TYPE)).isEqualTo("EndpointDataReferenceEntry");
        assertThat(compactResult.getString("transferProcessId")).isEqualTo(transferProcess.getTransferProcessId());
        assertThat(compactResult.getString("contractNegotiationId")).isEqualTo(transferProcess.getContractNegotiationId());
        assertThat(compactResult.getString("assetId")).isEqualTo(transferProcess.getAssetId());
        assertThat(compactResult.getString("providerId")).isEqualTo(transferProcess.getProviderId());
        assertThat(compactResult.getString("agreementId")).isEqualTo(transferProcess.getAgreementId());
    }

    @Test
    void ser_NegotiationState() {
        var state = new NegotiationState(TransferProcessStates.REQUESTED.name());
        var compactResult = serialize(state);

        assertThat(compactResult).isNotNull();
        assertThat(compactResult.getString(TYPE)).isEqualTo("NegotiationState");
        assertThat(compactResult.getString("state")).isEqualTo(TransferProcessStates.REQUESTED.name());

    }

    @Test
    void ser_TransferState() {
        var state = new TransferState(REQUESTED.name());
        var compactResult = serialize(state);

        assertThat(compactResult).isNotNull();
        assertThat(compactResult.getString(TYPE)).isEqualTo("TransferState");
        assertThat(compactResult.getString("state")).isEqualTo(REQUESTED.name());

    }

    @Test
    void de_ContractRequest() {
        var inputObject = contractRequestObject();
        var request = deserialize(inputObject, ContractRequest.class);

        assertThat(request).isNotNull();
        assertThat(request.getProviderId()).isEqualTo(inputObject.getJsonObject("policy").getString("assigner"));
        assertThat(request.getCallbackAddresses()).isNotEmpty();
        assertThat(request.getProtocol()).isEqualTo("test-protocol");
        assertThat(request.getCounterPartyAddress()).isEqualTo("test-address");
        assertThat(request.getContractOffer().getPolicy()).isNotNull();

    }

    @Test
    void de_TransferRequest() {
        var inputObject = transferRequestObject();
        var transferRequest = deserialize(inputObject, TransferRequest.class);

        assertThat(transferRequest).isNotNull();
        assertThat(transferRequest.getId()).isEqualTo(inputObject.getString(ID));
        assertThat(transferRequest.getCounterPartyAddress()).isEqualTo(inputObject.getString("counterPartyAddress"));
        assertThat(transferRequest.getContractId()).isEqualTo(inputObject.getString("contractId"));
        assertThat(transferRequest.getDataDestination()).extracting(DataAddress::getType).isEqualTo(inputObject.getJsonObject("dataDestination").getString("type"));
        assertThat(transferRequest.getPrivateProperties()).containsEntry(EDC_NAMESPACE + "fooPrivate", "bar");
        assertThat(transferRequest.getProtocol()).isEqualTo(inputObject.getString("protocol"));
        assertThat(transferRequest.getCallbackAddresses()).hasSize(inputObject.getJsonArray("callbackAddresses").size());
    }


    @Test
    void de_TerminateNegotiation() {
        var inputObject = terminateNegotiationObject();
        var terminateNegotiation = deserialize(inputObject, TerminateNegotiationCommand.class);

        assertThat(terminateNegotiation).isNotNull();
        assertThat(terminateNegotiation.getReason()).isEqualTo(inputObject.getString("reason"));
    }

    @Test
    void de_TerminateTransfer() {
        var inputObject = terminateTransferObject();
        var terminateTransfer = deserialize(inputObject, TerminateTransfer.class);

        assertThat(terminateTransfer).isNotNull();
        assertThat(terminateTransfer.reason()).isEqualTo(inputObject.getString("reason"));

    }

    @Test
    void de_SuspendTransfer() {
        var inputObject = suspendTransferObject();
        var terminateTransfer = deserialize(inputObject, SuspendTransfer.class);

        assertThat(terminateTransfer).isNotNull();
        assertThat(terminateTransfer.reason()).isEqualTo(inputObject.getString("reason"));

    }

    @Test
    void de_PolicyDefinition_withInForceDate() {

        var andConstraint = AndConstraint.Builder.newInstance()
                .constraint(AtomicConstraint.Builder.newInstance()
                        .leftExpression(new LiteralExpression(EDC_NAMESPACE + "inForceDate"))
                        .operator(Operator.GEQ)
                        .rightExpression(new LiteralExpression("contractAgreement+0s"))
                        .build())
                .constraint(AtomicConstraint.Builder.newInstance()
                        .leftExpression(new LiteralExpression(EDC_NAMESPACE + "inForceDate"))
                        .operator(Operator.LEQ)
                        .rightExpression(new LiteralExpression("contractAgreement+10s"))
                        .build())
                .build();

        var inputObject = policyDefinitionObject(inForceDatePermission("gteq", "contractAgreement+0s", "lteq", "contractAgreement+10s"));

        var terminateTransfer = deserialize(inputObject, PolicyDefinition.class);

        assertThat(terminateTransfer).isNotNull();
        assertThat(terminateTransfer.getPolicy().getPermissions().get(0).getConstraints())
                .usingRecursiveFieldByFieldElementComparator().containsOnly(andConstraint);

    }

    /**
     * Tests for entities that supports transformation from/to JsonObject
     */
    @ParameterizedTest(name = "{1}")
    @ArgumentsSource(JsonInputProvider.class)
    void serde(JsonObject inputObject, Class<?> klass, Function<JsonObject, JsonObject> mapper) {
        var typeTransformerRegistry = RUNTIME.getService(TypeTransformerRegistry.class);
        var jsonLd = RUNTIME.getService(JsonLd.class);
        var registry = typeTransformerRegistry.forContext(MANAGEMENT_API_CONTEXT);

        // Expand the input
        var expanded = jsonLd.expand(inputObject).orElseThrow(f -> new AssertionError(f.getFailureDetail()));

        // transform the expanded into the input klass type
        var result = registry.transform(expanded, klass).orElseThrow(failure -> new RuntimeException());
        // transform the klass type instance into JsonObject
        var object = registry.transform(result, JsonObject.class).orElseThrow(failure -> new RuntimeException());

        // Compact the result
        var compactResult = jsonLd.compact(object, MANAGEMENT_API_SCOPE);

        // checks that the compacted == inputObject
        assertThat(compactResult).isSucceeded().satisfies(compacted -> {
            var mapped = Optional.ofNullable(mapper).map(m -> m.apply(compacted)).orElse(compacted);
            assertThat(mapped).isEqualTo(inputObject);
        });
    }

    private JsonObject serialize(Object object) {
        var typeTransformerRegistry = RUNTIME.getService(TypeTransformerRegistry.class);
        var registry = typeTransformerRegistry.forContext(MANAGEMENT_API_CONTEXT);
        var jsonLd = RUNTIME.getService(JsonLd.class);

        var result = registry.transform(object, JsonObject.class).orElseThrow(failure -> new RuntimeException());
        return jsonLd.compact(result, MANAGEMENT_API_SCOPE).orElseThrow(failure -> new RuntimeException(failure.getFailureDetail()));
    }

    private <T> T deserialize(JsonObject inputObject, Class<T> klass) {
        var typeTransformerRegistry = RUNTIME.getService(TypeTransformerRegistry.class);
        var registry = typeTransformerRegistry.forContext(MANAGEMENT_API_CONTEXT);
        var jsonLd = RUNTIME.getService(JsonLd.class);

        var expanded = jsonLd.expand(inputObject).orElseThrow(f -> new AssertionError(f.getFailureDetail()));


        // checks that the type is correctly expanded to the EDC namespace
        assertThat(expanded.getJsonArray(TYPE)).first().satisfies(t -> {
            assertThat(((JsonString) t).getString()).startsWith(EDC_NAMESPACE);
        });

        return registry.transform(expanded, klass).orElseThrow(failure -> new RuntimeException());
    }

    private static class JsonInputProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            Function<JsonObject, JsonObject> mapper = this::policyMapper;

            return Stream.of(
                    Arguments.of(assetObject(), Asset.class, null),
                    Arguments.of(contractDefinitionObject(), ContractDefinition.class, null),
                    Arguments.of(secretObject(), Secret.class, null),
                    Arguments.of(querySpecObject(), QuerySpec.class, null),
                    Arguments.of(policyDefinitionObject(), PolicyDefinition.class, mapper)
            );
        }

        private JsonObject policyMapper(JsonObject compacted) {
            var policy = compacted.getJsonObject("policy");
            var newPolicy = Json.createObjectBuilder(policy);
            newPolicy.remove("@id");
            var newDefinition = Json.createObjectBuilder(compacted);
            newDefinition.remove("createdAt");
            newDefinition.add("policy", newPolicy);

            return newDefinition.build();
        }
    }


}
