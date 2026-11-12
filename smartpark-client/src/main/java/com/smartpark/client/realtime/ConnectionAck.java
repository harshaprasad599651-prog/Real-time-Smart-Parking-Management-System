package com.smartpark.client.realtime;

/**
 * First message after every (re)connection.
 *
 * @param currentSequence latest broadcast sequence on the server at connect time
 * @param replayedEvents  number of missed events that follow this message
 * @param resyncRequired  missed events could not be replayed; reload state through the REST API
 */
public record ConnectionAck(String sessionId, String serverInstanceId, long currentSequence, int replayedEvents,
                            boolean resyncRequired, String resyncReason, int heartbeatSeconds) {
}
