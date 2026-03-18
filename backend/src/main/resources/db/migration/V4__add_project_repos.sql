-- Create project_repos table
CREATE TABLE project_repos (
    id               VARCHAR(36) PRIMARY KEY,
    project_id       VARCHAR(36)   NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    git_url          VARCHAR(1024) NOT NULL,
    branch           VARCHAR(255),
    status           VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    last_analyzed_at TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_project_repos_project_git_url ON project_repos(project_id, git_url);
CREATE INDEX idx_project_repos_project_id ON project_repos(project_id);

-- Add repo_id column to services table
ALTER TABLE services ADD COLUMN repo_id VARCHAR(36) REFERENCES project_repos(id) ON DELETE SET NULL;
CREATE INDEX idx_services_repo_id ON services(repo_id);

-- Migrate existing data: create project_repos from projects.git_url
INSERT INTO project_repos (id, project_id, git_url, branch, status, created_at, updated_at)
SELECT
    gen_random_uuid()::varchar(36),
    id,
    git_url,
    NULL,
    CASE status
        WHEN 'READY' THEN 'READY'
        WHEN 'ERROR' THEN 'ERROR'
        WHEN 'ANALYZING' THEN 'ANALYZING'
        WHEN 'INGESTING' THEN 'INGESTING'
        ELSE 'PENDING'
    END,
    created_at,
    updated_at
FROM projects
WHERE git_url IS NOT NULL;

-- Update services to point to their project's repo
UPDATE services s
SET repo_id = pr.id
FROM project_repos pr
WHERE pr.project_id = s.project_id;
