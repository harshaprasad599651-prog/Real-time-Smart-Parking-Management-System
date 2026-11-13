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
    void readEndpointsRequireASignedInUser() {
        getAnonymous("/api/slots").expectProblem(401, "UNAUTHENTICATED");
        getAnonymous("/api/statistics").expectProblem(401, "UNAUTHENTICATED");
        getAnonymous("/api/health").expect(200);  // public

        String token = login("entry1", "Entry@123");
        getWithToken("/api/slots", token).expect(200);
        getWithToken("/api/statistics", token).expect(200);
    }
}
