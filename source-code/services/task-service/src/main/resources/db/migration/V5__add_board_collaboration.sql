CREATE TABLE card_comments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES team_project_tasks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    body VARCHAR(5000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_card_comments_task ON card_comments(task_id, created_at, id);

CREATE TABLE card_checklist_items (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES team_project_tasks(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    is_done BOOLEAN NOT NULL DEFAULT FALSE,
    position BIGINT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_card_checklist_task ON card_checklist_items(task_id, position, id);

CREATE TABLE board_labels (
    id UUID PRIMARY KEY,
    board_id UUID NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    color VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_board_labels_board ON board_labels(board_id);

CREATE TABLE card_labels (
    task_id UUID NOT NULL REFERENCES team_project_tasks(id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES board_labels(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, label_id)
);

CREATE TABLE card_watchers (
    task_id UUID NOT NULL REFERENCES team_project_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (task_id, user_id)
);

-- Local/test storage in PostgreSQL; move bytes to object storage before a large production rollout.
CREATE TABLE card_attachments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES team_project_tasks(id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    bytes BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_card_attachment_size CHECK (byte_size > 0 AND byte_size <= 5242880)
);
CREATE INDEX idx_card_attachments_task ON card_attachments(task_id, created_at);

CREATE TABLE board_messages (
    id UUID PRIMARY KEY,
    board_id UUID NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    body VARCHAR(5000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_board_messages_board ON board_messages(board_id, created_at DESC, id);

CREATE TABLE board_events (
    id BIGSERIAL PRIMARY KEY,
    board_id UUID NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    task_id UUID REFERENCES team_project_tasks(id) ON DELETE SET NULL,
    actor_id UUID,
    kind VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_board_events_board ON board_events(board_id, id);
CREATE INDEX idx_board_events_card ON board_events(task_id, id) WHERE task_id IS NOT NULL;
