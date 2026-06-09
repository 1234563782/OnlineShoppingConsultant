package com.onlineshopping.orchestrator.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onlineshopping.orchestrator.auth.dto.AuthUserResponse;
import com.onlineshopping.orchestrator.auth.dto.LoginRequest;
import com.onlineshopping.orchestrator.auth.dto.RegisterRequest;
import com.onlineshopping.orchestrator.auth.mapper.UserAccountMapper;
import com.onlineshopping.orchestrator.auth.model.UserAccountEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionStore authSessionStore;
    private final AuthCookieSupport authCookieSupport;

    public AuthService(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            AuthSessionStore authSessionStore,
            AuthCookieSupport authCookieSupport
    ) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.authSessionStore = authSessionStore;
        this.authCookieSupport = authCookieSupport;
    }

    public AuthUserResponse register(RegisterRequest request, HttpServletResponse response) {
        String username = normalizeUsername(request.getUsername());
        Long existing = userAccountMapper.selectCount(
                Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getUsername, username));
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
        }

        Instant now = Instant.now();
        UserAccountEntity account = new UserAccountEntity();
        account.setId(UUID.randomUUID().toString().replace("-", ""));
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        String displayName = request.getDisplayName();
        account.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName.trim());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        userAccountMapper.insert(account);

        issueSession(account, response);
        return toResponse(account);
    }

    public AuthUserResponse login(LoginRequest request, HttpServletResponse response) {
        String username = normalizeUsername(request.getUsername());
        UserAccountEntity account = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getUsername, username));
        if (account == null || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid username or password");
        }
        issueSession(account, response);
        return toResponse(account);
    }

    public AuthUserResponse me(HttpServletRequest request) {
        AuthUserPrincipal principal = AuthSupport.currentUser();
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not authenticated");
        }
        return new AuthUserResponse(principal.getUserId(), principal.getUsername(), principal.getDisplayName());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = authCookieSupport.readToken(request);
        authSessionStore.delete(token);
        authCookieSupport.clearToken(response);
    }

    private void issueSession(UserAccountEntity account, HttpServletResponse response) {
        AuthSession session = new AuthSession(account.getId(), account.getUsername(), account.getDisplayName());
        String token = authSessionStore.createSession(session);
        authCookieSupport.writeToken(response, token, authSessionStore.cookieMaxAge());
    }

    private AuthUserResponse toResponse(UserAccountEntity account) {
        return new AuthUserResponse(account.getId(), account.getUsername(), account.getDisplayName());
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
