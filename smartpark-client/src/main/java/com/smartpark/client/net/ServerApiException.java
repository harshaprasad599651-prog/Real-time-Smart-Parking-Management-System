package com.smartpark.client.net;

/**
 * Raised when the server answers with an unexpected HTTP status.
 */
public class ServerApiException extends RuntimeException {

    public ServerApiException(String message) {
        super(message);
    }
}
