package com.smartstay.booking.utils;

import org.springframework.security.core.context.SecurityContextHolder;

import com.smartstay.booking.security.UserPrincipal;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecurityUtils {

    public static UserPrincipal getCurrentUser() {
        log.debug("Getting current user");

        var auth = SecurityContextHolder.getContext()
                                        .getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return principal;
    }

}
