package com.smartpark.server.controller;

import com.jayway.jsonpath.DocumentContext;
import com.smartpark.server.support.AbstractApiIntegrationTest;
import com.smartpark.server.support.WsTestClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityApiTest extends AbstractApiIntegrationTest {

    @Test
    void filtersByRoleAndAction() {
        enter("AAA111", "CAR", "A-01").expect(201);
        leave("AAA111").expect(200);

        List<String> entryOps = get("/api/activity?role=ENTRY_OPERATOR").expect(200).json("$[*].username");
        assertThat(entryOps).isNotEmpty().containsOnly("entry1");

        List<String> exitActions = get("/api/activity?role=EXIT_OPERATOR").json("$[*].action");
        assertThat(exitActions).contains("VEHICLE_EXIT").doesNotContain("VEHICLE_ENTRY");

        List<String> entries = get("/api/activity?action=VEHICLE_ENTRY").json("$[*].description");
        assertThat(entries).singleElement().asString().contains("AAA111");

        get("/api/activity?role=PILOT").expectProblem(400, "INVALID_PARAMETER");
    }

    @Test
    void newActivityIsPushedLiveToAdminTerminalsOnly() throws Exception {
        try (WsTestClient admin = WsTestClient.connect(port, ADMIN, "ADMIN-PC", null);
             WsTestClient entry = WsTestClient.connect(port, ENTRY, "ENTRY-GATE-1", null)) {
            admin.await("CONNECTION_ACK");
            entry.await("CONNECTION_ACK");

            enter("LIVE123", "CAR", "A-02").expect(201);

            DocumentContext pushed = admin.await("ACTIVITY_RECORDED",
                    d -> "VEHICLE_ENTRY".equals(d.read("$.payload.action")), Duration.ofSeconds(10));
            assertThat(pushed.<String>read("$.payload.username")).isEqualTo("entry1");
            assertThat(pushed.<Integer>read("$.sequence")).isZero(); // not part of the sequenced stream

            assertThat(entry.receives("ACTIVITY_RECORDED", d -> true, Duration.ofSeconds(1))).isFalse();
        }
    }
}
