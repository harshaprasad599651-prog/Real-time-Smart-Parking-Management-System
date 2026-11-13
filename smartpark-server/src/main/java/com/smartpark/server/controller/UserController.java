package com.smartpark.server.controller;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.ChangeRoleRequest;
import com.smartpark.server.dto.CreateUserRequest;
import com.smartpark.server.dto.UserDto;
import com.smartpark.server.security.AuthenticatedOperator;
import com.smartpark.server.security.RequireRole;
import com.smartpark.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Operator accounts. Every endpoint here requires the ADMIN role (checked on the server). */
@RestController
@RequestMapping("/api/users")
@RequireRole(UserRole.ADMIN)
@Tag(name = "Users", description = "Operator accounts and roles (ADMIN only)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all accounts", description = "Sorted by username. Password hashes are never returned.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.Forbidden
    public List<UserDto> list() {
        return userService.listUsers();
    }

    @PostMapping
    @Operation(summary = "Create an operator account",
            description = "Username 3-30 characters (letters, digits, '.', '_', '-'); password 8-72 characters, "
                    + "stored as a BCrypt hash. The account is active immediately.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.Conflict
    public ResponseEntity<UserDto> create(@Parameter(hidden = true) AuthenticatedOperator admin,
                                          @Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.createUser(request, admin.username());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{u}")
                .buildAndExpand(created.username()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{username}/role")
    @Operation(summary = "Assign a role",
            description = "The user is signed out everywhere and gets the new role's screens at the next login. "
                    + "Refused (409) for your own account or for the last active administrator.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public UserDto changeRole(@Parameter(hidden = true) AuthenticatedOperator admin, @PathVariable String username,
                              @Valid @RequestBody ChangeRoleRequest request) {
        return userService.changeRole(username, request.role(), admin.username());
    }

    @PostMapping("/{username}/activate")
    @Operation(summary = "Activate an account")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    public UserDto activate(@Parameter(hidden = true) AuthenticatedOperator admin, @PathVariable String username) {
        return userService.setActive(username, true, admin.username());
    }

    @PostMapping("/{username}/deactivate")
    @Operation(summary = "Deactivate an account",
            description = "Signs the user out on every terminal immediately. Refused (409) for your own account "
                    + "or the last active administrator.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public UserDto deactivate(@Parameter(hidden = true) AuthenticatedOperator admin, @PathVariable String username) {
        return userService.setActive(username, false, admin.username());
    }
}
