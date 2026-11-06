package com.smartstay.booking.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

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

@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {

        final String userId = request.getHeader(HEADER_USER_ID);
        final String email = request.getHeader(HEADER_USER_EMAIL);
        final String role = request.getHeader(HEADER_USER_ROLE);

        if (userId != null && email != null && role != null) {
            // Spring's hasRole('X') checks for authority 'ROLE_X'
            final var authority = new SimpleGrantedAuthority("ROLE_" + role);
            final var principal = new UserPrincipal(UUID.fromString(userId), email);
            final var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));

            SecurityContextHolder.getContext()
                                 .setAuthentication(auth);
            log.debug("Security context set — userId={}, role={}", userId, role);
        }

        filterChain.doFilter(request, response);
    }
}
