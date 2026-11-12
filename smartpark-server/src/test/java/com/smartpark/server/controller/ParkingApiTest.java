package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Vehicle entry, exit and parking records over HTTP. */
class ParkingApiTest extends AbstractApiIntegrationTest {

    // ------------------------------------------------------------------ entry

    @Test
    void entryCreatesActiveSessionAndReceipt() {
        Response r = enter("wp cab-1234", "car", "a-01").expect(201);

        String ticket = r.json("$.ticketId");
        assertThat(ticket).matches("SP-\\d{8}-\\d{6}");
        assertThat((String) r.json("$.vehicleNumber")).isEqualTo("WPCAB1234");
        assertThat((String) r.json("$.vehicleCategory")).isEqualTo("CAR");
        assertThat((String) r.json("$.parkingSlotCode")).isEqualTo("A-01");
        assertThat((String) r.json("$.status")).isEqualTo("ACTIVE");
        assertThat(Instant.parse(r.json("$.entryTime"))).isBeforeOrEqualTo(Instant.now());
        assertThat((String) r.json("$.entryOperator")).isEqualTo(ENTRY);
        assertThat((Object) r.json("$.exitTime")).isNull();
        assertThat(r.location()).hasValueSatisfying(l -> assertThat(l).endsWith("/api/parking/sessions/" + ticket));

        assertThat((String) get("/api/parking/sessions/" + ticket).expect(200).json("$.parkingSlotCode")).isEqualTo("A-01");
        assertThat((String) get("/api/slots/A-01").json("$.status")).isEqualTo("OCCUPIED");
        assertThat((String) get("/api/slots/A-01").json("$.currentTicketId")).isEqualTo(ticket);
    }

    @Test
    void entryRejectsAlreadyParkedVehicle() {
        enter("ABC123", "CAR", "A-01").expect(201);

        enter("abc-123", "CAR", "A-02").expectProblem(409, "VEHICLE_ALREADY_PARKED");
        assertThat((String) get("/api/slots/A-02").json("$.status")).isEqualTo("AVAILABLE");
    }

    @Test
    void entryRejectsOccupiedSlot() {
        enter("ABC123", "CAR", "A-01").expect(201);

        Response r = enter("XYZ789", "CAR", "A-01").expectProblem(409, "SLOT_UNAVAILABLE");
        assertThat((String) r.json("$.detail")).contains("occupied");
        assertThat((String) r.json("$.instance")).isEqualTo("/api/parking/entry");
    }

    @Test
    void entryRejectsIncompatibleSlotUnknownSlotAndUnknownCategory() {
        enter("MC1234", "MOTORCYCLE", "A-01").expectProblem(422, "SLOT_NOT_COMPATIBLE");
        enter("ABC123", "CAR", "Z-99").expectProblem(404, "NOT_FOUND");
        enter("ABC123", "TRUCK", "A-01").expectProblem(404, "NOT_FOUND");
    }

    @Test
    void entryValidatesInput() {
        Response r = post("/api/parking/entry", ENTRY, """
                {"vehicleNumber": "#", "categoryName": "", "slotCode": ""}""").expectProblem(400, "VALIDATION_FAILED");
        assertThat((List<String>) r.json("$.errors[*].field"))
                .contains("vehicleNumber", "categoryName", "slotCode");
    }

    @Test
    void entryRequiresEntryOperatorOrAdmin() {
        String body = """
                {"vehicleNumber": "ABC123", "categoryName": "CAR", "slotCode": "A-01"}""";
        post("/api/parking/entry", EXIT, body).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        post("/api/parking/entry", null, body).expectProblem(400, "MISSING_HEADER");
        post("/api/parking/entry", ADMIN, body).expect(201);
    }

    // ------------------------------------------------------------------- exit

    @Test
    void exitLookupShowsSessionAndSlot() {
        String ticket = enter("WP CAB-1234", "ELECTRIC_CAR", "B-05").expect(201).json("$.ticketId");

        Response r = get("/api/parking/active/wp-cab-1234").expect(200);
        assertThat((String) r.json("$.session.ticketId")).isEqualTo(ticket);
        assertThat((String) r.json("$.session.vehicleCategory")).isEqualTo("ELECTRIC_CAR");
        assertThat((String) r.json("$.slot.slotCode")).isEqualTo("B-05");
        assertThat((String) r.json("$.slot.slotType")).isEqualTo("EV_CHARGING");
        assertThat((Integer) r.json("$.slot.floor")).isZero();
        assertThat((Integer) r.json("$.minutesParked")).isZero();

        get("/api/parking/active/NOPE99").expectProblem(404, "NO_ACTIVE_SESSION");
    }

    @Test
    void exitCompletesSessionAndReleasesSlot() {
        String ticket = enter("ABC123", "CAR", "A-01").expect(201).json("$.ticketId");

        Response r = leave("abc 123").expect(200);
        assertThat((String) r.json("$.ticketId")).isEqualTo(ticket);
        assertThat((String) r.json("$.status")).isEqualTo("COMPLETED");
        assertThat((String) r.json("$.exitOperator")).isEqualTo(EXIT);
        assertThat(Instant.parse(r.json("$.exitTime"))).isAfterOrEqualTo(Instant.parse(r.json("$.entryTime")));
        assertThat((Integer) r.json("$.durationMinutes")).isZero();

        assertThat((String) get("/api/slots/A-01").json("$.status")).isEqualTo("AVAILABLE");
        get("/api/parking/active/ABC123").expectProblem(404, "NO_ACTIVE_SESSION");
        leave("ABC123").expectProblem(404, "NO_ACTIVE_SESSION");
        enter("ABC123", "CAR", "A-01").expect(201); // can park again
    }

    @Test
    void exitRequiresExitOperatorOrAdmin() {
        enter("ABC123", "CAR", "A-01").expect(201);
        post("/api/parking/exit", ENTRY, """
                {"vehicleNumber": "ABC123"}""").expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        post("/api/parking/exit", ADMIN, """
                {"vehicleNumber": "ABC123"}""").expect(200);
    }

    // -------------------------------------------------------------- records

    @Test
    void listsActiveSessionsNewestFirst() {
        enter("AAA111", "CAR", "A-01").expect(201);
        enter("BBB222", "MOTORCYCLE", "M-01").expect(201);
        enter("CCC333", "CAR", "A-02").expect(201);
        leave("AAA111").expect(200);

        assertThat((List<String>) get("/api/parking/sessions/active").expect(200).json("$[*].vehicleNumber"))
                .containsExactly("CCC333", "BBB222");
    }

    @Test
    void searchesHistoryWithFiltersAndPaging() {
        Instant start = Instant.now().minusSeconds(1);
        for (int i = 1; i <= 5; i++) {
            enter("CAR00" + i, "CAR", "A-0" + i).expect(201);
        }
        leave("CAR001").expect(200);
        leave("CAR002").expect(200);
        enter("CAR001", "CAR", "C-01").expect(201); // second visit

        Response all = get("/api/parking/sessions?size=4").expect(200);
        assertThat((Integer) all.json("$.totalElements")).isEqualTo(6);
        assertThat((Integer) all.json("$.totalPages")).isEqualTo(2);
        assertThat((List<?>) all.json("$.content")).hasSize(4);
        assertThat((String) all.json("$.content[0].vehicleNumber")).isEqualTo("CAR001"); // newest first
        assertThat((List<?>) get("/api/parking/sessions?size=4&page=1").json("$.content")).hasSize(2);

        assertThat((Integer) get("/api/parking/sessions?vehicleNumber=car-001").json("$.totalElements")).isEqualTo(2);
        assertThat((Integer) get("/api/parking/sessions?status=COMPLETED").json("$.totalElements")).isEqualTo(2);
        assertThat((Integer) get("/api/parking/sessions?slotCode=a-03").json("$.totalElements")).isEqualTo(1);
        assertThat((Integer) get("/api/parking/sessions?from=" + start).json("$.totalElements")).isEqualTo(6);
        assertThat((Integer) get("/api/parking/sessions?to=" + start).json("$.totalElements")).isZero();
    }

    @Test
    void historyRejectsInvalidParameters() {
        get("/api/parking/sessions?size=0").expectProblem(400, "VALIDATION_FAILED");
        get("/api/parking/sessions?page=-1").expectProblem(400, "VALIDATION_FAILED");
        get("/api/parking/sessions?from=yesterday").expectProblem(400, "INVALID_PARAMETER");
        get("/api/parking/sessions?from=2026-11-13T10:00:00Z&to=2026-11-13T09:00:00Z")
                .expectProblem(400, "BAD_REQUEST");
        get("/api/parking/sessions/SP-00000000-000000").expectProblem(404, "NOT_FOUND");
    }
}
