package com.smartpark.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title, description and bearer-token security shown at /swagger-ui.html and in /v3/api-docs. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearerToken";

    @Bean
    OpenAPI smartParkOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer")
                        .description("Token from POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .info(new Info()
                        .title("SmartPark API")
                        .version("0.1.0")
                        .description("""
                                REST API of the SmartPark central server, used by the Entry, Exit and Admin JavaFX clients over the LAN.

                                **Authentication:** call `POST /api/auth/login` with username and password, then send \
                                `Authorization: Bearer <token>` with every write request (in Swagger UI: *Authorize*). \
                                Write operations also check the role: ADMIN for management, ENTRY_OPERATOR for entry, \
                                EXIT_OPERATOR for exit; ADMIN may do all. Read-only GET endpoints are open on the LAN.

                                **Errors** use RFC 9457 `application/problem+json` with a stable `code` property, \
                                e.g. `UNAUTHENTICATED`, `SLOT_UNAVAILABLE`, `VEHICLE_ALREADY_PARKED`, `VALIDATION_FAILED`.

                                **Times** are ISO-8601 UTC instants from the server clock."""));
    }
}
