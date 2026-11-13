package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Brute-force protection, password policy, input sanitisation and audit of refused operations. */
class SecurityHardeningApiTest extends AbstractApiIntegrationTest {

    @BeforeEach
    void seed() {
        seedSampleData();
    }

    @Test
    void repeatedWrongPasswordsLockTheAccountEvenForTheRightPassword() {
        for (int i = 0; i < 5; i++) {
            loginRaw("entry1", "wrong-" + i).expectProblem(401, "INVALID_CREDENTIALS");
        }
        Response locked = loginRaw("entry1", "Entry@123");            // correct password, still refused
        locked.expectProblem(429, "TOO_MANY_LOGIN_ATTEMPTS");
        assertThat((Integer) locked.json("$.retryAfterSeconds")).isBetween(1, 300);

        loginRaw("exit1", "Exit@123").expect(200);                     // other accounts unaffected
        assertThat(get("/api/activity?action=LOGIN_LOCKED").body()).contains("entry1");
    }

    @Test
    void aSuccessfulSignInResetsTheFailureCount() {
        for (int i = 0; i < 4; i++) {
            loginRaw("entry1", "wrong").expect(401);
        }
        loginRaw("entry1", "Entry@123").expect(200);
        for (int i = 0; i < 4; i++) {
            loginRaw("entry1", "wrong").expect(401);
        }
        loginRaw("entry1", "Entry@123").expect(200);
    }

    @Test
    void unknownUsersGetTheSameAnswerAsWrongPasswords() {
        Response unknown = loginRaw("nobody", "whatever1");
        Response wrong = loginRaw("entry1", "whatever1");
        unknown.expectProblem(401, "INVALID_CREDENTIALS");
        wrong.expectProblem(401, "INVALID_CREDENTIALS");
        assertThat((String) unknown.json("$.detail")).isEqualTo(wrong.json("$.detail"));
    }

    @Test
    void passwordsNeedALetterAndADigit() {
        Response r = post("/api/users", ADMIN, """
                {"username": "gate2", "password": "onlyletters", "role": "ENTRY_OPERATOR"}""");
        r.expectProblem(400, "VALIDATION_FAILED");
        assertThat((List<String>) r.json("$.errors[*].field")).contains("password");
        post("/api/users", ADMIN, """
                {"username": "gate2", "password": "letters4and5digits", "role": "ENTRY_OPERATOR"}""").expect(201);
    }

    @Test
    void controlCharactersCannotForgeActivityLogLines() {
        loginRaw("evil\nLOGIN_SUCCESS admin", "x1").expect(401);
        String failed = get("/api/activity?action=LOGIN_FAILED").body();
        // usernames are compared in lower case; the line break became a space: one log line, not two
        assertThat(failed).doesNotContain("\\n").contains("evil login_success admin");
    }

    @Test
    void notesAreStoredWithoutLineBreaksOrInvisibleCharacters() {
        Response r = post("/api/parking/entry", ENTRY, """
                {"vehicleNumber": "CLEAN1", "categoryName": "CAR", "slotCode": "A-01",
                 "notes": "Scratch\\r\\non door\\u202e\\u200b   bumper"}""").expect(201);
        assertThat((String) r.json("$.notes")).isEqualTo("Scratch on door bumper");
    }

    @Test
    void refusedOperationsAreAudited() {
        getAs("/api/users", ENTRY).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        post("/api/parking/exit", ENTRY, """
                {"vehicleNumber": "ABC123"}""").expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        String denied = get("/api/activity?action=ACCESS_DENIED").body();
        assertThat(denied).contains("entry1", "GET /api/users", "POST /api/parking/exit");
    }

    @Test
    void healthReportsTheDatabase() {
        Response r = getAnonymous("/api/health").expect(200);
        assertThat((String) r.json("$.status")).isEqualTo("UP");
        assertThat((String) r.json("$.database")).isEqualTo("UP");
    }

    private Response loginRaw(String username, String password) {
        return post("/api/auth/login", null, """
                {"username": %s, "password": "%s", "terminal": "TEST"}""".formatted(jsonString(username), password));
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
