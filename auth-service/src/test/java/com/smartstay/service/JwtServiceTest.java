package com.smartstay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import com.smartstay.config.SecurityProperties;
import com.smartstay.entity.RefreshToken;
import com.smartstay.entity.UserEntity;
import com.smartstay.enums.Role;
import com.smartstay.repository.RefreshTokenRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtServiceTest {

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ResourceLoader resourceLoader;

    @InjectMocks
    private JwtService jwtService;

    private static String privatePem;
    private static String publicPem;

    private UserEntity testUser;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        String privateB64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded());
        privatePem = "-----BEGIN PRIVATE KEY-----\n" + privateB64 + "\n-----END PRIVATE KEY-----\n";

        String publicB64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPublic().getEncoded());
        publicPem = "-----BEGIN PUBLIC KEY-----\n" + publicB64 + "\n-----END PUBLIC KEY-----\n";
    }

    @BeforeEach
    void setup() throws Exception {
        when(securityProperties.getPrivateKeyPath()).thenReturn("classpath:certs/private.pem");
        when(securityProperties.getPublicKeyPath()).thenReturn("classpath:certs/public.pem");
        when(securityProperties.getAccessTokenExpiry()).thenReturn(900L);
        when(securityProperties.getRefreshTokenExpiry()).thenReturn(604800L);

        when(resourceLoader.getResource(anyString()))
                .thenAnswer(inv -> {
                    String path = inv.getArgument(0, String.class);
                    return path.contains("private")
                            ? new ByteArrayResource(privatePem.getBytes())
                            : new ByteArrayResource(publicPem.getBytes());
                });

        // @PostConstruct doesn't run in Mockito tests — call manually
        jwtService.loadKeys();

        testUser = new UserEntity();
        testUser.setEmail("test@smartstay.com");
        testUser.setRole(Role.GUEST);
        // Set id via reflection since @GeneratedValue doesn't run outside JPA
        java.lang.reflect.Field idField = testUser.getClass().getSuperclass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(testUser, UUID.randomUUID());
    }

    // -------------------------------------------------------------------------
    // generateAccessToken
    // -------------------------------------------------------------------------

    @Test
    void generateAccessToken_shouldReturnNonBlankJwt() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    void generateAccessToken_shouldContainCorrectClaims() {
        String token = jwtService.generateAccessToken(testUser);
        Claims claims = jwtService.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo(testUser.getEmail());
        assertThat(claims.get("userId", String.class)).isEqualTo(testUser.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo(Role.GUEST.name());
    }

    // -------------------------------------------------------------------------
    // generateRefreshToken
    // -------------------------------------------------------------------------

    @Test
    void generateRefreshToken_shouldReturnNonBlankToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String token = jwtService.generateRefreshToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    void generateRefreshToken_shouldSaveToRepository() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        jwtService.generateRefreshToken(testUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // -------------------------------------------------------------------------
    // validateToken
    // -------------------------------------------------------------------------

    @Test
    void validateToken_shouldReturnClaimsForValidToken() {
        String token = jwtService.generateAccessToken(testUser);
        Claims claims = jwtService.validateToken(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(testUser.getEmail());
    }

    @Test
    void validateToken_shouldThrowForTamperedToken() {
        String token = jwtService.generateAccessToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateToken_shouldThrowForExpiredToken() {
        // Override expiry to -1 (already expired)
        when(securityProperties.getAccessTokenExpiry()).thenReturn(-1L);

        String token = jwtService.generateAccessToken(testUser);

        assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    // -------------------------------------------------------------------------
    // Extract helpers
    // -------------------------------------------------------------------------

    @Test
    void extractEmail_shouldReturnEmail() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.extractEmail(token)).isEqualTo(testUser.getEmail());
    }

    @Test
    void extractUserId_shouldReturnUserId() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.extractUserId(token)).isEqualTo(testUser.getId().toString());
    }

    @Test
    void extractRole_shouldReturnRole() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.extractRole(token)).isEqualTo(Role.GUEST.name());
    }
}
