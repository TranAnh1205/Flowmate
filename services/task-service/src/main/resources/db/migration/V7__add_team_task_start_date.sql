-- Team card scheduling remains independent of personal-task scheduling.
ALTER TABLE team_project_tasks ADD COLUMN start_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE team_project_tasks ADD CONSTRAINT chk_team_task_dates
    CHECK (start_at IS NULL OR due_at IS NULL OR start_at <= due_at);
