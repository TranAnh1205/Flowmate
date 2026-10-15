CREATE TABLE categories (
 id UUID PRIMARY KEY, user_id UUID NOT NULL, name VARCHAR(80) NOT NULL,
 color VARCHAR(20) NOT NULL, icon VARCHAR(50), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(user_id, name)
);
CREATE INDEX idx_categories_user ON categories(user_id);

CREATE TABLE goals (
 id UUID PRIMARY KEY, user_id UUID NOT NULL, title VARCHAR(160) NOT NULL, description TEXT,
 status VARCHAR(20) NOT NULL, target_date DATE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_goals_user ON goals(user_id);

CREATE TABLE tasks (
 id UUID PRIMARY KEY, user_id UUID NOT NULL, category_id UUID, goal_id UUID,
 title VARCHAR(255) NOT NULL, description TEXT, status VARCHAR(30) NOT NULL, priority VARCHAR(20) NOT NULL,
 start_at TIMESTAMPTZ, due_at TIMESTAMPTZ, estimated_minutes INTEGER, actual_minutes INTEGER,
 position INTEGER NOT NULL DEFAULT 0, recurrence_rule VARCHAR(255), parent_recurrence_id UUID,
 completed_at TIMESTAMPTZ, deleted_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT fk_tasks_category FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE SET NULL,
 CONSTRAINT fk_tasks_goal FOREIGN KEY(goal_id) REFERENCES goals(id) ON DELETE SET NULL
);
CREATE INDEX idx_tasks_user ON tasks(user_id);
CREATE INDEX idx_tasks_user_status ON tasks(user_id,status);
CREATE INDEX idx_tasks_user_due ON tasks(user_id,due_at);
CREATE INDEX idx_tasks_category ON tasks(category_id);

CREATE TABLE subtasks (
 id UUID PRIMARY KEY, task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
 title VARCHAR(255) NOT NULL, completed BOOLEAN NOT NULL DEFAULT FALSE, position INTEGER NOT NULL DEFAULT 0,
 completed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_subtasks_task ON subtasks(task_id);

CREATE TABLE tags (
 id UUID PRIMARY KEY, user_id UUID NOT NULL, name VARCHAR(60) NOT NULL, color VARCHAR(20) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, UNIQUE(user_id,name)
);
CREATE INDEX idx_tags_user ON tags(user_id);

CREATE TABLE task_tags (
 id UUID PRIMARY KEY, task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
 tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE, UNIQUE(task_id,tag_id)
);
CREATE INDEX idx_task_tags_task ON task_tags(task_id);
CREATE INDEX idx_task_tags_tag ON task_tags(tag_id);

