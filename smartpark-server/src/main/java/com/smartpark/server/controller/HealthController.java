package com.smartpark.server.controller;

import com.smartpark.server.dto.HealthResponse;
import com.smartpark.server.security.PublicEndpoint;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * Endpoint the desktop clients (and {@code Test-Connection.cmd}) call to verify they can reach
 * the server across the LAN. Returns the server time, the single clock source for the system.
 * <p>
 * It also pings MongoDB: {@code 200 {"status":"UP","database":"UP"}} when everything works,
 * {@code 503 {"status":"DEGRADED","database":"DOWN"}} when the server runs but the database is
 * unreachable, so operators can tell "server off" from "database off".
 */
@RestController
@RequestMapping("/api/health")
@PublicEndpoint
public class HealthController {

    private final String applicationName;
    private final BooleanSupplier databaseUp;

    @Autowired
    public HealthController(@Value("${spring.application.name}") String applicationName, MongoTemplate mongoTemplate) {
        this(applicationName, () -> ping(mongoTemplate));
    }

    HealthController(String applicationName, BooleanSupplier databaseUp) {
        this.applicationName = applicationName;
        this.databaseUp = databaseUp;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        boolean db = databaseUp.getAsBoolean();
        HealthResponse body = new HealthResponse(db ? "UP" : "DEGRADED", applicationName, Instant.now(), db ? "UP" : "DOWN");
        return ResponseEntity.status(db ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private static boolean ping(MongoTemplate mongoTemplate) {
        try {
            Document reply = mongoTemplate.executeCommand(new Document("ping", 1));
            return reply.get("ok") instanceof Number n && n.doubleValue() == 1.0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
