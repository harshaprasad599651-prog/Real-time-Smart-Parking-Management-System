package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleCategoryApiTest extends AbstractApiIntegrationTest {

    @Test
    void listsAllCategoriesSortedByName() {
        Response r = get("/api/categories").expect(200);

        List<String> names = r.json("$[*].categoryName");
        assertThat(names).containsExactly("ACCESSIBLE", "CAR", "ELECTRIC_CAR", "MOTORCYCLE", "VAN");
        assertThat((List<String>) r.json("$[?(@.categoryName=='CAR')].compatibleSlotTypes[*]"))
                .containsExactlyInAnyOrder("STANDARD", "LARGE");
    }

    @Test
    void getsOneCategoryCaseInsensitively() {
        get("/api/categories/motorcycle").expect(200);
        get("/api/categories/TRUCK").expectProblem(404, "NOT_FOUND");
    }

    @Test
    void adminCreatesCategory() {
        Response r = post("/api/categories", ADMIN, """
                {"categoryName": "light truck", "description": "Small trucks", "compatibleSlotTypes": ["LARGE"]}""")
                .expect(201);

        assertThat((String) r.json("$.categoryName")).isEqualTo("LIGHT_TRUCK");
        assertThat(r.location()).hasValueSatisfying(l -> assertThat(l).endsWith("/api/categories/LIGHT_TRUCK"));
        get("/api/categories/LIGHT_TRUCK").expect(200);
    }

    @Test
    void rejectsDuplicateCategory() {
        post("/api/categories", ADMIN, """
                {"categoryName": "car", "compatibleSlotTypes": ["STANDARD"]}""")
                .expectProblem(409, "DUPLICATE_RESOURCE");
    }

    @Test
    void validatesCategoryBody() {
        Response r = post("/api/categories", ADMIN, """
                {"categoryName": "1bad", "compatibleSlotTypes": []}""")
                .expectProblem(400, "VALIDATION_FAILED");

        assertThat((List<String>) r.json("$.errors[*].field"))
                .containsExactlyInAnyOrder("categoryName", "compatibleSlotTypes");
    }

    @Test
    void rejectsMalformedJsonAndUnknownEnum() {
        post("/api/categories", ADMIN, "{ not json").expectProblem(400, "MALFORMED_REQUEST");
        post("/api/categories", ADMIN, """
                {"categoryName": "BUS", "compatibleSlotTypes": ["HUGE"]}""")
                .expectProblem(400, "MALFORMED_REQUEST");
    }

    @Test
    void requiresAnActiveAdmin() {
        String body = """
                {"categoryName": "BUS", "compatibleSlotTypes": ["LARGE"]}""";
        post("/api/categories", null, body).expectProblem(401, "UNAUTHENTICATED");
        post("/api/categories", ENTRY, body).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
        post("/api/categories", "ghost", body).expectProblem(403, "OPERATOR_NOT_AUTHORIZED");
    }

    @Test
    void updatingCompatibleSlotTypesUpdatesSlots() {
        // VAN: LARGE only -> LARGE + STANDARD
        Response r = put("/api/categories/van", ADMIN, """
                {"description": "Vans and SUVs", "compatibleSlotTypes": ["LARGE", "STANDARD"]}""").expect(200);
        assertThat((String) r.json("$.description")).isEqualTo("Vans and SUVs");
        assertThat((List<String>) get("/api/slots/A-01").json("$.supportedVehicleCategories")).contains("VAN");
        assertThat((List<?>) get("/api/slots/available?category=VAN").json("$")).hasSize(4 + 16);

        // ...and back to LARGE only
        put("/api/categories/VAN", ADMIN, """
                {"description": "Vans", "compatibleSlotTypes": ["LARGE"]}""").expect(200);
        assertThat((List<String>) get("/api/slots/A-01").json("$.supportedVehicleCategories")).doesNotContain("VAN");
        assertThat((List<?>) get("/api/slots/available?category=VAN").json("$")).hasSize(4);
    }

    @Test
    void updateOfUnknownCategoryIs404() {
        put("/api/categories/TRUCK", ADMIN, """
                {"compatibleSlotTypes": ["LARGE"]}""").expectProblem(404, "NOT_FOUND");
    }
}
