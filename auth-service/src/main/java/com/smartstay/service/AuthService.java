package com.smartstay.service;

import java.time.OffsetDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstay.config.SecurityProperties;
import com.smartstay.dto.AuthResponse;
import com.smartstay.dto.LoginRequest;
import com.smartstay.dto.RefreshRequest;
import com.smartstay.dto.RegisterRequest;
import com.smartstay.entity.RefreshToken;
import com.smartstay.entity.UserEntity;
import com.smartstay.exceptions.BadCredentialsException;
import com.smartstay.exceptions.ResourceExistsException;
import com.smartstay.exceptions.ResourceNotFoundException;
import com.smartstay.repository.RefreshTokenRepository;
import com.smartstay.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    public AuthResponse register(RegisterRequest registerRequest) {
        log.info("Registering new user with email: {}", registerRequest.email());

        if (userRepository.existsByEmail(registerRequest.email())) {
            log.warn("Registration failed — email already exists: {}", registerRequest.email());
            throw new ResourceExistsException("User already exists by email: " + registerRequest.email());
        }

        final var userEntity = new UserEntity();
        userEntity.setEmail(registerRequest.email());
        userEntity.setPassword(passwordEncoder.encode(registerRequest.password()));
        userEntity.setRole(registerRequest.role());
        userEntity.setFirstName(registerRequest.firstName());
        userEntity.setLastName(registerRequest.lastName());
        userEntity.setActive(true);
        userRepository.save(userEntity);

        log.info("User registered successfully: id={}, email={}, role={}", userEntity.getId(), userEntity.getEmail(),
                userEntity.getRole());

        final var accessToken = jwtService.generateAccessToken(userEntity);
        final var refreshToken = jwtService.generateRefreshToken(userEntity);

        return new AuthResponse(accessToken, refreshToken, "Bearer", securityProperties.getAccessTokenExpiry());
    }

    public AuthResponse login(LoginRequest loginRequest) {
        log.info("Login attempt for email: {}", loginRequest.email());

        // Use the same exception for both "not found" and "wrong password" — prevents user enumeration
        final var userEntity = userRepository.findByEmail(loginRequest.email())
                                             .orElseThrow(() -> {
                                                 log.warn("Login failed — email not found: {}", loginRequest.email());
                                                 return new BadCredentialsException("Invalid credentials");
                                             });

        if (!userEntity.isActive()) {
            log.warn("Login failed — account is deactivated: id={}, email={}", userEntity.getId(),
                    loginRequest.email());
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(loginRequest.password(), userEntity.getPassword())) {
            log.warn("Login failed — wrong password for email: {}", loginRequest.email());
            throw new BadCredentialsException("Invalid credentials");
        }

        final var accessToken = jwtService.generateAccessToken(userEntity);
        final var refreshToken = jwtService.generateRefreshToken(userEntity);

        log.info("Login successful: id={}, email={}, role={}", userEntity.getId(), userEntity.getEmail(),
                userEntity.getRole());
        return new AuthResponse(accessToken, refreshToken, "Bearer", securityProperties.getAccessTokenExpiry());
    }

    public AuthResponse refresh(RefreshRequest refreshRequest) {
        log.info("Token refresh attempt");

        final RefreshToken existing = refreshTokenRepository.findByToken(refreshRequest.refreshToken())
                                                            .orElseThrow(() -> {
                                                                log.warn("Refresh failed — token not found");
                                                                return new ResourceNotFoundException(
                                                                        "Refresh token not found");
                                                            });

        if (existing.isRevoked()) {
            log.warn("Refresh failed — token already revoked: id={}", existing.getId());
            throw new BadCredentialsException("Refresh token has been revoked");
        }

        if (existing.getExpiresAt()
                    .isBefore(OffsetDateTime.now())) {
            log.warn("Refresh failed — token expired: id={}", existing.getId());
            throw new BadCredentialsException("Refresh token has expired");
        }

        // Revoke old token
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        // Issue a new token pair
        final var user = existing.getUser();
        final var accessToken = jwtService.generateAccessToken(user);
        final var newRefreshToken = jwtService.generateRefreshToken(user);

        log.info("Token refreshed successfully: userId={}", user.getId());
        return new AuthResponse(accessToken, newRefreshToken, "Bearer", securityProperties.getAccessTokenExpiry());
    }

    public void logout(RefreshRequest refreshRequest) {
        log.info("Logout attempt");

        final RefreshToken existing = refreshTokenRepository.findByToken(refreshRequest.refreshToken())
                                                            .orElseThrow(() -> {
                                                                log.warn("Logout failed — token not found");
                                                                return new ResourceNotFoundException(
                                                                        "Refresh token not found");
                                                            });

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        log.info("Logout successful: userId={}", existing.getUser()
                                                         .getId());
    }
}
