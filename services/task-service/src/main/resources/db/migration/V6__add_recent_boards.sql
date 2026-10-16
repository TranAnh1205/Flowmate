CREATE TABLE board_recent_views (
    user_id UUID NOT NULL,
    board_id UUID NOT NULL REFERENCES project_boards(id) ON DELETE CASCADE,
    viewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, board_id)
);
CREATE INDEX idx_board_recent_views_user ON board_recent_views(user_id, viewed_at DESC);
