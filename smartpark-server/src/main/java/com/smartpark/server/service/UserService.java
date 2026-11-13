package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.CreateUserRequest;
import com.smartpark.server.dto.UserDto;
import com.smartpark.server.exception.DuplicateResourceException;
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
        return repository.findAll().stream().map(UserDto::from).toList();
    }

    public UserDto setActive(@NotBlank String username, boolean active, @NotBlank String actor) {
        User user = repository.findByUsername(Normalizer.username(username))
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found"));
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
