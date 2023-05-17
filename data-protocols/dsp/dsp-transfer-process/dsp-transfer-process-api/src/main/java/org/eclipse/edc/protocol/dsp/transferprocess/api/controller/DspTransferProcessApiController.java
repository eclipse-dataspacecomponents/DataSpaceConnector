/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.transferprocess.api.controller;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.spi.transferprocess.TransferProcessProtocolService;
import org.eclipse.edc.connector.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferCompletionMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferRemoteMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferTerminationMessage;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.protocol.dsp.DspError;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.exception.AuthenticationFailedException;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static java.lang.String.format;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.jsonld.spi.TypeUtil.isOfExpectedType;
import static org.eclipse.edc.protocol.dsp.DspErrorDetails.BAD_REQUEST;
import static org.eclipse.edc.protocol.dsp.DspErrorDetails.NOT_IMPLEMENTED;
import static org.eclipse.edc.protocol.dsp.DspErrorDetails.UNAUTHORIZED;
import static org.eclipse.edc.protocol.dsp.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.BASE_PATH;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_COMPLETION;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_INITIAL_REQUEST;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_START;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_SUSPENSION;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_TERMINATION;
import static org.eclipse.edc.protocol.dsp.transferprocess.transformer.DspTransferProcessPropertyAndTypeNames.DSPACE_TRANSFER_COMPLETION_TYPE;
import static org.eclipse.edc.protocol.dsp.transferprocess.transformer.DspTransferProcessPropertyAndTypeNames.DSPACE_TRANSFER_PROCESS_REQUEST_TYPE;
import static org.eclipse.edc.protocol.dsp.transferprocess.transformer.DspTransferProcessPropertyAndTypeNames.DSPACE_TRANSFER_START_TYPE;
import static org.eclipse.edc.protocol.dsp.transferprocess.transformer.DspTransferProcessPropertyAndTypeNames.DSPACE_TRANSFER_TERMINATION_TYPE;
import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

/**
 * Provides the endpoints for receiving messages regarding transfers, like initiating, completing
 * and terminating a transfer process.
 */
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
@Path(BASE_PATH)
public class DspTransferProcessApiController {

    private static final String DSPACE_TRANSFER_ERROR = DSPACE_SCHEMA + "TransferError"; // TODO move to :dsp-core https://github.com/eclipse-edc/Connector/issues/3014

    private final Monitor monitor;
    private final TypeTransformerRegistry registry;
    private final TransferProcessProtocolService protocolService;
    private final IdentityService identityService;
    private final String dspCallbackAddress;
    private final JsonLd jsonLdService;

    public DspTransferProcessApiController(Monitor monitor,
                                           TypeTransformerRegistry registry,
                                           TransferProcessProtocolService protocolService,
                                           IdentityService identityService,
                                           String dspCallbackAddress,
                                           JsonLd jsonLdService) {
        this.monitor = monitor;
        this.protocolService = protocolService;
        this.registry = registry;
        this.identityService = identityService;
        this.dspCallbackAddress = dspCallbackAddress;
        this.jsonLdService = jsonLdService;
    }

    /**
     * Retrieves an existing transfer process. This functionality is not yet supported.
     *
     * @param id the ID of the process
     * @return the requested transfer process or an error.
     */
    @GET
    @Path("/{id}")
    public Response getTransferProcess(@PathParam("id") String id) {
        return errorResponse(Optional.of(id), Response.Status.NOT_IMPLEMENTED, NOT_IMPLEMENTED);
    }

    /**
     * Initiates a new transfer process that has been requested by the counter-party.
     *
     * @param jsonObject the {@link TransferRequestMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return the created transfer process or an error.
     */
    @POST
    @Path(TRANSFER_INITIAL_REQUEST)
    public Response initiateTransferProcess(JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        TransferProcess transferProcess;
        try {
            transferProcess = handleMessage(MessageSpec.Builder.newInstance(TransferRequestMessage.class)
                    .expectedMessageType(DSPACE_TRANSFER_PROCESS_REQUEST_TYPE)
                    .message(jsonObject)
                    .token(token)
                    .serviceCall(protocolService::notifyRequested)
                    .build());
        } catch (AuthenticationFailedException exception) {
            return errorResponse(Optional.empty(), Response.Status.UNAUTHORIZED, UNAUTHORIZED);
        } catch (InvalidRequestException exception) {
            monitor.debug(String.format(BAD_REQUEST + ": %s", exception.getMessages()));
            return errorResponse(Optional.empty(), Response.Status.BAD_REQUEST, BAD_REQUEST);
        } catch (Exception exception) {
            monitor.warning(String.format("Error processing %s. %s", DSPACE_TRANSFER_PROCESS_REQUEST_TYPE, exception));
            throw exception;
        }

        var result = registry.transform(transferProcess, JsonObject.class);
        if (result.failed()) {
            var errorCode = UUID.randomUUID();
            monitor.warning(String.format("Error transforming transfer process, error id %s: %s", errorCode, result.getFailureDetail()));
            return errorResponse(Optional.of(transferProcess.getCorrelationId()), Response.Status.INTERNAL_SERVER_ERROR, String.format("Error code %s", errorCode));
        }

        return Response.status(Response.Status.OK).type(MediaType.APPLICATION_JSON).entity(result.getContent()).build();
    }

    /**
     * Notifies the connector that a transfer process has been started by the counter-part.
     *
     * @param id         the ID of the process
     * @param jsonObject the {@link TransferStartMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return empty response or error.
     */
    @POST
    @Path("{id}" + TRANSFER_START)
    public Response transferProcessStart(@PathParam("id") String id, JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        try {
            handleMessage(MessageSpec.Builder.newInstance(TransferStartMessage.class)
                    .processId(id)
                    .expectedMessageType(DSPACE_TRANSFER_START_TYPE)
                    .message(jsonObject)
                    .token(token)
                    .serviceCall(protocolService::notifyStarted)
                    .build());
        } catch (AuthenticationFailedException exception) {
            monitor.debug(String.format(UNAUTHORIZED + "Requested process id %s: %s", id, exception.getMessages()));
            return errorResponse(Optional.of(id), Response.Status.UNAUTHORIZED, UNAUTHORIZED);
        } catch (InvalidRequestException exception) {
            monitor.debug(String.format(BAD_REQUEST + "Requested process id %s: %s", id, exception.getMessages()));
            return errorResponse(Optional.of(id), Response.Status.BAD_REQUEST, BAD_REQUEST);
        } catch (Exception exception) {
            monitor.warning(String.format("Error processing %s. %s", DSPACE_TRANSFER_START_TYPE, exception));
            throw exception;
        }

        return Response.status(Response.Status.OK).build();
    }

    /**
     * Notifies the connector that a transfer process has been completed by the counter-part.
     *
     * @param id         the ID of the process
     * @param jsonObject the {@link TransferCompletionMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return empty response or error.
     */
    @POST
    @Path("{id}" + TRANSFER_COMPLETION)
    public Response transferProcessCompletion(@PathParam("id") String id, JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        try {
            handleMessage(MessageSpec.Builder.newInstance(TransferCompletionMessage.class)
                    .processId(id)
                    .expectedMessageType(DSPACE_TRANSFER_COMPLETION_TYPE)
                    .message(jsonObject)
                    .token(token)
                    .serviceCall(protocolService::notifyCompleted)
                    .build());
        } catch (AuthenticationFailedException exception) {
            monitor.debug(String.format(UNAUTHORIZED + "Requested process id %s: %s", id, exception.getMessages()));
            return errorResponse(Optional.of(id), Response.Status.UNAUTHORIZED, UNAUTHORIZED);
        } catch (InvalidRequestException exception) {
            monitor.debug(String.format(BAD_REQUEST + "Requested process id %s: %s", id, exception.getMessages()));
            return errorResponse(Optional.of(id), Response.Status.BAD_REQUEST, BAD_REQUEST);
        } catch (Exception exception) {
            monitor.warning(String.format("Error processing %s. %s", DSPACE_TRANSFER_COMPLETION_TYPE, exception));
            throw exception;
        }

        return Response.status(Response.Status.OK).build();
    }

    /**
     * Notifies the connector that a transfer process has been terminated by the counter-part.
     *
     * @param id         the ID of the process
     * @param jsonObject the {@link TransferTerminationMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return empty response or error.
     */
    @POST
    @Path("{id}" + TRANSFER_TERMINATION)
    public Response transferProcessTermination(@PathParam("id") String id, JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        try {
            handleMessage(MessageSpec.Builder.newInstance(TransferTerminationMessage.class)
                    .processId(id)
                    .expectedMessageType(DSPACE_TRANSFER_TERMINATION_TYPE)
                    .message(jsonObject)
                    .token(token)
                    .serviceCall(protocolService::notifyTerminated)
                    .build());
        } catch (AuthenticationFailedException exception) {
            monitor.debug(String.format(UNAUTHORIZED + "Requested process id %s: %s", id, exception.getMessages()));
            return errorResponse(Optional.of(id), Response.Status.UNAUTHORIZED, UNAUTHORIZED);
        } catch (InvalidRequestException exception) {
            monitor.debug(String.format(BAD_REQUEST + "Requested process id %s: %s", id, exception.getMessages()));
            return errorResponse(Optional.of(id), Response.Status.BAD_REQUEST, BAD_REQUEST);
        } catch (Exception exception) {
            monitor.warning(String.format("Error processing %s. %s", DSPACE_TRANSFER_TERMINATION_TYPE, exception));
            throw exception;
        }

        return Response.status(Response.Status.OK).build();
    }

    /**
     * Notifies the connector that a transfer process has been suspended by the counter-part.
     * This functionality is not yet supported.
     *
     * @param id the ID of the process
     */
    @POST
    @Path("{id}" + TRANSFER_SUSPENSION)
    public Response transferProcessSuspension(@PathParam("id") String id) {
        return errorResponse(Optional.of(id), Response.Status.NOT_IMPLEMENTED, NOT_IMPLEMENTED);
    }

    /**
     * Handles an incoming message. Validates the identity token. Then verifies that the JSON-LD
     * message has the expected type, before transforming it to a respective message class instance.
     * If the process ID was part of the request's path, verifies that the ID in the message matches
     * the one from the path. Then calls the service method with message and claim token. Will throw
     * an exception if any of the operations fail.
     *
     * @param messageSpec the message spec
     * @return the transfer process returned by the service call
     */
    private <M extends TransferRemoteMessage> TransferProcess handleMessage(MessageSpec<M> messageSpec) {
        monitor.debug(() -> format("DSP: Incoming %s for transfer process%s",
                messageSpec.getMessageClass().getSimpleName(), messageSpec.getProcessId() != null ? ": " + messageSpec.getProcessId() : ""));

        var claimToken = checkAuthToken(messageSpec.getToken());

        var expanded = jsonLdService.expand(messageSpec.getMessage())
                .map(ej -> ej).orElseThrow(f -> new InvalidRequestException(f.getFailureDetail()));

        if (!isOfExpectedType(expanded, messageSpec.getExpectedMessageType())) {
            throw new InvalidRequestException(format("Request body was not of expected type: %s", messageSpec.getExpectedMessageType()));
        }
        var message = registry.transform(expanded, messageSpec.getMessageClass())
                .orElseThrow(failure -> new InvalidRequestException(format("Failed to read request body: %s", failure.getFailureDetail())));

        // set the remote protocol used
        message.setProtocol(DATASPACE_PROTOCOL_HTTP);

        validateProcessId(messageSpec, message);

        return messageSpec.getServiceCall().apply(message, claimToken).orElseThrow(exceptionMapper(TransferProcess.class));
    }

    private ClaimToken checkAuthToken(String token) {
        var tokenRepresentation = TokenRepresentation.Builder.newInstance().token(token).build();

        var result = identityService.verifyJwtToken(tokenRepresentation, dspCallbackAddress);
        if (result.failed()) {
            throw new AuthenticationFailedException();
        }

        return result.getContent();
    }

    /**
     * Ensures that a process id specified in an endpoint url matches the process id in the incoming DSP Json-Ld message.
     */
    private void validateProcessId(MessageSpec<?> messageSpec, TransferRemoteMessage message) {
        var expected = messageSpec.getProcessId();
        if (expected != null) {
            var actual = message.getProcessId();
            if (!expected.equals(actual)) {
                throw new InvalidRequestException(format("Invalid process ID. Expected: %s, actual: %s", expected, actual));
            }
        }
    }

    private Response errorResponse(Optional<String> processId, Response.Status code, String message) {
        var builder = DspError.Builder.newInstance()
                .type(DSPACE_TRANSFER_ERROR)
                .code(Integer.toString(code.getStatusCode()))
                .messages(List.of(message));

        processId.ifPresent(builder::processId);

        return Response.status(code).type(MediaType.APPLICATION_JSON)
                .entity(builder.build().toJson())
                .build();
    }

}
