-- Add repo_id column to analysis_jobs table
ALTER TABLE analysis_jobs ADD COLUMN repo_id VARCHAR(36) REFERENCES project_repos(id) ON DELETE SET NULL;
CREATE INDEX idx_analysis_jobs_repo_id ON analysis_jobs(repo_id);
