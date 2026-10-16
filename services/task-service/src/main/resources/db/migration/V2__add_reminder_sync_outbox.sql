ALTER TABLE tasks ADD COLUMN reminder_minutes_before INTEGER;
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_reminder_minutes CHECK (reminder_minutes_before IS NULL OR reminder_minutes_before >= 0);

CREATE TABLE reminder_sync_commands (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    action VARCHAR(10) NOT NULL,
    scheduled_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_reminder_sync_action CHECK (action IN ('UPSERT', 'DELETE')),
    CONSTRAINT chk_reminder_sync_schedule CHECK (action = 'DELETE' OR scheduled_at IS NOT NULL)
);
CREATE INDEX idx_reminder_sync_due ON reminder_sync_commands(next_attempt_at);
