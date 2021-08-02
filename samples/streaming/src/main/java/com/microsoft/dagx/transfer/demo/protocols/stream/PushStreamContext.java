package com.microsoft.dagx.transfer.demo.protocols.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.dagx.spi.DagxException;
import com.microsoft.dagx.spi.monitor.Monitor;
import com.microsoft.dagx.spi.security.Vault;
import com.microsoft.dagx.transfer.demo.protocols.common.ProtocolsSecretToken;
import com.microsoft.dagx.transfer.demo.protocols.http.HttpStreamSession;
import com.microsoft.dagx.transfer.demo.protocols.spi.stream.StreamContext;
import com.microsoft.dagx.transfer.demo.protocols.ws.WsPushStreamSession;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URI;

import static java.lang.String.format;

/**
 * Implements a push stream context that can create WebSocket-based and HTTP-based sessions.
 */
public class PushStreamContext implements StreamContext {
    private Vault vault;
    private OkHttpClient httpClient;
    private ObjectMapper objectMapper;
    private Monitor monitor;

    public PushStreamContext(Vault vault, OkHttpClient httpClient, ObjectMapper objectMapper, Monitor monitor) {
        this.vault = vault;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.monitor = monitor;
    }

    @Override
    public StreamSession createSession(String uri, String topicName, String secretName) {
        var accessToken = vault.resolveSecret(secretName);
        var destinationToken = readAccessToken(accessToken, secretName);
        var endpointUri = URI.create(uri);

        if ("ws".equalsIgnoreCase(endpointUri.getScheme())) {
            var session = new WsPushStreamSession(endpointUri, topicName, destinationToken, objectMapper, monitor);
            session.connect();
            return session;
        } else if ("https".equalsIgnoreCase(endpointUri.getScheme()) || "http".equalsIgnoreCase(endpointUri.getScheme())) {
            try {
                var endpointUrl = endpointUri.resolve(topicName).toURL();
                return new HttpStreamSession(endpointUrl, destinationToken, httpClient);
            } catch (MalformedURLException e) {
                throw new DagxException(e);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported scheme: " + endpointUri.getScheme());
        }
    }

    private String readAccessToken(@Nullable String accessToken, String destinationSecretName) {
        if (accessToken == null) {
            throw new DagxException("Access token not found in vault: " + destinationSecretName);
        }
        try {
            return objectMapper.readValue(accessToken, ProtocolsSecretToken.class).getToken();
        } catch (Exception e) {
            // NB: do not log the exception to avoid leaking credential information
            throw new DagxException(format("Error deserializing access token: %s [%s]", destinationSecretName, e.getClass().getName()));
        }
    }

}
