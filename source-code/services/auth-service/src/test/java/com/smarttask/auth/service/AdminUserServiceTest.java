package com.smarttask.auth.service;

import com.smarttask.auth.domain.*;
import com.smarttask.auth.dto.AdminDtos.*;
import com.smarttask.auth.exception.ApiException;
import com.smarttask.auth.repository.RefreshTokenRepository;
import com.smarttask.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock AdminActionTokenService actionTokens;
    @Mock AdminAuditService audit;
    AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(users, refreshTokens, actionTokens, audit);
    }

    @Test
    void suspendUserRevokesSessionsAndWritesAudit() {
        User admin = user(UserRole.ADMIN, AccountStatus.ACTIVE);
        User target = user(UserRole.USER, AccountStatus.ACTIVE);
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(users.findById(target.getId())).thenReturn(Optional.of(target));
        when(refreshTokens.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(target.getId()), any()))
                .thenReturn(0L);

        AdminUserResponse result = service.changeStatus(admin.getId(), target.getId(),
                new ChangeStatusRequest(AccountStatus.SUSPENDED, "Kiểm tra bảo mật"),
                "action-token", RequestMetadata.empty());

        assertEquals(AccountStatus.SUSPENDED, result.status());
        verify(actionTokens).validate(admin.getId(), "action-token");
        verify(refreshTokens).revokeActiveTokensByUserId(eq(target.getId()), any(), eq("Kiểm tra bảo mật"));
        verify(audit).record(eq(admin.getId()), eq(UserRole.ADMIN), eq("USER_SUSPENDED"),
                eq(target.getId()), eq("Kiểm tra bảo mật"), any(), any(), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void adminCannotChangeOwnStatus() {
        User admin = user(UserRole.ADMIN, AccountStatus.ACTIVE);
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));

        ApiException error = assertThrows(ApiException.class, () ->
                service.changeStatus(admin.getId(), admin.getId(),
                        new ChangeStatusRequest(AccountStatus.SUSPENDED, "Không hợp lệ"),
                        "action-token", RequestMetadata.empty()));

        assertEquals("CANNOT_CHANGE_SELF_STATUS", error.getCode());
        verify(refreshTokens, never()).revokeActiveTokensByUserId(any(), any(), any());
    }

    @Test
    void cannotDemoteLastActiveAdmin() {
        User actor = user(UserRole.ADMIN, AccountStatus.ACTIVE);
        User target = user(UserRole.ADMIN, AccountStatus.ACTIVE);
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(users.findById(target.getId())).thenReturn(Optional.of(target));
        when(users.countByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE)).thenReturn(1L);

        ApiException error = assertThrows(ApiException.class, () ->
                service.changeRole(actor.getId(), target.getId(),
                        new ChangeRoleRequest(UserRole.USER, "Thay đổi phân quyền"),
                        "action-token", RequestMetadata.empty()));

        assertEquals("LAST_ADMIN_REQUIRED", error.getCode());
    }

    @Test
    void cannotPromoteSuspendedUser() {
        User admin = user(UserRole.ADMIN, AccountStatus.ACTIVE);
        User target = user(UserRole.USER, AccountStatus.SUSPENDED);
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(users.findById(target.getId())).thenReturn(Optional.of(target));

        ApiException error = assertThrows(ApiException.class, () ->
                service.changeRole(admin.getId(), target.getId(),
                        new ChangeRoleRequest(UserRole.ADMIN, "Cấp quyền quản trị"),
                        "action-token", RequestMetadata.empty()));

        assertEquals("INACTIVE_ADMIN_PROMOTION", error.getCode());
    }

    @Test
    void revokeSingleSessionMarksTokenAndWritesAudit() {
        User admin = user(UserRole.ADMIN, AccountStatus.ACTIVE);
        User target = user(UserRole.USER, AccountStatus.ACTIVE);
        UUID sessionId = UUID.randomUUID();
        RefreshToken session = new RefreshToken();
        session.setUserId(target.getId());
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(users.findById(target.getId())).thenReturn(Optional.of(target));
        when(refreshTokens.findByIdAndUserId(sessionId, target.getId()))
                .thenReturn(Optional.of(session));

        service.revokeSession(admin.getId(), target.getId(), sessionId,
                new RevokeSessionsRequest("Thu hồi thiết bị thất lạc"),
                "action-token", RequestMetadata.empty());

        assertNotNull(session.getRevokedAt());
        assertEquals("Thu hồi thiết bị thất lạc", session.getRevokeReason());
        verify(actionTokens).validate(admin.getId(), "action-token");
        verify(audit).record(eq(admin.getId()), eq(UserRole.ADMIN),
                eq("USER_SESSION_REVOKED"), eq(target.getId()),
                eq("Thu hồi thiết bị thất lạc"), isNull(), isNull(),
                eq("SUCCESS"), any(), contains(sessionId.toString()));
    }

    private User user(UserRole role, AccountStatus status) {
        User value = new User();
        value.setId(UUID.randomUUID());
        value.setFullName(role.name());
        value.setEmail(UUID.randomUUID() + "@example.com");
        value.setPasswordHash("hash");
        value.setTimezone("Asia/Bangkok");
        value.setRole(role);
        value.setStatus(status);
        value.setLastLoginAt(Instant.now());
        return value;
    }
}
