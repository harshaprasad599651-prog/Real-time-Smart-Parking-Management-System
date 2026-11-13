package com.smartpark.server.controller;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.security.RequireRole;
import com.smartpark.server.websocket.RealtimeHub;
import com.smartpark.server.websocket.RealtimePayloads.RealtimeStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime")
@Tag(name = "Monitoring")
@RequireRole(UserRole.ADMIN)
public class RealtimeController {

    private final RealtimeHub hub;

    public RealtimeController(RealtimeHub hub) {
        this.hub = hub;
    }

    @GetMapping("/status")
    @Operation(summary = "Real-time connection status",
            description = "Clients currently connected to ws://<server>:8080/ws (operator, role, terminal, address, "
                    + "connected since, last heartbeat), plus the server instance id and the latest event sequence.")
    public RealtimeStatus status() {
        return hub.status();
    }
}
