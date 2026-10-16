-- Additive collaboration schema. Existing project tasks remain the source of truth for cards.
CREATE TABLE project_boards (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES team_projects(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    visibility VARCHAR(20) NOT NULL DEFAULT 'WORKSPACE',
    background VARCHAR(40) NOT NULL DEFAULT 'indigo',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_project_board_visibility CHECK (visibility IN ('WORKSPACE', 'PRIVATE')),
    CONSTRAINT uq_project_board_pair UNIQUE (project_id, id)
);
CREATE UNIQUE INDEX uq_project_default_board ON project_boards(project_id) WHERE is_default;
CREATE INDEX idx_project_boards_project ON project_boards(project_id, created_at);

CREATE TABLE board_members (
    board_id UUID NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (board_id, user_id),
    CONSTRAINT chk_board_member_role CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'))
);
CREATE INDEX idx_board_members_user ON board_members(user_id);

CREATE TABLE board_lists (
    id UUID PRIMARY KEY,
    board_id UUID NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    position BIGINT NOT NULL,
    workflow_status VARCHAR(30),
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_board_list_status CHECK (workflow_status IS NULL OR workflow_status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT uq_board_list_pair UNIQUE (board_id, id)
);
CREATE UNIQUE INDEX uq_board_list_workflow ON board_lists(board_id, workflow_status) WHERE workflow_status IS NOT NULL;
CREATE INDEX idx_board_lists_order ON board_lists(board_id, position, id);

CREATE TABLE board_cards (
    task_id UUID PRIMARY KEY REFERENCES team_project_tasks(id) ON DELETE CASCADE,
    board_id UUID NOT NULL,
    list_id UUID NOT NULL,
    position BIGINT NOT NULL,
    placed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_board_card_list FOREIGN KEY (board_id, list_id) REFERENCES board_lists(board_id, id)
);
CREATE INDEX idx_board_cards_order ON board_cards(list_id, position, task_id);

-- The default board/list set is created for old projects and for every new project.
CREATE FUNCTION create_default_project_board() RETURNS TRIGGER AS $$
DECLARE
    new_board UUID := gen_random_uuid();
    now_at TIMESTAMP WITH TIME ZONE := now();
BEGIN
    INSERT INTO project_boards(id, project_id, title, description, is_default, created_at, updated_at)
    VALUES (new_board, NEW.id, 'Bảng công việc', NULL, TRUE, now_at, now_at);
    INSERT INTO board_lists(id, board_id, title, position, workflow_status, created_at, updated_at)
    VALUES
      (gen_random_uuid(), new_board, 'Cần làm', 1000, 'TODO', now_at, now_at),
      (gen_random_uuid(), new_board, 'Đang làm', 2000, 'IN_PROGRESS', now_at, now_at),
      (gen_random_uuid(), new_board, 'Hoàn thành', 3000, 'COMPLETED', now_at, now_at),
      (gen_random_uuid(), new_board, 'Đã hủy', 4000, 'CANCELLED', now_at, now_at);
    INSERT INTO board_members(board_id, user_id, role, joined_at)
    VALUES (new_board, NEW.owner_id, 'ADMIN', now_at);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_default_project_board AFTER INSERT ON team_projects
    FOR EACH ROW EXECUTE FUNCTION create_default_project_board();

INSERT INTO project_boards(id, project_id, title, description, is_default, created_at, updated_at)
SELECT gen_random_uuid(), p.id, 'Bảng công việc', NULL, TRUE, now(), now()
FROM team_projects p;
INSERT INTO board_lists(id, board_id, title, position, workflow_status, created_at, updated_at)
SELECT gen_random_uuid(), b.id, v.title, v.position, v.workflow_status, now(), now()
FROM project_boards b
CROSS JOIN (VALUES ('Cần làm', 1000, 'TODO'), ('Đang làm', 2000, 'IN_PROGRESS'),
                   ('Hoàn thành', 3000, 'COMPLETED'), ('Đã hủy', 4000, 'CANCELLED'))
    AS v(title, position, workflow_status);
INSERT INTO board_members(board_id, user_id, role, joined_at)
SELECT b.id, m.user_id, CASE WHEN m.role = 'OWNER' THEN 'ADMIN' ELSE 'EDITOR' END, m.joined_at
FROM project_boards b JOIN team_project_members m ON m.project_id = b.project_id;
INSERT INTO board_cards(task_id, board_id, list_id, position, placed_at)
SELECT t.id, b.id, l.id,
       row_number() OVER (PARTITION BY l.id ORDER BY t.created_at, t.id) * 1000, now()
FROM team_project_tasks t
JOIN project_boards b ON b.project_id = t.project_id AND b.is_default
JOIN board_lists l ON l.board_id = b.id AND l.workflow_status = t.status;

-- Legacy project task APIs continue to place new tasks and track status changes.
CREATE FUNCTION sync_project_task_card() RETURNS TRIGGER AS $$
DECLARE
    target_board UUID;
    target_list UUID;
    existing_list_status VARCHAR(30);
    next_position BIGINT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT b.id, l.id INTO target_board, target_list
        FROM project_boards b JOIN board_lists l ON l.board_id = b.id
        WHERE b.project_id = NEW.project_id AND b.is_default AND l.workflow_status = NEW.status;
        PERFORM 1 FROM board_lists WHERE id = target_list FOR UPDATE;
        SELECT COALESCE(MAX(position), 0) + 1000 INTO next_position FROM board_cards WHERE list_id = target_list;
        INSERT INTO board_cards(task_id, board_id, list_id, position, placed_at)
        VALUES (NEW.id, target_board, target_list, next_position, now());
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        SELECT l.workflow_status INTO existing_list_status
        FROM board_cards c JOIN board_lists l ON l.id = c.list_id WHERE c.task_id = NEW.id;
        IF existing_list_status IS NOT NULL THEN
            SELECT l.id INTO target_list FROM board_cards c JOIN board_lists l ON l.board_id = c.board_id
            WHERE c.task_id = NEW.id AND l.workflow_status = NEW.status;
            IF target_list IS NOT NULL THEN
                PERFORM 1 FROM board_lists WHERE id = target_list FOR UPDATE;
                SELECT COALESCE(MAX(position), 0) + 1000 INTO next_position FROM board_cards WHERE list_id = target_list;
                UPDATE board_cards SET list_id = target_list, position = next_position WHERE task_id = NEW.id;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_sync_project_task_card AFTER INSERT OR UPDATE OF status ON team_project_tasks
    FOR EACH ROW EXECUTE FUNCTION sync_project_task_card();
