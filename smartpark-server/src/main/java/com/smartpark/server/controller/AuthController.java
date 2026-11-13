package com.smartpark.server.controller;

import com.smartpark.server.exception.TooManyLoginAttemptsException;
import com.smartpark.server.security.LoginAttemptLimiter;
import com.smartpark.server.util.Normalizer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import com.smartpark.server.security.PublicEndpoint;
import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.dto.LoginRequest;
import com.smartpark.server.dto.LoginResponse;
import com.smartpark.server.dto.UserDto;
import com.smartpark.server.exception.InvalidCredentialsException;
import com.smartpark.server.exception.UnauthenticatedException;
import com.smartpark.server.security.AuthTokenService;
import com.smartpark.server.security.AuthTokenService.IssuedToken;
import com.smartpark.server.security.AuthenticatedOperator;
import com.smartpark.server.security.RequestAuthenticator;
import com.smartpark.server.service.ActivityLogService;
import com.smartpark.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Sign in / sign out of a SmartPark terminal")
public class AuthController {

    private final UserService userService;
    private final AuthTokenService tokenService;
    private final ActivityLogService activityLog;
    private final LoginAttemptLimiter loginLimiter;

    public AuthController(UserService userService, AuthTokenService tokenService, ActivityLogService activityLog,
                          LoginAttemptLimiter loginLimiter) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.activityLog = activityLog;
        this.loginLimiter = loginLimiter;
    }

    @PublicEndpoint
    @PostMapping("/login")
    @SecurityRequirements // no token needed to log in
    @Operation(summary = "Sign in",
            description = "Checks the BCrypt password hash. Returns a bearer token for the REST API and the WebSocket. "
                    + "Wrong password, unknown user and inactive account all give the same 401 INVALID_CREDENTIALS. "
                    + "After repeated failures the account (or computer) is locked for a few minutes: 429 "
                    + "TOO_MANY_LOGIN_ATTEMPTS with a Retry-After header.")
    @ApiErrorResponses.Ok
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS")
    @ApiResponse(responseCode = "429", description = "TOO_MANY_LOGIN_ATTEMPTS")
    @ApiErrorResponses.BadRequest
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String username = Normalizer.username(request.username());
        String address = http.getRemoteAddr();
        Optional<Duration> locked = loginLimiter.lockedFor(username, address);
        if (locked.isPresent()) {
            activityLog.record(username, ActivityAction.LOGIN_LOCKED, "Sign-in refused while locked (from " + address + ")");
            throw new TooManyLoginAttemptsException(locked.get());
        }
        Optional<UserDto> user = userService.authenticate(request.username(), request.password());
        if (user.isEmpty()) {
            if (loginLimiter.recordFailure(username, address)) {
                activityLog.record(username, ActivityAction.LOGIN_LOCKED,
                        "Locked after repeated failed sign-ins (from " + address + ")");
            }
            throw new InvalidCredentialsException();
        }
        loginLimiter.recordSuccess(username);
        String terminal = Normalizer.label(request.terminal(), 40, "UNKNOWN");
        IssuedToken token = tokenService.issue(user.get().username(), user.get().role(), terminal);
        return new LoginResponse(token.token(), "Bearer", token.expiresAt(), user.get());
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", description = "Validates the token (and extends it). 401 if it expired.")
    @ApiErrorResponses.Ok
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED")
    public UserDto me(@Parameter(hidden = true) AuthenticatedOperator operator) {
        return userService.findByUsername(operator.username())
                .filter(UserDto::active)
                .orElseThrow(() -> new UnauthenticatedException("Account no longer active"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out", description = "Revokes the token immediately.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED")
    public ResponseEntity<Void> logout(@Parameter(hidden = true) AuthenticatedOperator operator,
                                       @Parameter(hidden = true)
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        RequestAuthenticator.bearerToken(authorization).ifPresent(tokenService::revoke);
        activityLog.record(operator.username(), ActivityAction.LOGOUT, "Signed out");
        return ResponseEntity.noContent().build();
    }
}
