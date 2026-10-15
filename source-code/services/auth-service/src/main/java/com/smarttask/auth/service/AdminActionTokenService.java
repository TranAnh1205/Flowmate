package com.smarttask.auth.service;

import com.smarttask.auth.domain.AdminActionToken;
import com.smarttask.auth.exception.ApiException;
import com.smarttask.auth.repository.AdminActionTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdminActionTokenService {
    private final AdminActionTokenRepository tokens;
    private final TokenHashService hashing;
    private final Duration ttl;

    public AdminActionTokenService(
            AdminActionTokenRepository tokens,
            TokenHashService hashing,
            @Value("${app.security.admin-action-token-ttl:PT5M}") Duration ttl) {
        this.tokens = tokens;
        this.hashing = hashing;
        this.ttl = ttl;
    }

    @Transactional
    public IssuedActionToken issue(UUID userId) {
        tokens.revokeAllByUserId(userId, Instant.now());
        String raw = UUID.randomUUID() + "." + UUID.randomUUID() + "." + UUID.randomUUID();
        AdminActionToken token = new AdminActionToken();
        token.setUserId(userId);
        token.setTokenHash(hashing.hash(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        tokens.save(token);
        return new IssuedActionToken(raw, ttl.toSeconds());
    }

    @Transactional(readOnly = true)
    public void validate(UUID userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_REAUTH_REQUIRED",
                    "Vui lòng xác thực lại trước khi thực hiện thao tác nhạy cảm");
        }
        AdminActionToken token = tokens.findByTokenHash(hashing.hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "INVALID_ADMIN_ACTION_TOKEN", "Phiên xác thực quản trị không hợp lệ"));
        if (!token.getUserId().equals(userId)
                || token.getRevokedAt() != null
                || !token.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "INVALID_ADMIN_ACTION_TOKEN", "Phiên xác thực quản trị đã hết hạn hoặc bị thu hồi");
        }
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAllByUserId(userId, Instant.now());
    }

    public record IssuedActionToken(String token, long expiresIn) {}
}
