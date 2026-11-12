package com.smartpark.server.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Turns every error into an RFC 9457 {@code application/problem+json} body:
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Conflict",
 *   "status": 409,
 *   "detail": "Slot A-01 is already occupied",
 *   "instance": "/api/parking/entry",
 *   "code": "SLOT_UNAVAILABLE",
 *   "timestamp": "2026-11-13T08:15:30Z",
 *   "errors": [ { "field": "vehicleNumber", "message": "must not be blank" } ]   // validation only
 * }
 * </pre>
 * {@code code} is a stable machine-readable value the JavaFX client can switch on.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** A field-level validation error. */
    public record FieldViolation(String field, String message) {
    }

    // ------------------------------------------------------------ domain errors

    @ExceptionHandler(SmartParkException.class)
    ProblemDetail handleDomain(SmartParkException e, WebRequest request) {
        HttpStatus status;
        String code;
        switch (e) {
            case NoActiveSessionException x -> { status = HttpStatus.NOT_FOUND; code = "NO_ACTIVE_SESSION"; }
            case ResourceNotFoundException x -> { status = HttpStatus.NOT_FOUND; code = "NOT_FOUND"; }
            case VehicleAlreadyParkedException x -> { status = HttpStatus.CONFLICT; code = "VEHICLE_ALREADY_PARKED"; }
            case SlotUnavailableException x -> { status = HttpStatus.CONFLICT; code = "SLOT_UNAVAILABLE"; }
            case DuplicateResourceException x -> { status = HttpStatus.CONFLICT; code = "DUPLICATE_RESOURCE"; }
            case InvalidStateException x -> { status = HttpStatus.CONFLICT; code = "INVALID_STATE"; }
            case ConcurrentUpdateException x -> { status = HttpStatus.CONFLICT; code = "CONCURRENT_UPDATE"; }
            case SlotNotCompatibleException x -> { status = HttpStatus.UNPROCESSABLE_CONTENT; code = "SLOT_NOT_COMPATIBLE"; }
            case OperatorNotAuthorizedException x -> { status = HttpStatus.FORBIDDEN; code = "OPERATOR_NOT_AUTHORIZED"; }
            default -> { status = HttpStatus.BAD_REQUEST; code = "BUSINESS_RULE_VIOLATION"; }
        }
        return problem(status, code, e.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e, WebRequest request) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "The record was changed by another operator. Reload and try again.", request);
    }

    // --------------------------------------------------------- invalid input

    /** Constraint violations raised by @Validated services. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException e, WebRequest request) {
        List<FieldViolation> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldViolation(leafName(v), v.getMessage()))
                .sorted((a, b) -> a.field().compareTo(b.field()))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
        pd.setProperty("errors", errors);
        return pd;
    }

    /** Invalid values rejected while normalising (e.g. a malformed vehicle number). */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException e, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), request);
    }

    /** {@code @Valid @RequestBody} failures. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
                .sorted((a, b) -> a.field().compareTo(b.field()))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    /** Constraint violations on {@code @RequestParam} / {@code @PathVariable}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(err -> new FieldViolation(
                        err instanceof FieldError fe ? fe.getField() : r.getMethodParameter().getParameterName(),
                        err.getDefaultMessage())))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    // ------------------------------------------------------- infrastructure

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ProblemDetail handleDatabaseDown(DataAccessResourceFailureException e, WebRequest request) {
        log.error("Database unavailable: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "The database is not reachable. Please try again shortly.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e, WebRequest request) {
        log.error("Unexpected error on {}", path(request), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request); // no internals leaked to the client
    }

    /**
     * Spring MVC's own errors (malformed JSON, missing header, wrong parameter type, 404, 405 ...)
     * also get a {@code code} and {@code timestamp}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd
                && (pd.getProperties() == null || !pd.getProperties().containsKey("code"))) {
            String code = switch (ex.getClass().getSimpleName()) {
                case "HttpMessageNotReadableException" -> "MALFORMED_REQUEST";
                case "MissingRequestHeaderException" -> "MISSING_HEADER";
                case "MissingServletRequestParameterException" -> "MISSING_PARAMETER";
                case "MethodArgumentTypeMismatchException", "TypeMismatchException" -> "INVALID_PARAMETER";
                case "NoResourceFoundException", "NoHandlerFoundException" -> "NOT_FOUND";
                case "HttpRequestMethodNotSupportedException" -> "METHOD_NOT_ALLOWED";
                case "HttpMediaTypeNotSupportedException" -> "UNSUPPORTED_MEDIA_TYPE";
                default -> HttpStatus.valueOf(statusCode.value()).name();
            };
            enrich(pd, code, request);
        }
        return response;
    }

    // -------------------------------------------------------------- helpers

    private static ProblemDetail problem(HttpStatus status, String code, String detail, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(pd, code, request);
        return pd;
    }

    private static void enrich(ProblemDetail pd, String code, WebRequest request) {
        if (pd.getInstance() == null) {
            pd.setInstance(URI.create(path(request)));
        }
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now());
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
    }

    private static String leafName(ConstraintViolation<?> v) {
        String name = "";
        for (Path.Node node : v.getPropertyPath()) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name;
    }
}
