package com.smartpark.server.repository;

import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findByRoleOrderByUsernameAsc(UserRole role);

    List<User> findAllByOrderByUsernameAsc();

    long countByRoleAndActiveTrue(UserRole role);
}
