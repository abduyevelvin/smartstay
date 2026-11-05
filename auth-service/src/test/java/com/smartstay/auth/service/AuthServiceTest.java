package com.smartstay.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.smartstay.auth.config.SecurityProperties;
import com.smartstay.auth.dto.AuthResponse;
import com.smartstay.auth.dto.LoginRequest;
import com.smartstay.auth.dto.RefreshRequest;
import com.smartstay.auth.dto.RegisterRequest;
import com.smartstay.auth.entity.RefreshToken;
import com.smartstay.auth.entity.UserEntity;
import com.smartstay.auth.enums.Role;
import com.smartstay.auth.exceptions.BadCredentialsException;
import com.smartstay.auth.exceptions.ResourceExistsException;
import com.smartstay.auth.exceptions.ResourceNotFoundException;
import com.smartstay.auth.repository.RefreshTokenRepository;
import com.smartstay.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecurityProperties securityProperties;

    @InjectMocks
    private AuthService authService;

    private UserEntity activeUser;
    private RefreshToken validRefreshToken;

    @BeforeEach
    void setup() {
        activeUser = new UserEntity();
        activeUser.setEmail("user@smartstay.com");
        activeUser.setPassword("$2a$hashed");
        activeUser.setRole(Role.GUEST);
        activeUser.setActive(true);

        validRefreshToken = new RefreshToken();
        validRefreshToken.setToken("valid-refresh-token");
        validRefreshToken.setUser(activeUser);
        validRefreshToken.setRevoked(false);
        validRefreshToken.setExpiresAt(OffsetDateTime.now().plusDays(7));

        when(securityProperties.getAccessTokenExpiry()).thenReturn(900L);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    void register_shouldSaveUserAndReturnTokens() {
        RegisterRequest request = new RegisterRequest(
                "new@smartstay.com", "password123", "John", "Doe", Role.GUEST);

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void register_shouldThrowWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "existing@smartstay.com", "password123", "John", "Doe", Role.GUEST);

        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResourceExistsException.class)
                .hasMessageContaining("existing@smartstay.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldHashPasswordBeforeSaving() {
        RegisterRequest request = new RegisterRequest(
                "new@smartstay.com", "plaintext", "John", "Doe", Role.GUEST);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("plaintext")).thenReturn("$2a$hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        verify(passwordEncoder).encode("plaintext");
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Test
    void login_shouldReturnTokensForValidCredentials() {
        LoginRequest request = new LoginRequest("user@smartstay.com", "password123");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", activeUser.getPassword())).thenReturn(true);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_shouldThrowWhenEmailNotFound() {
        LoginRequest request = new LoginRequest("unknown@smartstay.com", "password123");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_shouldThrowWhenPasswordIncorrect() {
        LoginRequest request = new LoginRequest("user@smartstay.com", "wrongpassword");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongpassword", activeUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_shouldThrowWhenUserIsInactive() {
        activeUser.setActive(false);
        LoginRequest request = new LoginRequest("user@smartstay.com", "password123");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        // Password check must NOT be called — inactive user short-circuits before it
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // refresh
    // -------------------------------------------------------------------------

    @Test
    void refresh_shouldIssueNewTokenPair() {
        RefreshRequest request = new RefreshRequest("valid-refresh-token");

        when(refreshTokenRepository.findByToken("valid-refresh-token"))
                .thenReturn(Optional.of(validRefreshToken));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void refresh_shouldRevokeOldToken() {
        RefreshRequest request = new RefreshRequest("valid-refresh-token");

        when(refreshTokenRepository.findByToken("valid-refresh-token"))
                .thenReturn(Optional.of(validRefreshToken));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.refresh(request);

        assertThat(validRefreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(validRefreshToken);
    }

    @Test
    void refresh_shouldThrowWhenTokenNotFound() {
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("ghost-token")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refresh_shouldThrowWhenTokenRevoked() {
        validRefreshToken.setRevoked(true);
        when(refreshTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.of(validRefreshToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("revoked-token")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void refresh_shouldThrowWhenTokenExpired() {
        validRefreshToken.setExpiresAt(OffsetDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.of(validRefreshToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("expired-token")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("expired");
    }

    // -------------------------------------------------------------------------
    // logout
    // -------------------------------------------------------------------------

    @Test
    void logout_shouldRevokeToken() {
        when(refreshTokenRepository.findByToken("valid-refresh-token"))
                .thenReturn(Optional.of(validRefreshToken));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.logout(new RefreshRequest("valid-refresh-token"));

        assertThat(validRefreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(validRefreshToken);
    }

    @Test
    void logout_shouldThrowWhenTokenNotFound() {
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout(new RefreshRequest("ghost-token")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
