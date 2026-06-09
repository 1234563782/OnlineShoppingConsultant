package com.onlineshopping.orchestrator.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthCookieFilter extends OncePerRequestFilter {

    private final AuthCookieSupport authCookieSupport;
    private final AuthSessionStore authSessionStore;

    public AuthCookieFilter(AuthCookieSupport authCookieSupport, AuthSessionStore authSessionStore) {
        this.authCookieSupport = authCookieSupport;
        this.authSessionStore = authSessionStore;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = authCookieSupport.readToken(request);
            if (token != null) {
                authSessionStore.find(token).ifPresent(session -> {
                    AuthUserPrincipal principal = new AuthUserPrincipal(
                            session.userId(),
                            session.username(),
                            session.displayName()
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
