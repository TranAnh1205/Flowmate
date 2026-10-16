CREATE TABLE reminders(id UUID PRIMARY KEY,user_id UUID NOT NULL,task_id UUID NOT NULL,scheduled_at TIMESTAMPTZ NOT NULL,type VARCHAR(30) NOT NULL,status VARCHAR(20) NOT NULL,processed_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,UNIQUE(task_id,scheduled_at,type));
CREATE INDEX idx_reminders_due ON reminders(status,scheduled_at);CREATE INDEX idx_reminders_user ON reminders(user_id);
CREATE TABLE notifications(id UUID PRIMARY KEY,user_id UUID NOT NULL,task_id UUID,title VARCHAR(180) NOT NULL,message VARCHAR(1000) NOT NULL,type VARCHAR(30) NOT NULL,read BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMPTZ NOT NULL,read_at TIMESTAMPTZ);
CREATE INDEX idx_notifications_user_created ON notifications(user_id,created_at DESC);CREATE INDEX idx_notifications_unread ON notifications(user_id,read);

