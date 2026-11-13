package com.smartstay.gateway.filter;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
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

        // Always strip X-Internal-Secret before forwarding to any downstream service.
        // This prevents an external caller from spoofing the header and bypassing
        // the internal-secret check on hotel-service's /rating endpoint.
        final var strippedExchange = exchange.mutate()
                                             .request(exchange.getRequest()
                                                              .mutate()
                                                              .headers(h -> h.remove("X-Internal-Secret"))
                                                              .build())
                                             .build();

        // Always let CORS preflight through — browsers send OPTIONS without an
        // Authorization header, so blocking it here would break every cross-origin
        // request before it even starts. The actual CORS policy is enforced by
        // CorsWebFilter; the JWT check is irrelevant for OPTIONS.
        if (HttpMethod.OPTIONS.equals(strippedExchange.getRequest().getMethod())) {
            return chain.filter(strippedExchange);
        }

        final var requestPath = strippedExchange.getRequest()
                                                .getPath()
                                                .value();

        for (String publicPath : securityProperties.getPublicPaths()) {
            if (antPathMatcher.match(publicPath, requestPath)) {
                log.debug("Public path matched [{}] — skipping JWT validation", requestPath);
                return chain.filter(strippedExchange);
            }
        }

        // GET-only public paths: no JWT required for read requests, but write
        // methods (POST/PUT/DELETE) on the same paths still need a valid token.
        if (HttpMethod.GET.equals(strippedExchange.getRequest().getMethod())) {
            for (String publicGetPath : securityProperties.getPublicGetPaths()) {
                if (antPathMatcher.match(publicGetPath, requestPath)) {
                    log.debug("Public GET path matched [{}] — skipping JWT validation", requestPath);
                    return chain.filter(strippedExchange);
                }
            }
        }

        final var authHeader = strippedExchange.getRequest()
                                               .getHeaders()
                                               .getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            final var token = authHeader.substring(7);
            try {
                final var claims = jwtService.validateToken(token);
                final var email = claims.getSubject();
                final var userId = claims.get("userId", String.class);
                final var role = claims.get("role", String.class);
                final var name = claims.get("name", String.class);

                log.debug("JWT validated for user [{}] with role [{}] on path [{}]", userId, role, requestPath);
                final var mutatedRequest = strippedExchange.getRequest()
                                                           .mutate()
                                                           .header("X-User-Email", email)
                                                           .header("X-User-Id", userId)
                                                           .header("X-User-Role", role)
                                                           .header("X-User-Name", name != null ? name : email)
                                                           .build();
                return chain.filter(strippedExchange.mutate()
                                                    .request(mutatedRequest)
                                                    .build());
            } catch (Exception e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
                return unauthorized(strippedExchange);
            }
        }

        return unauthorized(strippedExchange);
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
