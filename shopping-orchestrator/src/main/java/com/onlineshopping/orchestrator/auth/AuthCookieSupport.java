package com.onlineshopping.orchestrator.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieSupport {

    private final AuthProperties authProperties;

    public AuthCookieSupport(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (authProperties.getCookieName().equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value.trim();
            }
        }
        return null;
    }

    public void writeToken(HttpServletResponse response, String token, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(authProperties.getCookieName(), token)
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .path("/")
                .sameSite(authProperties.getCookieSameSite())
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearToken(HttpServletResponse response) {
        writeToken(response, "", Duration.ZERO);
    }
}
