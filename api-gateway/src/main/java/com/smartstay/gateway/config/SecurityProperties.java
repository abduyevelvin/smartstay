package com.smartstay.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private String publicKeyPath;
    private List<String> publicPaths;

    /**
     * Paths that are publicly accessible for HTTP GET requests only.
     * All other methods (POST, PUT, DELETE …) on these paths still require a valid JWT.
     */
    private List<String> publicGetPaths = List.of();
}
