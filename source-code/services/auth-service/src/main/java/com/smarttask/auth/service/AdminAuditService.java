package com.smarttask.auth.service;

import com.smarttask.auth.domain.AdminAuditLog;
import com.smarttask.auth.domain.UserRole;
import com.smarttask.auth.dto.AdminDtos.*;
import com.smarttask.auth.repository.AdminAuditLogRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdminAuditService {
    private final AdminAuditLogRepository logs;

    public AdminAuditService(AdminAuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, UserRole actorRole, String action, UUID targetId,
                       String reason, String previousState, String newState, String result,
                       RequestMetadata metadata, String extraMetadata) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActorUserId(actorId);
        log.setActorRole(actorRole.name());
        log.setAction(action);
        log.setTargetType("USER");
        log.setTargetId(targetId);
        log.setReason(reason);
        log.setPreviousState(previousState);
        log.setNewState(newState);
        log.setResult(result);
        log.setIpAddress(metadata == null ? null : metadata.ipAddress());
        log.setUserAgent(metadata == null ? null : metadata.userAgent());
        log.setMetadata(extraMetadata);
        logs.save(log);
    }

    @Transactional(readOnly = true)
    public AuditLogPage list(String action, UUID actorId, UUID targetId, String result,
                             Instant from, Instant to, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Specification<AdminAuditLog> spec = Specification.where(null);
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (actorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorId));
        }
        if (targetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetId"), targetId));
        }
        if (result != null && !result.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("result"), result));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        Page<AdminAuditLog> values = logs.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new AuditLogPage(values.stream().map(this::toResponse).toList(),
                values.getNumber(), values.getSize(), values.getTotalElements(), values.getTotalPages());
    }

    private AuditLogResponse toResponse(AdminAuditLog value) {
        return new AuditLogResponse(value.getId(), value.getActorUserId(), value.getActorRole(),
                value.getAction(), value.getTargetType(), value.getTargetId(), value.getReason(),
                value.getPreviousState(), value.getNewState(), value.getResult(), value.getIpAddress(),
                value.getUserAgent(), value.getMetadata(), value.getCreatedAt());
    }
}
