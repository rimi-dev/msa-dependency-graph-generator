CREATE TABLE users (
    id          VARCHAR(36)  PRIMARY KEY,
    github_id   BIGINT       NOT NULL UNIQUE,
    login       VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(200),
    email       VARCHAR(300),
    avatar_url  VARCHAR(1024),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_github_id ON users(github_id);
CREATE INDEX idx_users_login ON users(login);
