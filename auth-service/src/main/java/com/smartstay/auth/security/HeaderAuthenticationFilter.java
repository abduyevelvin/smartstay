package com.smartstay.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Trusts X-User-Id / X-User-Role headers forwarded by the API Gateway
 * (which already validated the JWT). Populates the SecurityContext so that
 *
 * @PreAuthorize on admin controller methods works correctly.
 */
@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {

        final String userId = request.getHeader(HEADER_USER_ID);
        final String role = request.getHeader(HEADER_USER_ROLE);

        if (userId != null && role != null) {
            // Spring's hasRole('X') checks for authority 'ROLE_X'
            final var authority = new SimpleGrantedAuthority("ROLE_" + role);
            final var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of(authority));
            SecurityContextHolder.getContext()
                                 .setAuthentication(auth);
            log.debug("Security context set — userId={}, role={}", userId, role);
        }

        filterChain.doFilter(request, response);
    }
}
