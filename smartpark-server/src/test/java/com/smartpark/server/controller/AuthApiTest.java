package com.smartpark.server.controller;

import com.smartpark.server.service.UserService;
import com.smartpark.server.support.AbstractApiIntegrationTest;
import com.smartpark.server.support.WsTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Login, token use, logout and token revocation over HTTP and WebSocket. */
class AuthApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    void loginReturnsTokenAndUser() {
        Response r = post("/api/auth/login", null, """
                {"username": "Entry1", "password": "Entry@123", "terminal": "ENTRY-GATE-1"}""").expect(200);

        assertThat((String) r.json("$.token")).hasSizeGreaterThan(40);
        assertThat((String) r.json("$.tokenType")).isEqualTo("Bearer");
        assertThat(Instant.parse(r.json("$.expiresAt"))).isAfter(Instant.now().plusSeconds(3600));
        assertThat((String) r.json("$.user.username")).isEqualTo("entry1");
        assertThat((String) r.json("$.user.role")).isEqualTo("ENTRY_OPERATOR");
        assertThat(r.body()).doesNotContain("password");
    }

    @Test
    void wrongPasswordUnknownUserAndInactiveUserAllGetTheSame401() {
        userService.setActive("exit1", false, "admin");
        for (String body : new String[]{
                "{\"username\": \"entry1\", \"password\": \"wrong-pass\"}",
                "{\"username\": \"nobody\", \"password\": \"Entry@123\"}",
                "{\"username\": \"exit1\", \"password\": \"Exit@123\"}"}) {
            Response r = post("/api/auth/login", null, body).expectProblem(401, "INVALID_CREDENTIALS");
            assertThat((String) r.json("$.detail")).isEqualTo("Invalid username or password");
        }
        post("/api/auth/login", null, "{\"username\": \"\", \"password\": \"\"}").expectProblem(400, "VALIDATION_FAILED");
    }

    @Test
    void tokenIdentifiesTheCallerAndRolesAreEnforced() {
        String entryToken = login("entry1", "Entry@123");
        String exitToken = login("exit1", "Exit@123");

        assertThat((String) getWithToken("/api/auth/me", entryToken).expect(200).json("$.role")).isEqualTo("ENTRY_OPERATOR");

        String entry = """
                {"vehicleNumber": "TOK123", "categoryName": "CAR", "slotCode": "A-01"}""";
        postWithToken("/api/parking/entry", exitToken, entry).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        Response ok = postWithToken("/api/parking/entry", entryToken, entry).expect(201);
        assertThat((String) ok.json("$.entryOperator")).isEqualTo("entry1");

        postWithToken("/api/slots/A-02/disable", entryToken, null).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        postWithToken("/api/slots/A-02/disable", login("admin", "Admin@123"), null).expect(200);
    }

    @Test
    void missingInvalidOrWrongSchemeCredentialsGive401() {
        getAnonymous("/api/auth/me").expectProblem(401, "UNAUTHENTICATED");
        getWithToken("/api/auth/me", "not-a-real-token").expectProblem(401, "UNAUTHENTICATED");
        getWithAuthorization("/api/auth/me", "Basic YWRtaW46QWRtaW5AMTIz").expectProblem(401, "UNAUTHENTICATED");
    }

    @Test
    void logoutRevokesTheToken() {
        String token = login("admin", "Admin@123");
        postWithToken("/api/auth/logout", token, null).expect(204);

        getWithToken("/api/auth/me", token).expectProblem(401, "UNAUTHENTICATED");
        assertThat(get("/api/activity?username=admin&limit=5").body()).contains("LOGOUT", "LOGIN_SUCCESS");
    }

    @Test
    void deactivatingAUserSignsThemOutEverywhere() {
        String token = login("exit1", "Exit@123");
        userService.setActive("exit1", false, "admin");

        getWithToken("/api/auth/me", token).expectProblem(401, "UNAUTHENTICATED");
    }

    @Test
    void webSocketAcceptsAccessTokenAndRejectsBadOnes() throws Exception {
        String token = login("entry1", "Entry@123");
        try (WsTestClient ws = WsTestClient.connect(port, null, "ENTRY-GATE-1", "access_token=" + token)) {
            assertThat(ws.await("CONNECTION_ACK").<String>read("$.actor")).isEqualTo("entry1");
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> WsTestClient.connect(port, null, "X", "access_token=bogus"))
                .rootCause().hasMessageContaining("401");
    }
}
