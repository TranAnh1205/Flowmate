package com.smarttask.auth.dto;

import com.smarttask.auth.domain.AccountStatus;
import com.smarttask.auth.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record AdminUserResponse(
            UUID id,
            String fullName,
            String email,
            String avatarUrl,
            String timezone,
            UserRole role,
            AccountStatus status,
            String statusReason,
            Instant statusChangedAt,
            UUID statusChangedBy,
            Instant lastLoginAt,
            long activeSessions,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record AdminUserPage(
            List<AdminUserResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record ChangeStatusRequest(
            @NotNull AccountStatus status,
            @NotBlank @Size(min = 5, max = 500) String reason
    ) {}

    public record ChangeRoleRequest(
            @NotNull UserRole role,
            @NotBlank @Size(min = 5, max = 500) String reason
    ) {}

    public record RevokeSessionsRequest(
            @NotBlank @Size(min = 5, max = 500) String reason
    ) {}

    public record SessionResponse(
            UUID id,
            String deviceName,
            String ipAddress,
            String userAgent,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            Instant revokedAt,
            String revokeReason,
            boolean active
    ) {}

    public record UserStatisticsResponse(
            long totalUsers,
            long activeUsers,
            long lockedUsers,
            long suspendedUsers,
            long deactivatedUsers,
            long adminUsers,
            long newUsersToday,
            long newUsersThisWeek
    ) {}

    public record AuditLogResponse(
            UUID id,
            UUID actorUserId,
            String actorRole,
            String action,
            String targetType,
            UUID targetId,
            String reason,
            String previousState,
            String newState,
            String result,
            String ipAddress,
            String userAgent,
            String metadata,
            Instant createdAt
    ) {}

    public record AuditLogPage(
            List<AuditLogResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record RequestMetadata(String ipAddress, String userAgent, String deviceName) {
        public static RequestMetadata empty() {
            return new RequestMetadata(null, null, null);
        }
    }
}
