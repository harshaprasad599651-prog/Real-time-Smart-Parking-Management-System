package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringApiTest extends AbstractApiIntegrationTest {

    @Test
    void statisticsReflectCurrentState() {
        enter("CAR001", "CAR", "A-01").expect(201);
        enter("CAR002", "CAR", "C-01").expect(201);
        enter("MC0001", "MOTORCYCLE", "M-01").expect(201);
        leave("CAR002").expect(200);
        post("/api/slots/A-08/disable", ADMIN, null).expect(200);

        Response s = get("/api/statistics").expect(200);

        assertThat((Integer) s.json("$.totalSlots")).isEqualTo(30);
        assertThat((Integer) s.json("$.occupiedSlots")).isEqualTo(2);
        assertThat((Integer) s.json("$.disabledSlots")).isEqualTo(1);
        assertThat((Integer) s.json("$.availableSlots")).isEqualTo(27);
        assertThat((Double) s.json("$.occupancyRatePercent")).isEqualTo(6.9); // 2 / 29
        assertThat((Integer) s.json("$.activeSessions")).isEqualTo(2);
        assertThat((Integer) s.json("$.entriesToday")).isEqualTo(3);
        assertThat((Integer) s.json("$.exitsToday")).isEqualTo(1);
        assertThat((Double) s.json("$.averageStayMinutesToday")).isNotNull();

        assertThat((List<Integer>) s.json("$.categories[?(@.categoryName=='CAR')].parkedVehicles")).containsExactly(1);
        assertThat((List<Integer>) s.json("$.categories[?(@.categoryName=='MOTORCYCLE')].availableSlots")).containsExactly(5);
        assertThat((List<Integer>) s.json("$.floors[*].floor")).containsExactly(0, 1);
        assertThat((List<Integer>) s.json("$.floors[?(@.floor==1)].totalSlots")).containsExactly(8);
    }

    @Test
    void statisticsOfEmptyCarPark() {
        Response s = get("/api/statistics").expect(200);
        assertThat((Double) s.json("$.occupancyRatePercent")).isEqualTo(0.0);
        assertThat((Object) s.json("$.averageStayMinutesToday")).isNull();
    }

    @Test
    void activityLogShowsOperatorActions() {
        enter("CAR001", "CAR", "A-01").expect(201);
        enter("CAR001", "CAR", "A-02").expect(409);
        leave("CAR001").expect(200);

        assertThat((List<String>) get("/api/activity?username=ENTRY1").expect(200).json("$[*].action"))
                .containsSubsequence("ALLOCATION_REJECTED", "VEHICLE_ENTRY");
        assertThat((List<String>) get("/api/activity?limit=1").json("$[*].action")).containsExactly("VEHICLE_EXIT");
        get("/api/activity?limit=0").expectProblem(400, "VALIDATION_FAILED");
    }

    @Test
    void healthAndApiDocumentationAreServed() {
        assertThat((String) get("/api/health").expect(200).json("$.status")).isEqualTo("UP");

        Response docs = get("/v3/api-docs").expect(200);
        assertThat((String) docs.json("$.info.title")).isEqualTo("SmartPark API");
        assertThat(docs.body()).contains("/api/parking/entry", "/api/parking/exit", "/api/slots/available",
                "/api/categories/{categoryName}", "/api/statistics");
        assertThat(get("/swagger-ui.html").expect(200).body()).contains("swagger-ui");

        get("/api/does-not-exist").expectProblem(404, "NOT_FOUND");
    }
}
