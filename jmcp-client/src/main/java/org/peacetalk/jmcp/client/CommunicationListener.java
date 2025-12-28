package org.peacetalk.jmcp.client;

import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;

/**
 * Interface for listening to communication events
 */
public interface CommunicationListener {
    void onRequestSent(JsonRpcRequest request);

    void onResponseReceived(JsonRpcResponse response);

    void onError(String message, Exception exception);

    void onServerStderr(String line);
}
