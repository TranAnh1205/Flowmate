package com.smarttask.task.service;

import com.smarttask.task.dto.CollaborationDtos.*;
import com.smarttask.task.exception.TaskApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CollaborationService {
    private static final int MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final BoardService boards;

    public CollaborationService(JdbcTemplate jdbc, BoardService boards) {
        this.jdbc = jdbc;
        this.boards = boards;
    }

    @Transactional(readOnly = true)
    public List<Comment> comments(UUID userId, UUID projectId, UUID boardId, UUID taskId) {
        requireCard(userId, projectId, boardId, taskId, false);
        return jdbc.query("SELECT * FROM card_comments WHERE task_id = ? AND deleted_at IS NULL " +
                        "ORDER BY created_at, id LIMIT 200",
                (rs, n) -> comment(rs), taskId);
    }

    @Transactional
    public Comment addComment(UUID userId, UUID projectId, UUID boardId, UUID taskId, TextRequest request) {
        requireCard(userId, projectId, boardId, taskId, true);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO card_comments(id, task_id, author_id, body, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                id, taskId, userId, request.body().trim(), at(now), at(now));
        event(boardId, taskId, userId, "COMMENT_ADDED");
        return new Comment(id, taskId, userId, request.body().trim(), now, now);
    }

    @Transactional
    public void deleteComment(UUID userId, UUID projectId, UUID boardId, UUID taskId, UUID commentId) {
        requireCard(userId, projectId, boardId, taskId, true);
        UUID author = jdbc.query("SELECT author_id FROM card_comments WHERE id = ? AND task_id = ? " +
                        "AND deleted_at IS NULL",
                (rs, n) -> (UUID) rs.getObject(1), commentId, taskId).stream().findFirst()
                .orElseThrow(() -> notFound("COMMENT_NOT_FOUND", "Không tìm thấy bình luận"));
        if (!author.equals(userId) &&
                !"ADMIN".equals(boards.access(userId, projectId, boardId).myRole())) throw forbidden();
        jdbc.update("UPDATE card_comments SET deleted_at = ? WHERE id = ?", at(Instant.now()), commentId);
        event(boardId, taskId, userId, "COMMENT_DELETED");
    }

    @Transactional(readOnly = true)
    public List<ChecklistItem> checklist(UUID userId, UUID projectId, UUID boardId, UUID taskId) {
        requireCard(userId, projectId, boardId, taskId, false);
        return jdbc.query("SELECT * FROM card_checklist_items WHERE task_id = ? ORDER BY position, id",
                (rs, n) -> checklistItem(rs), taskId);
    }

    @Transactional
    public ChecklistItem addChecklist(UUID userId, UUID projectId, UUID boardId, UUID taskId,
                                      ChecklistRequest request) {
        requireCard(userId, projectId, boardId, taskId, true);
        Long max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(position), 0) FROM card_checklist_items WHERE task_id = ?",
                Long.class, taskId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        long position = (max == null ? 0 : max) + 1000;
        jdbc.update("INSERT INTO card_checklist_items(id, task_id, title, is_done, position, " +
                        "created_by, created_at, updated_at) VALUES (?, ?, ?, FALSE, ?, ?, ?, ?)",
                id, taskId, request.title().trim(), position, userId, at(now), at(now));
        event(boardId, taskId, userId, "CHECKLIST_ADDED");
        return new ChecklistItem(id, taskId, request.title().trim(), false, position, now);
    }

    @Transactional
    public ChecklistItem updateChecklist(UUID userId, UUID projectId, UUID boardId, UUID taskId,
                                         UUID itemId, ChecklistUpdateRequest request) {
        requireCard(userId, projectId, boardId, taskId, true);
        int count = jdbc.update("UPDATE card_checklist_items SET title = ?, is_done = ?, updated_at = ? " +
                        "WHERE id = ? AND task_id = ?",
                request.title().trim(), request.done(), at(Instant.now()), itemId, taskId);
        if (count == 0) throw notFound("CHECKLIST_ITEM_NOT_FOUND", "Không tìm thấy mục kiểm tra");
        event(boardId, taskId, userId, "CHECKLIST_UPDATED");
        return jdbc.queryForObject("SELECT * FROM card_checklist_items WHERE id = ?",
                (rs, n) -> checklistItem(rs), itemId);
    }

    @Transactional
    public void deleteChecklist(UUID userId, UUID projectId, UUID boardId, UUID taskId, UUID itemId) {
        requireCard(userId, projectId, boardId, taskId, true);
        int count = jdbc.update("DELETE FROM card_checklist_items WHERE id = ? AND task_id = ?",
                itemId, taskId);
        if (count == 0) throw notFound("CHECKLIST_ITEM_NOT_FOUND", "Không tìm thấy mục kiểm tra");
        event(boardId, taskId, userId, "CHECKLIST_DELETED");
    }

    @Transactional(readOnly = true)
    public List<Attachment> attachments(UUID userId, UUID projectId, UUID boardId, UUID taskId) {
        requireCard(userId, projectId, boardId, taskId, false);
        return jdbc.query("SELECT id, task_id, original_name, content_type, byte_size, " +
                        "uploaded_by, created_at FROM card_attachments " +
                        "WHERE task_id = ? AND deleted_at IS NULL ORDER BY created_at, id",
                (rs, n) -> attachment(rs), taskId);
    }

    @Transactional
    public Attachment upload(UUID userId, UUID projectId, UUID boardId, UUID taskId,
                             MultipartFile file) {
        requireCard(userId, projectId, boardId, taskId, true);
        if (file.isEmpty() || file.getSize() > MAX_ATTACHMENT_BYTES)
            throw badRequest("INVALID_ATTACHMENT_SIZE", "Tệp phải lớn hơn 0 và không quá 5 MB");
        String original = file.getOriginalFilename();
        if (original == null) original = "attachment";
        original = original.replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1).trim();
        if (original.isBlank()) original = "attachment";
        if (original.length() > 255) original = original.substring(0, 255);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_ATTACHMENT_BYTES)
                throw badRequest("INVALID_ATTACHMENT_SIZE", "Tệp phải lớn hơn 0 và không quá 5 MB");
            jdbc.update("INSERT INTO card_attachments(id, task_id, uploaded_by, original_name, " +
                            "content_type, byte_size, bytes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    id, taskId, userId, original, "application/octet-stream", bytes.length, bytes, at(now));
            event(boardId, taskId, userId, "ATTACHMENT_ADDED");
            return new Attachment(id, taskId, original, "application/octet-stream",
                    bytes.length, userId, now);
        } catch (IOException error) {
            throw badRequest("ATTACHMENT_READ_FAILED", "Không thể đọc tệp tải lên");
        }
    }

    @Transactional(readOnly = true)
    public Download download(UUID userId, UUID projectId, UUID boardId, UUID taskId, UUID attachmentId) {
        requireCard(userId, projectId, boardId, taskId, false);
        return jdbc.query("SELECT original_name, bytes FROM card_attachments " +
                        "WHERE id = ? AND task_id = ? AND deleted_at IS NULL",
                (rs, n) -> new Download(rs.getString("original_name"), rs.getBytes("bytes")),
                attachmentId, taskId).stream().findFirst()
                .orElseThrow(() -> notFound("ATTACHMENT_NOT_FOUND", "Không tìm thấy tệp"));
    }

    @Transactional
    public void deleteAttachment(UUID userId, UUID projectId, UUID boardId, UUID taskId,
                                 UUID attachmentId) {
        requireCard(userId, projectId, boardId, taskId, true);
        UUID uploader = jdbc.query("SELECT uploaded_by FROM card_attachments " +
                        "WHERE id = ? AND task_id = ? AND deleted_at IS NULL",
                (rs, n) -> (UUID) rs.getObject(1), attachmentId, taskId).stream().findFirst()
                .orElseThrow(() -> notFound("ATTACHMENT_NOT_FOUND", "Không tìm thấy tệp"));
        if (!uploader.equals(userId) &&
                !"ADMIN".equals(boards.access(userId, projectId, boardId).myRole())) throw forbidden();
        jdbc.update("UPDATE card_attachments SET deleted_at = ? WHERE id = ?",
                at(Instant.now()), attachmentId);
        event(boardId, taskId, userId, "ATTACHMENT_DELETED");
    }

    @Transactional(readOnly = true)
    public List<Message> messages(UUID userId, UUID projectId, UUID boardId, Instant before, int limit) {
        boards.access(userId, projectId, boardId);
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbc.query("SELECT * FROM board_messages WHERE board_id = ? AND deleted_at IS NULL " +
                        "AND created_at < ? ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, n) -> message(rs), boardId, at(before == null ? Instant.now().plusSeconds(1) : before),
                bounded);
    }

    @Transactional
    public Message addMessage(UUID userId, UUID projectId, UUID boardId, TextRequest request) {
        boards.editAccess(userId, projectId, boardId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO board_messages(id, board_id, author_id, body, created_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                id, boardId, userId, request.body().trim(), at(now));
        event(boardId, null, userId, "MESSAGE_ADDED");
        return new Message(id, boardId, userId, request.body().trim(), now);
    }

    @Transactional(readOnly = true)
    public List<Event> events(UUID userId, UUID projectId, UUID boardId, long afterId) {
        boards.access(userId, projectId, boardId);
        String query = afterId <= 0
                ? "SELECT * FROM (SELECT id, board_id, task_id, actor_id, kind, created_at " +
                  "FROM board_events WHERE board_id = ? ORDER BY id DESC LIMIT 100) recent ORDER BY id"
                : "SELECT id, board_id, task_id, actor_id, kind, created_at FROM board_events " +
                  "WHERE board_id = ? AND id > ? ORDER BY id LIMIT 100";
        Object[] args = afterId <= 0 ? new Object[] {boardId} : new Object[] {boardId, afterId};
        return jdbc.query(query,
                (rs, n) -> new Event(rs.getLong("id"), (UUID) rs.getObject("board_id"),
                        (UUID) rs.getObject("task_id"), (UUID) rs.getObject("actor_id"),
                        rs.getString("kind"), instant(rs.getTimestamp("created_at"))),
                args);
    }

    @Transactional(readOnly = true)
    public List<Label> labels(UUID userId, UUID projectId, UUID boardId) {
        boards.access(userId, projectId, boardId);
        return jdbc.query("SELECT id, board_id, name, color FROM board_labels WHERE board_id = ? " +
                        "ORDER BY name, id",
                (rs, n) -> label(rs), boardId);
    }

    @Transactional
    public Label addLabel(UUID userId, UUID projectId, UUID boardId, LabelRequest request) {
        boards.editAccess(userId, projectId, boardId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO board_labels(id, board_id, name, color, created_at) VALUES (?, ?, ?, ?, ?)",
                id, boardId, request.name().trim(), request.color(), at(Instant.now()));
        event(boardId, null, userId, "LABEL_ADDED");
        return new Label(id, boardId, request.name().trim(), request.color());
    }

    @Transactional
    public void attachLabel(UUID userId, UUID projectId, UUID boardId, UUID taskId, UUID labelId) {
        requireCard(userId, projectId, boardId, taskId, true);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM board_labels WHERE id = ? AND board_id = ?",
                Integer.class, labelId, boardId);
        if (count == null || count == 0) throw notFound("LABEL_NOT_FOUND", "Không tìm thấy nhãn");
        jdbc.update("INSERT INTO card_labels(task_id, label_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                taskId, labelId);
        event(boardId, taskId, userId, "LABEL_ATTACHED");
    }

    @Transactional(readOnly = true)
    public List<Label> cardLabels(UUID userId, UUID projectId, UUID boardId, UUID taskId) {
        requireCard(userId, projectId, boardId, taskId, false);
        return jdbc.query("SELECT l.id, l.board_id, l.name, l.color FROM card_labels c " +
                        "JOIN board_labels l ON l.id = c.label_id WHERE c.task_id = ? AND l.board_id = ?",
                (rs, n) -> label(rs), taskId, boardId);
    }

    @Transactional
    public void watch(UUID userId, UUID projectId, UUID boardId, UUID taskId, boolean watching) {
        requireCard(userId, projectId, boardId, taskId, false);
        if (watching)
            jdbc.update("INSERT INTO card_watchers(task_id, user_id, created_at) VALUES (?, ?, ?) " +
                            "ON CONFLICT DO NOTHING", taskId, userId, at(Instant.now()));
        else
            jdbc.update("DELETE FROM card_watchers WHERE task_id = ? AND user_id = ?", taskId, userId);
        event(boardId, taskId, userId, watching ? "CARD_WATCHED" : "CARD_UNWATCHED");
    }

    @Transactional(readOnly = true)
    public List<UUID> watchers(UUID userId, UUID projectId, UUID boardId, UUID taskId) {
        requireCard(userId, projectId, boardId, taskId, false);
        return jdbc.query("SELECT w.user_id FROM card_watchers w " +
                        "JOIN team_project_members m ON m.user_id = w.user_id AND m.project_id = ? " +
                        "WHERE w.task_id = ?",
                (rs, n) -> (UUID) rs.getObject(1), projectId, taskId);
    }

    private void requireCard(UUID userId, UUID projectId, UUID boardId, UUID taskId, boolean edit) {
        if (edit) boards.editAccess(userId, projectId, boardId);
        else boards.access(userId, projectId, boardId);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM board_cards c JOIN team_project_tasks t ON t.id = c.task_id " +
                "WHERE c.task_id = ? AND c.board_id = ? AND t.project_id = ? AND t.deleted_at IS NULL",
                Integer.class, taskId, boardId, projectId);
        if (count == null || count == 0) throw notFound("BOARD_CARD_NOT_FOUND", "Không tìm thấy thẻ");
    }

    private void event(UUID boardId, UUID taskId, UUID actor, String kind) {
        jdbc.update("INSERT INTO board_events(board_id, task_id, actor_id, kind, created_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                boardId, taskId, actor, kind, at(Instant.now()));
    }

    private static Comment comment(ResultSet rs) throws SQLException {
        return new Comment((UUID) rs.getObject("id"), (UUID) rs.getObject("task_id"),
                (UUID) rs.getObject("author_id"), rs.getString("body"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
    }

    private static ChecklistItem checklistItem(ResultSet rs) throws SQLException {
        return new ChecklistItem((UUID) rs.getObject("id"), (UUID) rs.getObject("task_id"),
                rs.getString("title"), rs.getBoolean("is_done"), rs.getLong("position"),
                instant(rs.getTimestamp("created_at")));
    }

    private static Attachment attachment(ResultSet rs) throws SQLException {
        return new Attachment((UUID) rs.getObject("id"), (UUID) rs.getObject("task_id"),
                rs.getString("original_name"), rs.getString("content_type"), rs.getLong("byte_size"),
                (UUID) rs.getObject("uploaded_by"), instant(rs.getTimestamp("created_at")));
    }

    private static Message message(ResultSet rs) throws SQLException {
        return new Message((UUID) rs.getObject("id"), (UUID) rs.getObject("board_id"),
                (UUID) rs.getObject("author_id"), rs.getString("body"),
                instant(rs.getTimestamp("created_at")));
    }

    private static Label label(ResultSet rs) throws SQLException {
        return new Label((UUID) rs.getObject("id"), (UUID) rs.getObject("board_id"),
                rs.getString("name"), rs.getString("color"));
    }

    private static Timestamp at(Instant value) { return Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static TaskApiException notFound(String code, String message) {
        return new TaskApiException(HttpStatus.NOT_FOUND, code, message);
    }
    private static TaskApiException badRequest(String code, String message) {
        return new TaskApiException(HttpStatus.BAD_REQUEST, code, message);
    }
    private static TaskApiException forbidden() {
        return new TaskApiException(HttpStatus.FORBIDDEN, "BOARD_ACCESS_DENIED",
                "Bạn không có quyền thao tác trên bảng");
    }

    public record Download(String filename, byte[] bytes) {}
}
