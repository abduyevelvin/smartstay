package com.smartstay.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.smartstay.auth.dto.AuthResponse;
import com.smartstay.auth.dto.LoginRequest;
import com.smartstay.auth.dto.RefreshRequest;
import com.smartstay.auth.dto.RegisterRequest;
import com.smartstay.auth.enums.Role;
import com.smartstay.auth.repository.RefreshTokenRepository;
import com.smartstay.auth.repository.UserRepository;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("auth_db_test")
            .withUsername("test")
            .withPassword("test");

    static Path privateKeyPath;
    static Path publicKeyPath;

    @BeforeAll
    static void generateKeys() throws Exception {
        Path tempDir = Files.createTempDirectory("test-certs");
        privateKeyPath = tempDir.resolve("private.pem");
        publicKeyPath = tempDir.resolve("public.pem");

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        String privateB64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded());
        Files.writeString(privateKeyPath,
                "-----BEGIN PRIVATE KEY-----\n" + privateB64 + "\n-----END PRIVATE KEY-----\n");

        String publicB64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPublic().getEncoded());
        Files.writeString(publicKeyPath,
                "-----BEGIN PUBLIC KEY-----\n" + publicB64 + "\n-----END PUBLIC KEY-----\n");
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.private-key-path", () -> "file:" + privateKeyPath.toAbsolutePath());
        registry.add("app.security.public-key-path", () -> "file:" + publicKeyPath.toAbsolutePath());
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/register
    // -------------------------------------------------------------------------

    @Test
    void register_shouldReturn201WithTokens() {
        RegisterRequest request = new RegisterRequest(
                "john@smartstay.com", "password123", "John", "Doe", Role.GUEST);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void register_shouldReturn409WhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "duplicate@smartstay.com", "password123", "John", "Doe", Role.GUEST);

        restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);
        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/v1/auth/register", request, String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_shouldReturn400WhenEmailInvalid() {
        RegisterRequest request = new RegisterRequest(
                "not-an-email", "password123", "John", "Doe", Role.GUEST);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_shouldReturn400WhenPasswordTooShort() {
        RegisterRequest request = new RegisterRequest(
                "john@smartstay.com", "short", "John", "Doe", Role.GUEST);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/login
    // -------------------------------------------------------------------------

    @Test
    void login_shouldReturn200WithTokens() {
        registerUser("login@smartstay.com", "password123");

        LoginRequest request = new LoginRequest("login@smartstay.com", "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void login_shouldReturn401WhenWrongPassword() {
        registerUser("user@smartstay.com", "password123");

        LoginRequest request = new LoginRequest("user@smartstay.com", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_shouldReturn401WhenEmailNotFound() {
        LoginRequest request = new LoginRequest("ghost@smartstay.com", "password123");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/refresh
    // -------------------------------------------------------------------------

    @Test
    void refresh_shouldReturn200WithNewTokens() {
        AuthResponse registered = registerUser("refresh@smartstay.com", "password123");

        RefreshRequest request = new RefreshRequest(registered.refreshToken());
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/refresh", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
        // New refresh token must differ from the old one
        assertThat(response.getBody().refreshToken()).isNotEqualTo(registered.refreshToken());
    }

    @Test
    void refresh_shouldReturn401WhenTokenUsedTwice() {
        AuthResponse registered = registerUser("reuse@smartstay.com", "password123");
        RefreshRequest request = new RefreshRequest(registered.refreshToken());

        restTemplate.postForEntity("/api/v1/auth/refresh", request, AuthResponse.class);
        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/v1/auth/refresh", request, String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/logout
    // -------------------------------------------------------------------------

    @Test
    void logout_shouldReturn204() {
        AuthResponse registered = registerUser("logout@smartstay.com", "password123");

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/auth/logout", new RefreshRequest(registered.refreshToken()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logout_shouldInvalidateToken() {
        AuthResponse registered = registerUser("invalidate@smartstay.com", "password123");
        RefreshRequest request = new RefreshRequest(registered.refreshToken());

        restTemplate.postForEntity("/api/v1/auth/logout", request, Void.class);

        // Trying to refresh with the logged-out token must fail
        ResponseEntity<String> refreshAttempt = restTemplate.postForEntity(
                "/api/v1/auth/refresh", request, String.class);
        assertThat(refreshAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private AuthResponse registerUser(String email, String password) {
        RegisterRequest request = new RegisterRequest(email, password, "Test", "User", Role.GUEST);
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
