package com.smartstay.gateway.service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.smartstay.gateway.config.SecurityProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final SecurityProperties securityProperties;
    private final ResourceLoader resourceLoader;

    private PublicKey publicKey;

    // -------------------------------------------------------------------------
    // Load public key from resources
    // -------------------------------------------------------------------------

    @PostConstruct
    public void loadPublicKey() {
        try {
            publicKey = loadPublicKey(securityProperties.getPublicKeyPath());
            log.info("Public key loaded successfully");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load public key", e);
        }
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
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            throw new IllegalStateException("Invalid token", e);
        }
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
