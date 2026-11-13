package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiTest extends AbstractApiIntegrationTest {

    @Test
    void listsAccountsWithoutPasswordHashes() {
        Response r = get("/api/users").expect(200);
        assertThat((List<String>) r.json("$[*].username")).containsExactly("admin", "entry1", "exit1");
        assertThat(r.body()).doesNotContain("password");
    }

    @Test
    void createsAnOperatorWhoCanSignIn() {
        Response r = post("/api/users", ADMIN, """
                {"username": "Entry2", "password": "Gate2@pass", "role": "ENTRY_OPERATOR"}""").expect(201);
        assertThat((String) r.json("$.username")).isEqualTo("entry2");
        assertThat((Boolean) r.json("$.active")).isTrue();
        assertThat(r.location()).hasValueSatisfying(l -> assertThat(l).endsWith("/api/users/entry2"));

        String token = login("entry2", "Gate2@pass");
        assertThat((String) getWithToken("/api/auth/me", token).json("$.role")).isEqualTo("ENTRY_OPERATOR");
    }

    @Test
    void validatesNewAccounts() {
        post("/api/users", ADMIN, """
                {"username": "x", "password": "short", "role": null}""").expectProblem(400, "VALIDATION_FAILED");
        post("/api/users", ADMIN, """
                {"username": "entry1", "password": "password123", "role": "EXIT_OPERATOR"}""")
                .expectProblem(409, "DUPLICATE_RESOURCE");
    }

    @Test
    void changingARoleTakesEffectAtOnceAndSignsTheUserOut() {
        String token = login("entry1", "Entry@123");
        Response r = put("/api/users/entry1/role", ADMIN, """
                {"role": "EXIT_OPERATOR"}""").expect(200);
        assertThat((String) r.json("$.role")).isEqualTo("EXIT_OPERATOR");

        getWithToken("/api/auth/me", token).expectProblem(401, "UNAUTHENTICATED");          // signed out
        getAs("/api/parking/sessions", ENTRY).expect(200);                                  // now an exit operator
        assertThat(get("/api/activity?action=USER_ROLE_CHANGED").body()).contains("from ENTRY_OPERATOR to EXIT_OPERATOR");
    }

    @Test
    void adminsCannotLockThemselvesOut() {
        post("/api/users/admin/deactivate", ADMIN, null).expectProblem(409, "INVALID_STATE");
        put("/api/users/admin/role", ADMIN, """
                {"role": "ENTRY_OPERATOR"}""").expectProblem(409, "INVALID_STATE");
    }

    @Test
    void theLastActiveAdministratorCannotBeRemoved() {
        post("/api/users", ADMIN, """
                {"username": "admin2", "password": "Admin2@pass", "role": "ADMIN"}""").expect(201);
        // admin2 demotes admin: allowed, admin2 remains
        put("/api/users/admin/role", "admin2", """
                {"role": "EXIT_OPERATOR"}""").expect(200);
        // now admin2 is the only admin: a third admin cannot take that away... and admin2 cannot remove itself
        post("/api/users/admin2/deactivate", "admin2", null).expectProblem(409, "INVALID_STATE");
    }

    @Test
    void unknownUserIs404() {
        post("/api/users/nobody/activate", ADMIN, null).expectProblem(404, "NOT_FOUND");
    }
}
