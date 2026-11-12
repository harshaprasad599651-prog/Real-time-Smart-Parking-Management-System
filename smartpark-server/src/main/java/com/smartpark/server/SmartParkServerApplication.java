package com.smartpark.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the SmartPark central server.
 * <p>
 * Runs on the Admin computer and exposes the REST API and the WebSocket
 * endpoint used by the Entry, Exit and Admin JavaFX clients over the LAN.
 */
@SpringBootApplication
public class SmartParkServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkServerApplication.class, args);
    }
}
