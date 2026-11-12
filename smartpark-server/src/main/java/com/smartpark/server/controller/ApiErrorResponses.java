package com.smartpark.server.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Reusable OpenAPI declarations of the error responses (all are application/problem+json). */
final class ApiErrorResponses {

    private ApiErrorResponses() {
    }

    /** Declares the 200 response explicitly; springdoc omits it once error responses are declared. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "OK")
    @interface Ok {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "400", description = "Validation failed (code VALIDATION_FAILED, MALFORMED_REQUEST, MISSING_HEADER ...)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @interface BadRequest {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "403", description = "Operator unknown, inactive or lacks the required role (OPERATOR_NOT_AUTHORIZED)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @interface Forbidden {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "404", description = "Resource not found (NOT_FOUND / NO_ACTIVE_SESSION)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @interface NotFound {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "409", description = "Conflict with current state (SLOT_UNAVAILABLE, VEHICLE_ALREADY_PARKED, "
            + "DUPLICATE_RESOURCE, INVALID_STATE, CONCURRENT_UPDATE)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @interface Conflict {
    }
}
