package com.smartpark.server.support;

import com.mongodb.ConnectionString;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Skips MongoDB integration tests (instead of failing the build) when no MongoDB
 * server is reachable. The address comes from {@code SMARTPARK_TEST_MONGODB_URI},
 * default {@code mongodb://localhost:27017}.
 */
public class MongoAvailableCondition implements ExecutionCondition {

    public static final String DEFAULT_URI = "mongodb://localhost:27017";

    private static Boolean available;

    public static String testUri() {
        String uri = System.getenv("SMARTPARK_TEST_MONGODB_URI");
        return uri == null || uri.isBlank() ? DEFAULT_URI : uri;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String uri = testUri();
        return isAvailable(uri)
                ? ConditionEvaluationResult.enabled("MongoDB reachable at " + uri)
                : ConditionEvaluationResult.disabled("MongoDB not reachable at " + uri
                + " - start MongoDB to run the integration tests");
    }

    private static synchronized boolean isAvailable(String uri) {
        if (available == null) {
            String hostPort = new ConnectionString(uri).getHosts().getFirst();
            String host = hostPort.contains(":") ? hostPort.substring(0, hostPort.lastIndexOf(':')) : hostPort;
            int port = hostPort.contains(":") ? Integer.parseInt(hostPort.substring(hostPort.lastIndexOf(':') + 1)) : 27017;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1500);
                available = true;
            } catch (IOException e) {
                available = false;
            }
        }
        return available;
    }
}
