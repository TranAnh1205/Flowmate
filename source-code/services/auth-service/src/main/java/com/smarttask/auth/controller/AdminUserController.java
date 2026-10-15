package com.smarttask.auth.controller;

import com.smarttask.auth.domain.AccountStatus;
import com.smarttask.auth.domain.UserRole;
import com.smarttask.auth.dto.AdminDtos.*;
import com.smarttask.auth.security.AdminAuthorities;
import com.smarttask.auth.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + AdminAuthorities.USER_VIEW + "')")
    public AdminUserPage users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(q, role, status, page, size, sort);
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('" + AdminAuthorities.USER_VIEW + "')")
    public AdminUserResponse detail(@PathVariable UUID userId) {
        return service.detail(userId);
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasAuthority('" + AdminAuthorities.USER_SUSPEND + "')")
    public AdminUserResponse changeStatus(
            Authentication authentication,
            @PathVariable UUID userId,
            @RequestHeader(value = "X-Admin-Action-Token", required = false) String actionToken,
            @Valid @RequestBody ChangeStatusRequest request,
            HttpServletRequest http) {
        return service.changeStatus(userId(authentication), userId, request, actionToken,
                AdminRequestMetadata.from(http));
    }

    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasAuthority('" + AdminAuthorities.USER_ROLE_UPDATE + "')")
    public AdminUserResponse changeRole(
            Authentication authentication,
            @PathVariable UUID userId,
            @RequestHeader(value = "X-Admin-Action-Token", required = false) String actionToken,
            @Valid @RequestBody ChangeRoleRequest request,
            HttpServletRequest http) {
        return service.changeRole(userId(authentication), userId, request, actionToken,
                AdminRequestMetadata.from(http));
    }

    @GetMapping("/users/{userId}/sessions")
    @PreAuthorize("hasAuthority('" + AdminAuthorities.USER_VIEW + "')")
    public List<SessionResponse> sessions(@PathVariable UUID userId) {
        return service.sessions(userId);
    }

    @PostMapping("/users/{userId}/sessions/revoke-all")
    @PreAuthorize("hasAuthority('" + AdminAuthorities.SESSION_REVOKE + "')")
    public Map<String, Object> revokeAllSessions(
            Authentication authentication,
            @PathVariable UUID userId,
            @RequestHeader(value = "X-Admin-Action-Token", required = false) String actionToken,
            @Valid @RequestBody RevokeSessionsRequest request,
            HttpServletRequest http) {
        int count = service.revokeAllSessions(userId(authentication), userId, request, actionToken,
                AdminRequestMetadata.from(http));
        return Map.of("message", "Đã thu hồi các phiên đăng nhập", "revokedSessions", count);
    }

    @PostMapping("/users/{userId}/sessions/{sessionId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + AdminAuthorities.SESSION_REVOKE + "')")
    public void revokeSession(
            Authentication authentication,
            @PathVariable UUID userId,
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-Admin-Action-Token", required = false) String actionToken,
            @Valid @RequestBody RevokeSessionsRequest request,
            HttpServletRequest http) {
        service.revokeSession(userId(authentication), userId, sessionId, request, actionToken,
                AdminRequestMetadata.from(http));
    }

    @GetMapping("/statistics/users")
    @PreAuthorize("hasAuthority('" + AdminAuthorities.USER_VIEW + "')")
    public UserStatisticsResponse statistics() {
        return service.statistics();
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
