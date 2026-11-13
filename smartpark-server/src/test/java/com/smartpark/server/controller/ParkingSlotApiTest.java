package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingSlotApiTest extends AbstractApiIntegrationTest {

    @Test
    void listsAllSlotsInMapOrder() {
        List<String> codes = get("/api/slots").expect(200).json("$[*].slotCode");

        assertThat(codes).hasSize(30);
        assertThat(codes.subList(0, 3)).containsExactly("A-01", "A-02", "A-03");
        assertThat(codes.getLast()).isEqualTo("C-08");
    }

    @Test
    void filtersByCategoryStatusAndFloor() {
        assertThat((List<?>) get("/api/slots?category=MOTORCYCLE").json("$")).hasSize(6);
        assertThat((List<?>) get("/api/slots?floor=1").json("$")).hasSize(8);
        assertThat((List<?>) get("/api/slots?category=electric_car&floor=0").json("$")).hasSize(14);
        assertThat((List<?>) get("/api/slots?status=OCCUPIED").json("$")).isEmpty();

        get("/api/slots?status=FULL").expectProblem(400, "INVALID_PARAMETER");
        get("/api/slots?category=TRUCK").expectProblem(404, "NOT_FOUND");
    }

    @Test
    void availableSlotsFollowOccupancy() {
        assertThat((List<String>) get("/api/slots/available?category=VAN").json("$[*].slotCode"))
                .containsExactly("B-01", "B-02", "B-03", "B-04");
        enter("VAN0001", "VAN", "B-02").expect(201);

        assertThat((List<String>) get("/api/slots/available?category=VAN").json("$[*].slotCode"))
                .containsExactly("B-01", "B-03", "B-04");
        assertThat((List<?>) get("/api/slots/available").json("$")).hasSize(29);
    }

    @Test
    void getsOneSlot() {
        Response r = get("/api/slots/b-05").expect(200);
        assertThat((String) r.json("$.slotType")).isEqualTo("EV_CHARGING");
        assertThat((List<String>) r.json("$.supportedVehicleCategories")).containsExactly("ELECTRIC_CAR");
        get("/api/slots/Z-99").expectProblem(404, "NOT_FOUND");
    }

    @Test
    void adminCreatesSlotWithDerivedCategories() {
        Response r = post("/api/slots", ADMIN, """
                {"slotCode": "d-01", "slotType": "STANDARD", "floor": 1, "row": "d", "column": 1}""").expect(201);

        assertThat((String) r.json("$.slotCode")).isEqualTo("D-01");
        assertThat((String) r.json("$.row")).isEqualTo("D");
        assertThat((String) r.json("$.status")).isEqualTo("AVAILABLE");
        assertThat((List<String>) r.json("$.supportedVehicleCategories"))
                .containsExactlyInAnyOrder("ACCESSIBLE", "CAR", "ELECTRIC_CAR");
        assertThat(r.location()).hasValueSatisfying(l -> assertThat(l).endsWith("/api/slots/D-01"));
    }

    @Test
    void rejectsDuplicateCodeOrPositionAndInvalidInput() {
        post("/api/slots", ADMIN, """
                {"slotCode": "A-01", "slotType": "STANDARD", "floor": 9, "row": "Z", "column": 1}""")
                .expectProblem(409, "DUPLICATE_RESOURCE");
        post("/api/slots", ADMIN, """
                {"slotCode": "X-01", "slotType": "STANDARD", "floor": 0, "row": "A", "column": 1}""")
                .expectProblem(409, "DUPLICATE_RESOURCE");
        Response invalid = post("/api/slots", ADMIN, """
                {"slotCode": "", "floor": 0, "row": "A1", "column": 0}""").expectProblem(400, "VALIDATION_FAILED");
        assertThat((List<String>) invalid.json("$.errors[*].field"))
                .contains("slotCode", "slotType", "row", "column");
        post("/api/slots", ADMIN, """
                {"slotCode": "X-02", "slotType": "STANDARD", "supportedVehicleCategories": ["TRUCK"],
                 "floor": 3, "row": "X", "column": 2}""").expectProblem(404, "NOT_FOUND");
    }

    @Test
    void adminUpdatesSlot() {
        Response r = put("/api/slots/A-08", ADMIN, """
                {"slotType": "LARGE", "floor": 2, "row": "E", "column": 1}""").expect(200);

        assertThat((String) r.json("$.slotType")).isEqualTo("LARGE");
        assertThat((Integer) r.json("$.floor")).isEqualTo(2);
        assertThat((List<String>) r.json("$.supportedVehicleCategories"))
                .containsExactlyInAnyOrder("CAR", "VAN", "ELECTRIC_CAR");
        put("/api/slots/A-07", ADMIN, """
                {"slotType": "STANDARD", "floor": 2, "row": "E", "column": 1}""")
                .expectProblem(409, "DUPLICATE_RESOURCE");
    }

    @Test
    void occupiedSlotCannotBeUpdatedOrDisabled() {
        enter("CAR0001", "CAR", "A-01").expect(201);

        put("/api/slots/A-01", ADMIN, """
                {"slotType": "STANDARD", "floor": 0, "row": "A", "column": 1}""")
                .expectProblem(409, "INVALID_STATE");
        post("/api/slots/A-01/disable", ADMIN, null).expectProblem(409, "INVALID_STATE");
    }

    @Test
    void disableAndEnableSlot() {
        assertThat((String) post("/api/slots/A-03/disable", ADMIN, null).expect(200).json("$.status")).isEqualTo("DISABLED");
        assertThat((List<String>) get("/api/slots/available?category=CAR").json("$[*].slotCode")).doesNotContain("A-03");
        post("/api/slots/A-03/disable", ADMIN, null).expectProblem(409, "INVALID_STATE");
        enter("CAR0001", "CAR", "A-03").expectProblem(409, "SLOT_UNAVAILABLE");

        assertThat((String) post("/api/slots/A-03/enable", ADMIN, null).expect(200).json("$.status")).isEqualTo("AVAILABLE");
        post("/api/slots/A-03/enable", ADMIN, null).expectProblem(409, "INVALID_STATE");
    }

    @Test
    void slotManagementRequiresAdmin() {
        post("/api/slots/A-03/disable", ENTRY, null).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        post("/api/slots/A-03/disable", null, null).expectProblem(401, "UNAUTHENTICATED");
        put("/api/slots/A-03", EXIT, """
                {"slotType": "STANDARD", "floor": 0, "row": "A", "column": 3}""")
                .expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
    }
}
