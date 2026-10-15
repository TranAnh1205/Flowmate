package com.smarttask.auth.service;

import com.smarttask.auth.domain.*;
import com.smarttask.auth.dto.AdminDtos.*;
import com.smarttask.auth.exception.ApiException;
import com.smarttask.auth.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AdminActionTokenService actionTokens;
    private final AdminAuditService audit;

    public AdminUserService(UserRepository users, RefreshTokenRepository refreshTokens,
                            AdminActionTokenService actionTokens, AdminAuditService audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.actionTokens = actionTokens;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminUserPage list(String query, UserRole role, AccountStatus status,
                              int page, int size, String sort) {
        Sort safeSort = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, safeSort);
        Specification<User> specification = Specification.where(null);
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)));
        }
        if (role != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("role"), role));
        }
        if (status != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("status"), status));
        }
        Page<User> result = users.findAll(specification, pageable);
        return new AdminUserPage(result.stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminUserResponse detail(UUID userId) {
        return toResponse(getUser(userId));
    }

    @Transactional
    public AdminUserResponse changeStatus(UUID actorId, UUID targetId, ChangeStatusRequest request,
                                          String actionToken, RequestMetadata metadata) {
        actionTokens.validate(actorId, actionToken);
        User actor = requireAdmin(actorId);
        User target = getUser(targetId);
        if (actorId.equals(targetId)) {
            throw conflict("CANNOT_CHANGE_SELF_STATUS", "Admin không thể thay đổi trạng thái của chính mình");
        }
        if (request.status() != AccountStatus.ACTIVE && request.status() != AccountStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_STATUS",
                    "Admin chỉ có thể kích hoạt hoặc tạm ngừng tài khoản");
        }
        if (target.getStatus() == request.status()) {
            throw conflict("ACCOUNT_STATUS_UNCHANGED", "Tài khoản đã ở trạng thái yêu cầu");
        }
        if (target.getRole() == UserRole.ADMIN && request.status() != AccountStatus.ACTIVE) {
            ensureAnotherActiveAdmin();
        }
        String before = state(target);
        target.setStatus(request.status());
        target.setStatusReason(request.reason().trim());
        target.setStatusChangedAt(Instant.now());
        target.setStatusChangedBy(actorId);
        if (request.status() != AccountStatus.ACTIVE) {
            refreshTokens.revokeActiveTokensByUserId(targetId, Instant.now(), request.reason().trim());
            actionTokens.revokeAll(targetId);
        }
        String action = request.status() == AccountStatus.ACTIVE ? "USER_ACTIVATED" : "USER_SUSPENDED";
        audit.record(actorId, actor.getRole(), action, targetId, request.reason().trim(),
                before, state(target), "SUCCESS", metadata, null);
        return toResponse(target);
    }

    @Transactional
    public AdminUserResponse changeRole(UUID actorId, UUID targetId, ChangeRoleRequest request,
                                        String actionToken, RequestMetadata metadata) {
        actionTokens.validate(actorId, actionToken);
        User actor = requireAdmin(actorId);
        User target = getUser(targetId);
        if (actorId.equals(targetId)) {
            throw conflict("CANNOT_CHANGE_SELF_ROLE", "Admin không thể thay đổi quyền của chính mình");
        }
        if (target.getStatus() != AccountStatus.ACTIVE && request.role() == UserRole.ADMIN) {
            throw conflict("INACTIVE_ADMIN_PROMOTION", "Không thể cấp quyền Admin cho tài khoản không hoạt động");
        }
        if (target.getRole() == request.role()) {
            throw conflict("ROLE_UNCHANGED", "Tài khoản đã có quyền được yêu cầu");
        }
        if (target.getRole() == UserRole.ADMIN && request.role() == UserRole.USER) {
            ensureAnotherActiveAdmin();
        }
        String before = state(target);
        target.setRole(request.role());
        refreshTokens.revokeActiveTokensByUserId(targetId, Instant.now(), "Quyền tài khoản đã thay đổi");
        actionTokens.revokeAll(targetId);
        audit.record(actorId, actor.getRole(), "USER_ROLE_CHANGED", targetId, request.reason().trim(),
                before, state(target), "SUCCESS", metadata, null);
        return toResponse(target);
    }

    @Transactional
    public int revokeAllSessions(UUID actorId, UUID targetId, RevokeSessionsRequest request,
                                 String actionToken, RequestMetadata metadata) {
        actionTokens.validate(actorId, actionToken);
        User actor = requireAdmin(actorId);
        getUser(targetId);
        if (actorId.equals(targetId)) {
            throw conflict("CANNOT_REVOKE_SELF_SESSIONS",
                    "Hãy dùng chức năng đăng xuất để thu hồi phiên của chính bạn");
        }
        int count = refreshTokens.revokeActiveTokensByUserId(targetId, Instant.now(), request.reason().trim());
        actionTokens.revokeAll(targetId);
        audit.record(actorId, actor.getRole(), "USER_SESSIONS_REVOKED", targetId,
                request.reason().trim(), null, null, "SUCCESS", metadata,
                "{\"revokedSessions\":" + count + "}");
        return count;
    }

    @Transactional
    public void revokeSession(UUID actorId, UUID targetId, UUID sessionId, RevokeSessionsRequest request,
                              String actionToken, RequestMetadata metadata) {
        actionTokens.validate(actorId, actionToken);
        User actor = requireAdmin(actorId);
        getUser(targetId);
        if (actorId.equals(targetId)) {
            throw conflict("CANNOT_REVOKE_SELF_SESSIONS",
                    "Hãy dùng chức năng đăng xuất để thu hồi phiên của chính bạn");
        }
        RefreshToken session = refreshTokens.findByIdAndUserId(sessionId, targetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                        "Không tìm thấy phiên đăng nhập"));
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())) {
            throw conflict("SESSION_NOT_ACTIVE", "Phiên đăng nhập không còn hoạt động");
        }
        session.setRevokedAt(Instant.now());
        session.setRevokeReason(request.reason().trim());
        audit.record(actorId, actor.getRole(), "USER_SESSION_REVOKED", targetId,
                request.reason().trim(), null, null, "SUCCESS", metadata,
                "{\"sessionId\":\"" + sessionId + "\"}");
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(UUID userId) {
        getUser(userId);
        Instant now = Instant.now();
        return refreshTokens.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(value -> new SessionResponse(value.getId(), value.getDeviceName(), value.getIpAddress(),
                        value.getUserAgent(), value.getCreatedAt(), value.getLastUsedAt(), value.getExpiresAt(),
                        value.getRevokedAt(), value.getRevokeReason(),
                        value.getRevokedAt() == null && value.getExpiresAt().isAfter(now)))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserStatisticsResponse statistics() {
        Instant startToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startWeek = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        return new UserStatisticsResponse(
                users.count(),
                users.countByStatus(AccountStatus.ACTIVE),
                users.countByStatus(AccountStatus.LOCKED),
                users.countByStatus(AccountStatus.SUSPENDED),
                users.countByStatus(AccountStatus.DEACTIVATED),
                users.countByRole(UserRole.ADMIN),
                users.countByCreatedAtGreaterThanEqual(startToday),
                users.countByCreatedAtGreaterThanEqual(startWeek));
    }

    @Transactional(readOnly = true)
    public User requireAdmin(UUID userId) {
        User user = getUser(userId);
        if (user.getRole() != UserRole.ADMIN || !user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ACCESS_DENIED",
                    "Tài khoản không có quyền quản trị");
        }
        return user;
    }

    private AdminUserResponse toResponse(User user) {
        long activeSessions = refreshTokens.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                user.getId(), Instant.now());
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getAvatarUrl(), user.getTimezone(), user.getRole(), user.getStatus(),
                user.getStatusReason(), user.getStatusChangedAt(), user.getStatusChangedBy(),
                user.getLastLoginAt(), activeSessions, user.getCreatedAt(), user.getUpdatedAt());
    }

    private User getUser(UUID userId) {
        return users.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }

    private void ensureAnotherActiveAdmin() {
        if (users.countByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw conflict("LAST_ADMIN_REQUIRED", "Không thể khóa hoặc hạ quyền Admin cuối cùng");
        }
    }

    private Sort parseSort(String value) {
        if (value == null || value.isBlank()) return Sort.by(Sort.Direction.DESC, "createdAt");
        String[] parts = value.split(",", 2);
        String property = switch (parts[0]) {
            case "fullName", "email", "createdAt", "lastLoginAt", "status", "role" -> parts[0];
            default -> "createdAt";
        };
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private String state(User user) {
        return "{\"role\":\"" + user.getRole() + "\",\"status\":\"" + user.getStatus() + "\"}";
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
