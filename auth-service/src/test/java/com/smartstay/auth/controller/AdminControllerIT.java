package com.smartstay.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.smartstay.auth.dto.AssignRoleRequest;
import com.smartstay.auth.dto.RegisterRequest;
import com.smartstay.auth.dto.UserSummaryResponse;
import com.smartstay.auth.entity.UserEntity;
import com.smartstay.auth.enums.Role;
import com.smartstay.auth.repository.RefreshTokenRepository;
import com.smartstay.auth.repository.UserRepository;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16").withDatabaseName("auth_db_test")
                                                                                     .withUsername("test")
                                                                                     .withPassword("test");

    static Path privateKeyPath;
    static Path publicKeyPath;

    @BeforeAll
    static void generateKeys() throws Exception {
        Path tempDir = Files.createTempDirectory("test-certs-admin");
        privateKeyPath = tempDir.resolve("private.pem");
        publicKeyPath = tempDir.resolve("public.pem");

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        String privB64 = Base64.getMimeEncoder(64, "\n".getBytes())
                               .encodeToString(kp.getPrivate()
                                                 .getEncoded());
        Files.writeString(privateKeyPath, "-----BEGIN PRIVATE KEY-----\n" + privB64 + "\n-----END PRIVATE KEY-----\n");

        String pubB64 = Base64.getMimeEncoder(64, "\n".getBytes())
                              .encodeToString(kp.getPublic()
                                                .getEncoded());
        Files.writeString(publicKeyPath, "-----BEGIN PUBLIC KEY-----\n" + pubB64 + "\n-----END PUBLIC KEY-----\n");
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.private-key-path", () -> "file:" + privateKeyPath.toAbsolutePath());
        registry.add("app.security.public-key-path", () -> "file:" + publicKeyPath.toAbsolutePath());
        // Disable admin seeder to keep test DB clean; each test creates its own data
        registry.add("app.admin.email", () -> "seeded-admin@test.com");
        registry.add("app.admin.password", () -> "Seeded@1234");
    }

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RefreshTokenRepository refreshTokenRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDb() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/v1/admin/users
    // ─────────────────────────────────────────────────────────────

    @Test
    void getUsers_shouldReturn200WithAdminRole() {
        UserEntity admin = createUser(Role.ADMIN, "admin@test.com");
        createUser(Role.GUEST, "guest@test.com");
        createUser(Role.HOTEL_MANAGER, "manager@test.com");

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/users", HttpMethod.GET,
                adminHeaders(admin), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUsers_shouldReturn403WithoutAdminRole() {
        UserEntity guest = createUser(Role.GUEST, "nonadmin@test.com");

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/users", HttpMethod.GET,
                userHeaders(guest, "GUEST"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getUsers_shouldReturn403WithoutAnyHeaders() {
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/users", HttpMethod.GET,
                new HttpEntity<>(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getUsers_shouldReturnAllUsers() {
        UserEntity admin = createUser(Role.ADMIN, "a@test.com");
        createUser(Role.GUEST, "b@test.com");
        createUser(Role.HOTEL_MANAGER, "c@test.com");

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/users", HttpMethod.GET,
                adminHeaders(admin), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Body contains all 3 emails
        assertThat(response.getBody()).contains("a@test.com", "b@test.com", "c@test.com");
    }

    // ─────────────────────────────────────────────────────────────
    // PATCH /api/v1/admin/users/{id}/role
    // ─────────────────────────────────────────────────────────────

    @Test
    void assignRole_shouldPromoteGuestToHotelManager() {
        UserEntity admin = createUser(Role.ADMIN, "admin2@test.com");
        UserEntity guest = createUser(Role.GUEST, "guest2@test.com");

        ResponseEntity<UserSummaryResponse> response = restTemplate.exchange(
                "/api/v1/admin/users/" + guest.getId() + "/role", HttpMethod.PATCH,
                new HttpEntity<>(new AssignRoleRequest(Role.HOTEL_MANAGER), adminHeaders(admin).getHeaders()),
                UserSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()
                           .role()).isEqualTo(Role.HOTEL_MANAGER);

        // Verify persisted in DB
        assertThat(userRepository.findById(guest.getId())
                                 .get()
                                 .getRole()).isEqualTo(Role.HOTEL_MANAGER);
    }

    @Test
    void assignRole_shouldDemoteHotelManagerToGuest() {
        UserEntity admin = createUser(Role.ADMIN, "admin3@test.com");
        UserEntity manager = createUser(Role.HOTEL_MANAGER, "manager3@test.com");

        ResponseEntity<UserSummaryResponse> response = restTemplate.exchange(
                "/api/v1/admin/users/" + manager.getId() + "/role", HttpMethod.PATCH,
                new HttpEntity<>(new AssignRoleRequest(Role.GUEST), adminHeaders(admin).getHeaders()),
                UserSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()
                           .role()).isEqualTo(Role.GUEST);
    }

    @Test
    void assignRole_shouldReturn403WhenAssigningAdminRole() {
        UserEntity admin = createUser(Role.ADMIN, "admin4@test.com");
        UserEntity guest = createUser(Role.GUEST, "guest4@test.com");

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/users/" + guest.getId() + "/role",
                HttpMethod.PATCH, new HttpEntity<>(new AssignRoleRequest(Role.ADMIN), adminHeaders(admin).getHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void assignRole_shouldReturn403WhenChangingAnotherAdminRole() {
        UserEntity admin1 = createUser(Role.ADMIN, "admin5@test.com");
        UserEntity admin2 = createUser(Role.ADMIN, "admin6@test.com");

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/users/" + admin2.getId() + "/role",
                HttpMethod.PATCH,
                new HttpEntity<>(new AssignRoleRequest(Role.GUEST), adminHeaders(admin1).getHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void assignRole_shouldReturn404WhenUserNotFound() {
        UserEntity admin = createUser(Role.ADMIN, "admin7@test.com");
        UUID nonExistentId = UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/users/" + nonExistentId + "/role",
                HttpMethod.PATCH,
                new HttpEntity<>(new AssignRoleRequest(Role.HOTEL_MANAGER), adminHeaders(admin).getHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignRole_shouldReturn403WithoutAdminRole() {
        UserEntity guest = createUser(Role.GUEST, "guest8@test.com");
        UserEntity target = createUser(Role.GUEST, "target8@test.com");

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/admin/users/" + target.getId() + "/role",
                HttpMethod.PATCH,
                new HttpEntity<>(new AssignRoleRequest(Role.HOTEL_MANAGER), userHeaders(guest, "GUEST").getHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Create and persist a user with the given role.
     */
    private UserEntity createUser(Role role,
                                  String email) {
        UserEntity u = new UserEntity();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Test@1234"));
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(role);
        u.setActive(true);
        return userRepository.save(u);
    }

    /**
     * HttpEntity with ADMIN headers (simulates gateway forwarding).
     */
    private HttpEntity<Void> adminHeaders(UserEntity admin) {
        return userHeaders(admin, "ADMIN");
    }

    /**
     * HttpEntity with arbitrary role headers (simulates gateway forwarding).
     */
    private HttpEntity<Void> userHeaders(UserEntity user,
                                         String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", user.getId()
                                     .toString());
        headers.set("X-User-Role", role);
        return new HttpEntity<>(null, headers);
    }

    /**
     * Register via the public API and return the response.
     */
    @SuppressWarnings("unused")
    private void registerGuest(String email) {
        restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, "Password@1", "T", "U", Role.GUEST), Void.class);
    }
}
