package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Server-side role-based access control: the same request gets 401 / 403 / 200 depending
 * on who sends it, whatever the client UI shows.
 */
class AccessControlApiTest extends AbstractApiIntegrationTest {

    @Test
    void publicEndpointsNeedNoSignIn() {
        getAnonymous("/api/health").expect(200);
        post("/api/auth/login", null, """
                {"username": "admin", "password": "Admin@123"}""").expect(200);
    }

    @Test
    void everythingElseRequiresASignedInUser() {
        for (String path : new String[]{"/api/slots", "/api/categories", "/api/statistics",
                "/api/parking/sessions/active", "/api/users", "/api/activity", "/api/realtime/status"}) {
            getAnonymous(path).expectProblem(401, "UNAUTHENTICATED");
        }
    }

    @Test
    void sharedReadEndpointsAreOpenToEveryRole() {
        for (String user : new String[]{ADMIN, ENTRY, EXIT}) {
            getAs("/api/slots", user).expect(200);
            getAs("/api/categories", user).expect(200);
            getAs("/api/statistics", user).expect(200);
            getAs("/api/parking/sessions/active", user).expect(200);
        }
    }

    @Test
    void adminOnlyEndpointsRefuseOperators() {
        for (String path : new String[]{"/api/users", "/api/activity", "/api/realtime/status"}) {
            getAs(path, ENTRY).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
            getAs(path, EXIT).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
            getAs(path, ADMIN).expect(200);
        }
        post("/api/users", ENTRY, """
                {"username": "sneaky", "password": "password123", "role": "ADMIN"}""")
                .expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        post("/api/users/exit1/deactivate", EXIT, null).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
    }

    @Test
    void historyAndExitLookupAreForExitOperatorsAndAdmins() {
        getAs("/api/parking/sessions", ENTRY).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        getAs("/api/parking/sessions", EXIT).expect(200);
        getAs("/api/parking/active/ABC123", ENTRY).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        getAs("/api/parking/active/ABC123", EXIT).expectProblem(404, "NO_ACTIVE_SESSION");
    }

    @Test
    void deactivatedAccountLosesAccessImmediately() {
        post("/api/users/entry1/deactivate", ADMIN, null).expect(200);
        getAs("/api/slots", ENTRY).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        post("/api/users/entry1/activate", ADMIN, null).expect(200);
        getAs("/api/slots", ENTRY).expect(200);
    }
}
