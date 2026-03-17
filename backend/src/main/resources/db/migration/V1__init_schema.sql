-- Initial schema for MSA Dependency Graph Generator

CREATE TABLE IF NOT EXISTS projects (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    git_url     VARCHAR(2048),
    status      VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS services (
    id          VARCHAR(36) PRIMARY KEY,
    project_id  VARCHAR(36)  NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    path        VARCHAR(2048),
    tech_stack  VARCHAR(50)  NOT NULL DEFAULT 'UNKNOWN',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dependencies (
    id                VARCHAR(36) PRIMARY KEY,
    source_service_id VARCHAR(36) NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    target_service_id VARCHAR(36) NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    type              VARCHAR(50) NOT NULL DEFAULT 'HTTP',
    detail            VARCHAR(1024),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_projects_slug ON projects (slug);
CREATE INDEX IF NOT EXISTS idx_projects_status ON projects (status);
CREATE INDEX IF NOT EXISTS idx_services_project_id ON services (project_id);
CREATE INDEX IF NOT EXISTS idx_dependencies_source_id ON dependencies (source_service_id);
CREATE INDEX IF NOT EXISTS idx_dependencies_target_id ON dependencies (target_service_id);
