package com.smartpark.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description shown at /swagger-ui.html and in /v3/api-docs. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI smartParkOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SmartPark API")
                .version("0.1.0")
                .description("""
                        REST API of the SmartPark central server, used by the Entry, Exit and Admin JavaFX clients over the LAN.

                        **Operator identification (interim):** every write request must send the header \
                        `X-Operator: <username>`. The user must exist, be active and have a suitable role \
                        (ADMIN for management, ENTRY_OPERATOR for entry, EXIT_OPERATOR for exit; ADMIN may do all). \
                        This will be replaced by login tokens.

                        **Errors** use RFC 9457 `application/problem+json` with a stable `code` property, \
                        e.g. `SLOT_UNAVAILABLE`, `VEHICLE_ALREADY_PARKED`, `NO_ACTIVE_SESSION`, `VALIDATION_FAILED`.

                        **Times** are ISO-8601 UTC instants from the server clock."""));
    }
}
