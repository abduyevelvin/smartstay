package com.smartstay.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.smartstay.auth.entity.UserEntity;
import com.smartstay.auth.enums.Role;
import com.smartstay.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds a single ADMIN user on first startup if none exists.
 * <p>
 * Default credentials (override via env vars for production):
 * APP_ADMIN_EMAIL    = admin@smartstay.com
 * APP_ADMIN_PASSWORD = Admin@1234
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@smartstay.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@1234}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin user already exists — skipping seed");
            return;
        }

        final var admin = new UserEntity();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setFirstName("Admin");
        admin.setLastName("SmartStay");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);

        log.info("Admin user seeded: email={}", adminEmail);
    }
}
