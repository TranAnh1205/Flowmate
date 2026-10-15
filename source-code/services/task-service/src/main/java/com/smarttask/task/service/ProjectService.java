package com.smarttask.task.service;

import com.smarttask.task.domain.TaskPriority;
import com.smarttask.task.domain.TaskStatus;
import com.smarttask.task.dto.ProjectDtos.*;
import com.smarttask.task.exception.TaskApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProjectService {
    private static final String PROJECT_SELECT = """
            SELECT p.id, p.name, p.description, p.status, p.owner_id, p.created_at,
                   m.role AS my_role,
                   (SELECT COUNT(*) FROM team_project_members pm WHERE pm.project_id = p.id) AS member_count,
                   (SELECT COUNT(*) FROM team_project_tasks t WHERE t.project_id = p.id AND t.deleted_at IS NULL) AS task_count,
                   (SELECT COUNT(*) FROM team_project_tasks t WHERE t.project_id = p.id
                       AND t.deleted_at IS NULL AND t.status = 'COMPLETED') AS completed_task_count
            FROM team_projects p JOIN team_project_members m ON m.project_id = p.id
            WHERE m.user_id = ?
            """;
    private static final String TASK_SELECT = """
            SELECT id, project_id, title, description, status, priority, created_by,
                   assignee_id, start_at, due_at, completed_at, version, created_at, updated_at
            FROM team_project_tasks WHERE project_id = ? AND deleted_at IS NULL
            """;

    private final JdbcTemplate jdbc;

    public ProjectService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ProjectResponse create(UUID userId, String email, CreateProjectRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO team_projects(id, owner_id, name, description, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, userId, request.name().trim(), request.description(), at(now), at(now));
        jdbc.update("INSERT INTO team_project_members(project_id, user_id, email, role, joined_at) " +
                        "VALUES (?, ?, ?, 'OWNER', ?)",
                id, userId, normalizeEmail(email), at(now));
        return get(userId, id);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID userId) {
        return jdbc.query(PROJECT_SELECT + " ORDER BY p.updated_at DESC", projectRow(), userId);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID userId, UUID projectId) {
        return jdbc.query(PROJECT_SELECT + " AND p.id = ?", projectRow(), userId, projectId)
                .stream().findFirst().orElseThrow(() -> notFound("PROJECT_NOT_FOUND", "Không tìm thấy dự án"));
    }

    @Transactional
    public ProjectResponse update(UUID userId, UUID projectId, UpdateProjectRequest request) {
        requireOwner(projectId, userId);
        jdbc.update("UPDATE team_projects SET name = ?, description = ?, status = ?, updated_at = ? WHERE id = ?",
                request.name().trim(), request.description(), request.status(), at(Instant.now()), projectId);
        return get(userId, projectId);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID userId, UUID projectId) {
        requireMember(projectId, userId);
        return jdbc.query("SELECT user_id, email, role, joined_at FROM team_project_members " +
                        "WHERE project_id = ? ORDER BY joined_at, user_id",
                (rs, rowNum) -> new MemberResponse(uuid(rs, "user_id"), rs.getString("email"),
                        rs.getString("role"), instant(rs, "joined_at")), projectId);
    }

    @Transactional
    public InvitationResponse invite(UUID userId, UUID projectId, InviteMemberRequest request) {
        requireOwner(projectId, userId);
        requireActive(projectId);
        jdbc.queryForObject("SELECT id FROM team_projects WHERE id = ? FOR UPDATE", UUID.class, projectId);
        String email = normalizeEmail(request.email());
        Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM team_project_members " +
                "WHERE project_id = ? AND email = ?", Integer.class, projectId, email);
        if (members != null && members > 0) {
            throw new TaskApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "Email này đã thuộc dự án");
        }
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM team_project_invitations " +
                        "WHERE project_id = ? AND invited_email = ? AND accepted_at IS NULL AND expires_at > ?",
                Integer.class, projectId, email, at(Instant.now()));
        if (pending != null && pending > 0) {
            throw new TaskApiException(HttpStatus.CONFLICT, "INVITATION_PENDING", "Email này đã có lời mời chưa xử lý");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(7, ChronoUnit.DAYS);
        UUID invitationId = UUID.randomUUID();
        jdbc.update("INSERT INTO team_project_invitations" +
                        "(id, project_id, invited_email, created_by, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                invitationId, projectId, email, userId, at(expiresAt), at(now));
        return new InvitationResponse(invitationId, projectId, get(userId, projectId).name(), email, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> invitations(String email) {
        return jdbc.query("SELECT i.id, i.project_id, p.name, i.invited_email, i.expires_at " +
                        "FROM team_project_invitations i JOIN team_projects p ON p.id = i.project_id " +
                        "WHERE i.invited_email = ? AND i.accepted_at IS NULL AND i.expires_at > ? " +
                        "AND p.status = 'ACTIVE' ORDER BY i.created_at DESC",
                (rs, rowNum) -> new InvitationResponse(uuid(rs, "id"), uuid(rs, "project_id"),
                        rs.getString("name"), rs.getString("invited_email"), instant(rs, "expires_at")),
                normalizeEmail(email), at(Instant.now()));
    }

    @Transactional
    public ProjectResponse accept(UUID userId, String email, AcceptInvitationRequest request) {
        String normalizedEmail = normalizeEmail(email);
        Invitation invitation = jdbc.query("SELECT project_id, invited_email, expires_at, accepted_at " +
                        "FROM team_project_invitations WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> new Invitation(uuid(rs, "project_id"), instant(rs, "expires_at"),
                        instant(rs, "accepted_at"), rs.getString("invited_email")), request.invitationId())
                .stream().findFirst()
                .orElseThrow(() -> notFound("INVITATION_NOT_FOUND", "Không tìm thấy lời mời"));
        if (!invitation.invitedEmail().equals(normalizedEmail)) {
            throw notFound("INVITATION_NOT_FOUND", "Không tìm thấy lời mời");
        }
        if (invitation.acceptedAt() != null || !invitation.expiresAt().isAfter(Instant.now())) {
            throw new TaskApiException(HttpStatus.GONE, "INVITATION_EXPIRED", "Lời mời đã dùng hoặc hết hạn");
        }
        requireActive(invitation.projectId());
        if (isMember(invitation.projectId(), userId)) {
            throw new TaskApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "Bạn đã là thành viên dự án");
        }
        Instant now = Instant.now();
        jdbc.update("INSERT INTO team_project_members(project_id, user_id, email, role, joined_at) " +
                        "VALUES (?, ?, ?, 'MEMBER', ?)",
                invitation.projectId(), userId, normalizedEmail, at(now));
        jdbc.update("UPDATE team_project_invitations SET accepted_by = ?, accepted_at = ? WHERE id = ?",
                userId, at(now), request.invitationId());
        return get(userId, invitation.projectId());
    }

    @Transactional
    public void removeMember(UUID userId, UUID projectId, UUID memberId) {
        requireOwner(projectId, userId);
        if (userId.equals(memberId)) {
            throw new TaskApiException(HttpStatus.BAD_REQUEST, "OWNER_CANNOT_LEAVE", "Chủ dự án không thể rời dự án");
        }
        removeMembership(projectId, memberId);
    }

    @Transactional
    public void leave(UUID userId, UUID projectId) {
        String role = requireMember(projectId, userId);
        if ("OWNER".equals(role)) {
            throw new TaskApiException(HttpStatus.BAD_REQUEST, "OWNER_CANNOT_LEAVE", "Chủ dự án không thể rời dự án");
        }
        removeMembership(projectId, userId);
    }

    private void removeMembership(UUID projectId, UUID memberId) {
        if (!isMember(projectId, memberId)) {
            throw notFound("MEMBER_NOT_FOUND", "Không tìm thấy thành viên");
        }
        jdbc.update("UPDATE team_project_tasks SET assignee_id = NULL, updated_at = ?, version = version + 1 " +
                "WHERE project_id = ? AND assignee_id = ?",
                at(Instant.now()), projectId, memberId);
        jdbc.update("DELETE FROM team_project_members WHERE project_id = ? AND user_id = ?", projectId, memberId);
    }

    @Transactional
    public ProjectTaskResponse createTask(UUID userId, UUID projectId, CreateProjectTaskRequest request) {
        validateDates(request.startAt(), request.dueAt());
        String role = requireMember(projectId, userId);
        requireActive(projectId);
        UUID assigneeId = request.assigneeId() == null ? userId : request.assigneeId();
        if (!"OWNER".equals(role) && !userId.equals(assigneeId)) {
            throw forbidden();
        }
        requireAssignee(projectId, assigneeId);
        TaskStatus status = request.status() == null ? TaskStatus.TODO : request.status();
        TaskPriority priority = request.priority() == null ? TaskPriority.MEDIUM : request.priority();
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO team_project_tasks(id, project_id, title, description, status, priority, " +
                        "created_by, assignee_id, start_at, due_at, completed_at, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                id, projectId, request.title().trim(), request.description(), status.name(), priority.name(),
                userId, assigneeId, at(request.startAt()), at(request.dueAt()),
                at(status == TaskStatus.COMPLETED ? now : null),
                at(now), at(now));
        return task(userId, projectId, id);
    }

    @Transactional(readOnly = true)
    public ProjectTaskPage tasks(UUID userId, UUID projectId, TaskStatus status, int page, int size) {
        requireMember(projectId, userId);
        if (page < 0 || size < 1) {
            throw new TaskApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Trang không hợp lệ");
        }
        int safeSize = Math.min(size, 100);
        String filter = status == null ? "" : " AND status = ?";
        Object[] countArgs = status == null ? new Object[] {projectId} : new Object[] {projectId, status.name()};
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM team_project_tasks WHERE project_id = ? " +
                "AND deleted_at IS NULL" + filter, Long.class, countArgs);
        Object[] listArgs = status == null
                ? new Object[] {projectId, safeSize, (long) page * safeSize}
                : new Object[] {projectId, status.name(), safeSize, (long) page * safeSize};
        List<ProjectTaskResponse> content = jdbc.query(TASK_SELECT + filter +
                " ORDER BY due_at NULLS LAST, created_at DESC LIMIT ? OFFSET ?", taskRow(), listArgs);
        long count = total == null ? 0 : total;
        return new ProjectTaskPage(content, page, safeSize, count, (int) ((count + safeSize - 1) / safeSize));
    }

    @Transactional(readOnly = true)
    public ProjectTaskResponse task(UUID userId, UUID projectId, UUID taskId) {
        requireMember(projectId, userId);
        return jdbc.query(TASK_SELECT + " AND id = ?", taskRow(), projectId, taskId)
                .stream().findFirst().orElseThrow(() -> notFound("PROJECT_TASK_NOT_FOUND", "Không tìm thấy công việc"));
    }

    @Transactional
    public ProjectTaskResponse updateTask(UUID userId, UUID projectId, UUID taskId,
                                          UpdateProjectTaskRequest request) {
        validateDates(request.startAt(), request.dueAt());
        String role = requireMember(projectId, userId);
        requireActive(projectId);
        ProjectTaskResponse current = task(userId, projectId, taskId);
        requireTaskEditor(userId, role, current);
        if (!"OWNER".equals(role) && !Objects.equals(current.assigneeId(), request.assigneeId())) {
            throw forbidden();
        }
        requireAssignee(projectId, request.assigneeId());
        Instant completedAt = request.status() == TaskStatus.COMPLETED
                ? current.completedAt() == null ? Instant.now() : current.completedAt() : null;
        int changed = jdbc.update("UPDATE team_project_tasks SET title = ?, description = ?, status = ?, " +
                        "priority = ?, assignee_id = ?, start_at = ?, due_at = ?, completed_at = ?, updated_at = ?, " +
                        "version = version + 1 WHERE id = ? AND project_id = ? AND version = ? AND deleted_at IS NULL",
                request.title().trim(), request.description(), request.status().name(), request.priority().name(),
                request.assigneeId(), at(request.startAt()), at(request.dueAt()), at(completedAt),
                at(Instant.now()), taskId,
                projectId, request.version());
        requireVersion(changed);
        return task(userId, projectId, taskId);
    }

    @Transactional
    public ProjectTaskResponse changeStatus(UUID userId, UUID projectId, UUID taskId,
                                            ChangeProjectTaskStatusRequest request) {
        String role = requireMember(projectId, userId);
        requireActive(projectId);
        ProjectTaskResponse current = task(userId, projectId, taskId);
        requireTaskEditor(userId, role, current);
        Instant completedAt = request.status() == TaskStatus.COMPLETED
                ? current.completedAt() == null ? Instant.now() : current.completedAt() : null;
        int changed = jdbc.update("UPDATE team_project_tasks SET status = ?, completed_at = ?, " +
                        "updated_at = ?, version = version + 1 WHERE id = ? AND project_id = ? " +
                        "AND version = ? AND deleted_at IS NULL",
                request.status().name(), at(completedAt), at(Instant.now()), taskId, projectId, request.version());
        requireVersion(changed);
        return task(userId, projectId, taskId);
    }

    @Transactional
    public void deleteTask(UUID userId, UUID projectId, UUID taskId) {
        String role = requireMember(projectId, userId);
        requireActive(projectId);
        ProjectTaskResponse current = task(userId, projectId, taskId);
        if (!"OWNER".equals(role) && !userId.equals(current.createdBy())) {
            throw forbidden();
        }
        jdbc.update("UPDATE team_project_tasks SET deleted_at = ?, updated_at = ?, version = version + 1 " +
                        "WHERE id = ? AND project_id = ? AND deleted_at IS NULL",
                at(Instant.now()), at(Instant.now()), taskId, projectId);
    }

    private void requireTaskEditor(UUID userId, String role, ProjectTaskResponse task) {
        if (!"OWNER".equals(role) && !userId.equals(task.createdBy()) && !userId.equals(task.assigneeId())) {
            throw forbidden();
        }
    }

    private void requireAssignee(UUID projectId, UUID assigneeId) {
        if (assigneeId != null && !isMember(projectId, assigneeId)) {
            throw new TaskApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEE", "Người được giao phải thuộc dự án");
        }
    }

    private String requireMember(UUID projectId, UUID userId) {
        return jdbc.query("SELECT role FROM team_project_members WHERE project_id = ? AND user_id = ?",
                (rs, rowNum) -> rs.getString(1), projectId, userId).stream().findFirst()
                .orElseThrow(() -> notFound("PROJECT_NOT_FOUND", "Không tìm thấy dự án"));
    }

    private void requireOwner(UUID projectId, UUID userId) {
        if (!"OWNER".equals(requireMember(projectId, userId))) throw forbidden();
    }

    private void requireActive(UUID projectId) {
        try {
            String status = jdbc.queryForObject("SELECT status FROM team_projects WHERE id = ?", String.class, projectId);
            if (!"ACTIVE".equals(status)) {
                throw new TaskApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", "Dự án đã lưu trữ");
            }
        } catch (EmptyResultDataAccessException error) {
            throw notFound("PROJECT_NOT_FOUND", "Không tìm thấy dự án");
        }
    }

    private boolean isMember(UUID projectId, UUID userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM team_project_members " +
                "WHERE project_id = ? AND user_id = ?", Integer.class, projectId, userId);
        return count != null && count > 0;
    }

    private void requireVersion(int changed) {
        if (changed == 0) {
            throw new TaskApiException(HttpStatus.CONFLICT, "STALE_PROJECT_TASK",
                    "Công việc đã được người khác cập nhật. Vui lòng tải lại.");
        }
    }

    private TaskApiException forbidden() {
        return new TaskApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "Bạn không có quyền thực hiện thao tác này");
    }

    private TaskApiException notFound(String code, String message) {
        return new TaskApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private RowMapper<ProjectResponse> projectRow() {
        return (rs, rowNum) -> new ProjectResponse(uuid(rs, "id"), rs.getString("name"),
                rs.getString("description"), rs.getString("status"), uuid(rs, "owner_id"),
                rs.getString("my_role"), rs.getLong("member_count"), rs.getLong("task_count"),
                rs.getLong("completed_task_count"), instant(rs, "created_at"));
    }

    private void validateDates(Instant startAt, Instant dueAt) {
        if (startAt != null && dueAt != null && startAt.isAfter(dueAt)) {
            throw new TaskApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_DATES",
                    "Ngày bắt đầu không thể sau hạn hoàn thành");
        }
    }

    private RowMapper<ProjectTaskResponse> taskRow() {
        return (rs, rowNum) -> new ProjectTaskResponse(uuid(rs, "id"), uuid(rs, "project_id"),
                rs.getString("title"), rs.getString("description"),
                TaskStatus.valueOf(rs.getString("status")), TaskPriority.valueOf(rs.getString("priority")),
                uuid(rs, "created_by"), uuid(rs, "assignee_id"), instant(rs, "start_at"),
                instant(rs, "due_at"),
                instant(rs, "completed_at"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp at(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record Invitation(UUID projectId, Instant expiresAt, Instant acceptedAt, String invitedEmail) {}
}
