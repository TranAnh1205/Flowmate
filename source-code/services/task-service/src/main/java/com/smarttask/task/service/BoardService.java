package com.smarttask.task.service;

import com.smarttask.task.domain.TaskPriority;
import com.smarttask.task.domain.TaskStatus;
import com.smarttask.task.dto.BoardDtos.*;
import com.smarttask.task.dto.ProjectDtos.CreateProjectTaskRequest;
import com.smarttask.task.dto.ProjectDtos.ProjectTaskResponse;
import com.smarttask.task.dto.ProjectDtos.UpdateProjectTaskRequest;
import com.smarttask.task.exception.TaskApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class BoardService {
    private final JdbcTemplate jdbc;
    private final ProjectService projects;

    public BoardService(JdbcTemplate jdbc, ProjectService projects) {
        this.jdbc = jdbc;
        this.projects = projects;
    }

    public BoardSummary access(UUID userId, UUID projectId, UUID boardId) {
        return requireBoard(userId, projectId, boardId);
    }

    public void editAccess(UUID userId, UUID projectId, UUID boardId) {
        requireEditor(userId, projectId, boardId);
        requireActive(projectId);
    }

    @Transactional
    public void recordView(UUID userId, UUID projectId, UUID boardId) {
        requireBoard(userId, projectId, boardId);
        jdbc.update("INSERT INTO board_recent_views(user_id, board_id, viewed_at) VALUES (?, ?, ?) " +
                        "ON CONFLICT (user_id, board_id) DO UPDATE SET viewed_at = EXCLUDED.viewed_at",
                userId, boardId, at(Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<RecentBoard> recent(UUID userId) {
        List<RecentRow> rows = jdbc.query("SELECT v.board_id, b.project_id, p.name AS project_name, " +
                        "v.viewed_at FROM board_recent_views v JOIN project_boards b ON b.id = v.board_id " +
                        "JOIN team_projects p ON p.id = b.project_id " +
                        "WHERE v.user_id = ? AND b.archived_at IS NULL ORDER BY v.viewed_at DESC LIMIT 20",
                (rs, n) -> new RecentRow((UUID) rs.getObject("board_id"),
                        (UUID) rs.getObject("project_id"), rs.getString("project_name"),
                        instant(rs.getTimestamp("viewed_at"))), userId);
        List<RecentBoard> visible = new ArrayList<>();
        for (RecentRow row : rows) {
            try {
                visible.add(new RecentBoard(requireBoard(userId, row.projectId(), row.boardId()),
                        row.projectName(), row.viewedAt()));
            } catch (TaskApiException ignored) {
                // Membership may have been revoked since the last visit.
            }
        }
        return visible;
    }

    @Transactional(readOnly = true)
    public List<BoardSummary> boards(UUID userId, UUID projectId) {
        requireProjectMember(userId, projectId);
        return jdbc.query(
                "SELECT b.*, pm.role AS project_role, bm.role AS board_role FROM project_boards b " +
                "JOIN team_project_members pm ON pm.project_id = b.project_id AND pm.user_id = ? " +
                "LEFT JOIN board_members bm ON bm.board_id = b.id AND bm.user_id = ? " +
                "WHERE b.project_id = ? AND b.archived_at IS NULL " +
                "ORDER BY b.is_default DESC, b.created_at, b.id",
                (rs, n) -> summary(rs, role(rs.getString("project_role"),
                        rs.getString("board_role"), rs.getString("visibility"))),
                userId, userId, projectId).stream().filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public BoardDetails board(UUID userId, UUID projectId, UUID boardId) {
        BoardSummary board = requireBoard(userId, projectId, boardId);
        Map<UUID, List<BoardCard>> cardsByList = new HashMap<>();
        jdbc.query("SELECT c.list_id, c.position, t.id, t.title, t.description, t.status, t.priority, " +
                "t.assignee_id, t.start_at, t.due_at, t.version FROM board_cards c " +
                "JOIN team_project_tasks t ON t.id = c.task_id " +
                "WHERE c.board_id = ? AND t.project_id = ? AND t.deleted_at IS NULL " +
                "ORDER BY c.list_id, c.position, t.id",
                (rs, n) -> new CardRow((UUID) rs.getObject("list_id"), card(rs)),
                boardId, projectId).forEach(row ->
                cardsByList.computeIfAbsent(row.listId(), ignored -> new ArrayList<>()).add(row.card()));
        List<BoardList> lists = jdbc.query(
                "SELECT id, title, position, workflow_status FROM board_lists " +
                "WHERE board_id = ? AND archived_at IS NULL ORDER BY position, id",
                (rs, n) -> new BoardList((UUID) rs.getObject("id"), rs.getString("title"),
                        rs.getLong("position"), rs.getString("workflow_status"),
                        cardsByList.getOrDefault((UUID) rs.getObject("id"), List.of())),
                boardId);
        return new BoardDetails(board, lists);
    }

    @Transactional
    public BoardSummary create(UUID userId, UUID projectId, CreateBoardRequest request) {
        if (!"OWNER".equals(requireProjectMember(userId, projectId))) throw forbidden();
        requireActive(projectId);
        requireSupportedVisibility(request.visibility());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO project_boards(id, project_id, title, description, visibility, " +
                        "background, is_default, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, FALSE, ?, ?)",
                id, projectId, request.title().trim(), request.description(),
                request.visibility() == null ? "WORKSPACE" : request.visibility(),
                background(request.background()), at(now), at(now));
        String[][] defaults = switch (request.template() == null ? "basic" : request.template()) {
            case "sprint" -> new String[][] {{"Backlog", "TODO"}, {"Đang làm", "IN_PROGRESS"},
                    {"Review", null}, {"Hoàn thành", "COMPLETED"}, {"Đã hủy", "CANCELLED"}};
            case "content" -> new String[][] {{"Ý tưởng", "TODO"}, {"Bản nháp", "IN_PROGRESS"},
                    {"Duyệt nội dung", null}, {"Đã xuất bản", "COMPLETED"}, {"Đã hủy", "CANCELLED"}};
            default -> new String[][] {{"Cần làm", "TODO"}, {"Đang làm", "IN_PROGRESS"},
                    {"Hoàn thành", "COMPLETED"}, {"Đã hủy", "CANCELLED"}};
        };
        for (int i = 0; i < defaults.length; i++) {
            jdbc.update("INSERT INTO board_lists(id, board_id, title, position, workflow_status, " +
                            "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), id, defaults[i][0], (i + 1L) * 1000,
                    defaults[i][1], at(now), at(now));
        }
        jdbc.update("INSERT INTO board_members(board_id, user_id, role, joined_at) VALUES (?, ?, 'ADMIN', ?)",
                id, userId, at(now));
        event(id, null, userId, "BOARD_CREATED");
        return requireBoard(userId, projectId, id);
    }

    @Transactional
    public BoardSummary update(UUID userId, UUID projectId, UUID boardId, UpdateBoardRequest request) {
        requireAdmin(userId, projectId, boardId);
        requireActive(projectId);
        requireSupportedVisibility(request.visibility());
        jdbc.update("UPDATE project_boards SET title = ?, description = ?, visibility = ?, background = ?, " +
                        "updated_at = ? WHERE id = ? AND project_id = ? AND archived_at IS NULL",
                request.title().trim(), request.description(), request.visibility(),
                background(request.background()), at(Instant.now()), boardId, projectId);
        event(boardId, null, userId, "BOARD_UPDATED");
        return requireBoard(userId, projectId, boardId);
    }

    @Transactional
    public BoardSummary copyBoard(UUID userId, UUID projectId, UUID boardId, CopyBoardRequest request) {
        requireAdmin(userId, projectId, boardId);
        BoardSummary source = requireBoard(userId, projectId, boardId);
        requireActive(projectId);
        lockBoard(boardId);
        Instant now = Instant.now();
        UUID copyId = UUID.randomUUID();
        jdbc.update("INSERT INTO project_boards(id, project_id, title, description, visibility, " +
                        "background, is_default, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'WORKSPACE', ?, FALSE, ?, ?)",
                copyId, projectId, request.title().trim(), source.description(),
                source.background(), at(now), at(now));
        jdbc.update("INSERT INTO board_members(board_id, user_id, role, joined_at) " +
                        "VALUES (?, ?, 'ADMIN', ?)", copyId, userId, at(now));

        Map<UUID, UUID> copiedLists = new HashMap<>();
        List<ListCopy> lists = jdbc.query("SELECT id, title, position, workflow_status FROM board_lists " +
                        "WHERE board_id = ? AND archived_at IS NULL ORDER BY position, id",
                (rs, n) -> new ListCopy((UUID) rs.getObject("id"), rs.getString("title"),
                        rs.getLong("position"), rs.getString("workflow_status")), boardId);
        for (ListCopy list : lists) {
            UUID newId = UUID.randomUUID();
            copiedLists.put(list.id(), newId);
            jdbc.update("INSERT INTO board_lists(id, board_id, title, position, workflow_status, " +
                            "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    newId, copyId, list.title(), list.position(), list.workflowStatus(), at(now), at(now));
        }

        Map<UUID, UUID> copiedLabels = new HashMap<>();
        List<LabelCopy> labels = jdbc.query("SELECT id, name, color FROM board_labels WHERE board_id = ?",
                (rs, n) -> new LabelCopy((UUID) rs.getObject("id"), rs.getString("name"),
                        rs.getString("color")), boardId);
        for (LabelCopy label : labels) {
            UUID newId = UUID.randomUUID();
            copiedLabels.put(label.id(), newId);
            jdbc.update("INSERT INTO board_labels(id, board_id, name, color, created_at) " +
                            "VALUES (?, ?, ?, ?, ?)", newId, copyId, label.name(), label.color(), at(now));
        }

        List<CardCopy> cards = jdbc.query("SELECT c.list_id, c.position, t.id, t.title, " +
                        "t.description, t.status, t.priority, t.assignee_id, t.start_at, t.due_at, t.completed_at " +
                        "FROM board_cards c JOIN team_project_tasks t ON t.id = c.task_id " +
                        "WHERE c.board_id = ? AND t.project_id = ? AND t.deleted_at IS NULL " +
                        "ORDER BY c.list_id, c.position, t.id",
                (rs, n) -> new CardCopy((UUID) rs.getObject("list_id"), rs.getLong("position"),
                        (UUID) rs.getObject("id"), rs.getString("title"), rs.getString("description"),
                        rs.getString("status"), rs.getString("priority"),
                        (UUID) rs.getObject("assignee_id"), instant(rs.getTimestamp("start_at")),
                        instant(rs.getTimestamp("due_at")),
                        instant(rs.getTimestamp("completed_at"))), boardId, projectId);
        for (CardCopy card : cards) {
            UUID taskId = UUID.randomUUID();
            jdbc.update("INSERT INTO team_project_tasks(id, project_id, title, description, status, " +
                            "priority, created_by, assignee_id, start_at, due_at, completed_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    taskId, projectId, card.title(), card.description(), card.status(),
                    card.priority(), userId, card.assigneeId(), atOrNull(card.startAt()),
                    atOrNull(card.dueAt()),
                    atOrNull(card.completedAt()), at(now), at(now));
            // The legacy INSERT trigger places new tasks on the default board first.
            jdbc.update("UPDATE board_cards SET board_id = ?, list_id = ?, position = ?, placed_at = ? " +
                            "WHERE task_id = ?", copyId, copiedLists.get(card.listId()),
                    card.position(), at(now), taskId);
            jdbc.update("INSERT INTO card_checklist_items(id, task_id, title, is_done, position, " +
                            "created_by, created_at, updated_at) " +
                            "SELECT gen_random_uuid(), ?, title, is_done, position, ?, ?, ? " +
                            "FROM card_checklist_items WHERE task_id = ?",
                    taskId, userId, at(now), at(now), card.id());
            List<UUID> cardLabelIds = jdbc.query("SELECT label_id FROM card_labels WHERE task_id = ?",
                    (rs, n) -> (UUID) rs.getObject(1), card.id());
            for (UUID labelId : cardLabelIds) {
                UUID targetLabel = copiedLabels.get(labelId);
                if (targetLabel != null) jdbc.update("INSERT INTO card_labels(task_id, label_id) VALUES (?, ?)",
                        taskId, targetLabel);
            }
        }
        event(copyId, null, userId, "BOARD_COPIED");
        return requireBoard(userId, projectId, copyId);
    }

    @Transactional
    public BoardList addList(UUID userId, UUID projectId, UUID boardId, ListRequest request) {
        requireEditor(userId, projectId, boardId);
        requireActive(projectId);
        lockBoard(boardId);
        Long max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(position), 0) FROM board_lists WHERE board_id = ?",
                Long.class, boardId);
        UUID id = UUID.randomUUID();
        long position = (max == null ? 0 : max) + 1000;
        Instant now = Instant.now();
        jdbc.update("INSERT INTO board_lists(id, board_id, title, position, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                id, boardId, request.title().trim(), position, at(now), at(now));
        event(boardId, null, userId, "LIST_CREATED");
        return new BoardList(id, request.title().trim(), position, null, List.of());
    }

    @Transactional
    public BoardList renameList(UUID userId, UUID projectId, UUID boardId, UUID listId, ListRequest request) {
        requireEditor(userId, projectId, boardId);
        requireActive(projectId);
        int count = jdbc.update("UPDATE board_lists SET title = ?, updated_at = ? " +
                        "WHERE id = ? AND board_id = ? AND archived_at IS NULL",
                request.title().trim(), at(Instant.now()), listId, boardId);
        if (count == 0) throw notFound("BOARD_LIST_NOT_FOUND", "Không tìm thấy danh sách");
        event(boardId, null, userId, "LIST_RENAMED");
        return jdbc.queryForObject(
                "SELECT id, title, position, workflow_status FROM board_lists WHERE id = ?",
                (rs, n) -> new BoardList((UUID) rs.getObject("id"), rs.getString("title"),
                        rs.getLong("position"), rs.getString("workflow_status"), List.of()), listId);
    }

    @Transactional
    public void reorderLists(UUID userId, UUID projectId, UUID boardId, ReorderListsRequest request) {
        requireEditor(userId, projectId, boardId);
        requireActive(projectId);
        lockBoard(boardId);
        List<UUID> current = jdbc.query(
                "SELECT id FROM board_lists WHERE board_id = ? AND archived_at IS NULL",
                (rs, n) -> (UUID) rs.getObject(1), boardId);
        if (request.listIds().size() != current.size() ||
                new HashSet<>(request.listIds()).size() != current.size() ||
                !new HashSet<>(request.listIds()).equals(new HashSet<>(current))) {
            throw badRequest("INVALID_LIST_ORDER", "Thứ tự danh sách không hợp lệ");
        }
        for (int i = 0; i < request.listIds().size(); i++) {
            jdbc.update("UPDATE board_lists SET position = ?, updated_at = ? WHERE id = ? AND board_id = ?",
                    (i + 1L) * 1000, at(Instant.now()), request.listIds().get(i), boardId);
        }
        event(boardId, null, userId, "LIST_REORDERED");
    }

    @Transactional
    public void archiveList(UUID userId, UUID projectId, UUID boardId, UUID listId) {
        requireEditor(userId, projectId, boardId);
        requireActive(projectId);
        lockBoard(boardId);
        ListState list = requireList(boardId, listId);
        if (list.workflowStatus() != null)
            throw badRequest("WORKFLOW_LIST_REQUIRED", "Không thể xóa cột trạng thái mặc định");
        Integer cards = jdbc.queryForObject("SELECT COUNT(*) FROM board_cards WHERE list_id = ?",
                Integer.class, listId);
        if (cards != null && cards > 0)
            throw conflict("LIST_NOT_EMPTY", "Chuyển hết thẻ ra khỏi danh sách trước khi xóa");
        jdbc.update("UPDATE board_lists SET archived_at = ?, updated_at = ? WHERE id = ? AND board_id = ?",
                at(Instant.now()), at(Instant.now()), listId, boardId);
        event(boardId, null, userId, "LIST_ARCHIVED");
    }

    @Transactional
    public BoardCard createCard(UUID userId, UUID projectId, UUID boardId, UUID listId,
                                CreateCardRequest request) {
        requireEditor(userId, projectId, boardId);
        String workflow = requireList(boardId, listId).workflowStatus();
        TaskStatus status = workflow == null ? TaskStatus.TODO : TaskStatus.valueOf(workflow);
        ProjectTaskResponse task = projects.createTask(userId, projectId,
                new CreateProjectTaskRequest(request.title(), request.description(), status,
                        request.priority(), request.assigneeId(), request.startAt(), request.dueAt()));
        jdbc.update("UPDATE board_cards SET board_id = ?, list_id = ? WHERE task_id = ?",
                boardId, listId, task.id());
        event(boardId, task.id(), userId, "CARD_CREATED");
        Integer index = jdbc.queryForObject("SELECT COUNT(*) FROM board_cards WHERE list_id = ?",
                Integer.class, listId);
        return moveCard(userId, projectId, boardId, task.id(),
                new MoveCardRequest(listId, index == null ? 0 : index - 1, task.version()));
    }

    @Transactional
    public BoardCard updateCard(UUID userId, UUID projectId, UUID boardId, UUID taskId,
                                UpdateProjectTaskRequest request) {
        requireEditor(userId, projectId, boardId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM board_cards WHERE task_id = ? AND board_id = ?",
                Integer.class, taskId, boardId);
        if (count == null || count == 0)
            throw notFound("BOARD_CARD_NOT_FOUND", "Không tìm thấy thẻ");
        projects.updateTask(userId, projectId, taskId, request);
        event(boardId, taskId, userId, "CARD_UPDATED");
        return jdbc.queryForObject(
                "SELECT t.id, t.title, t.description, t.status, t.priority, t.assignee_id, " +
                "t.start_at, t.due_at, t.version, c.position FROM team_project_tasks t " +
                "JOIN board_cards c ON c.task_id = t.id WHERE t.id = ?",
                (rs, n) -> card(rs), taskId);
    }

    @Transactional
    public BoardCard copyCard(UUID userId, UUID projectId, UUID boardId, UUID taskId,
                              CopyCardRequest request) {
        requireBoard(userId, projectId, boardId);
        requireEditor(userId, projectId, request.targetBoardId());
        requireActive(projectId);
        requireCardOnBoard(projectId, boardId, taskId);
        String workflow = requireList(request.targetBoardId(), request.listId()).workflowStatus();
        ProjectTaskResponse source = projects.task(userId, projectId, taskId);
        TaskStatus status = workflow == null ? source.status() : TaskStatus.valueOf(workflow);
        String projectRole = requireProjectMember(userId, projectId);
        UUID assignee = "OWNER".equals(projectRole) || userId.equals(source.assigneeId())
                ? source.assigneeId() : userId;
        String suffix = " (bản sao)";
        String copyTitle = source.title().substring(0, Math.min(source.title().length(), 255 - suffix.length())) + suffix;
        ProjectTaskResponse copied = projects.createTask(userId, projectId,
                new CreateProjectTaskRequest(copyTitle, source.description(),
                        status, source.priority(), assignee, source.startAt(), source.dueAt()));
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(position), 0) FROM board_cards WHERE list_id = ?",
                Long.class, request.listId());
        jdbc.update("UPDATE board_cards SET board_id = ?, list_id = ?, position = ? WHERE task_id = ?",
                request.targetBoardId(), request.listId(), (max == null ? 0 : max) + 1000, copied.id());
        jdbc.update("INSERT INTO card_checklist_items(id, task_id, title, is_done, position, created_by, " +
                        "created_at, updated_at) SELECT gen_random_uuid(), ?, title, is_done, position, ?, " +
                        "now(), now() FROM card_checklist_items WHERE task_id = ?",
                copied.id(), userId, taskId);
        if (boardId.equals(request.targetBoardId())) {
            jdbc.update("INSERT INTO card_labels(task_id, label_id) SELECT ?, label_id " +
                    "FROM card_labels WHERE task_id = ?", copied.id(), taskId);
        }
        event(request.targetBoardId(), copied.id(), userId, "CARD_COPIED");
        return cardById(copied.id());
    }

    @Transactional
    public BoardCard transferCard(UUID userId, UUID projectId, UUID boardId, UUID taskId,
                                  TransferCardRequest request) {
        if (boardId.equals(request.targetBoardId())) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM board_cards WHERE list_id = ?",
                    Integer.class, request.listId());
            Integer alreadyThere = jdbc.queryForObject("SELECT COUNT(*) FROM board_cards WHERE task_id = ? AND list_id = ?",
                    Integer.class, taskId, request.listId());
            return moveCard(userId, projectId, boardId, taskId,
                    new MoveCardRequest(request.listId(), Math.max(0, (count == null ? 0 : count) -
                            (alreadyThere == null ? 0 : alreadyThere)), request.version()));
        }
        requireEditor(userId, projectId, boardId);
        requireEditor(userId, projectId, request.targetBoardId());
        requireActive(projectId);
        UUID first = boardId.compareTo(request.targetBoardId()) < 0 ? boardId : request.targetBoardId();
        UUID second = first.equals(boardId) ? request.targetBoardId() : boardId;
        lockBoard(first);
        lockBoard(second);
        String workflow = requireList(request.targetBoardId(), request.listId()).workflowStatus();
        requireCardOnBoard(projectId, boardId, taskId);
        ProjectTaskResponse task = projects.task(userId, projectId, taskId);
        String projectRole = requireProjectMember(userId, projectId);
        if (!"OWNER".equals(projectRole) && !userId.equals(task.createdBy()) &&
                !userId.equals(task.assigneeId())) throw forbidden();
        if (task.version() != request.version())
            throw conflict("STALE_PROJECT_TASK", "Thẻ đã được cập nhật. Vui lòng tải lại.");
        UUID oldList = jdbc.queryForObject("SELECT list_id FROM board_cards WHERE task_id = ? FOR UPDATE",
                UUID.class, taskId);
        String status = workflow == null ? task.status().name() : workflow;
        Instant now = Instant.now();
        int changed = jdbc.update("UPDATE team_project_tasks SET status = ?, " +
                        "completed_at = CASE WHEN ? = 'COMPLETED' THEN COALESCE(completed_at, ?) ELSE NULL END, " +
                        "updated_at = ?, version = version + 1 WHERE id = ? AND project_id = ? AND version = ?",
                status, status, at(now), at(now), taskId, projectId, task.version());
        if (changed != 1) throw conflict("STALE_PROJECT_TASK", "Thẻ đã được cập nhật. Vui lòng tải lại.");
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(position), 0) FROM board_cards WHERE list_id = ?",
                Long.class, request.listId());
        jdbc.update("UPDATE board_cards SET board_id = ?, list_id = ?, position = ? WHERE task_id = ?",
                request.targetBoardId(), request.listId(), (max == null ? 0 : max) + 1000, taskId);
        jdbc.update("DELETE FROM card_labels WHERE task_id = ? AND label_id IN " +
                "(SELECT id FROM board_labels WHERE board_id = ?)", taskId, boardId);
        renumber(oldList, cardIds(oldList, taskId));
        event(boardId, null, userId, "CARD_TRANSFERRED_OUT");
        event(request.targetBoardId(), taskId, userId, "CARD_TRANSFERRED_IN");
        return cardById(taskId);
    }

    @Transactional
    public void archiveCard(UUID userId, UUID projectId, UUID boardId, UUID taskId) {
        requireEditor(userId, projectId, boardId);
        requireCardOnBoard(projectId, boardId, taskId);
        projects.deleteTask(userId, projectId, taskId);
        event(boardId, taskId, userId, "CARD_ARCHIVED");
    }

    @Transactional
    public BoardCard restoreCard(UUID userId, UUID projectId, UUID boardId, UUID taskId) {
        requireEditor(userId, projectId, boardId);
        requireActive(projectId);
        UUID creator = jdbc.query("SELECT t.created_by FROM team_project_tasks t " +
                        "JOIN board_cards c ON c.task_id = t.id WHERE t.id = ? AND t.project_id = ? " +
                        "AND c.board_id = ? AND t.deleted_at IS NOT NULL",
                (rs, n) -> (UUID) rs.getObject(1), taskId, projectId, boardId).stream().findFirst()
                .orElseThrow(() -> notFound("BOARD_CARD_NOT_FOUND", "Không tìm thấy thẻ đã lưu trữ"));
        if (!"OWNER".equals(requireProjectMember(userId, projectId)) && !userId.equals(creator)) throw forbidden();
        jdbc.update("UPDATE team_project_tasks SET deleted_at = NULL, updated_at = ?, version = version + 1 " +
                "WHERE id = ? AND project_id = ?", at(Instant.now()), taskId, projectId);
        event(boardId, taskId, userId, "CARD_RESTORED");
        return cardById(taskId);
    }

    @Transactional(readOnly = true)
    public List<BoardCard> archivedCards(UUID userId, UUID projectId, UUID boardId) {
        requireBoard(userId, projectId, boardId);
        return jdbc.query("SELECT t.id, t.title, t.description, t.status, t.priority, t.assignee_id, " +
                        "t.start_at, t.due_at, t.version, c.position FROM team_project_tasks t " +
                        "JOIN board_cards c ON c.task_id = t.id " +
                        "WHERE t.project_id = ? AND c.board_id = ? AND t.deleted_at IS NOT NULL " +
                        "ORDER BY t.deleted_at DESC LIMIT 100",
                (rs, n) -> card(rs), projectId, boardId);
    }

    @Transactional
    public BoardCard moveCard(UUID userId, UUID projectId, UUID boardId, UUID taskId, MoveCardRequest request) {
        requireEditor(userId, projectId, boardId);
        requireActive(projectId);
        lockBoard(boardId);
        String workflow = requireList(boardId, request.listId()).workflowStatus();
        List<CardState> states = jdbc.query(
                "SELECT t.created_by, t.assignee_id, t.status, t.version, c.list_id " +
                "FROM team_project_tasks t JOIN board_cards c ON c.task_id = t.id " +
                "WHERE t.id = ? AND t.project_id = ? AND c.board_id = ? AND t.deleted_at IS NULL FOR UPDATE",
                (rs, n) -> new CardState((UUID) rs.getObject("created_by"),
                        (UUID) rs.getObject("assignee_id"), rs.getString("status"),
                        rs.getLong("version"), (UUID) rs.getObject("list_id")),
                taskId, projectId, boardId);
        if (states.isEmpty()) throw notFound("BOARD_CARD_NOT_FOUND", "Không tìm thấy thẻ");
        CardState state = states.getFirst();
        String projectRole = requireProjectMember(userId, projectId);
        if (!"OWNER".equals(projectRole) && !userId.equals(state.createdBy()) &&
                !userId.equals(state.assigneeId())) throw forbidden();
        if (state.version() != request.version())
            throw conflict("STALE_PROJECT_TASK", "Thẻ đã được cập nhật. Vui lòng tải lại.");
        String status = workflow == null ? state.status() : workflow;
        Instant now = Instant.now();
        int changed = jdbc.update("UPDATE team_project_tasks SET status = ?, " +
                        "completed_at = CASE WHEN ? = 'COMPLETED' THEN COALESCE(completed_at, ?) ELSE NULL END, " +
                        "updated_at = ?, version = version + 1 " +
                        "WHERE id = ? AND project_id = ? AND version = ? AND deleted_at IS NULL",
                status, status, at(now), at(now), taskId, projectId, state.version());
        if (changed != 1) throw conflict("STALE_PROJECT_TASK", "Thẻ đã được cập nhật. Vui lòng tải lại.");
        jdbc.update("UPDATE board_cards SET list_id = ?, position = ? WHERE task_id = ? AND board_id = ?",
                request.listId(), Long.MAX_VALUE, taskId, boardId);
        List<UUID> destination = cardIds(request.listId(), taskId);
        if (request.index() > destination.size())
            throw badRequest("INVALID_CARD_POSITION", "Vị trí thẻ không hợp lệ");
        destination.add(request.index(), taskId);
        renumber(request.listId(), destination);
        if (!state.listId().equals(request.listId()))
            renumber(state.listId(), cardIds(state.listId(), taskId));
        event(boardId, taskId, userId, "CARD_MOVED");
        return jdbc.queryForObject(
                "SELECT t.id, t.title, t.description, t.status, t.priority, t.assignee_id, " +
                        "t.start_at, t.due_at, t.version, c.position FROM team_project_tasks t " +
                "JOIN board_cards c ON c.task_id = t.id WHERE t.id = ?",
                (rs, n) -> card(rs), taskId);
    }

    private List<UUID> cardIds(UUID listId, UUID excluded) {
        return new ArrayList<>(jdbc.query(
                "SELECT c.task_id FROM board_cards c JOIN team_project_tasks t ON t.id = c.task_id " +
                "WHERE c.list_id = ? AND c.task_id <> ? AND t.deleted_at IS NULL " +
                "ORDER BY c.position, c.task_id",
                (rs, n) -> (UUID) rs.getObject(1), listId, excluded));
    }

    private void requireCardOnBoard(UUID projectId, UUID boardId, UUID taskId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM board_cards c " +
                "JOIN team_project_tasks t ON t.id = c.task_id " +
                "WHERE c.task_id = ? AND c.board_id = ? AND t.project_id = ? AND t.deleted_at IS NULL",
                Integer.class, taskId, boardId, projectId);
        if (count == null || count == 0)
            throw notFound("BOARD_CARD_NOT_FOUND", "Không tìm thấy thẻ");
    }

    private BoardCard cardById(UUID taskId) {
        return jdbc.queryForObject("SELECT t.id, t.title, t.description, t.status, t.priority, " +
                        "t.assignee_id, t.start_at, t.due_at, t.version, c.position FROM team_project_tasks t " +
                        "JOIN board_cards c ON c.task_id = t.id WHERE t.id = ?",
                (rs, n) -> card(rs), taskId);
    }

    private void renumber(UUID listId, List<UUID> ids) {
        for (int i = 0; i < ids.size(); i++)
            jdbc.update("UPDATE board_cards SET position = ? WHERE task_id = ? AND list_id = ?",
                    (i + 1L) * 1000, ids.get(i), listId);
    }

    private ListState requireList(UUID boardId, UUID listId) {
        return jdbc.query(
                "SELECT id, workflow_status FROM board_lists WHERE id = ? AND board_id = ? AND archived_at IS NULL",
                (rs, n) -> new ListState((UUID) rs.getObject("id"), rs.getString("workflow_status")),
                listId, boardId).stream().findFirst()
                .orElseThrow(() -> notFound("BOARD_LIST_NOT_FOUND", "Không tìm thấy danh sách"));
    }

    private void lockBoard(UUID boardId) {
        jdbc.queryForObject("SELECT id FROM project_boards WHERE id = ? FOR UPDATE", UUID.class, boardId);
    }

    private String requireProjectMember(UUID userId, UUID projectId) {
        return jdbc.query("SELECT role FROM team_project_members WHERE project_id = ? AND user_id = ?",
                (rs, n) -> rs.getString(1), projectId, userId).stream().findFirst()
                .orElseThrow(() -> notFound("PROJECT_NOT_FOUND", "Không tìm thấy dự án"));
    }

    private void requireActive(UUID projectId) {
        String status = jdbc.queryForObject("SELECT status FROM team_projects WHERE id = ?",
                String.class, projectId);
        if (!"ACTIVE".equals(status)) throw conflict("PROJECT_ARCHIVED", "Dự án đã lưu trữ");
    }

    private BoardSummary requireBoard(UUID userId, UUID projectId, UUID boardId) {
        requireProjectMember(userId, projectId);
        return jdbc.query("SELECT b.*, pm.role AS project_role, bm.role AS board_role " +
                        "FROM project_boards b JOIN team_project_members pm " +
                        "ON pm.project_id = b.project_id AND pm.user_id = ? " +
                        "LEFT JOIN board_members bm ON bm.board_id = b.id AND bm.user_id = ? " +
                        "WHERE b.id = ? AND b.project_id = ? AND b.archived_at IS NULL",
                (rs, n) -> summary(rs, role(rs.getString("project_role"),
                        rs.getString("board_role"), rs.getString("visibility"))),
                userId, userId, boardId, projectId).stream().filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> notFound("BOARD_NOT_FOUND", "Không tìm thấy bảng"));
    }

    private BoardSummary summary(ResultSet rs, String role) throws SQLException {
        if (role == null) return null;
        return new BoardSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("project_id"),
                rs.getString("title"), rs.getString("description"), rs.getString("visibility"),
                rs.getString("background"), role, rs.getBoolean("is_default"),
                instant(rs.getTimestamp("created_at")));
    }

    private String role(String projectRole, String boardRole, String visibility) {
        if ("OWNER".equals(projectRole)) return "ADMIN";
        if (boardRole != null) return boardRole;
        return "WORKSPACE".equals(visibility) ? "EDITOR" : null;
    }

    private void requireEditor(UUID userId, UUID projectId, UUID boardId) {
        String role = requireBoard(userId, projectId, boardId).myRole();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role)) throw forbidden();
    }

    private void requireAdmin(UUID userId, UUID projectId, UUID boardId) {
        if (!"ADMIN".equals(requireBoard(userId, projectId, boardId).myRole())) throw forbidden();
    }

    private String background(String value) {
        if (value == null || value.isBlank()) return "indigo";
        if (!Set.of("indigo", "blue", "green", "rose", "slate").contains(value))
            throw badRequest("INVALID_BACKGROUND", "Màu nền không hợp lệ");
        return value;
    }

    private void event(UUID boardId, UUID taskId, UUID actorId, String kind) {
        jdbc.update("INSERT INTO board_events(board_id, task_id, actor_id, kind, created_at) " +
                        "VALUES (?, ?, ?, ?, ?)", boardId, taskId, actorId, kind, at(Instant.now()));
    }

    private void requireSupportedVisibility(String value) {
        // Legacy /projects/{id}/tasks APIs still expose project tasks to every project member.
        // Private boards must stay disabled until those APIs enforce board-level visibility too.
        if ("PRIVATE".equals(value))
            throw badRequest("PRIVATE_BOARD_NOT_READY", "Bảng riêng tư chưa khả dụng");
    }

    private BoardCard card(ResultSet rs) throws SQLException {
        return new BoardCard((UUID) rs.getObject("id"), rs.getString("title"),
                rs.getString("description"), TaskStatus.valueOf(rs.getString("status")),
                TaskPriority.valueOf(rs.getString("priority")), (UUID) rs.getObject("assignee_id"),
                instant(rs.getTimestamp("start_at")), instant(rs.getTimestamp("due_at")),
                rs.getLong("version"), rs.getLong("position"));
    }

    private static Timestamp at(Instant value) { return Timestamp.from(value); }
    private static Timestamp atOrNull(Instant value) { return value == null ? null : at(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static TaskApiException notFound(String code, String message) {
        return new TaskApiException(HttpStatus.NOT_FOUND, code, message);
    }
    private static TaskApiException badRequest(String code, String message) {
        return new TaskApiException(HttpStatus.BAD_REQUEST, code, message);
    }
    private static TaskApiException conflict(String code, String message) {
        return new TaskApiException(HttpStatus.CONFLICT, code, message);
    }
    private static TaskApiException forbidden() {
        return new TaskApiException(HttpStatus.FORBIDDEN, "BOARD_ACCESS_DENIED",
                "Bạn không có quyền thao tác trên bảng");
    }

    private record CardRow(UUID listId, BoardCard card) {}
    private record CardState(UUID createdBy, UUID assigneeId, String status, long version, UUID listId) {}
    private record ListState(UUID id, String workflowStatus) {}
    private record RecentRow(UUID boardId, UUID projectId, String projectName, Instant viewedAt) {}
    private record ListCopy(UUID id, String title, long position, String workflowStatus) {}
    private record LabelCopy(UUID id, String name, String color) {}
    private record CardCopy(UUID listId, long position, UUID id, String title, String description,
                            String status, String priority, UUID assigneeId, Instant startAt, Instant dueAt,
                            Instant completedAt) {}
}
