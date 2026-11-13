package com.smartpark.server.controller;

import com.smartpark.server.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** With the default production setting, the password-less X-Operator header is ignored. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartpark.security.operator-header-enabled=false")
class OperatorHeaderDisabledTest extends AbstractApiIntegrationTest {

    @Test
    void operatorHeaderAloneIsRejectedButBearerTokenWorks() {
        String body = """
                {"vehicleNumber": "HDR123", "categoryName": "CAR", "slotCode": "A-01"}""";

        post("/api/parking/entry", ENTRY, body).expectProblem(401, "UNAUTHENTICATED");

        String token = login("entry1", "Entry@123");
        assertThat((String) postWithToken("/api/parking/entry", token, body).expect(201).json("$.entryOperator"))
                .isEqualTo("entry1");
    }

    @Test
    void readOnlyEndpointsStayOpen() {
        get("/api/slots").expect(200);
        get("/api/statistics").expect(200);
    }
}
