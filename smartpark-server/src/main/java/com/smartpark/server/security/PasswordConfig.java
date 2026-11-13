package com.smartpark.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing. Only BCrypt hashes (with a random salt per password) are stored, never
 * plain passwords.
 * <p>
 * The work factor ({@code smartpark.security.bcrypt-strength}, default 12 = 2^12 rounds, about
 * 0.2-0.3 s per check on a desktop PC) makes offline guessing expensive while keeping sign-in
 * fast enough. Existing hashes keep working when the strength is changed, because each hash
 * records the strength it was made with.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${smartpark.security.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
