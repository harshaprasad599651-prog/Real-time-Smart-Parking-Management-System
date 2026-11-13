package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Receipts: stored ticket information, print recording and reprinting by ticket ID. */
class ReceiptApiTest extends AbstractApiIntegrationTest {

    @Test
    void aSuccessfulEntryStoresTheTicketInformationForTheReceipt() {
        String ticket = enter("wp cab-1234", "CAR", "A-01").expect(201).json("$.ticketId");

        Response r = getAs("/api/parking/receipts/" + ticket, ENTRY).expect(200);
        assertThat((String) r.json("$.ticketId")).isEqualTo(ticket);
        assertThat((String) r.json("$.vehicleNumber")).isEqualTo("WPCAB1234");
        assertThat((String) r.json("$.vehicleCategory")).isEqualTo("CAR");
        assertThat((String) r.json("$.parkingSlotCode")).isEqualTo("A-01");
        assertThat((String) r.json("$.slotType")).isEqualTo("STANDARD");
        assertThat((Integer) r.json("$.floor")).isZero();
        assertThat((String) r.json("$.row")).isEqualTo("A");
        assertThat((Integer) r.json("$.column")).isEqualTo(1);
        assertThat((String) r.json("$.entryTime")).isNotBlank();
        assertThat((String) r.json("$.entryOperator")).isEqualTo(ENTRY);
        assertThat((String) r.json("$.sessionStatus")).isEqualTo("ACTIVE");
        assertThat((Integer) r.json("$.printCount")).isZero();
    }

    @Test
    void aRejectedEntryHasNoTicketAndNoReceipt() {
        enter("FIRST1", "CAR", "A-01").expect(201);
        enter("SECOND2", "CAR", "A-01").expectProblem(409, "SLOT_UNAVAILABLE");

        Response sessions = get("/api/parking/sessions?vehicleNumber=SECOND2").expect(200);
        assertThat((Integer) sessions.json("$.totalElements")).isZero();
        getAs("/api/parking/receipts/SP-19990101-000001", ENTRY).expectProblem(404, "NOT_FOUND");
    }

    @Test
    void reprintingCountsCopiesButNeverCreatesASession() {
        String ticket = enter("REPRINT1", "CAR", "A-02").expect(201).json("$.ticketId");

        Response first = post("/api/parking/receipts/" + ticket + "/prints", ENTRY, """
                {"printerName": "Receipt printer"}""").expect(200);
        assertThat((Integer) first.json("$.printCount")).isEqualTo(1);
        assertThat((String) first.json("$.lastPrintedBy")).isEqualTo(ENTRY);

        // reprint by ticket ID, typed in lower case
        Response copy = post("/api/parking/receipts/" + ticket.toLowerCase() + "/prints", ADMIN, null).expect(200);
        assertThat((Integer) copy.json("$.printCount")).isEqualTo(2);
        assertThat((String) copy.json("$.lastPrintedBy")).isEqualTo(ADMIN);

        // still exactly one session, still ACTIVE, slot unchanged
        Response sessions = get("/api/parking/sessions?vehicleNumber=REPRINT1").expect(200);
        assertThat((Integer) sessions.json("$.totalElements")).isEqualTo(1);
        assertThat((String) sessions.json("$.content[0].status")).isEqualTo("ACTIVE");
        assertThat((String) get("/api/slots/A-02").json("$.currentTicketId")).isEqualTo(ticket);

        assertThat(get("/api/activity?action=RECEIPT_PRINTED").body()).contains(ticket, "Receipt printer");
        assertThat(get("/api/activity?action=RECEIPT_REPRINTED").body()).contains(ticket, "copy 1");
    }

    @Test
    void aReceiptCanStillBeReprintedAfterTheVehicleLeft() {
        String ticket = enter("GONE1", "CAR", "A-03").expect(201).json("$.ticketId");
        leave("GONE1").expect(200);

        Response r = getAs("/api/parking/receipts/" + ticket, ENTRY).expect(200);
        assertThat((String) r.json("$.sessionStatus")).isEqualTo("COMPLETED");
        assertThat((String) r.json("$.parkingSlotCode")).isEqualTo("A-03");
        post("/api/parking/receipts/" + ticket + "/prints", ENTRY, null).expect(200);
        assertThat((String) get("/api/slots/A-03").json("$.status")).isEqualTo("AVAILABLE");
    }

    @Test
    void onlyEntryOperatorsAndAdminsUseReceipts() {
        String ticket = enter("ROLE1", "CAR", "A-04").expect(201).json("$.ticketId");
        getAs("/api/parking/receipts/" + ticket, EXIT).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        post("/api/parking/receipts/" + ticket + "/prints", EXIT, null).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        getAnonymous("/api/parking/receipts/" + ticket).expectProblem(401, "UNAUTHENTICATED");
    }

    @Test
    void unknownTicketsAreReported() {
        post("/api/parking/receipts/SP-20000101-000009/prints", ENTRY, null).expectProblem(404, "NOT_FOUND");
    }
}
