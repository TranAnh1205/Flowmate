CREATE TABLE team_projects (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_team_project_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE team_project_members (
    project_id UUID NOT NULL REFERENCES team_projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT chk_team_project_role CHECK (role IN ('OWNER', 'MEMBER'))
);
CREATE INDEX idx_team_project_members_user ON team_project_members(user_id);

CREATE TABLE team_project_invitations (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES team_projects(id) ON DELETE CASCADE,
    invited_email VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_by UUID,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_team_project_invitations_project ON team_project_invitations(project_id);
CREATE INDEX idx_team_project_invitations_email ON team_project_invitations(invited_email, expires_at);

CREATE TABLE team_project_tasks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES team_projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL,
    assignee_id UUID,
    due_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_team_task_assignee FOREIGN KEY (project_id, assignee_id)
        REFERENCES team_project_members(project_id, user_id)
);
CREATE INDEX idx_team_project_tasks_project_status ON team_project_tasks(project_id, status);
CREATE INDEX idx_team_project_tasks_project_due ON team_project_tasks(project_id, due_at);
CREATE INDEX idx_team_project_tasks_assignee ON team_project_tasks(assignee_id);
