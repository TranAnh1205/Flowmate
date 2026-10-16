package com.smarttask.auth.controller;

import com.smarttask.auth.domain.User;
import com.smarttask.auth.dto.AdminDtos.AuditLogPage;
import com.smarttask.auth.security.AdminAuthorities;
import com.smarttask.auth.service.AdminAuditService;
import com.smarttask.auth.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {
    private final AdminAuditService audit;
    private final AdminUserService users;

    public AdminAuditController(AdminAuditService audit, AdminUserService users) {
        this.audit = audit;
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + AdminAuthorities.AUDIT_VIEW + "')")
    public AuditLogPage list(
            Authentication authentication,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest http) {
        UUID viewerId = UUID.fromString(authentication.getName());
        User viewer = users.requireAdmin(viewerId);
        AuditLogPage response = audit.list(action, actorId, targetId, result, from, to, page, size);
        audit.record(viewerId, viewer.getRole(), "AUDIT_LOG_VIEWED", null,
                "Xem nhật ký quản trị", null, null, "SUCCESS",
                AdminRequestMetadata.from(http), "{\"page\":" + page + "}");
        return response;
    }
}
