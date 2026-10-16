package com.smarttask.auth.service;

import com.smarttask.auth.domain.*;
import com.smarttask.auth.dto.AdminDtos.RequestMetadata;
import com.smarttask.auth.dto.AuthDtos.*;
import com.smarttask.auth.exception.ApiException;
import com.smarttask.auth.repository.*;
import com.smarttask.auth.service.AdminActionTokenService.IssuedActionToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final UserSettingsRepository settings;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashService hashing;
    private final AdminActionTokenService adminActionTokens;
    private final AdminAuditService audit;
    private final Duration refreshTokenTtl;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       UserSettingsRepository settings, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TokenHashService hashing,
                       AdminActionTokenService adminActionTokens, AdminAuditService audit,
                       @Value("${app.security.refresh-token-ttl:P30D}") Duration refreshTokenTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.settings = settings;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hashing = hashing;
        this.adminActionTokens = adminActionTokens;
        this.audit = audit;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email đã được sử dụng");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTimezone("Asia/Bangkok");
        user.setRole(UserRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user = users.save(user);

        UserSettings userSettings = new UserSettings();
        userSettings.setUserId(user.getId());
        userSettings.setWorkStartTime(LocalTime.of(8, 0));
        userSettings.setWorkEndTime(LocalTime.of(17, 30));
        userSettings.setDefaultReminderMinutes(30);
        userSettings.setWeekStartDay(1);
        userSettings.setLanguage("vi");
        userSettings.setTheme("system");
        settings.save(userSettings);
        return issueTokens(user, RequestMetadata.empty());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, RequestMetadata.empty());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, RequestMetadata metadata) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(this::invalidCredentials);
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_UNAVAILABLE",
                    "Tài khoản đã bị khóa hoặc tạm ngừng");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (user.getRole() == UserRole.ADMIN) {
                audit.record(user.getId(), UserRole.ADMIN, "ADMIN_LOGIN_FAILED", user.getId(),
                        "Mật khẩu không chính xác", null, null, "FAILED", metadata, null);
            }
            throw invalidCredentials();
        }
        user.setLastLoginAt(Instant.now());
        if (user.getRole() == UserRole.ADMIN) {
            audit.record(user.getId(), UserRole.ADMIN, "ADMIN_LOGIN_SUCCESS", user.getId(),
                    "Đăng nhập quản trị", null, null, "SUCCESS", metadata, null);
        }
        return issueTokens(user, metadata);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        return refresh(request, RequestMetadata.empty());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request, RequestMetadata metadata) {
        RefreshToken token = refreshTokens.findByTokenHash(hashing.hash(request.refreshToken()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh Token không hợp lệ"));
        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh Token đã hết hạn hoặc bị thu hồi");
        }
        token.setRevokedAt(Instant.now());
        token.setRevokeReason("Refresh token rotation");
        token.setLastUsedAt(Instant.now());
        User user = users.findById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Tài khoản không khả dụng"));
        return issueTokens(user, metadata);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokens.findByTokenHash(hashing.hash(request.refreshToken())).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            token.setRevokeReason("Người dùng đăng xuất");
        });
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeActiveTokensByUserId(userId, Instant.now(), "Đổi mật khẩu");
        adminActionTokens.revokeAll(userId);
    }

    @Transactional
    public ReauthenticateResponse reauthenticate(UUID userId, ReauthenticateRequest request) {
        User user = getUser(userId);
        if (user.getRole() != UserRole.ADMIN || !user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ACCESS_DENIED",
                    "Tài khoản không có quyền quản trị");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        IssuedActionToken value = adminActionTokens.issue(userId);
        return new ReauthenticateResponse(value.token(), value.expiresIn());
    }

    @Transactional(readOnly = true)
    public UserResponse profile(UUID userId) { return toUserResponse(getUser(userId)); }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.setFullName(request.fullName().trim());
        user.setAvatarUrl(request.avatarUrl());
        try { ZoneId.of(request.timezone()); } catch (DateTimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Múi giờ không hợp lệ");
        }
        user.setTimezone(request.timezone());
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings(UUID userId) { return toSettings(getUserSettings(userId)); }

    @Transactional
    public SettingsResponse updateSettings(UUID userId, UpdateSettingsRequest request) {
        if (!request.workStartTime().isBefore(request.workEndTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Giờ bắt đầu phải trước giờ kết thúc");
        }
        UserSettings value = getUserSettings(userId);
        value.setWorkStartTime(request.workStartTime());
        value.setWorkEndTime(request.workEndTime());
        value.setDefaultReminderMinutes(request.defaultReminderMinutes());
        value.setWeekStartDay(request.weekStartDay());
        value.setLanguage(request.language());
        value.setTheme(request.theme());
        return toSettings(value);
    }

    private AuthResponse issueTokens(User user, RequestMetadata metadata) {
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setTokenHash(hashing.hash(rawRefresh));
        stored.setExpiresAt(Instant.now().plus(refreshTokenTtl));
        stored.setDeviceName(metadata.deviceName());
        stored.setIpAddress(metadata.ipAddress());
        stored.setUserAgent(metadata.userAgent());
        stored.setLastUsedAt(Instant.now());
        refreshTokens.save(stored);
        return new AuthResponse(jwtService.createAccessToken(user), rawRefresh, "Bearer",
                jwtService.expiresInSeconds(), toUserResponse(user));
    }

    private User getUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }
    private UserSettings getUserSettings(UUID userId) {
        return settings.findByUserId(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SETTINGS_NOT_FOUND", "Không tìm thấy cài đặt"));
    }
    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getAvatarUrl(),
                user.getTimezone(), user.getRole(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
    }
    private SettingsResponse toSettings(UserSettings value) {
        return new SettingsResponse(value.getWorkStartTime(), value.getWorkEndTime(), value.getDefaultReminderMinutes(),
                value.getWeekStartDay(), value.getLanguage(), value.getTheme());
    }
    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác");
    }
}
