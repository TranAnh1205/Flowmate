ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN status VARCHAR(20),
    ADD COLUMN status_reason VARCHAR(500),
    ADD COLUMN status_changed_at TIMESTAMPTZ,
    ADD COLUMN status_changed_by UUID,
    ADD COLUMN last_login_at TIMESTAMPTZ;

UPDATE users
SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'SUSPENDED' END;

ALTER TABLE users
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'DEACTIVATED')),
    ADD CONSTRAINT fk_users_status_changed_by FOREIGN KEY (status_changed_by) REFERENCES users(id);

ALTER TABLE users DROP COLUMN active;

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_created_at ON users(created_at);

ALTER TABLE refresh_tokens
    ADD COLUMN device_name VARCHAR(160),
    ADD COLUMN ip_address VARCHAR(64),
    ADD COLUMN user_agent VARCHAR(500),
    ADD COLUMN last_used_at TIMESTAMPTZ,
    ADD COLUMN revoke_reason VARCHAR(500);

CREATE TABLE admin_audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    actor_role VARCHAR(20) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID,
    reason VARCHAR(500),
    previous_state TEXT,
    new_state TEXT,
    result VARCHAR(20) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_audit_created_at ON admin_audit_logs(created_at DESC);
CREATE INDEX idx_admin_audit_actor ON admin_audit_logs(actor_user_id);
CREATE INDEX idx_admin_audit_target ON admin_audit_logs(target_id);
CREATE INDEX idx_admin_audit_action ON admin_audit_logs(action);

CREATE TABLE admin_action_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_action_tokens_user ON admin_action_tokens(user_id);
