package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.CreateUserRequest;
import com.smartpark.server.dto.UserDto;
import com.smartpark.server.exception.DuplicateResourceException;
import com.smartpark.server.exception.InvalidStateException;
import com.smartpark.server.exception.OperatorNotAuthorizedException;
import com.smartpark.server.exception.ResourceNotFoundException;
import com.smartpark.server.repository.UserRepository;
import com.smartpark.server.security.AuthTokenService;
import com.smartpark.server.util.Normalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** User accounts: creation, password checking and operator authorisation. */
@Service
@Validated
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLog;
    private final AuthTokenService tokenService;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, ActivityLogService activityLog,
                       AuthTokenService tokenService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.activityLog = activityLog;
        this.tokenService = tokenService;
    }

    public UserDto createUser(@Valid @NotNull CreateUserRequest request, @NotBlank String actor) {
        String username = Normalizer.username(request.username());
        if (repository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        User user = new User(username, passwordEncoder.encode(request.password()), request.role(), true,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            user = repository.insert(user);
        } catch (DuplicateKeyException e) {
            // Another request created the same username between the check and the insert.
            throw new DuplicateResourceException("Username '" + username + "' is already taken", e);
        }
        activityLog.record(actor, ActivityAction.USER_CREATED, "Created user " + username + " (" + request.role() + ")");
        return UserDto.from(user);
    }

    /**
     * Checks a username/password pair.
     *
     * @return the user if the credentials are correct and the account is active
     */
    public Optional<UserDto> authenticate(@NotBlank String username, @NotBlank String password) {
        String normalized = Normalizer.username(username);
        Optional<User> user = repository.findByUsername(normalized)
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()));
        activityLog.record(normalized,
                user.isPresent() ? ActivityAction.LOGIN_SUCCESS : ActivityAction.LOGIN_FAILED,
                user.isPresent() ? "Login successful" : "Login failed");
        return user.map(UserDto::from);
    }

    public Optional<UserDto> findByUsername(@NotBlank String username) {
        return repository.findByUsername(Normalizer.username(username)).map(UserDto::from);
    }

    public List<UserDto> listUsers() {
        return repository.findAllByOrderByUsernameAsc().stream().map(UserDto::from).toList();
    }

    /**
     * Activates or deactivates an account. Deactivation signs the user out on every terminal.
     *
     * @throws InvalidStateException if an admin tries to deactivate their own account, or the
     *                               last active administrator would be deactivated
     */
    public UserDto setActive(@NotBlank String username, boolean active, @NotBlank String actor) {
        User user = repository.findByUsername(Normalizer.username(username))
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found"));
        if (!active && user.getUsername().equals(Normalizer.username(actor))) {
            throw new InvalidStateException("You cannot deactivate your own account");
        }
        if (!active && user.isActive() && user.getRole() == UserRole.ADMIN && isLastActiveAdmin()) {
            throw new InvalidStateException("At least one active administrator is required");
        }
        user.setActive(active);
        user = repository.save(user);
        if (!active) {
            tokenService.revokeAll(user.getUsername()); // signed out on every terminal
        }
        activityLog.record(actor, ActivityAction.USER_STATUS_CHANGED,
                (active ? "Activated" : "Deactivated") + " user " + user.getUsername());
        return UserDto.from(user);
    }

    /**
     * Assigns a new role. The user is signed out everywhere so their next login gets the screens
     * of the new role.
     *
     * @throws InvalidStateException if an admin changes their own role, or the last active
     *                               administrator would lose the ADMIN role
     */
    public UserDto changeRole(@NotBlank String username, @NotNull UserRole role, @NotBlank String actor) {
        User user = repository.findByUsername(Normalizer.username(username))
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found"));
        if (user.getRole() == role) {
            return UserDto.from(user);
        }
        if (user.getUsername().equals(Normalizer.username(actor))) {
            throw new InvalidStateException("You cannot change your own role");
        }
        if (user.getRole() == UserRole.ADMIN && user.isActive() && isLastActiveAdmin()) {
            throw new InvalidStateException("At least one active administrator is required");
        }
        UserRole previous = user.getRole();
        user.setRole(role);
        user = repository.save(user);
        tokenService.revokeAll(user.getUsername());
        activityLog.record(actor, ActivityAction.USER_ROLE_CHANGED,
                "Changed role of " + user.getUsername() + " from " + previous + " to " + role);
        return UserDto.from(user);
    }

    private boolean isLastActiveAdmin() {
        return repository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1;
    }

    /**
     * Returns the user if it exists, is active and has one of the allowed roles.
     *
     * @throws OperatorNotAuthorizedException otherwise
     */
    public User requireOperator(String username, Set<UserRole> allowedRoles) {
        if (username == null || username.isBlank()) {
            throw new OperatorNotAuthorizedException("Operator username is required");
        }
        User user = repository.findByUsername(Normalizer.username(username))
                .orElseThrow(() -> new OperatorNotAuthorizedException("Unknown operator '" + username + "'"));
        if (!user.isActive()) {
            throw new OperatorNotAuthorizedException("Operator '" + user.getUsername() + "' is inactive");
        }
        if (!allowedRoles.contains(user.getRole())) {
            throw new OperatorNotAuthorizedException(
                    "Operator '" + user.getUsername() + "' (" + user.getRole() + ") may not perform this operation");
        }
        return user;
    }
}
