package com.smartpark.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client settings: where the central server is and which terminal this PC is.
 * <p>
 * Values are resolved in this order (later wins):
 * <ol>
 *     <li>Built-in defaults in {@code /smartpark-client.properties} on the classpath</li>
 *     <li>An optional {@code smartpark-client.properties} file in the working directory</li>
 *     <li>JVM system properties, e.g. {@code -Dsmartpark.server.host=192.168.1.10}</li>
 * </ol>
 */
public record ClientConfig(String serverHost, int serverPort, String terminalName) {

    public static final String FILE_NAME = "smartpark-client.properties";

    private static final String KEY_HOST = "smartpark.server.host";
    private static final String KEY_PORT = "smartpark.server.port";
    private static final String KEY_TERMINAL = "smartpark.terminal.name";

    public static ClientConfig load() {
        Properties props = new Properties();

        try (InputStream in = ClientConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read built-in " + FILE_NAME, e);
        }

        Path external = Path.of(FILE_NAME);
        if (Files.isRegularFile(external)) {
            try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + external.toAbsolutePath(), e);
            }
        }

        for (String key : new String[]{KEY_HOST, KEY_PORT, KEY_TERMINAL}) {
            String override = System.getProperty(key);
            if (override != null && !override.isBlank()) {
                props.setProperty(key, override);
            }
        }

        String host = props.getProperty(KEY_HOST, "localhost").trim();
        String portText = props.getProperty(KEY_PORT, "8080").trim();
        String terminal = props.getProperty(KEY_TERMINAL, "UNNAMED-TERMINAL").trim();

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid " + KEY_PORT + ": " + portText, e);
        }
        return new ClientConfig(host, port, terminal);
    }

    /** Base URL for REST calls, e.g. {@code http://192.168.1.10:8080}. */
    public URI httpBaseUri() {
        return URI.create("http://" + serverHost + ":" + serverPort);
    }

    /** URL of the real-time WebSocket endpoint, e.g. {@code ws://192.168.1.10:8080/ws}. */
    public URI webSocketUri() {
        return URI.create("ws://" + serverHost + ":" + serverPort + "/ws");
    }
}
