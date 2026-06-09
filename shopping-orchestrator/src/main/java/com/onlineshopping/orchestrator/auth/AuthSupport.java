package com.onlineshopping.orchestrator.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthSupport {

    private AuthSupport() {
    }

    public static AuthUserPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public static String requireUserId() {
        AuthUserPrincipal principal = currentUser();
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "not authenticated"
            );
        }
        return principal.getUserId();
    }
}
