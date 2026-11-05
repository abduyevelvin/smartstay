package com.smartstay.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private String privateKeyPath;
    private String publicKeyPath;
    private long accessTokenExpiry;
    private long refreshTokenExpiry;
}
