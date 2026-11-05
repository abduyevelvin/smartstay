package com.smartstay.gateway.filter;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import com.smartstay.gateway.config.SecurityProperties;
import com.smartstay.gateway.service.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange,
                             final GatewayFilterChain chain) {

        final var requestPath = exchange.getRequest()
                                        .getPath()
                                        .value();

        for (String publicPath : securityProperties.getPublicPaths()) {
            if (antPathMatcher.match(publicPath, requestPath)) {
                log.debug("Public path matched [{}] — skipping JWT validation", requestPath);
                return chain.filter(exchange);
            }
        }

        final var authHeader = exchange.getRequest()
                                       .getHeaders()
                                       .getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            final var token = authHeader.substring(7);
            try {
                final var claims = jwtService.validateToken(token);
                final var email = claims.getSubject();
                final var userId = claims.get("userId", String.class);
                final var role = claims.get("role", String.class);

                log.debug("JWT validated for user [{}] with role [{}] on path [{}]", userId, role, requestPath);
                final var mutatedRequest = exchange.getRequest()
                                                   .mutate()
                                                   .header("X-User-Email", email)
                                                   .header("X-User-Id", userId)
                                                   .header("X-User-Role", role)
                                                   .build();
                return chain.filter(exchange.mutate()
                                            .request(mutatedRequest)
                                            .build());
            } catch (Exception e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
                return unauthorized(exchange);
            }
        }

        return unauthorized(exchange);
    }

    private Mono<Void> unauthorized(final ServerWebExchange exchange) {
        exchange.getResponse()
                .setStatusCode(UNAUTHORIZED);

        return exchange.getResponse()
                       .setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
