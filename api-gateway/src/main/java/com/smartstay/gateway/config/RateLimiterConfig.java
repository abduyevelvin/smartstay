package com.smartstay.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    // For public routes - key by IP address
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            final var address = exchange.getRequest()
                                        .getRemoteAddress();
            final var ip = address != null ? address.getAddress()
                                                    .getHostAddress() : "unknown";

            return Mono.just(ip);
        };
    }

    // For authenticated routes — key by userId (set by JwtAuthenticationFilter)
    // Falls back to IP if the header is missing
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            final var userId = exchange.getRequest()
                                       .getHeaders()
                                       .getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            // Fallback to IP for unauthenticated requests
            final var address = exchange.getRequest()
                                        .getRemoteAddress();
            final var ip = address != null ? address.getAddress()
                                                    .getHostAddress() : "unknown";
            return Mono.just("anon-" + ip);
        };
    }
}
