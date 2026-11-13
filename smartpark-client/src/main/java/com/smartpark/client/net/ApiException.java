package com.smartpark.client.net;

import java.util.List;

/**
 * A failed API call, carrying the server's RFC 9457 problem details.
 *
 * @param status HTTP status, or 0 if the server could not be reached
 * @param code   SmartPark error code, e.g. INVALID_CREDENTIALS, SLOT_UNAVAILABLE, SERVER_UNREACHABLE
 */
public class ApiException extends RuntimeException {

    public static final String SERVER_UNREACHABLE = "SERVER_UNREACHABLE";

    /** One invalid field from a VALIDATION_FAILED response. */
    public record FieldError(String field, String message) {
    }

    private final int status;
    private final String code;
    private final List<FieldError> fieldErrors;

    public ApiException(int status, String code, String message, List<FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiException unreachable(String detail) {
        return new ApiException(0, SERVER_UNREACHABLE, "Cannot reach the SmartPark server (" + detail + ")", null);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    public boolean isUnauthorized() {
        return status == 401;
    }

    /** Text suitable for showing to the operator. */
    public String userMessage() {
        if (!fieldErrors.isEmpty()) {
            return fieldErrors.stream().map(f -> f.field() + ": " + f.message())
                    .reduce((a, b) -> a + "\n" + b).orElse(getMessage());
        }
        return getMessage();
    }
}
