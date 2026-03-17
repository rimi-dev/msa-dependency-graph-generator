CREATE TABLE analysis_jobs (
    id          VARCHAR(36)  PRIMARY KEY,
    project_id  VARCHAR(36)  REFERENCES projects(id) ON DELETE SET NULL,
    step        VARCHAR(50)  NOT NULL DEFAULT 'CLONING',
    progress    INT          NOT NULL DEFAULT 0,
    message     VARCHAR(500) NOT NULL DEFAULT 'Job created',
    error       TEXT,
    repo_url    VARCHAR(1024),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_analysis_jobs_project_id ON analysis_jobs(project_id);
