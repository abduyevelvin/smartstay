package com.smartstay.auth.service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.smartstay.auth.config.SecurityProperties;
import com.smartstay.auth.entity.RefreshToken;
import com.smartstay.auth.entity.UserEntity;
import com.smartstay.auth.repository.RefreshTokenRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final SecurityProperties securityProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ResourceLoader resourceLoader;

    private PrivateKey privateKey;
    private PublicKey publicKey;


    // -------------------------------------------------------------------------
    // Load keys from resources
    // -------------------------------------------------------------------------

    @PostConstruct
    public void loadKeys() {
        try {
            privateKey = loadPrivateKey(securityProperties.getPrivateKeyPath());
            publicKey = loadPublicKey(securityProperties.getPublicKeyPath());
            log.info("RSA keys loaded successfully");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA keys", e);
        }
    }

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    public String generateAccessToken(UserEntity user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId()
                                 .toString());
        claims.put("role", user.getRole()
                               .name());

        long expiryMillis = securityProperties.getAccessTokenExpiry() * 1000L;

        return Jwts.builder()
                   .claims(claims)
                   .subject(user.getEmail())
                   .issuedAt(new Date())
                   .expiration(new Date(System.currentTimeMillis() + expiryMillis))
                   .signWith(privateKey)
                   .compact();
    }

    public String generateRefreshToken(UserEntity user) {
        String tokenValue = UUID.randomUUID()
                                .toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(tokenValue);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(OffsetDateTime.now()
                                                .plusSeconds(securityProperties.getRefreshTokenExpiry()));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    // -------------------------------------------------------------------------
    // Token validation
    // -------------------------------------------------------------------------

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                       .verifyWith(publicKey)
                       .build()
                       .parseSignedClaims(token)
                       .getPayload();
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            throw new JwtException("Invalid or expired token", e);
        }
    }

    public String extractEmail(String token) {
        return validateToken(token).getSubject();
    }

    public String extractUserId(String token) {
        return validateToken(token).get("userId", String.class);
    }

    public String extractRole(String token) {
        return validateToken(token).get("role", String.class);
    }

    // -------------------------------------------------------------------------
    // Key loading helpers
    // -------------------------------------------------------------------------

    private PrivateKey loadPrivateKey(String resourcePath) throws Exception {
        String pem = readPemContent(resourcePath);
        String cleaned = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder()
                               .decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA")
                         .generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String resourcePath) throws Exception {
        String pem = readPemContent(resourcePath);
        String cleaned = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                            .replace("-----END PUBLIC KEY-----", "")
                            .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder()
                               .decode(cleaned);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA")
                         .generatePublic(spec);
    }

    private String readPemContent(String resourcePath) throws Exception {
        var resource = resourceLoader.getResource(resourcePath);
        return new String(resource.getInputStream()
                                  .readAllBytes());
    }
}
