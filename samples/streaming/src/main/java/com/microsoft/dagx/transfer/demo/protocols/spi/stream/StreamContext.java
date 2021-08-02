package com.microsoft.dagx.transfer.demo.protocols.spi.stream;

import com.microsoft.dagx.transfer.demo.protocols.stream.StreamSession;

/**
 * A context for creating stream sessions.
 */
public interface StreamContext {

    /**
     * Creates a stream session.
     *
     * @param uri the stream endpoint uri
     * @param topicName the topic name data is to be sent to
     * @param secretName the topic secret to be resolved by the context
     * @return the session
     */
    StreamSession createSession(String uri, String topicName, String secretName);


}
